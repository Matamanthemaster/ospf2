package com.mws.ospf;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;
import inet.ipaddr.IPAddressNetwork;
import inet.ipaddr.IPAddressString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**<p><h1>Type 1 LSA</h1></p>
 * <p>A type 1 (Router) LSA. LSAs store data for the LSDB, and being sent via LSU packets. The class is used to store
 * an LSA on the node, formatted data, and contains methods to convert it to a either a full LSU buffer, or DBD summary</p>
 * <p>Future work should be to split up this class into a base RSA class, which is extended by each individual type
 * of LSA. For this project in the current state, a single class is all that is required.</p>
 */
class RLSA {
    //TODO: Test classes and fix inevitable bugs
    //TODO: Investigate if the DBD packet is needed. Likely remove the LSDB class in favour of the config class.
    //TODO: Finish Javadocs
    //TODO: Handle exceptions from RLSA and LinkData.
    //TODO: Populate RLSA list with data from this node
    //TODO: DBD exchange
    //TODO: Something else related to this class, I have forgotten what exactly now. It was good, related data method?
    //region STATIC METHODS
    static boolean isChecksumCorrect(byte[] lsaBuffer) {
        //Store original checksum
        int checksum = (lsaBuffer[16] << 8) | lsaBuffer[17];

        //Remove checksum and lsage from checksum calculation, check checksum and return if matching.
        lsaBuffer[0] = lsaBuffer[1] = lsaBuffer[16] = lsaBuffer[17] = 0x00;
        return checksum == fletcherChecksum16(lsaBuffer);
    }

    static int fletcherChecksum16(byte[] buffer) {
        //Starting variables
        int C0 = 0, C1 = 0;

        //Loop over each 'word', 8 bits, performing checksum function. Operation performed under modulo 255, keeps vars
        //in range of 1 byte.
        for (byte word : buffer) {
            C0 = (C0 + word) % 255;
            C1 = (C1 + C0) % 255;
        }

        //C1 + C0 in place of setting n+0 and n+1. Allows same method to make and check the checksum.
        return (C0 << 8) | C1;
    }
    //endregion STATIC METHODS

    //region OBJECT PROPERTIES
    //TODO: Change access rights
    int lsAge;
    IPAddressString lsID;
    IPAddressString advertisingRouter;
    int lsSeqNumber;
    List<LinkData> links = new ArrayList<>();
    //endregion OBJECT PROPERTIES

    //TODO: add update method for ls age
    //region OBJECT METHODS
    /**<p><h1>Type 1 LSA Constructor</h1></p>
     * <p>Construct a router LSA packet to store specified parameters. Takes all available information about </p>
     * @param lsAge Sets the initial value for LS age
     * @param lsID
     * @param advertisingRouter
     * @param lsSeqNumber
     * @param links
     */
    public RLSA(int lsAge, IPAddressString lsID, IPAddressString advertisingRouter, int lsSeqNumber, List<LinkData> links) {
        if (lsAge > 65535)
            throw new IllegalArgumentException("lsAge must be convertable to a short, so less than 65535");

        this.lsAge = lsAge;
        this.lsID = lsID;
        this.advertisingRouter = advertisingRouter;
        this.lsSeqNumber = lsSeqNumber;
        this.links = links;
    }

