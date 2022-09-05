package com.mws.ospf;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Shorts;
import com.mws.ospf.pdt.ExternalStates;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressNetwork;
import inet.ipaddr.IPAddressString;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.*;

import static com.mws.ospf.StdDaemon.*;

//https://docs.oracle.com/javase/8/docs/technotes/guides/security/crypto/CryptoSpec.html

/**<p><h1>Encryption Daemon</h1></p>
 * <p>Class contains methods that are executed when the application is in encrypted daemon mode. Behaviour of
 * application process flow is controlled by this class</p>
 * <p>This class is the antithesis of StdDaemon</p>
 */
public class EncDaemon{
    //region STATIC PROPERTIES
    //endregion
    private static final Thread threadDHHelloListen = new Thread(EncDaemon::MulticastReceiveThread, "Thread-DHHello-Receive");
    //region STATIC METHODS
    public static void Main() {
        Launcher.PrintToUser("Encrypted Daemon Program Run");

        //Start stat process if conditions set
        if (Stat.endNoAdjacencies != -1)
            Stat.SetupStats();

        SetupHelloSocket();

        //Router Interface negotiations need to be setup for the first time, so a separate public key exists on each
        //interface. Creating new instances of objects sets up these parameters, including public key.
        for (RouterInterface rInt: Config.thisNode.interfaceList) {
            //Skip processing for disabled interfaces
            if (!rInt.isEnabled)
                continue;
            rInt.dhExchange = new DHExchange(rInt);
        }

        //Create a timer for hello and set it to run instantly. Running the timer schedules further running.
        timerHelloSend = new Timer();
        timerHelloSend.schedule(new TimerTask() {
            @Override
            public void run() {
                SendDHPubKey();

                SendHelloPackets();
            }

        }, 0, 10 * 1000);

        threadDHHelloListen.start();
    }

    static void SendHelloPackets() {
        //Skip over neighbours that can't send packets
        if (multicastSocket.isClosed())
            return;

        for (NeighbourNode neighbour: Config.neighboursTable) {
            //Skip neighbours without encryption setup.
            if (neighbour.enParam == null)
                continue;

            //Encrypt the generic hello buffer,
            byte[] encHelloBuffer = neighbour.enParam.Encrypt(MakeHelloPacket());

            try {
                DatagramPacket helloPacket = new DatagramPacket(encHelloBuffer, encHelloBuffer.length, multicastSocketAddr);
                multicastSocket.send(helloPacket);
            } catch (UnknownHostException ex) {
                DaemonErrorHandle("UNLIKELY EXCEPTION: 224.0.0.5 not a valid IP", ex);
            } catch (IOException ex) {
                DaemonErrorHandle("Enc Daemon: IOException when sending hello datagram packet", ex);
            }
        }
    }

    private static void SendDHPubKey() {
        try {
            for(RouterInterface rInt: Config.thisNode.interfaceList) {
                //Skip processing for disabled interfaces
                if (!rInt.isEnabled)
                    continue;

                //Skip processing for interfaces which already have negotiated a secret
                if (rInt.dhExchange.flagComplete)
                    continue;

                //Set up data to be sent
                byte[] dhHelloBuffer = rInt.dhExchange.makeDHPubKey();
                DatagramPacket dhHelloPacket = new DatagramPacket(dhHelloBuffer, dhHelloBuffer.length, multicastSocketAddr);

                //send data to interface
                multicastSocket.setNetworkInterface(rInt.ToNetworkInterface());
                multicastSocket.send(dhHelloPacket);
            }
        } catch (UnknownHostException ex) {
            DaemonErrorHandle("UNLIKELY EXCEPTION: 224.0.0.5 not a valid IP", ex);
        } catch (IOException ex) {
            DaemonErrorHandle("Enc Daemon: IOException when sending DH Hello datagram packet", ex);
        }
    }

