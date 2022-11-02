package com.mws.ospf;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Shorts;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressNetwork;
import inet.ipaddr.IPAddressString;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

import static com.mws.ospf.Launcher.operationMode;

/**<p><h1>Standard Daemon</h1></p>
 * <p>The standard OSPF daemon process. Forms and manages standard OSPF process flow, following the OSPFv2 RFC.
 * Methods provided can be used in the encrypted daemon, such as MakeHelloPacket and DaemonErrorHandle.</p>
 * <p></p>
 * <p>Very code heavy class.</p>
 */
class StdDaemon {
    //region STATIC PROPERTIES
    static final int MTU = 1300;
    static MulticastSocket multicastSocket;
    static InetSocketAddress multicastSocketAddr;
    static Timer timerHelloSend;
    private static final Thread threadStdMulticastListen = new Thread(StdDaemon::receiveMulticastThread, "Thread-Hello-Receive");
    static final int HEADER_LENGTH = 24;
    //endregion

    //region STATIC METHODS
    /**<p><h1>StdDaemon Main Method</h1></p>
     * <p>Entrypoint into the StdDaemon. Sets up the hello protocol behaviour to allow nodes to start working with
     * implemented methods to handle communication. Init the normal process flow of OSPF.</p>
     */
    static void main() {
        Launcher.printToUser("Standard Daemon Program Run");

        //Start stat process if conditions set
        if (Stat.endNoAdjacencies != -1)
            Stat.setupStats();

        Config.lsdb = new LSDB();
        setupMulticastSocket();

        //Start listening for hello packets before sending them. Should force that packets are not received before
        threadStdMulticastListen.start();

        //Create a timer for hello and set it to run instantly. Running the timer schedules further running.
        timerHelloSend = new Timer();
        timerHelloSend.schedule(new TimerTask() {
            @Override
            public void run() {
                sendHelloPackets();
            }
        }, 0, 10 * 1000);
    }

    /**<p><h1>Setup Multicast Socket</h1></p>
     * <p>Sets up the static properties around the multicast socket. Sets the socket address, socket, sets TTL, and
     * joints all interfaces to the multicast group.</p>
     * <p>Handles its own exceptions.</p>
     */
    static void setupMulticastSocket() {
        //used for multicasting. Binds the ospf multicast address to all interfaces using this socket.
        multicastSocket = null;
        try {
            multicastSocketAddr = new InetSocketAddress(InetAddress.getByName("224.0.0.5"), 25565);
            multicastSocket = new MulticastSocket(multicastSocketAddr.getPort());
            multicastSocket.setTimeToLive(1);

            for (RouterInterface rInt : Config.thisNode.interfaceList) {
                multicastSocket.joinGroup(multicastSocketAddr, rInt.toNetworkInterface());
            }
        } catch (UnknownHostException ex) {
            //InetAddress.getByName()
            handleDaemonError("Exception in setting up udp multicast: could not get ip address 224.0.0.5" + ex.getMessage(), ex);
        } catch (SocketException ex) {
            //rInt.toNetworkInterface()
            handleDaemonError("Exception in setting up udp multicast: Interface.toNetworkInterface(): 'Throws SocketException if IOException is thrown'" + ex.getMessage(), ex);
        } catch (IOException ex) {
            //new MulticastSocket()
            handleDaemonError("Exception in setting up udp multicast: IOException" + ex.getMessage(), ex);
        } catch (Exception ex) {
            //Uncaught exception
            handleDaemonError("Generic exception in setting up udp multicast" + ex.getMessage(), ex);
        }

        //If helloSocket was not correctly set and no exception was gotten, hard exit
        if (multicastSocket == null)
            handleDaemonError("", null);
    }