    public RLSA(byte[] lsaBuffer) {

        if (!isChecksumCorrect(lsaBuffer))
            throw new ArithmeticException("The checksum in the provided buffer is invalid");

        if (lsaBuffer.length != ((lsaBuffer[18] << 8) | lsaBuffer[19]))
            throw new ArithmeticException("The buffer length does not match the header length ");

        //LS Age 0,1
        int lsAge = (lsaBuffer[0] << 8) | lsaBuffer[1];
        this.lsAge = (short) lsAge;

        //LS ID 4,5,6,7
        this.lsID = new IPAddressNetwork.IPAddressGenerator().from(
                Arrays.copyOfRange(lsaBuffer, 4, 8)
        ).toAddressString();

        //Advertising Router ID 8,9,10,11
        this.advertisingRouter = new IPAddressNetwork.IPAddressGenerator().from(
                Arrays.copyOfRange(lsaBuffer, 8, 12)
        ).toAddressString();

        //LS Sequence No. 12,13,14,15
        this.lsSeqNumber = ((lsaBuffer[12] << 24) | (lsaBuffer[13] << 16) | (lsaBuffer[14] << 8) | lsaBuffer[15]);

        //Link State Information, byte 24 onwards. First check packet link state information sent is correct and was not
        //malformed by the other node.
        int noLinks = (lsaBuffer[22] << 8) | lsaBuffer[23];

        int checkNoLinks = lsaBuffer.length;
        checkNoLinks -= 24;

        if (checkNoLinks % 12 != 0)
            throw new ArithmeticException("Malformed link state information after number of links");
        if (noLinks != (checkNoLinks / 12))
            throw new ArithmeticException("Reported number of links doesn't match the number of links in the packet.");

        //Now link state information format was verified, scrape data from the packet.
        for (int i = 0; i < noLinks * 12; i+= 12) {
            //Create a subset of the lsaBuffer for the individual link data. Exclude first 24 bytes of header. Only copy
            //the 12 bytes relative to the current link information.
            byte[] linkBuffer = Arrays.copyOfRange(lsaBuffer, 24+i, 24+i+12);

            //Scrape all needed data from the buffer subset, combine it into a LinkData object.
            LinkData newLinkData = new LinkData(
                    new IPAddressNetwork.IPAddressGenerator().from(
                            Arrays.copyOfRange(linkBuffer, i, i+4)
                    ).toAddressString(),
                    new IPAddressNetwork.IPAddressGenerator().from(
                            Arrays.copyOfRange(linkBuffer, i+4, i+8)
                    ).toAddressString(),
                    Arrays.copyOfRange(linkBuffer, i+10, i+12)
            );

            this.links.add(newLinkData);
        }

    }

    public byte[] makeRLSABuffer() {
        byte[] buffer = {
                0x00, 0x00,             //lsage //0,1
                            0x00,       //options //2
                                  0x01, //LSA Type (Router LSA) //3
                0x00, 0x00, 0x00 ,0x00, //LS ID //4,5,6,7
                0x00, 0x00, 0x00, 0x00, //Advertising router //8,9,10,11
                0x00, 0x00, 0x00, 0x00, //ls sequence number //12,13,14,15
                0x00, 0x00,             //ls checksum //16,17
                            0x00, 0x00, //length, header 20 bytes + body //18,19
                0x00, 0x00,             //0, v, b, e, 0 (ignore flags) //20,21
                            0x00, 0x00, //number of links //22,23
                //+ 12 bytes per link
        };

        byte[] lsAgeB = Ints.toByteArray(this.lsAge);
        buffer[0] = lsAgeB[3];
        buffer[1] = lsAgeB[4];

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
        byte[] linkNoB = Ints.toByteArray(this.links.size());
        buffer[22] = linkNoB[2];
        buffer[23] = linkNoB[3];

        for (LinkData link: this.links)
        {
            buffer = Bytes.concat(buffer, link.makeBuffer());
        }

        //length
        //could also overflow. Again, with the number of interfaces in play, not likely.
        byte[] pLength = Shorts.toByteArray((short) buffer.length);
        buffer[18] = pLength[0];
        buffer[19] = pLength[1];

        //checksum
        //create buffer that doesn't contain the ls age field. Use that to calculate a checksum.
        //Take returned int, update the value in the buffer.
        byte[] checksumBuffer =  buffer;
        checksumBuffer[0] = checksumBuffer[1] = 0x00;//checksum is buffer without age.
        byte[] checksum = Ints.toByteArray(fletcherChecksum16(checksumBuffer));
        buffer[16] = checksum[2];
        buffer[17] = checksum[3];

        return buffer;
    }
    //endregion OBJECT METHODS
}