    //TODO: How does UDP socket handle fragmentation (packets over MTU of 1500, as public key is likely to be over this
    private static void MulticastReceiveThread() {
        byte[] pBytes =  new byte[4000];
        DatagramPacket pReturned = new DatagramPacket(pBytes, pBytes.length);

        //Setup thread loop, blocked by receive, stops when thread is interrupted.
        while (!Thread.currentThread().isInterrupted()) {
            try  {
                multicastSocket.receive(pReturned);
            } catch (IOException ex) {
                DaemonErrorHandle("IOException on MulticastReceiveThread", ex);
            }

            //region VALIDATE
            //Get value used multiple times, source IP.
            IPAddress pSource = new IPAddressNetwork.IPAddressGenerator().from(pReturned.getAddress());

            //Treat the packet as valid initially, scrape the length from the packet and use it to truncate the buffer
            int pLength = Short.toUnsignedInt(Shorts.fromByteArray(Arrays.copyOfRange(pBytes, 2, 4)));
            byte[] packetBuffer = Arrays.copyOfRange(pReturned.getData(), 0, (pLength));

            //Only accept packets that are the encrypted protocol version (v4 in this artefact).
            if (packetBuffer[0] != 0x04)
                continue;

            //validate the data. If invalid, packetBuffer will be null so skip over current packet (loop).
            packetBuffer = StdDaemon.ValidateOSPFHeader(packetBuffer, pSource);
            if (packetBuffer == null)
                continue;

            //Try to decrypt the data. TryDecryptBytes only decrypts packets not DH PubKey. Reject packet on null return/
            packetBuffer = TryDecryptBytes(packetBuffer, pSource);
            if (packetBuffer == null)
                continue;
            //endregion VALIDATE

            //region SCRAPE NEIGHBOUR
            //RID in bytes 4,5,6,7 to string. Using IPAddress class to convert bytes to the RID, easy peasy.
            IPAddressString neighbourRID = new IPAddressNetwork.IPAddressGenerator().from(
                    Arrays.copyOfRange(packetBuffer, 4, 8)
            ).toAddressString();
            NeighbourNode neighbour = NeighbourNode.GetNeighbourNodeByRID(neighbourRID);
            //endregion SCRAPE NEIGHBOUR

            switch (packetBuffer[1]) {
                //Hello packet
                case 0x01 -> ProcessHelloPacket(neighbour, neighbourRID, packetBuffer);
                //DH PubKey packet
                case 0x06 -> ProcessDHPubKeyPacket(neighbour, neighbourRID, pSource, packetBuffer);
            }
        }
    }

    static void ProcessHelloPacket(NeighbourNode neighbour, IPAddressString neighbourRID, byte[] packetBuffer) {
        int pLength = packetBuffer.length;

        //region SCRAPE KNOWN RIDS
        //Gather known RIDs from the hello packet
        List<IPAddressString> reportedKnownRIDs = new ArrayList<>();
        if (pLength > 44) {
            if ((pLength - 44) %4 != 0) {
                //Not allowed, number of bytes after should be a multiple of 4 bytes. Ignore packet
                System.err.println("Neighbour Node reported adjacent neighbours incorrectly");
                return;
            }

            //Number of 4 byte pairs after the first 44 hello header bytes.
            int knownNeighboursListLength = (pLength - 44) / 4;

            //Loop through each 4 bytes after BDR RID, which are all recently known adjacent RIDs.
            for (int i = 0; i < knownNeighboursListLength; i++) {
                int curByteOffset = 44 + (i*4);
                reportedKnownRIDs.add(new IPAddressNetwork.IPAddressGenerator().from(
                        Arrays.copyOfRange(packetBuffer, curByteOffset, curByteOffset+4)
                ).toAddressString());
            }
        }
        //endregion SCRAPE KNOWN RIDS

        if (neighbour.GetState() == ExternalStates.DOWN) {
            //Treat priority byte as string, parse string -> int
            neighbour.priority = Integer.parseUnsignedInt(packetBuffer[31] + "");

            Config.thisNode.knownNeighbours.add(neighbourRID);

            Launcher.PrintToUser("New Adjacency for node '" + neighbourRID + "'");

            neighbour.SetState(ExternalStates.INIT);

            /*Not in OSPF spec to send a hello packet on Down -> Init state, but allows quicker convergence,
            not waiting for hello timer to expire*/
            SendHelloPackets();
        }

        //Update neighbour parameters for each received hello packet
        neighbour.knownNeighbours = reportedKnownRIDs;
        neighbour.ResetInactiveTimer();

        /*If in init state and this neighbour reports to know of this current node, this is the conditions for the
        2WayReceived event. Trigger it*/
        if (neighbour.GetState() == ExternalStates.INIT && neighbour.knownNeighbours.contains(Config.thisNode.GetRID()))
            TwoWayReceivedEvent(neighbour);
    }

    static void ProcessDHPubKeyPacket(NeighbourNode neighbour, IPAddressString neighbourRID, IPAddress pSource, byte[] packetBuffer) {
        if (neighbour == null) {
            neighbour = new NeighbourNode(neighbourRID, pSource);

            Launcher.PrintToUser("New DH solicitation for reported node '" +neighbour.GetRID() + "'");

            Config.neighboursTable.add(neighbour);

            /*This is a new packet from an unknown neighbour node. send a DH packet immediately with this
            node's public key, so it can generate the secret key.*/
            SendDHPubKey();
        }

        /*The neighbour isn't null now, so this node knows which public key to use. A key was received, so
        this node can calculate the final DH secret.*/
        neighbour.rIntOwner.dhExchange.receiveDHKey(neighbour, packetBuffer);

        EncDaemon.SendHelloPackets();
    }

    /**<p><h1>Encrypted 2WayReceived Event</h1></p>
     * <p>On neighbour state Init, if a node receives a hello packet with its own RID echoed, the event 2WayReceived is
     * fired. This method is the trigger for the exchange protocol to start for the neighbour node.</p>
     * <p>The standard and the encrypted 2WayReceived events must be different due to the encryption steps.</p>
     * @param neighbourNode node that has had the 2WayReceived event trigger
     */
    static void TwoWayReceivedEvent(NeighbourNode neighbourNode) {
        //Quick sanity check TwoWayReceived did occur
        if (neighbourNode.GetState() != ExternalStates.INIT)
            return;

        //Set Correct state for event
        neighbourNode.SetState(ExternalStates.EXSTART);


        //Current future method
        //Statistics Endpoint test
        if ((Config.thisNode.knownNeighbours.size() >= Stat.endNoAdjacencies) && Stat.endNoAdjacencies != -1)
            Stat.EndStats();
    }

