package com.mws.ospf;

import com.google.common.primitives.Bytes;
import inet.ipaddr.IPAddressNetwork;
import inet.ipaddr.IPAddressString;

/**<p><h1>Link Data Class</h1></p>
 * <p>Class to store link state information about a specific link on a node. Can store link state information for
 * either ThisNode or learned from a NeighbourNode.</p>
 */
class LinkData {
    //region STATIC CONSTANTS
    static final int LINK_DATA_SIZE = 12;
    //endregion STATIC CONSTANTS

    //region OBJECT PARAMETERS
    private final byte[] linkID;
    private final byte[] data;
    /**
     * Assumed to be {0x01}, for p2p link.
     */
    private final byte[] type;
    private final byte[] metric;
    //endregion OBJECT PARAMETERS

    //region OBJECT METHODS
    /**<p><h1>Link Data Constructor</h1></p>
     * <p>Construct a Link Data object containing link state information for an LSA record. Data is stored in a way to
     * easily create a buffer from it. Most of the time data should be retrieved by the makeBuffer() method.</p>
     * @param linkID id for a specific link to relate data to
     * @param data link state data to store, equal to four bytes in length. Can be a neighbour RID
     * @param metric a short in byte array form, must equal 2 bytes in size
     */
    public LinkData(IPAddressString linkID, IPAddressString data, byte[] metric) {
        this.linkID = linkID.getAddress().getBytes();
        this.data = data.getAddress().getBytes();
        this.type = new byte[]{0x01};//p2p
        if (metric.length != 2)
            throw new IllegalArgumentException("Metric must be two bytes");

        this.metric = metric;
    }

    /**<p><h1>Link ID Getter</h1></p>
     * <p>Get the one property link ID, in the format it is provided in to the constructor.</p>
     * @return the link ID of the object
     */
    public IPAddressString getLinkID() {
        return new IPAddressNetwork.IPAddressGenerator().from(
                this.linkID
        ).toAddressString();
    }

    /**<p><h1>Link Data Getter</h1></p>
     * <p>Get the one property link data, in the format it is provided in to the constructor.</p>
     * @return the link data of the object
     */
    public IPAddressString getData() {
        return new IPAddressNetwork.IPAddressGenerator().from(
                this.data
        ).toAddressString();
    }

    /**<p><h1>Link Type Getter</h1></p>
     * <p>Get the one property type, in the byte format it is displayed in.</p>
     * @return the link type property of the object
     */
    public byte getVarType() {
        return this.type[0];
    }

    /**<p><h1>Link Metric Getter</h1></p>
     * <p>Get the one property link metric, in the numeric format that makes it easy to work with, and represents it
     * best while providing padding to prevent it being negative number.</p>
     * @return the link metric of the object
     */
    public int getMetric() {
        return (this.metric[0] << 8 & 0xff00) | (this.metric[1] & 0xff);
    }

    /**<p><h1>Make Link Data Buffer</h1></p>
     * <p>Creates the link data object as the desired 12 byte long buffer, used in creating and sending an LSA from this
     * data.</p>
     * @return link data converted to a buffer
     */
    byte[] makeBuffer() {
        //Represented in a different format than I usually make this method. Only #TOS is static in the method, and so
        //the buffer can be created by concatenating all the buffers that make up this object. This saves updating
        //variables constantly, and this class was designed about making this method as efficient as I can.
        return Bytes.concat(
                this.linkID, //0,1,2,3
                this.data, //4,5,6,7
                this.type, //8
                new byte[] {
                        0x00,//number of TOS //9
                },
                this.metric //10,11
                //TOS ID, ignore, TOS Metric, (PER TOS ENTRY)
        );
    }
    //endregion OBJECT METHODS
}
