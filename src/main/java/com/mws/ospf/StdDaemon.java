package com.mws.ospf;

import jnr.ffi.LibraryLoader;

public class StdDaemon {

    public static void Main() {
        OspfCLib clib = LibraryLoader.create(OspfCLib.class).search("/home/matthew/IdeaProjects/ospf/out/artifacts/ospf_jar/").load("OspfCLib");
        int rc = clib.TestJNR("12345678901");
        System.out.println(rc);

        byte[] hwAddr = {(byte)0xc0, 0x25, (byte)0xe9, 0x1f, 0x22, 0x57};//c0:25:e9:1f:22:57
        byte[] ospfBuffer = {
                //GENERIC OSPF HEADER
                0x02,//version
                0x01,//message type
                0x00, 0x2c,//packet length
                (byte)0xc0, (byte)0xa8, 0x01, 0x01,//source router (dotted decimal)
                0x00, 0x00, 0x00, 0x01,//area id (dotted decimal)
                0x20, (byte) 0xf2,//checksum
                0x00, 0x00,//Auth type//0x00, 0x01
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,//Auth Data//0x63, 0x69, 0x73, 0x63, 0x6f, 0x00, 0x00, 0x00

                //OSPF HELLO PACKET HEADER
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x00,//Network Mask
                0x00, 0x0a,//hello interval
                0x12,//options
                0x01,//router priority
                0x00, 0x00, 0x00, 0x28,//router dead interval
                0x0a, 0x00, 0x00, 0x02,//DR
                0x00, 0x00, 0x00, 0x00,//BDR

                //OSPF LLS DATA BLOCK
                (byte)0xff, (byte)0xf6,//checksum
                0x00, 0x03,//lls data length
                0x00, 0x01, 0x00, 0x04, 0x00, 0x00, 0x00, 0x01//extended options TLV
        };

        clib.SendPacket("enp5s0", hwAddr, "192.168.1.87", ospfBuffer, ospfBuffer.length);
    }
}
