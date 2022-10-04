package com.mws.ospf;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Shorts;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressNetwork;
import inet.ipaddr.IPAddressString;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.UnknownHostException;
import java.util.*;

import static com.mws.ospf.StdDaemon.*;

//https://docs.oracle.com/javase/8/docs/technotes/guides/security/crypto/CryptoSpec.html

/**<p><h1>Encryption Daemon</h1></p>
 * <p>Class contains methods that are executed when the application is in encrypted daemon mode. Behaviour of
 * application process flow is controlled by this class. It uses several methods and properties from the StdDaemon,
 * while providing opposite functionality.</p>
 */
public class EncDaemon {
    //region STATIC PROPERTIES
    static final Thread threadEncMulticastListen = new Thread(EncDaemon::receiveMulticastThread,
            "Thread-DHHello-Receive");
    //endregion

    //region STATIC METHODS
    /**<p><h1>EncDaemon Main Method</h1></p>
     * <p>Entrypoint into the EncDaemon. Sets up the hello protocol behaviour to allow nodes to start working with
     * implemented methods to handle communication. Init the encrypted process flow of new OSPF.</p>
     * <p>Utilises the same socket as the StdDaemon.</p>
     */
    static void main() {
        Launcher.printToUser("Encrypted Daemon Program Run");

        //Start stat process if conditions set
        if (Stat.endNoAdjacencies != -1)
            Stat.setupStats();

        setupMulticastSocket();

        //Router Interface negotiations need to be setup for the first time, so a separate public key exists on each
        //interface. Creating new instances of objects sets up these parameters, including public key.
        for (RouterInterface rInt: Config.thisNode.interfaceList) {
            //Skip processing for disabled interfaces
            if (!rInt.isEnabled)
                continue;
            rInt.dhExchange = new DHExchange(rInt);
        }

        //Start listening for hello packets before sending them. Should force that packets are not received before
        threadEncMulticastListen.start();

        //Create a timer for hello and set it to run instantly. Running the timer schedules further running.
        timerHelloSend = new Timer();
        timerHelloSend.schedule(new TimerTask() {
            @Override
            public void run() {
                sendHelloPackets();
                sendDHPubKey();
            }

        }, 0, 10 * 1000);
    }

    /**<p><h1>Send Hello Packets</h1></p>
     * <p>Method used to send hello packets. Uses the method makeHelloPacket for the packet buffer, using
     * multicastSocket to send the packet to each neighbour. It is part of the timer task for timerHelloSend</p>
     * <p>This method differs significantly from the version in StdDaemon, firstly by sending only to neighbours. A
     * neighbour is required for the EncryptionParameters. Also, the version of makeHelloPacket requires the neighbour,
     * and encrypts the data before sending it.</p>
     */
    static void sendHelloPackets() {
        //Prevent sending if multicast socket was not setup prior. More of a sanity check.
        if (multicastSocket.isClosed())
            return;


        for (NeighbourNode neighbour: Config.neighboursTable) {
            //Skip neighbours without encryption setup.
            if (neighbour.enParam == null)
                continue;

            //Create an encrypted hello buffer, which already cotnains the encrypted data, correct header checksum and length
            byte[] encHelloBuffer = makeHelloPacket(neighbour);

            try {
                //Construct packet, set output interface to the current neighbour's interface, send packet on interface.
                DatagramPacket helloPacket = new DatagramPacket(encHelloBuffer, encHelloBuffer.length, multicastSocketAddr);
                multicastSocket.setNetworkInterface(neighbour.rIntOwner.toNetworkInterface());
                multicastSocket.send(helloPacket);

            } catch (UnknownHostException ex) {
                handleDaemonError("UNLIKELY EXCEPTION: 224.0.0.5 not a valid IP", ex);
            } catch (IOException ex) {
                handleDaemonError("Enc Daemon: IOException when sending hello datagram packet", ex);
            }
        }
    }

