package com.mws.ospf;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Shorts;
import com.mws.ospf.pdt.ExternalStates;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressNetwork;
import inet.ipaddr.IPAddressString;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

/**<p><h1>Standard Daemon</h1></p>
 * <p>The standard OSPF daemon process. Forms and manages standard OSPF process flow, following the OSPFv2 RFC.
 * Methods provided can be used in the encrypted daemon, such as MakeHelloPacket and DaemonErrorHandle.</p>
 * <p></p>
 * <p>Very code heavy class.</p>
 */
public class StdDaemon {
    //region STATIC PROPERTIES
    static MulticastSocket multicastSocket;
    static InetSocketAddress multicastSocketAddr;
    static Timer timerHelloSend;

    //Setup thread for hello multicast server
    private static final Thread threadHelloListen = new Thread(StdDaemon::MulticastReceiveThread, "Thread-Hello-Receive");
    //endregion

    //region STATIC METHODS
    /**<p><h1>StdDaemon Main Method</h1></p>
     * <p>Entrypoint into the StdDaemon. Sets up the hello protocol behaviour to allow nodes to start working with
     * implemented methods to handle communication. Init the normal process flow of OSPF.</p>
     */
    static void Main() {
        Launcher.PrintToUser("Standard Daemon Program Run");

        //Start stat process if conditions set
        if (Stat.endNoAdjacencies != -1)
            Stat.SetupStats();

        SetupHelloSocket();

        //Create a timer for hello and set it to run instantly. Running the timer schedules further running.
        timerHelloSend = new Timer();
        timerHelloSend.schedule(new TimerTask() {
            @Override
            public void run() {
                SendHelloPackets();
            }
        }, 0, 10 * 1000);

        threadHelloListen.start();
    }

    static void SetupHelloSocket() {
        //used for multicasting. Binds the ospf multicast address to all interfaces using this socket.
        multicastSocket = null;
        try {
            multicastSocketAddr = new InetSocketAddress(InetAddress.getByName("224.0.0.5"), 25565);
            multicastSocket = new MulticastSocket(multicastSocketAddr.getPort());
            multicastSocket.setTimeToLive(1);

            for (RouterInterface rInt : Config.thisNode.interfaceList) {
                multicastSocket.joinGroup(multicastSocketAddr, rInt.ToNetworkInterface());
            }
        } catch (UnknownHostException ex) {
            //InetAddress.getByName()
            DaemonErrorHandle("Exception in setting up udp multicast: could not get ip address 224.0.0.5" + ex.getMessage(), ex);
        } catch (SocketException ex) {
            //rInt.toNetworkInterface()
            DaemonErrorHandle("Exception in setting up udp multicast: Interface.toNetworkInterface(): 'Throws SocketException if IOException is thrown'" + ex.getMessage(), ex);
        } catch (IOException ex) {
            //new MulticastSocket()
            DaemonErrorHandle("Exception in setting up udp multicast: IOException" + ex.getMessage(), ex);
        } catch (Exception ex) {
            //Uncaught exception
            DaemonErrorHandle("Generic exception in setting up udp multicast" + ex.getMessage(), ex);
        }

        //If helloSocket was not correctly set and no exception was gotten, hard exit
        if (multicastSocket == null)
            DaemonErrorHandle("", null);
    }

    /**<p><h1>Send a Hello Packet</h1></p>
     * <p>Method used to send a hello packet. Uses the method MakeHelloPacket for the packet buffer, using  socketHello
     * multicast socket to send to each RouterInterface.</p>
     * <p>Can be called individually to send a packet. Also is the TimerTask that executes on timerHelloSend tick</p>
     */
    static void SendHelloPackets() {

        //Create a datagram packet to send, send it out all network interfaces.
        try {
            //Make buffer and datagram packet to send
            byte[] helloBuffer = MakeHelloPacket();
            DatagramPacket helloPacket = new DatagramPacket(helloBuffer, helloBuffer.length, multicastSocketAddr);

            //send packet to all enabled interfaces.
            for (RouterInterface rInt: Config.thisNode.interfaceList) {
                if (!rInt.isEnabled)
                    continue;

                multicastSocket.setNetworkInterface(rInt.ToNetworkInterface());
                multicastSocket.send(helloPacket);
            }
        } catch (UnknownHostException ex) {
            DaemonErrorHandle("Std Daemon: Unknown host when creating datagram packet. Java couldn't resolve" +
                    "the host 224.0.0.5 somehow? This shouldn't be possible", ex);
        } catch (IOException ex) {
            DaemonErrorHandle("Std Daemon: IOException when sending hello datagram packet", ex);
        }
    }

