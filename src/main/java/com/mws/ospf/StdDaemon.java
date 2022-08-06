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
    private static MulticastSocket socketHello;
    private static InetSocketAddress socAddrHello;
    private static Timer timerHelloSend;

    //Setup thread for hello multicast server
    private static final Thread threadHelloListen = new Thread(StdDaemon::HelloReceiveThread, "Thread-Hello-Receive");
    //endregion

    //region STATIC METHODS
    /**<p><h1>StdDaemon Main Method</h1></p>
     * <p>Entrypoint into the StdDaemon. Sets up the hello protocol behaviour to allow nodes to start working with
     * implemented methods to handle communication. Init the normal process flow of OSPF.</p>
     */
    static void Main() {
        //region SET HELLOSOCKET
        //used for multicasting. Binds the ospf multicast address to all interfaces using this socket.
        socketHello = null;
        try {
            socAddrHello = new InetSocketAddress(InetAddress.getByName("224.0.0.5"), 25565);
            socketHello = new MulticastSocket(socAddrHello.getPort());
            socketHello.setTimeToLive(1);

            for (RouterInterface rInt : Config.thisNode.interfaceList) {
                socketHello.joinGroup(socAddrHello, rInt.ToNetworkInterface());
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
        if (socketHello == null)
            DaemonErrorHandle("", null);
        //endregion SET HELLOSOCKET

        //region SET RECEIVE HANDLER
        //Create a timer for hello and set it to run instantly. Running the timer schedules further running.
        timerHelloSend = new Timer();
        timerHelloSend.schedule(new TimerTask() {
            @Override
            public void run() {
                SendHelloPacket(null);
            }
        }, 0, 10 * 1000);

        threadHelloListen.start();
        //endregion
    }

    /**<p><h1>Send a Hello Packet</h1></p>
     * <p>Method used to send a hello packet. Uses the method MakeHelloPacket for the packet buffer, using  socketHello
     * multicast socket to send to each RouterInterface.</p>
     * <p>Can be called individually to send a packet. Also is the TimerTask that executes on timerHelloSend tick</p>
     * <p>sendInterface can be specified to limit the hello packet send to a specific interface, for the purpose of
     * forcing an adjacency to form quicker, by sending a more up-to-date hello packet, and hopefully receiving one.</p>
     * @param sendInterface A specific interface to send to. Either null or a RouterInterface
     */
    private static void SendHelloPacket(RouterInterface sendInterface) {

        //Make buffer
        byte[] helloBuffer = MakeHelloPacket();

        //Create a datagram packet to send, send it out all network interfaces.
        try {
            DatagramPacket helloPacket = new DatagramPacket(helloBuffer, helloBuffer.length, socAddrHello);

            //sendInterface is specified, only send hello on the specified interface.
            if (sendInterface != null) {
                if (!sendInterface.isEnabled)
                    DaemonErrorHandle("SendHelloPacket: sendInterface specified is disabled.", null);

                socketHello.setNetworkInterface(sendInterface.ToNetworkInterface());
                socketHello.send(helloPacket);
                return;
            }

            //sendInterface is null, send packet to all enabled interfaces.
            for (RouterInterface rInt: Config.thisNode.interfaceList) {
                if (!rInt.isEnabled)
                    continue;

                socketHello.setNetworkInterface(rInt.ToNetworkInterface());
                socketHello.send(helloPacket);
            }
        } catch (UnknownHostException ex) {
            DaemonErrorHandle("Unknown host when creating datagram packet. Java couldn't resolve the host" +
                    "224.0.0.5 somehow? This shouldn't be possible", ex);
        } catch (IOException ex) {
            DaemonErrorHandle("IOException when sending hello datagram packet", ex);
        }
    }

    /**<p><h1>Hello Packet Receive Method</h1></p>
     * <p>Method implemented as a thread to receive a hello packet from any network joined to the multicast group.
     * Thread is required as the receive method is blocking.</p>
     * <p>Once a packet is received, it is verified, data is scraped from it, and then the data is used in protocol
     * functions and stored in Config.</p>
     */
    private static void HelloReceiveThread() {
        //Buffer for raw data.
        byte[] pBytes = new byte[500];
        DatagramPacket pReturned = new DatagramPacket(pBytes, pBytes.length);

        //Only runs if not interrupted. If interrupted thread will end execution. To cancel, interrupt.
        while (!Thread.currentThread().isInterrupted()) {

            try {
                socketHello.receive(pReturned);
            } catch (IOException ex) {
                DaemonErrorHandle("Exception on CheckHelloBuffer receive", ex);
            }

            //Get value used multiple times, source IP.
            IPAddress pSource = new IPAddressNetwork.IPAddressGenerator().from(pReturned.getAddress());

            //region VERIFY PACKET
            //Check this node was not the source, and interface is enabled.
            if (RouterInterface.GetInterfaceByIP(pSource) != null)
                continue;
            try {
                if (!RouterInterface.GetInterfaceByIPNetwork(pSource).isEnabled)
                    continue;
            } catch (NullPointerException ex) {
              DaemonErrorHandle("HelloReceiveThread: Hello packet received on interface not in RouterInterface." +
                      " This should not happen, as socketHello shouldn't be joined to an interface that has not been" +
                      " created, and sho a hello packet should not have been received.", ex);
            }

            //verify single byte values, saves adv. processing if basic fields are wrong.
            //proto version (2)
            if (pBytes[0] != 0x02)
                continue;
            //message type (hello)
            if (pBytes[1] != 0x01)
                continue;


            //Setup new buffer to be of the specified length.

            /*Get Packet length
            Get bytes from the packet buffer. Starts from 2 inc., to byte 4 exclu. This length is trusted and used
            for the rest of this handle. Don't need to verify if correct, as checksum will do that based on this
            length value.*/
            int pLength = Short.toUnsignedInt(Shorts.fromByteArray(Arrays.copyOfRange(pBytes, 2, 4)));
            byte[] packetBuffer = Arrays.copyOfRange(pBytes, 0, (pLength));

            //Get checksum. Bytes 12, 13
            long pChecksum = Short.toUnsignedLong(Shorts.fromByteArray(Arrays.copyOfRange(pBytes, 12, 14)));

            packetBuffer[12] = packetBuffer[13] = 0;//Unset checksum, so checksum calc will be correct.

            //verify checksums. If wrong, print special message. Important for debugging, inform server of line error.
            if (pChecksum != Launcher.IpChecksum(packetBuffer)) {
                System.err.println("Hello packet checksum mismatch. Got " + pChecksum + ", expected " +
                        Launcher.IpChecksum(packetBuffer));
                continue;
            }
            //endregion VERIFY PACKET
            //int pLength
            //byte[] packetBuffer

            //region GATHER DATA
            //RID in bytes 4,5,6,7 to string. Using IPAddress class to convert bytes to the RID, easy peasy.
            IPAddress pNeighbourRIDAddr = new IPAddressNetwork.IPAddressGenerator().from(
                    Arrays.copyOfRange(packetBuffer, 4, 8)
            );
            IPAddressString neighbourRID = pNeighbourRIDAddr.toAddressString();

            //Known neighbours RIDs.
            List<IPAddressString> reportedKnownRIDs = new ArrayList<>();
            if (pLength > 44) {
                if ((pLength - 44) %4 != 0) {
                    //Not allowed, number of bytes after should be a multiple of 4 bytes.
                    System.err.println("Neighbour Node reported adjacent neighbours incorrectly");
                    continue;
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
            //endregion GATHER DATA

            //region USE DATA

            /*For existing neighbour node in the neighbours table, determine based on state what event has been received.
            On Down, this is Down->Init HelloReceived event. On Init, if the neighbour is in the neighbours table, this
            is a 2-WayReceived event.*/
            NeighbourNode neighbour = NeighbourNode.GetNeighbourNodeByRID(neighbourRID);

            if (neighbour != null) {
                neighbour.knownNeighbours = reportedKnownRIDs;//Update knowledge of neighbour nodes

                //Set state correctly.
                switch (neighbour.GetState()) {
                    case DOWN -> neighbour.SetState(ExternalStates.INIT);//Down state if neighbour expired already.
                    case INIT -> {
                        if (neighbour.knownNeighbours.contains(Config.thisNode.GetRID())) {
                            neighbour.SetState(ExternalStates.EXSTART);

                            /*OSPF event "2-WayReceived", on p2p network, set to ExStart state, and begin exchange
                            process. On Event 2-WayReceived, it isn't specified that OSPF should send a new packet to
                            the neighbour node, but this is intended to force event 2-WayReceived on the neighbour
                            node, so it doesn't have to wait for the hello timer to fire*/
                            SendHelloPacket(neighbour.rIntOwner);
                        }
                    }//case INIT
                }//switch (neighbour.state)

                neighbour.ResetInactiveTimer();
                continue;
            }

            //Neighbour doesn't exist:
            //Convert priority from data, use IP from packet header and RID from packet, create neighbour in table.
            int neighbourPriority = Integer.parseUnsignedInt(pBytes[31] + "");

            neighbour = new NeighbourNode(neighbourRID, neighbourPriority, pSource);
            neighbour.knownNeighbours = reportedKnownRIDs;//Set initial knowledge of neighbour nodes

            System.out.println("Received packet from new neighbour, with valid checksum. Neighbour ID: " +
                    neighbour.GetRID());

            Config.neighboursTable.add(neighbour);
            Config.thisNode.knownNeighbours.add(neighbourRID);

            /*Not in OSPF spec to send a hello packet on Down -> Init state, but allows quicker convergence, not waiting
            //for hello timer to expire*/
            SendHelloPacket(neighbour.rIntOwner);
            //endregion USE DATA
        }
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
        byte[] neighboursBuffer = new byte[Config.neighboursTable.size() * 4];

        int byteOffset = 0;
        for (NeighbourNode neighbour: Config.neighboursTable) {
            byte[] rid = neighbour.GetRIDBytes();
            neighboursBuffer[byteOffset] = rid[0];
            neighboursBuffer[byteOffset + 1] = rid[1];
            neighboursBuffer[byteOffset + 2] = rid[2];
            neighboursBuffer[byteOffset + 3] = rid[3];

            byteOffset += 4;
        }
        ospfBuffer = Bytes.concat(ospfBuffer, neighboursBuffer);

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
        socketHello.close();
        System.exit(-2);
    }
    //endregion
}