    /**<p><h1>Send Diffie-Hellman PubKey Packets</h1></p>
     * <p>Method used to send Diffie-Hellman PubKey packets, new for the encrypted daemon. Packets are sent to the
     * multicastSocket, and sent to each RouterInterface, similar to standard OSPF hello. It is part of the timer task
     * for timerHelloSend</p>
     * <p>Interfaces will only be sent packets if the interface is enabled, and if a DH keypair process has not already
     * been created for the specific interface, as part of a packet limiting function for p2p links. Expansion of the
     * DHExchange class to support MA links in the future would require modification of this feature.</p>
     * <p>The method is heavy reliant on the DHExchange class for each router interface to create a buffer, containing
     * the correct public key.</p>
     * <p>This </p>
     */
    private static void sendDHPubKey() {
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
                multicastSocket.setNetworkInterface(rInt.toNetworkInterface());
                multicastSocket.send(dhHelloPacket);
            }
        } catch (UnknownHostException ex) {
            handleDaemonError("UNLIKELY EXCEPTION: 224.0.0.5 not a valid IP", ex);
        } catch (IOException ex) {
            handleDaemonError("Enc Daemon: IOException when sending DH Hello datagram packet", ex);
        }
    }

    /**<p><h1>Encrypted Multicast Receive Handle Method</h1></p>
     * <p>Method implemented as a thread to receive an packet from any network joined to the multicast group. A thread
     * is required as the receive method is blocking.</p>
     * <p>Once a packet is received, it is verified, data is scraped and it is passed onto specific handle methods, for
     * use in protocol functions and features.</p>
     * <p>This variant differs from the standard OSPF version by trying to incorporate decryption, as well as
     * addition of encrypted ospf (OSPFv4) specific packet (type 6)</p>
     */
    private static void receiveMulticastThread() {
        byte[] pBytes =  new byte[4000];
        DatagramPacket pReturned = new DatagramPacket(pBytes, pBytes.length);

        //Setup thread loop, blocked by receive, stops when thread is interrupted.
        while (!Thread.currentThread().isInterrupted()) {
            try  {
                multicastSocket.receive(pReturned);
            } catch (IOException ex) {
                handleDaemonError("IOException on MulticastReceiveThread", ex);
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
            packetBuffer = StdDaemon.validateOSPFHeader(packetBuffer, pSource);
            if (packetBuffer == null)
                continue;

            //Try to decrypt the data. TryDecryptBytes only decrypts packets not DH PubKey. Reject packet on null return/
            packetBuffer = tryDecryptBytes(packetBuffer, pSource);
            if (packetBuffer == null)
                continue;
            //endregion VALIDATE


            //region SCRAPE NEIGHBOUR
            //RID in bytes 4,5,6,7 to string. Using IPAddress class to convert bytes to the RID, easy peasy.
            IPAddressString neighbourRID = new IPAddressNetwork.IPAddressGenerator().from(
                    Arrays.copyOfRange(packetBuffer, 4, 8)
            ).toAddressString();
            NeighbourNode neighbour = NeighbourNode.getNeighbourNodeByRID(neighbourRID);
            //endregion


            switch (packetBuffer[1]) {
                //Hello packet
                case 0x01 -> {
                    if (neighbour != null)
                        processHelloPacket(neighbour, neighbourRID, packetBuffer);
                }
                //DH PubKey packet
                case 0x06 -> processDHPubKeyPacket(neighbour, neighbourRID, pSource, packetBuffer);
            }
        }
    }

    /**<p><h1>EncDaemon Process Hello Packet</h1></p>
     * <p>Processes a validated hello packet received on the multicast socket. The method scrapes the known neighbours
     * list, and manipulates the neighbour on this node in the configuration and neighbours table.</p>
     * <p>This method differs from the StdDaemon as for encryption, neighbours have to exist before, and so never
     * start off as null. Also, the 2WayReceived method called is different for encryption.</p>
     * @param neighbour scraped neighbour to manipulate, from NeighbourNode.getNeighbourNodeByRID
     * @param neighbourRID scraped neighbour RID from buffer
     * @param packetBuffer raw, but manipulated and validated  packet buffer
     */
    private static void processHelloPacket(@NotNull NeighbourNode neighbour, @NotNull IPAddressString neighbourRID,
                                   byte @NotNull [] packetBuffer) {
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

        if (neighbour.getState() == ExternalStates.DOWN) {
            //Treat priority byte as string, parse string -> int
            neighbour.priority = Integer.parseUnsignedInt(packetBuffer[31] + "");

            Config.thisNode.knownNeighbours.add(neighbourRID);

            Launcher.printToUser("New Adjacency for node '" + neighbourRID + "'");

            neighbour.setState(ExternalStates.INIT);

            /*Not in OSPF spec to send a hello packet on Down -> Init state, but allows quicker convergence,
            not waiting for hello timer to expire*/
            sendHelloPackets();
        }

        //Update neighbour parameters for each received hello packet
        neighbour.knownNeighbours = reportedKnownRIDs;
        neighbour.resetInactiveTimer();

        /*If in init state and this neighbour reports to know of this current node, this is the conditions for the
        2WayReceived event. Trigger it*/
        if (neighbour.getState() == ExternalStates.INIT && neighbour.knownNeighbours.contains(Config.thisNode.getRID()))
            twoWayReceivedEvent(neighbour);
    }

    /**<p><h1>Process Diffie-Hellman PubKey Packet</h1></p>
     * <p>Processes a validated DH PubKey packet received on the multicast socket. The method creates a neighbour if
     * it was not yet created, and receives the reported public key. This is used to complete the DHExchange for the
     * RouterInterface, which will itself create the secrets used in ciphers.</p>
     * @param neighbour scraped neighbour to manipulate, from NeighbourNode.getNeighbourNodeByRID or null
     * @param neighbourRID scraped neighbour RID from buffer
     * @param pSource ip address of packet source, used to create a new neighbour node and link RouterInterface
     * @param packetBuffer raw, but manipulated and validated  packet buffer
     */
    private static void processDHPubKeyPacket(NeighbourNode neighbour, @NotNull IPAddressString neighbourRID,
                                      @NotNull IPAddress pSource, byte @NotNull [] packetBuffer) {
        /*Check first that there already isn't a key being processed for the interface. If there is, processing the
        packet will be ineffective, making the key a second time, and sending extra hello packets*/
        RouterInterface rInt = RouterInterface.getInterfaceByIPNetwork(pSource);
        if (rInt == null)
            return;
        if (rInt.dhExchange.flagProcessingKey)
            return;

        if (neighbour == null) {
            neighbour = new NeighbourNode(neighbourRID, pSource);


            Config.neighboursTable.add(neighbour);

            /*This is a new packet from an unknown neighbour node. send a DH packet immediately with this
            node's public key, so it can guarantee to generate the secret key this cycle.
            Nodes are likely to send and receive two copies. For normal hello that's ok, because the second packet
            contains the neighbour in the sent neighbours list

            For experiment purposes, the wait function makes it likely only one packet is needed, so if wait is being
            used, don't send guarantee sync with extra packets*/
            if (!(Launcher.flagWait || Launcher.flagStart)) {
                sendDHPubKey();
            }
        }

        Launcher.printToUser("New DH solicitation for reported node '" + neighbour.getRID() + "'");

        /*The neighbour isn't null now, so this node knows which public key to use. A key was received, so
        this node can calculate the final DH secret.*/
        neighbour.rIntOwner.dhExchange.receiveDHKey(neighbour, packetBuffer);

        //Not in OSPF spec to send an extra packet on receiving one from a down neighbour.
        if (neighbour.getState() == ExternalStates.DOWN)
            EncDaemon.sendHelloPackets();
    }

    /**<p><h1>Encrypted 2WayReceived Event</h1></p>
     * <p>On neighbour state Init, if a node receives a hello packet with its own RID echoed, the event 2WayReceived is
     * fired. This method is the trigger for the exchange protocol to start for the neighbour node.</p>
     * <p>The standard and the encrypted 2WayReceived events must be different due to the encryption steps.</p>
     * @param neighbourNode node that has had the 2WayReceived event trigger
     */
    private static void twoWayReceivedEvent(NeighbourNode neighbourNode) {
        //Quick sanity check TwoWayReceived did occur
        if (neighbourNode.getState() != ExternalStates.INIT)
            return;

        //Set Correct state for event
        neighbourNode.setState(ExternalStates.EXSTART);

        //Prevent the DHExchange from sending DHPubKey packets on the rint (ASSUMES INTERFACE IS P2P)
        neighbourNode.rIntOwner.dhExchange.flagComplete = true;

        //Current future method
        //Statistics Endpoint test
        if ((Config.thisNode.knownNeighbours.size() >= Stat.endNoAdjacencies) && Stat.endNoAdjacencies != -1)
            Stat.endStats();
    }

    /**<p><h1>Make Hello Packet</h1></p>
     * <p>From a generic byte buffer (treated as unsigned), return a hello packet. The packet includes corrected values
     * based on this node's RID, neighbours RIDs, updating the length and internet checksum.</p>
     * <p>This node's rid is derived from Config.thisNode.rid, the RIDs of neighbours are derived from
     * Config.neighboursTable. The router IDs from each NeighbourNode.rid</p>
     * <p>This version is specific for encryption, and encrypts the data payload past the header.</p>
     * @return the completed hello packet in bytes.
     * @param neighbour the neighbour node being communicated with, required for the encryption
     */
    private static byte[] makeHelloPacket(NeighbourNode neighbour) {
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
        };

        byte[] dataBuffer = {
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
            byte[] rid = Config.thisNode.getRIDBytes();
            ospfBuffer[4] = rid[0];
            ospfBuffer[5] = rid[1];
            ospfBuffer[6] = rid[2];
            ospfBuffer[7] = rid[3];
        } catch (Exception ex)  {
            handleDaemonError("Error when creating an OSPF packet: Substituting in router ID.", ex);
        }

        //Update Network Mask? (24, 25, 26, 27)
        //not for this experiment

        //Append neighbours to the data buffer
        for (NeighbourNode n: Config.neighboursTable) {
            //Skip over non-adjacent nodes
            if (n.getState() == ExternalStates.DOWN)
                continue;

            dataBuffer = Bytes.concat(dataBuffer, n.getRIDBytes());
        }

        //Encrypt the buffer of known neighbours and append it to the data portion of the packet.
        dataBuffer = neighbour.enParam.encrypt(dataBuffer);
        ospfBuffer = Bytes.concat(ospfBuffer, dataBuffer);

        //Update packet length (2, 3), Update Checksum (12, 13)
        return updateChecksumAndLength(ospfBuffer);
    }

    /**<p><h1>Try to Decrypt Packet Buffer</h1></p>
     * <p>Method attempts decrypting a provided packet buffer, assuming data starts after the known OSPF header. The
     * method attempts to validate and retrieve factors required for decrypting, including a NeighbourNode</p>
     * <p>The method will return either the starting buffer for a DH PubKey packet, null </p>
     * @param data the packet buffer to operate on. Requires the standard OSPF header structure
     * @param pSource the packet source IP address
     * @return the full decrypted packet or null for invalid parameters or conditions
     */
    private static byte[] tryDecryptBytes(byte[] data, IPAddress pSource) {

        //Skip data already in plaintext (DH PubKey packet)
        if (data[1] == 0x06)
            return data;

        //Get neighbour
        IPAddressString neighbourRID = new IPAddressNetwork.IPAddressGenerator().from(
                Arrays.copyOfRange(data, 4, 8)
        ).toAddressString();
        NeighbourNode neighbour = NeighbourNode.getNeighbourNodeByRID(neighbourRID);

        //If neighbour doesn't exist, and the packet was not a DH PubKey, then reject the packet returning null.
        if (neighbour == null)
            return null;

        //Sanity check, verify neighbour was received on the correct network.
        if (!neighbour.rIntOwner.equals(RouterInterface.getInterfaceByIPNetwork(pSource)))
            return null;

        //Reject the packet with null if no encryption parameters have been set up. Can't encrypt or decrypt.
        if (neighbour.enParam == null)
            return null;

        //Finally, perform the decrypt on a packet that should be valid.
        return neighbour.enParam.decrypt(data);
    }
    //endregion
}
