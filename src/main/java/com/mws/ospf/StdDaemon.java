package com.mws.ospf;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.google.common.primitives.Shorts;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class StdDaemon {

    private static MulticastSocket socketHello;
    private static Timer timerHelloSend;
    private static Thread threadHelloListen;

    static void Main() {
        //Set helloSocket, used for multicasting. Binds the ospf multicast address to all interfaces using this socket.
        socketHello = null;
        try {
            InetAddress ipDest = InetAddress.getByName("224.0.0.5");
            InetSocketAddress ipDestSocket = new InetSocketAddress(ipDest, 25565);
            socketHello = new MulticastSocket(25565);
            socketHello.setTimeToLive(1);

            for (RouterInterface rInt : Config.thisNode.interfaceList) {
                socketHello.joinGroup(ipDestSocket, rInt.ToNetworkInterface());
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

        //End set hello socket

        //Set recieve handler


        //Create a timer for hello and set it to run instantly. Running the timer schedules further running.
        timerHelloSend = new Timer();
        timerHelloSend.schedule(new TimerTask() {
            @Override
            public void run() {
                SendHelloPacket();
            }
        }, 0);

        //Setup thread for hello multicast server
        threadHelloListen = new Thread(StdDaemon::HelloReceiveThread);
        threadHelloListen.start();
    }

    public static void SendHelloPacket() {
        //Make buffer
        byte[] helloBuffer = MakeHelloPacket();

        //Create a datagram packet to send, send it out all network interfaces.
        try {
            DatagramPacket helloPacket = new DatagramPacket(helloBuffer,
                    helloBuffer.length,
                    InetAddress.getByName("224.0.0.5"),
                    25565);

            for (RouterInterface rInt: Config.thisNode.interfaceList) {
                socketHello.setNetworkInterface(rInt.ToNetworkInterface());
                socketHello.send(helloPacket);
            }
        } catch (UnknownHostException ex) {
            DaemonErrorHandle("Unknown host when creating datagram packet. Java couldn't resolve the host" +
                    "224.0.0.5 somehow? This shouldn't be possible", ex);
        } catch (IOException ex) {
            DaemonErrorHandle("IOException when sending hello datagram packet" + ex.getMessage(), ex);
        }

        //Schedule next run to execute the same method, based on the hello timer interval.
        timerHelloSend.schedule(new TimerTask() {
            @Override
            public void run() {
                SendHelloPacket();
            }
        }, 10 * 1000);
    }

    public static void HelloReceiveThread()
    {
        //Buffer for raw data.
        byte[] returnB = new byte[500];
        DatagramPacket returnP = new DatagramPacket(returnB, returnB.length);

        //Only runs if not interrupted. If interrupted thread will end execution. To cancel, interrupt.
        while (Thread.currentThread().isInterrupted()) {
            try {
                socketHello.receive(returnP);
            } catch (IOException ex) {
                DaemonErrorHandle("Exception on CheckHelloBuffer receive", ex);
            }



            //verify single byte values, saves adv. processing if basic fields are wrong.

            //proto version (2)
            if (returnB[0] != 0x02)
                continue;
            //message type (hello)
            if (returnB[1] != 0x01)
                continue;



            //Setup new buffer to be of the specified length.

            /*Get Packet length
            Get bytes from the packet buffer. Starts from 2 inc., to byte 4 exclu. This length is trusted and used
            for the rest of this handle. Don't need to verify if correct, as checksum will do that based on this
            length value.*/
            int pLength = Shorts.fromByteArray(Arrays.copyOfRange(returnB, 2, 4));

            byte[] packetBuffer = Arrays.copyOfRange(returnB, 0, pLength);

            //Get checksum. Bytes 12, 13
            long pChecksum = Longs.fromByteArray(Arrays.copyOfRange(returnB,12, 14));

            //verify checksums. If wrong, print special message. Important for debugging.
            if (pChecksum != Launcher.IpChecksum(packetBuffer)) {
                System.err.println("Hello packet checksum mismatch. Got " + pChecksum + ", expected " + Launcher.IpChecksum(packetBuffer));
                continue;
            }
        }

    }

    /**<p><h1>Make Hello Packet</h1></p>
     * <p>From a generic byte buffer (treated as unsigned), return a hello packet. The packet includes corrected values
     * based on this node's RID, neighbours RIDs, updating the length and internet checksum.</p>
     * <p></p>
     * <p>This node's RID is derived from Config.thisNode.rID</p>
     * <p>The RIDs of neighbours are derived from Config.neighboursTable. The router IDs from each NeighbourNode.rID</p>
     * @return the completed hello packet in bytes.
     */
    public static byte[] MakeHelloPacket() {
        //Generic buffer that will be updated with specific values.
        byte[] ospfBuffer = {
                //GENERIC OSPF HEADER
                0x02,//version
                0x01,//message type
                0x00, 0x2c,//packet length
                (byte) 0xc0, (byte) 0xa8, 0x01, 0x01,//source router rid (dotted decimal)
                0x00, 0x00, 0x00, 0x00,//area id (dotted decimal)
                0x20, (byte) 0xf2,//checksum
                0x00, 0x00,//Auth type//0x00, 0x01
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,//Auth Data//0x63, 0x69, 0x73, 0x63, 0x6f, 0x00, 0x00, 0x00//"Cisco"

                //OSPF HELLO PACKET HEADER
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x00,//Network Mask
                0x00, 0x0a,//hello interval//10
                0x00,//options
                0x01,//router priority
                0x00, 0x00, 0x00, 0x28,//router dead interval//40
                0x00, 0x00, 0x00, 0x00,//DR
                0x00, 0x00, 0x00, 0x00,//BDR
        };

        //Update router ID (4, 5, 6, 7)
        try {
            byte[] ridBytes = Config.thisNode.rID.getBytes();
            ospfBuffer[4] = ridBytes[0];
            ospfBuffer[5] = ridBytes[1];
            ospfBuffer[6] = ridBytes[2];
            ospfBuffer[7] = ridBytes[3];
        } catch (Exception ex)  {
            DaemonErrorHandle("Error when creating an OSPF packet: Substituting in router ID.", ex);
        }

        //Update Network Mask? (24, 25, 26, 27)
        //not for this experiment

        //Append neighbours
        byte[] neighboursBuffer = new byte[Config.neighboursTable.size() * 4];

        int byteOffset = 0;
        for (int i = 0; i < Config.neighboursTable.size(); i++)
        {
            Node neighbour = Config.neighboursTable.get(i);
            byte[] rIDBytes = neighbour.rID.getBytes();
            neighboursBuffer[byteOffset] = rIDBytes[0];
            neighboursBuffer[byteOffset + 1] = rIDBytes[1];
            neighboursBuffer[byteOffset + 2] = rIDBytes[2];
            neighboursBuffer[byteOffset + 3] = rIDBytes[3];

            byteOffset += 4;
        }
        ospfBuffer = Bytes.concat(ospfBuffer, neighboursBuffer);

        //Update packet length (2, 3)
        int packetLength = ospfBuffer.length;
        ByteBuffer lenBuffer = ByteBuffer.allocate(4);
        lenBuffer.putInt(packetLength);
        ospfBuffer[2] = lenBuffer.array()[2];
        ospfBuffer[3] = lenBuffer.array()[3];//CHECK THAT RESULT IS IN BIG ENDIAN. ASSUME IT'S LIKELY IN LITTLE EDIAN.

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
    private static void DaemonErrorHandle(String message, Exception ex) {
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
}