    /**<p><h1>Hello Packet Receive Method</h1></p>
     * <p>Method implemented as a thread to receive a hello packet from any network joined to the multicast group.
     * Thread is required as the receive method is blocking.</p>
     * <p>Once a packet is received, it is verified, data is scraped from it, and then the data is used in protocol
     * functions and stored in Config.</p>
     */
    private static void MulticastReceiveThread() {
        //Buffer for raw data.
        byte[] pBytes = new byte[4000];
        DatagramPacket pReturned = new DatagramPacket(pBytes, pBytes.length);

        //Only runs if not interrupted. If interrupted thread will end execution. To cancel, interrupt.
        while (!Thread.currentThread().isInterrupted()) {

            try {
                multicastSocket.receive(pReturned);
            } catch (IOException ex) {
                DaemonErrorHandle("Exception on HelloReceiveThread", ex);
            }

            //Get value used multiple times, source IP.
            IPAddress pSource = new IPAddressNetwork.IPAddressGenerator().from(pReturned.getAddress());

            //Treat the packet as valid initially, scrape the length from the packet and use it to truncate the buffer
            int pLength = Short.toUnsignedInt(Shorts.fromByteArray(Arrays.copyOfRange(pBytes, 2, 4)));
            byte[] packetBuffer = Arrays.copyOfRange(pReturned.getData(), 0, pLength);

            //Only accept packets that are the normal (unencrypted) protocol version (ospfv2)
            if (pBytes[0] != 0x02)
                continue;

            //validate the data. If invalid, packetBuffer will be null so skip over current packet (loop).
            packetBuffer = StdDaemon.ValidateOSPFHeader(packetBuffer, pSource);
            if (packetBuffer == null)
                continue;

            //region SCRAPE NEIGHBOUR
            //RID in bytes 4,5,6,7 to string. Using IPAddress class to convert bytes to the RID, easy peasy.
            IPAddressString neighbourRID = new IPAddressNetwork.IPAddressGenerator().from(
                    Arrays.copyOfRange(packetBuffer, 4, 8)
            ).toAddressString();
            NeighbourNode neighbour = NeighbourNode.GetNeighbourNodeByRID(neighbourRID);
            //endregion SCRAPE NEIGHBOUR

            //Branch, pass to packet processor for each specific packet type.
            switch (packetBuffer[1]) {
                //Hello Packet
                case 0x01 -> ProcessHelloPacket(neighbour, neighbourRID, pSource, packetBuffer);
            }

        }
    }

    static byte[] ValidateOSPFHeader(byte[] packetBuffer, IPAddress pSource) {
        //region VERIFY PACKET
        //Check this node was not the source, if so reject the packet by null
        if (RouterInterface.GetInterfaceByIP(pSource) != null)
            return null;

        RouterInterface receiveInt = RouterInterface.GetInterfaceByIPNetwork(pSource);
        if (receiveInt == null) {
            DaemonErrorHandle("UNLIKELY EXCEPTION: Packet received from interface this node is not in", null);
            return null;
        }

        //Reject packets received on interfaces that are not enabled.
        if (!receiveInt.isEnabled)
            return null;

        //Get checksum. Bytes 12, 13
        long pChecksum = Short.toUnsignedLong(Shorts.fromByteArray(Arrays.copyOfRange(packetBuffer, 12, 14)));

        packetBuffer[12] = packetBuffer[13] = 0;//Unset checksum, so checksum calc will be correct.

        //verify checksums. If wrong, print special message. Important for debugging, inform server of line error.
        if (pChecksum != Launcher.IpChecksum(packetBuffer)) {
            System.err.println("Packet checksum mismatch. Got " + pChecksum + ", expected " +
                    Launcher.IpChecksum(packetBuffer));
            return null;
        }
        return packetBuffer;
    }

    static void ProcessHelloPacket(NeighbourNode neighbour, IPAddressString neighbourRID, IPAddress pSource,
                                   byte[] packetBuffer) {
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

        //Add new neighbour
        if (neighbour == null) {
            neighbour = new NeighbourNode(neighbourRID, pSource);
            Config.neighboursTable.add(neighbour);
        }

        //Update neighbour parameters only for state change Down -> Init.
        if (neighbour.GetState() == ExternalStates.DOWN) {
            //Treat priority byte as string, parse string -> int
            neighbour.priority = Integer.parseUnsignedInt(packetBuffer[31] + "");

            Config.thisNode.knownNeighbours.add(neighbourRID);

            Launcher.PrintToUser("New Adjacency for node '" + neighbourRID + "'");

            neighbour.SetState(ExternalStates.INIT);

            /*Not in OSPF spec to send a hello packet on Down -> Init state, but allows quicker convergence, not
            waiting for hello timer to expire*/
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

    /**<p><h1>Standard 2WayReceived Event</h1></p>
     * <p>On neighbour state Init, if a node receives a hello packet with its own RID echoed, the event 2WayReceived is
     * fired. This method is the trigger for the exchange protocol to start for the neighbour node.</p>
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
                0x02,//version
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

    /**<p><h1>Handle Daemon Exception</h1></p>
     * <p>Handles exceptions in this daemon process. Supply a message to print, to stdout, and pass an exception to help
     * with debugging. Also clean exits the application</p>
     * @param message A message to print to the user
     * @param ex an exception passed from an exception handler. Can be null.
     */
    static void DaemonErrorHandle(String message, Exception ex) {
        //Message handle
        if (ex != null) {
            System.out.println("Exception in daemon process: " + message + ": \nStack trace follows:\n");
            ex.printStackTrace();
        } else {
            System.out.println("Exception in daemon process, no exception: " + message);
        }

        //Clean exit
        timerHelloSend.cancel();
        threadHelloListen.interrupt();
        multicastSocket.close();
        System.exit(-3);
    }
    //endregion
}
