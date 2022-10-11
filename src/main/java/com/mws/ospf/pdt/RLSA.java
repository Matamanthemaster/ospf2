package com.mws.ospf.pdt;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;
import com.mws.ospf.RouterInterface;
import inet.ipaddr.IPAddressString;

import java.util.ArrayList;
import java.util.List;

public class RLSA {
    public short lsAge;
    IPAddressString lsID;
    IPAddressString advertisingRouter;
    int lsSeqNumber;
    short lsChecksum;
    int lslength;
    IPAddressString data;
    List<RouterInterface> interfaces = new ArrayList<>();

    public byte[] MakeRLSABuffer() {
        byte[] buffer = {
                0x00, 0x00, //lsage //0,1
                0x00,//options //2
                0x01,//LSA Type (Router LSA) //3
                0x00, 0x00, 0x00 ,0x00,//LS ID //4,5,6,7
                0x00, 0x00, 0x00, 0x00,//Advertising router //8,9,10,11
                0x00, 0x00, 0x00, 0x00,//ls sequence number //12,13,14,15
                0x00, 0x00,//ls checksum //16,17
                0x00, 0x00,//length, header 20 bytes + body //18,19
                0x00, 0x00,//0, v, b, e, 0 (ignore flags) //20,21
                0x00, 0x00,//number of links //22,23
        };

        byte[] lsAgeB = Shorts.toByteArray(this.lsAge);
        buffer[0] = lsAgeB[0];
        buffer[1] = lsAgeB[1];

        //ls id
        byte[] lsIDB = this.lsID.getAddress().getBytes();
        buffer[4] = lsIDB[0];
        buffer[5] = lsIDB[1];
        buffer[6] = lsIDB[2];
        buffer[7] = lsIDB[3];

        //advertising router
        byte[] advertisingRouterB = this.advertisingRouter.getAddress().getBytes();
        buffer[8] = advertisingRouterB[0];
        buffer[9] = advertisingRouterB[1];
        buffer[10] = advertisingRouterB[2];
        buffer[11] = advertisingRouterB[3];

        //ls sequence number
        byte[] lsSeqNumB = Ints.toByteArray(this.lsSeqNumber);
        buffer[12] = lsSeqNumB[0];
        buffer[13] = lsSeqNumB[1];
        buffer[14] = lsSeqNumB[2];
        buffer[15] = lsSeqNumB[3];

        //no links
        //possible to overflow as size can be greater than the short. Not a practical issue.
        byte[] linkNoB = Ints.toByteArray(this.interfaces.size());
        buffer[22] = linkNoB[3];
        buffer[23] = linkNoB[4];

        for (RouterInterface rint: this.interfaces)
        {
            byte[] interfaceRSABufferAddition = {
                    0x00, 0x00, 0x00, 0x00,//Link ID //0,1,2,3
                    0x00, 0x00, 0x00, 0x00,//Link Data //4,5,6,7
                    0x01,//type //8
                    0x00,//number of TOS //9
                    0x00, 0x01,//metric //10,11
                    0x00, 0x00, 0x00, 0x01//TOS ID, 0, TOS metric (2 bytes) //12,13,14,15
            };


            buffer = Bytes.concat(buffer, interfaceRSABufferAddition);
        }

        //length
        //could also overflow. Again, with the number of interfaces in play, not likely.
        byte[] pLength = Shorts.toByteArray((short) buffer.length);
        buffer[18] = pLength[0];
        buffer[19] = pLength[1];


        //ls checksum
        byte[] checksumBuffer = buffer;
        checksumBuffer[0] = checksumBuffer[1] = 0x00;//checksum is buffer without age.


        return buffer;
    }
}