    /**<p><h1>Send Hello Packets</h1></p>
     * <p>Method used to send hello packets. Uses the method makeHelloPacket for the packet buffer, using
     * multicastSocket to send to each RouterInterface. This is the timer task for timerHelloSend</p>
     */
    static void sendHelloPackets() {

        //Create a datagram packet to send, send it out all network interfaces.
        try {
            //Make buffer and datagram packet to send
            byte[] helloBuffer = makeHelloPacket();
            DatagramPacket helloPacket = new DatagramPacket(helloBuffer, helloBuffer.length, multicastSocketAddr);

            //send packet to all enabled interfaces.
            for (RouterInterface rInt: Config.thisNode.interfaceList) {
                if (!rInt.isEnabled)
                    continue;

                multicastSocket.setNetworkInterface(rInt.toNetworkInterface());
                multicastSocket.send(helloPacket);
            }
        } catch (UnknownHostException ex) {
            handleDaemonError("Std Daemon: Unknown host when creating datagram packet. Java couldn't resolve" +
                    "the host 224.0.0.5 somehow? This shouldn't be possible", ex);
        } catch (IOException ex) {
            handleDaemonError("Std Daemon: IOException when sending hello datagram packet", ex);
        }
    }

    /**<p><h1>Send Packet to Neighbour</h1></p>
     * <p>After the hello protocol, send a provided datagram packet to a specified neighbour over the multicast
     * interface. The sent packet will be directional. The full buffer should be passed to this method.</p>
     * <p>This method is protocol version aware, and will handle encrypt for the EncDaemon.</p>
     * @param neighbour neighbour to direct a packet to
     * @param packetBuffer packet buffer to be sent in a DatagramPacket to the neighbour
     */
    static void sendPacketToNeighbour(NeighbourNode neighbour, byte[] packetBuffer) {
        try {
            /*If encrypted OSPF, encrypt the packetBuffer and update checksum and length in header. The only excess code
            is due to the need to encrypt the data.*/
            if (operationMode == 0x04)
                packetBuffer = updateChecksumAndLength(neighbour.enParam.encrypt(packetBuffer));

            //For all packets, packetBuffer is now correct, make a packet from it, send it in the neighbour's direction.
            DatagramPacket packet = new DatagramPacket(packetBuffer, packetBuffer.length, multicastSocketAddr);
            multicastSocket.setNetworkInterface(neighbour.rIntOwner.toNetworkInterface());
            multicastSocket.send(packet);

            //Final act, assuming handleDaemonError not called. Reset the rxmt retransmission timer;
            neighbour.resetRxmtTimer();
        } catch (SocketException ex) {
            handleDaemonError("SocketException when sending a packet to neighbour", ex);
        } catch (IOException ex) {
            handleDaemonError("IOException when sending a packet to neighbour", ex);
        }
    }