    /**<p><h1>Make Hello Packet</h1></p>
     * <p>From a generic byte buffer (treated as unsigned), return a hello packet. The packet includes corrected values
     * based on this node's RID, neighbours RIDs, updating the length and internet checksum.</p>
     * <p></p>
     * <p>This node's rid is derived from Config.thisNode.rid</p>
     * <p>The RIDs of neighbours are derived from Config.neighboursTable. The router IDs from each NeighbourNode.rid</p>
     * @return the completed hello packet in bytes.
     */
    static byte[] MakeHelloPacket() {
        //Generic buffer that will be updated with specific values.
        byte[] ospfBuffer = {
                //GENERIC OSPF HEADER
                0x04,//version
                0x01,//message type
                0x00, 0x2c,//packet length
                (byte) 0xc0, (byte) 0xa8, 0x01, 0x01,//source router rid (dotted decimal)
                0x00, 0x00, 0x00, 0x00,//area id (dotted decimal)
                0x00, 0x00,//checksum
                0x00, 0x00,//Auth type//0x00, 0x01
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,//Auth Data//0x63, 0x69, 0x73, 0x63, 0x6f, 0x00, 0x00, 0x00//"Cisco"

                //OSPF HELLO PACKET HEADER
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,//Network Mask, p2p networks equal to 0.0.0.0.
                0x00, 0x0a,//hello interval//10
                0x00,//options
                0x01,//router priority
                0x00, 0x00, 0x00, 0x28,//router dead interval//40
                0x00, 0x00, 0x00, 0x00,//DR
                0x00, 0x00, 0x00, 0x00,//BDR
        };

        //Update router ID (4, 5, 6, 7)
        try {
            byte[] rid = Config.thisNode.GetRIDBytes();
            ospfBuffer[4] = rid[0];
            ospfBuffer[5] = rid[1];
            ospfBuffer[6] = rid[2];
            ospfBuffer[7] = rid[3];
        } catch (Exception ex)  {
            DaemonErrorHandle("Error when creating an OSPF packet: Substituting in router ID.", ex);
        }

        //Update Network Mask? (24, 25, 26, 27)
        //not for this experiment

        //Append neighbours
        for (NeighbourNode neighbour: Config.neighboursTable) {
            //Skip over non-adjacent nodes
            if (neighbour.GetState() == ExternalStates.DOWN)
                continue;

            ospfBuffer = Bytes.concat(ospfBuffer, neighbour.GetRIDBytes());
        }


        //Update packet length (2, 3)
        int packetLength = ospfBuffer.length;
        ByteBuffer lenBuffer = ByteBuffer.allocate(4);
        lenBuffer.putInt(packetLength);
        ospfBuffer[2] = lenBuffer.array()[2];
        ospfBuffer[3] = lenBuffer.array()[3];


        //Update Checksum (12, 13)
        ByteBuffer chkBuffer = ByteBuffer.allocate(8);
        chkBuffer.putLong(Launcher.IpChecksum(ospfBuffer));
        ospfBuffer[12] = chkBuffer.array()[6];
        ospfBuffer[13] = chkBuffer.array()[7];

        return ospfBuffer;
    }

    /**<p><h1>Try to Decrypt Packet Buffer</h1></p>
     * <p>Method attempts decrypting a provided packet buffer, assuming data starts after the known OSPF header. The
     * method attempts to validate and retrieve factors required for decrypting, including a NeighbourNode</p>
     * <p>The method will return either the starting buffer for a DH PubKey packet, null </p>
     * @param data the packet buffer to operate on. Requires the standard OSPF header structure
     * @param pSource the packet source IP address
     * @return the full decrypted packet or null for invalid parameters or conditions
     */
    private static byte[] TryDecryptBytes(byte[] data, IPAddress pSource) {

        //Skip data already in plaintext (DH PubKey packet)
        if (data[1] == 0x06)
            return data;

        //Get neighbour
        IPAddressString neighbourRID = new IPAddressNetwork.IPAddressGenerator().from(
                Arrays.copyOfRange(data, 4, 8)
        ).toAddressString();
        NeighbourNode neighbour = NeighbourNode.GetNeighbourNodeByRID(neighbourRID);

        //If neighbour doesn't exist, and the packet was not a DH PubKey, then reject the packet returning null.
        if (neighbour == null)
            return null;

        //Sanity check, verify neighbour was received on the correct network.
        if (!neighbour.rIntOwner.equals(RouterInterface.GetInterfaceByIPNetwork(pSource)))
            return null;

        //Reject the packet with null if no encryption parameters have been set up. Can't encrypt or decrypt.
        if (neighbour.enParam == null)
            return null;

        //Finally, perform the decrypt on a packet that should be valid.
        return neighbour.enParam.Decrypt(data);
    }
    //endregion
}
