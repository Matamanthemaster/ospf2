package com.mws.ospf.pdt;

import java.nio.ByteBuffer;

public class HelloPacket {
    final byte VERSION = 2;
    final byte MSG_TYPE = 1;
    short PacketLength = 56;//
    short sourceRID;//
    final short AREAID = 0;
    short checksum;//
    final short AUTH_TYPE = 0;
    final long AUTH_DATA = 0;

    byte[] ToBytes()
    {
        byte[] ospfBuffer = {
                //GENERIC OSPF HEADER
                0x02,//version
                0x01,//message type
                0x00, 0x2c,//packet length
                (byte) 0xc0, (byte) 0xa8, 0x01, 0x01,//source router (dotted decimal)
                0x00, 0x00, 0x00, 0x01,//area id (dotted decimal)
                0x20, (byte) 0xf2,//checksum
                0x00, 0x00,//Auth type//0x00, 0x01
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,//Auth Data//0x63, 0x69, 0x73, 0x63, 0x6f, 0x00, 0x00, 0x00

                //OSPF HELLO PACKET HEADER
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x00,//Network Mask
                0x00, 0x0a,//hello interval
                0x12,//options
                0x01,//router priority
                0x00, 0x00, 0x00, 0x28,//router dead interval
                0x00, 0x00, 0x00, 0x00,//DR
                0x00, 0x00, 0x00, 0x00,//BDR

                //OSPF LLS DATA BLOCK
                (byte) 0xff, (byte) 0xf6,//checksum
                0x00, 0x03,//lls data length
                0x00, 0x01, 0x00, 0x04, 0x00, 0x00, 0x00, 0x01//extended options TLV
        };

        //Update packet length (2, 3)
        ByteBuffer verBuffer = ByteBuffer.allocate(2);
        verBuffer.putShort(this.PacketLength);
        ospfBuffer[2] = verBuffer.array()[0];
        ospfBuffer[3] = verBuffer.array()[1];//CHECK THAT RESULT IS IN BIG EDIAN. ASSUME IT'S LIKELY IN LITTLE EDIAN.



        //4,5,6,7 12,13 24,25,26,27 28,29 32,33,34,35

        return ospfBuffer;
    }

}