    /**<p><h1>Standard Multicast Receive Handle Method</h1></p>
     * <p>Method implemented as a thread to receive an packet from any network joined to the multicast group. A thread
     * is required as the receive method is blocking.</p>
     * <p>Once a packet is received, it is verified, data is scraped and it is passed onto specific handle methods, for
     * use in protocol functions and features.</p>
     */
    private static void receiveMulticastThread() {
        //Buffer for raw data.
        byte[] pBytes = new byte[4000];
        DatagramPacket pReturned = new DatagramPacket(pBytes, pBytes.length);

        //Only runs if not interrupted. If interrupted thread will end execution. To cancel, interrupt.
        while (!Thread.currentThread().isInterrupted()) {

            try {
                multicastSocket.receive(pReturned);
            } catch (IOException ex) {
                handleDaemonError("Exception on HelloReceiveThread", ex);
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
            packetBuffer = StdDaemon.validateOSPFHeader(packetBuffer, pSource);
            if (packetBuffer == null)
                continue;

            //region SCRAPE NEIGHBOUR
            //RID in bytes 4,5,6,7 to string. Using IPAddress class to convert bytes to the RID, easy peasy.
            IPAddressString neighbourRID = new IPAddressNetwork.IPAddressGenerator().from(
                    Arrays.copyOfRange(packetBuffer, 4, 8)
            ).toAddressString();
            NeighbourNode neighbour = NeighbourNode.getNeighbourNodeByRID(neighbourRID);
            //endregion SCRAPE NEIGHBOUR

            //Branch, pass to packet processor for each specific packet type.
            switch (packetBuffer[1]) {
                case 0x01 -> processHelloPacket(neighbour, neighbourRID, pSource, packetBuffer);
                case 0x02 -> {
                    assert neighbour != null;
                    processDBDPacket(neighbour, packetBuffer);
                }
            }
        }
    }

    /**<p><h1>Validate OSPF Header</h1></p>
     * <p>Scrapes and checks received data in the OSPF header is valid. In the event the header is invalid, null is
     * returned, to be handled by the receiveMulticastThread implementations as drop packet.</p>
     * <p>Checks the packet was not sent by this node, that the packet was sent from a connected network, that the
     * received interface was enabled, and that the checksum is valid.</p>
     * @param packetBuffer truncated received packet buffer
     * @param pSource IPAddress source of packet
     * @return the validated initial packet buffer, or null for drop packet
     */
    static byte[] validateOSPFHeader(byte[] packetBuffer, IPAddress pSource) {
        //region VERIFY PACKET
        //Check this node was not the source, if so reject the packet by null
        if (RouterInterface.getInterfaceByIP(pSource) != null)
            return null;

        RouterInterface receiveInt = RouterInterface.getInterfaceByIPNetwork(pSource);
        if (receiveInt == null) {
            System.err.println("UNLIKELY CONDITION: Packet received from network this node is not in");
            return null;
        }

        //Reject packets received on interfaces that are not enabled.
        if (!receiveInt.isEnabled)
            return null;

        //Get checksum. Bytes 12, 13
        long pChecksum = Short.toUnsignedLong(Shorts.fromByteArray(Arrays.copyOfRange(packetBuffer, 12, 14)));

        packetBuffer[12] = packetBuffer[13] = 0;//Unset checksum, so checksum calc will be correct.

        //verify checksums. If wrong, print special message. Important for debugging, inform server of line error.
        if (pChecksum != Launcher.ipChecksum(packetBuffer)) {
            System.err.println("Packet checksum mismatch. Got " + pChecksum + ", expected " +
                    Launcher.ipChecksum(packetBuffer));
            return null;
        }
        return packetBuffer;
    }

    /**<p><h1>StdDaemon Process Hello Packet</h1></p>
     * <p>Processes a validated hello packet received on the multicast socket. The method scrapes the known neighbours
     * list, and manipulates the neighbour on this node in the configuration and neighbours table.</p>
     * @param neighbour scraped neighbour to manipulate, from NeighbourNode.getNeighbourNodeByRID
     * @param neighbourRID scraped neighbour RID from buffer
     * @param pSource ip address of packet source, used to create a new neighbour node
     * @param packetBuffer raw, but manipulated and validated  packet buffer
     */
    private static void processHelloPacket(NeighbourNode neighbour, IPAddressString neighbourRID, IPAddress pSource,
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
        if (neighbour.getState() == ExternalStates.DOWN) {
            //Treat priority byte as string, parse string -> int
            neighbour.priority = Integer.parseUnsignedInt(packetBuffer[31] + "");

            Config.thisNode.knownNeighbours.add(neighbourRID);

            Launcher.printToUser("New Adjacency for node '" + neighbourRID + "'");

            neighbour.setState(ExternalStates.INIT);

            /*Not in OSPF spec to send a hello packet on Down -> Init state, but allows quicker convergence, not
            waiting for hello timer to expire*/
            sendHelloPackets();
        }

        //Update neighbour parameters for each received hello packet
        neighbour.knownNeighbours = reportedKnownRIDs;
        neighbour.resetInactiveTimer();

        /*If in init state and this neighbour reports to know of this current node, this is the conditions for the
        2WayReceived event. Trigger it*/
        if (neighbour.getState() == ExternalStates.INIT && neighbour.knownNeighbours.contains(Config.thisNode.getRID()))
            evTwoWayReceived(neighbour);
    }
    //TODO: how does OSPF work with multiple exchanges working at the same time? Would more exchange need to take place to reflect new LSAs on the neighbours?

    /**<p><h1>StdDaemon Process DBD packet</h1></p>
     * <p>Process a validated DBD packet received on the multicast socket. This method passes data received to correct
     * methods. The method manages the process flow for DBD packets, including process flows for master and slave nodes
     * </p>
     * @param neighbour scraped neighbour to manipulate, from NeighbourNode.getNeighbourNodeByRID
     * @param packetBuffer raw, but manipulated and validated  packet buffer
     */
    static void processDBDPacket(NeighbourNode neighbour, byte[] packetBuffer) {
        neighbour.lastReceivedDBD = new DBDPacket(packetBuffer);

        //region DBD M/S ELECTION
        if (neighbour.lastReceivedDBD.isFirstPacket() && neighbour.lastReceivedDBD.listLSAs.size() == 0
        && neighbour.getState().equals(ExternalStates.EXSTART)) {
            //if RFC conditions match for initial packet, set master / slave based on higher RID.
            neighbour.isMaster = neighbour.getRIDAsInt() > Config.thisNode.getRIDAsInt();
            neighbour.setState(ExternalStates.EXCHANCE);

            //For the slave, update last sent ddSeqNo to last received to avoid a check, and then do nothing
            if (neighbour.isMaster){
                neighbour.lastSentDBD.setDDSeqNo(neighbour.lastReceivedDBD.getDDSeqNo());
                return;
            }
        }
        //endregion DBD M/S ELECTION

        //region RETRANSMIT BAD PACKETS
        if (!neighbour.isMaster) {
            if (neighbour.lastReceivedDBD.getDDSeqNo() == neighbour.lastSentDBD.getDDSeqNo() - 1) {
                sendPacketToNeighbour(neighbour, neighbour.lastSentDBD.packetBuffer);
                return;
            }
        } else {
            if (neighbour.lastReceivedDBD.getDDSeqNo() == neighbour.lastSentDBD.getDDSeqNo()) {
                sendPacketToNeighbour(neighbour, neighbour.lastSentDBD.packetBuffer);
                return;
            }
        }
        //endregion RETRANSMIT BAD PACKETS

        //Only continue processing if in exchange (negotiation done event, or during exchange packet receive).
        if (neighbour.getState() != ExternalStates.EXCHANCE)
            return;

        //region VALIDATE PACKET
        if (!neighbour.isMaster) {
            if ((neighbour.lastReceivedDBD.getDDSeqNo() != neighbour.lastSentDBD.getDDSeqNo()) &&
                    !neighbour.lastReceivedDBD.isFirstPacket()) {
                Launcher.printToUser("Last received DBD packet from slave had invalid sequence number");
                System.out.println("Received " + neighbour.lastReceivedDBD.getDDSeqNo() + ", Sent " +
                        neighbour.lastSentDBD.getDDSeqNo());
                return;
            }
        } else {
            if (neighbour.lastReceivedDBD.getDDSeqNo() != neighbour.lastSentDBD.getDDSeqNo() + 1) {
                Launcher.printToUser("Last received DBD packet from master had unexpected sequence number");
                System.out.println("Received " + neighbour.lastReceivedDBD.getDDSeqNo() + ", Expected " +
                        neighbour.lastSentDBD.getDDSeqNo() + 1);
                return;
            }
        }
        //endregion VALIDATE PACKET

        //Scrape and store data now validated
        neighbour.lsaRequestList.addAll(neighbour.lastReceivedDBD.listLSAs);

        //region MASTER ENDPOINT
        if (!neighbour.lastSentDBD.isMoreBitSet() && !neighbour.lastReceivedDBD.isMoreBitSet() && !neighbour.isMaster) {
            evExchangeDone(neighbour);
            return;
        }
        //endregion MASTER ENDPOINT

        //region RESPOND WITH NO MORE DATA & SLAVE ENDPOINT
        if (!neighbour.isMaster) {
            if (!neighbour.lastSentDBD.isMoreBitSet() && neighbour.lastReceivedDBD.isMoreBitSet()) {
                neighbour.lastSentDBD = new DBDPacket(MTU, neighbour.lastSentDBD.getDDSeqNo() + 1, (byte) 0x01, null);
                sendPacketToNeighbour(neighbour, neighbour.lastSentDBD.packetBuffer);
                return;
            }
        } else {
            if (!neighbour.lastSentDBD.isMoreBitSet()) {
                neighbour.lastSentDBD = new DBDPacket(MTU, neighbour.lastReceivedDBD.getDDSeqNo(), (byte) 0x00, null);
                sendPacketToNeighbour(neighbour, neighbour.lastSentDBD.packetBuffer);

                //region SLAVE ENDPOINT
                if (!neighbour.lastReceivedDBD.isMoreBitSet())
                    evExchangeDone(neighbour);
                //endregion SLAVE ENDPOINT
                return;
            }
        }
        //endregion RESPOND WITH NO MORE DATA & SLAVE ENDPOINT

        //region RESPOND WITH MORE DATA & SLAVE ENDPOINT
        byte flags;
        if (!neighbour.isMaster)
            flags = 0x01;//MS bit
        else
            flags = 0x00;//No MS bit

        //Counter, determine if over MTU
        int maxNoData = (MTU - HEADER_LENGTH) - DBDPacket.DBD_HEADER_LENGTH;

        //List containing data
        List<RLSA> sendLSAList = new ArrayList<>();

        //Select LSAs to send. Make sure not over MTU using maxNoData.
        for (int i = neighbour.lastSentLSAIndex; i < Config.lsdb.routerLSAs.size(); i++) {
            RLSA lsa = Config.lsdb.routerLSAs.get(i);
            maxNoData -= lsa.getLength();//Take LSA length away from MTU budget. If over budget, don't add and set M
            if (maxNoData < 0) {
                flags = (byte) (flags | 0x02);//Add M bit
                neighbour.lastSentLSAIndex = --i;
                break;
            }
            //TODO: should full LSA be exchanged? If yes add lsa, if no make new summary LSA and add to list (current).
            sendLSAList.add(new RLSA(lsa.makeRLSAHeaderBuffer()));
        }

        //Build packet, using correct sequence no. send packet.
        if (!neighbour.isMaster) {
            neighbour.lastSentDBD = new DBDPacket(MTU, neighbour.lastSentDBD.getDDSeqNo() + 1, flags, sendLSAList);
            sendPacketToNeighbour(neighbour, neighbour.lastSentDBD.packetBuffer);
        } else {
            neighbour.lastSentDBD = new DBDPacket(MTU, neighbour.lastReceivedDBD.getDDSeqNo(), flags, sendLSAList);
            sendPacketToNeighbour(neighbour, neighbour.lastSentDBD.packetBuffer);

            //region SLAVE ENDPOINT
            if (!neighbour.lastReceivedDBD.isMoreBitSet() && !neighbour.lastSentDBD.isMoreBitSet())
                evExchangeDone(neighbour);
            //endregion SLAVE ENDPOINT
        }
        //endregion RESPOND WITH MORE DATA & SLAVE ENDPOINT
    }

    /**<p><h1>2WayReceived Event</h1></p>
     * <p>On neighbour state Init, if a node receives a hello packet with its own RID echoed, the event 2WayReceived is
     * fired. This method is the trigger for the exchange protocol to start for the neighbour node. This method is
     * protocol version aware.</p>
     * @param neighbour node that has had the 2WayReceived event trigger
     */
    static void evTwoWayReceived(NeighbourNode neighbour) {
        //Quick sanity check TwoWayReceived did occur
        if (neighbour.getState() != ExternalStates.INIT)
            return;

        //Set Correct state for event
        neighbour.setState(ExternalStates.EXSTART);

        //Update complete flag for encrypted OSPF. Both Std and Enc will perform the check, no unnecessary difference
        if (operationMode == 0x04)
            neighbour.rIntOwner.dhExchange.flagComplete = true;

        //Refresh the local LSA, which will add the new neighbour
        Config.lsdb.setupLocalRLSA();

        neighbour.lastSentDBD = new DBDPacket(MTU, new Random().nextInt(), (byte) 0x07, null);
        sendPacketToNeighbour(neighbour, neighbour.lastSentDBD.packetBuffer);
    }

    /**<p><h1>Exchange Done Event</h1></p>
     * <p>Once the DBD exchange is complete, and both nodes have received all DBD packets from one another, call this
     * event method.</p>
     * @param neighbour node that has had the NegotiationDone event triggered
     */
    private static void evExchangeDone(NeighbourNode neighbour) {
        //Quick sanity test
        if (neighbour.getState() != ExternalStates.EXCHANCE)
            return;

        //Last packet was acknowledged, cancel the retransmission of the very last packet.
        neighbour.cancelRxmtTimer();

        //Set Correct state for event
        neighbour.setState(ExternalStates.LOADING);

        //Current future method
        //Statistics Endpoint test
        if ((Config.thisNode.knownNeighbours.size() >= Stat.endNoAdjacencies) && Stat.endNoAdjacencies != -1)
            Stat.endStats();


        //Display data received from each neighbour.
        //1) Header, 2) Neighbour number, 3) LSA headers 4) LSA data (encoded form)
        Launcher.printToUser("Data In Request Lists:");
        for (NeighbourNode n: Config.neighboursTable) {
            System.out.println(neighbour.lsaRequestList.size() + " Records:\n\r" +
                    "lsID, Adv. Router, Seq#, age");
            for (RLSA lsa: n.lsaRequestList) {
                System.out.println(lsa.toString());
                for (LinkData link: lsa.links) {
                    Launcher.printBuffer(link.makeBuffer());
                }
            }
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
    private static byte[] makeHelloPacket() {
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

        //Append neighbours
        for (NeighbourNode neighbour: Config.neighboursTable) {
            //Skip over non-adjacent nodes
            if (neighbour.getState() == ExternalStates.DOWN)
                continue;

            ospfBuffer = Bytes.concat(ospfBuffer, neighbour.getRIDBytes());
        }


        //Update packet length (2, 3), Update Checksum (12, 13)
        return updateChecksumAndLength(ospfBuffer);
    }

    /**<p><h1>Update Checksum and Packet Length in Packet Buffer</h1></p>
     * <p>Processes a starting buffer for any make method, in the process updating the packet length and checksum fields
     * to correct values.</p>
     * @param buffer buffer to operate on and scrape data from
     * @return original buffer with modified packet length and checksum fields
     */
    static byte[] updateChecksumAndLength(byte[] buffer) {
        //Update length, using ByteBuffer to put the int value in array, truncated to short
        //Length in positions 2 and 3 in OSPF header
        int packetLength = buffer.length;
        ByteBuffer lenBuffer = ByteBuffer.allocate(4);
        lenBuffer.putInt(packetLength);
        buffer[2] = lenBuffer.array()[2];
        buffer[3] = lenBuffer.array()[3];

        //Update checksum, using ByteBuffer to put the resulting long value in array, truncated to short
        //Checksum in positions 12 and 13
        //Clear checksum, so it won't be counted in the checksum calculation
        buffer[12] = buffer[13] = 0;
        ByteBuffer chkBuffer = ByteBuffer.allocate(8);

        //Calculate checksum, store it in the chkBuffer, then store it in the output buffer
        chkBuffer.putLong(Launcher.ipChecksum(buffer));
        buffer[12] = chkBuffer.array()[6];
        buffer[13] = chkBuffer.array()[7];

        return buffer;
    }

    /**<p><h1>Handle Daemon Exception</h1></p>
     * <p>Handles exceptions in this daemon process. Supply a message to print, to stdout, and pass an exception to help
     * with debugging. Also clean exits the application</p>
     * @param message A message to print to the user
     * @param ex an exception passed from an exception handler. Can be null.
     */
    static void handleDaemonError(String message, Exception ex) {
        //Message handle
        if (ex != null) {
            System.out.println("Exception in daemon process: " + message + ": \nStack trace follows:\n");
            ex.printStackTrace();
        } else {
            System.out.println("Exception in daemon process, no exception: " + message);
        }

        //Clean exit
        if (timerHelloSend != null)
            timerHelloSend.cancel();
        if (threadStdMulticastListen.isAlive())
            threadStdMulticastListen.interrupt();
        if (EncDaemon.threadEncMulticastListen.isAlive())
            EncDaemon.threadEncMulticastListen.interrupt();
        if (multicastSocket != null)
            multicastSocket.close();
        System.exit(-3);
    }
    //endregion
}
