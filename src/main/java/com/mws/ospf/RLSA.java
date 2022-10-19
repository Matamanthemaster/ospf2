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
    //TODO: DBD exchange
    //region STATIC CONSTANTS
    static final int INITIAL_SEQUENCE_NUMBER = 0x80000001;
    static final int MAX_SEQUENCE_NUMBER = 0x7fffffff;
    /**<p><h1>MaxAge</h1></p>
     * <p>
     *         The maximum age that an LSA can attain. When an LSA's LS age
     *         field reaches MaxAge, it is reflooded in an attempt to flush the
     *         LSA from the routing domain (See Section 14). LSAs of age MaxAge
     *         are not used in the routing table calculation.  The value of
     *         MaxAge is set to 1 hour.
     * </p>
     */
    static final int MAX_AGE = 3600;
    //endregion STATIC CONSTANTS

    //region STATIC METHODS
    /**<p><h1>Compare Checksum to Real Value</h1></p>
     * <p>Takes the stored checksum in the LSA header and compares it to the actual value of the calculated checksum.
     * If they match returns true, else false. Implements the fletcherChecksum16 method</p>
     * @param lsaBuffer the LSA buffer to check
     * @return whether the checksum field in the header matches the
     * @throws IllegalArgumentException if the lsa buffer didn't contain an LSA header
     */
    static boolean isChecksumCorrect(byte[] lsaBuffer) {
        if (lsaBuffer.length < 20)
            throw new IllegalArgumentException("The LSA buffer provided doesn't meet the size requirement for the LSA" +
                    " header, and so the LSA is invalid");

        //Store original checksum
        int checksum = (lsaBuffer[16] << 8) | lsaBuffer[17];

        //Remove checksum and lsage from checksum calculation, check checksum and return if matching.
        lsaBuffer[0] = lsaBuffer[1] = lsaBuffer[16] = lsaBuffer[17] = 0x00;
        return checksum == fletcherChecksum16(lsaBuffer);
    }

    /**<p><h1>Calculate Fletcher Checksum</h1></p>
     * <p>Used the fletcher (16) algorithm to calculate a checksum for a given buffer. The checksum detects errors in
     * the data.</p>
     * @param buffer buffer to operate on, which the checksum is calculated for
     * @return the calculated checksum as a short stored in an int
     */
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

    //TODO: Change access rights
    //region OBJECT PROPERTIES
    /**<p><h1>14.  Aging The Link State Database</h1></p>
     * <p>
     *     Each LSA has an LS age field.  The LS age is expressed in seconds.
     *     An LSA's LS age field is incremented while it is contained in a
     *     router's database.  Also, when copied into a Link State Update
     *     Packet for flooding out a particular interface, the LSA's LS age is
     *     incremented by InfTransDelay.
     * </p><p></p><p>
     *     An LSA's LS age is never incremented past the value MaxAge.  LSAs
     *     having age MaxAge are not used in the routing table calculation.  As
     *     a router ages its link state database, an LSA's LS age may reach
     *     MaxAge.[21] At this time, the router must attempt to flush the LSA
     *     from the routing domain.  This is done simply by reflooding the
     *     MaxAge LSA just as if it was a newly originated LSA (see Section
     *     13.3).
     * </p><p></p><p>
     *     When creating a Database summary list for a newly forming adjacency,
     *     any MaxAge LSAs present in the link state database are added to the
     *     neighbor's Link state retransmission list instead of the neighbor's
     *     Database summary list.  See Section 10.3 for more details.
     * </p><p></p><p>
     *     A MaxAge LSA must be removed immediately from the router's link
     *     state database as soon as both a) it is no longer contained on any
     *     neighbor Link state retransmission lists and b) none of the router's
     *     neighbors are in states Exchange or Loading.
     * </p><p></p><p>
     *     When, in the process of aging the link state database, an LSA's LS
     *     age hits a multiple of CheckAge, its LS checksum should be verified.
     *     If the LS checksum is incorrect, a program or memory error has been
     *     detected, and at the very least the router itself should be
     *     restarted.
     * </p>
     */
    int lsAge;
    /**<p><h1>LSA lsID</h1></p>
     * <p>
     *             This field identifies the piece of the routing domain that
     *             is being described by the LSA.  Depending on the LSA's LS
     *             type, the Link State ID takes on the values listed in Table
     *             16.
     * </p><p></p>
     * <p>For ls type 1: The originating router's Router ID.</p>
     */
    IPAddressString lsID;
    /**<p><h1>LSA Advertising Router</h1></p>
     * <p>
     *             The Router ID of the router that originated the LSA.  For
     *             example, in network-LSAs this field is equal to the Router ID of
     *             the network's Designated Router.
     * </p>
     */
    IPAddressString advertisingRouter;
    /**<p><h1>LSA LS sequence number</h1></p>
     * <p>
     *             The sequence number -N (0x80000000) is reserved (and
     *             unused).  This leaves -N + 1 (0x80000001) as the smallest
     *             (and therefore oldest) sequence number; this sequence number
     *             is referred to as the constant InitialSequenceNumber. A
     *             router uses InitialSequenceNumber the first time it
     *             originates any LSA.  Afterwards, the LSA's sequence number
     *             is incremented each time the router originates a new
     *             instance of the LSA.  When an attempt is made to increment
     *             the sequence number past the maximum value of N - 1
     *             (0x7fffffff; also referred to as MaxSequenceNumber), the
     *             current instance of the LSA must first be flushed from the
     *             routing domain.  This is done by prematurely aging the LSA
     *             (see Section 14.1) and reflooding it.  As soon as this flood
     *             has been acknowledged by all adjacent neighbors, a new
     *             instance can be originated with sequence number of
     *             InitialSequenceNumber.
     * </p>
     */
    int lsSeqNumber;
    List<LinkData> links = new ArrayList<>();
    //endregion OBJECT PROPERTIES

    //region OBJECT METHODS
    /**<p><h1>Type 1 LSA Constructor</h1></p>
     * <p>Construct a router LSA packet to store specified parameters. Takes all available LSA information from provided
     * parameters. This constructor is intended for processing a router LSA for this node, where all the information is
     * local and not received in packet form. For this reason, advertising router is set statically as thisNode.</p>
     * @param lsSeqNumber The LSA sequence number. For a new LSA this should be RLSA.INITIAL_SEQUENCE_NUMBER
     * @param links Sets the links
     */
    public RLSA(int lsSeqNumber, List<LinkData> links) {
        this.links = links;

        //The time in seconds since the LSA was originated.
        //Now is originated.
        this.lsAge = 0;
        this.lsID = this.advertisingRouter = Config.thisNode.getRID();
        this.lsSeqNumber = lsSeqNumber;
    }

    /**<p><h1>Type 1 LSA Constructor from Buffer</h1></p>
     * <p>Construct a router LSA packet to store specified parameters. Takes all available LSA information from a
     * received router LSA buffer. This constructor is intended for processing a router LSA from another node, where all
     * information is reported by the adjacent node. No stored data is static from this node as it is all from
     * another.</p>
     * <p>The RLSA buffer can either be for a full RLSA, or for only the 20 header bytes. The full LSA is useful for
     * the LSDB, while the header is useful for LSA request lists.</p>
     * @param lsaBuffer the LSA buffer from another node containing the standard LSA header
     * @throws IllegalArgumentException an invalid LSA buffer was provided
     * @throws ArithmeticException data provided was invalid. Either checksum or length fields, or the link data
     */
    public RLSA(byte[] lsaBuffer) {
        if (lsaBuffer.length < 20)
            throw new IllegalArgumentException("The LSA buffer provided doesn't meet the size requirement for the LSA" +
                    " header, and so the LSA is invalid");

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

        /*All data before this point was in the 20 byte header. If there is no more data, as in the header was only
        being processed, then end here. If there is more than 20 bytes, then */
        if (lsaBuffer.length == 20)
            return;



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

    /**<p><h1>Age This LSA</h1></p>
     * <p>Increments the age, which should be done every second. If the age gets too old, remove it from the LSA list.</p>
     * <p>The local RLSA should not be blindly deleted, so the LSDB remove method resets the local RLSA</p>
     */
    void ageLSA() {
        lsAge++;
        if (lsAge >= MAX_AGE)
            Config.lsdb.removeRLSA(this);

    }

    /**<p><h1>Are Two LSAs Equal</h1></p>
     * <p>Determine if a provided RLSA is equal to this LSA. Two LSAs are equal when 1) LS types are equal.
     * 2) LS sequence numbers are equal. And 3) the advertising routers are the same.</p>
     * @param checkLSA RLSA to check against
     * @return whether the LSAs are the same
     */
    boolean areLSAsEqual(RLSA checkLSA) {
        return checkLSA.lsSeqNumber == this.lsSeqNumber &
                checkLSA.advertisingRouter == this.advertisingRouter;
    }

    /**<p>Is This LSA Newer</p>
     * <p>Determine if this RLSA is newer or equal in age to a check RLSA. This can determine which LSA is more
     * up-to-date</p>
     * @param checkLSA RLSA to check against
     * @return whether this LSA is newer or identical in age to the check LSA
     */
    boolean isThisLSANewer(RLSA checkLSA) {
        //true if this.lsAge is newer or the same as the test. False if lsAge is newer.
        return checkLSA.lsAge < this.lsAge;
    }

    /**<p><h1>Make Router LSA Header Buffer</h1></p>
     * <p>Use the data stored in the local object to create an LSA header, conforming to the OSPF LSA 20 byte header.</p>
     * <p>This header is used as a summary for DBD packets, or used in creation of the full router LSA. It will contain
     * a completed length and checksum field, which should be overridden once the end buffer is complete.</p>
     * @return the router LSA header buffer
     */
    public byte[] makeRLSAHeaderBuffer() {
        byte[] buffer = {
                0x00, 0x00,             //lsage //0,1
                            0x00,       //options //2
                                  0x01, //LSA Type (Router LSA) //3
                0x00, 0x00, 0x00, 0x00, //LS ID //4,5,6,7
                0x00, 0x00, 0x00, 0x00, //Advertising router //8,9,10,11
                0x00, 0x00, 0x00, 0x00, //ls sequence number //12,13,14,15
                0x00, 0x00,             //ls checksum //16,17
                            0x00, 0x14, //length, header 20 bytes (unless overridden)//18,19
        };

        byte[] lsAgeB = Ints.toByteArray(this.lsAge);
        buffer[0] = lsAgeB[2];
        buffer[1] = lsAgeB[3];

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

        //Finally, update the length field and checksum field. Return the buffer containing the updated fields.
        //Will be overriden anyway by makeRLSABuffer, and length will remain the same.
        return updateLSAHChecksumAndLength(buffer);
    }

    /**<p><h1>Make Router LSA Buffer</h1></p>
     * <p>Makes a full router LSA buffer, containing all link data and LSA flags. Flags are assumed as 0. The returned
     * value has a completed LSA header checksum and length field.</p>
     * @return a full router LSA buffer
     */
    public byte[] makeRLSABuffer() {
        byte[] buffer = {
                0x00, 0x00,             //0, v, b, e, 0 (ignore flags) //20,21
                            0x00, 0x00, //number of links //22,23
                //+ 12 bytes per link
        };

        buffer = Bytes.concat(makeRLSAHeaderBuffer(), buffer);

        //no links
        //possible to overflow as size can be greater than the short. Not a practical issue.
        byte[] linkNoB = Ints.toByteArray(this.links.size());
        buffer[22] = linkNoB[2];
        buffer[23] = linkNoB[3];

        //link data (12 bytes per link).
        for (LinkData link: this.links)
        {
            buffer = Bytes.concat(buffer, link.makeBuffer());
        }

        //Finally, update the length field and checksum field. Return the buffer containing the updated fields.
        return updateLSAHChecksumAndLength(buffer);
    }

    /**<p><h1>Update LSA Header Checksum and Length Fields</h1></p>
     * <p>Updates the fields length and checksum for a given buffer. The buffer is assumed to take the format of an
     * LSA, where the first 20 bytes conform to the standard LSA header. Checksum is in bytes 16 and 17, length is in
     * bytes 18 and 19. The checksum used is specifically the fletcher16 checksum for LSA headers.</p>
     * @param buffer buffer containing fields to update, which will also derive the length and checksum
     * @return buffer containing updated checksum and length fields
     */
    private byte[] updateLSAHChecksumAndLength(byte[] buffer) {
        //check buffer to be updated contains bytes to be updated, otherwise the method was called with invalid data.
        if (buffer.length < 20)
            StdDaemon.handleDaemonError("RLSA update checksum method was passed an invalid buffer", null);

        //length
        //could also overflow. Again, with the number of interfaces in play, not likely.
        byte[] pLength = Shorts.toByteArray((short) buffer.length);
        buffer[18] = pLength[0];
        buffer[19] = pLength[1];

        //checksum
        //create buffer that doesn't contain the ls age field and checksum. Use that to calculate a checksum.
        //Take returned int, update the value in the buffer.
        byte[] checksumBuffer =  buffer;
        checksumBuffer[0] = checksumBuffer[1] = checksumBuffer[16] = checksumBuffer[17] = 0x00;//checksum is buffer without age.
        byte[] checksum = Ints.toByteArray(fletcherChecksum16(checksumBuffer));
        buffer[16] = checksum[2];
        buffer[17] = checksum[3];

        return buffer;
    }
    //endregion OBJECT METHODS
}

