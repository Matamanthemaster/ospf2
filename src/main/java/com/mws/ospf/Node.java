package com.mws.ospf;

import inet.ipaddr.IPAddressString;

import java.util.ArrayList;
import java.util.List;

/**<p><h1>Node</h1></p>
 * <p>Base class for NeighbourNode and ThisNode. stores the common variables between both extend classes. Basic node
 * constraints are defined in this class.</p>
 */
abstract class Node {
    //region OBJECT PROPERTIES
    private IPAddressString rid; //Router ID, a 32-bit integer represented in dotted decimal, identically to an IPv4 address.
    List<IPAddressString> knownNeighbours = new ArrayList<>();
    //endregion

    //region OBJECT METHODS
    /**<p><h1>Node</h1></p>
     * <p>Construct a generic node with a specified rid that matches constraints of dotted decimal</p>
     * <p></p>
     * <p>e.g. 0.0.0.1, 1.1.1.1, 200.200.200.200</p>
     * @param rid Router ID, 4 byte dotted decimal string, cannot be 0.0.0.0
     * @throws IllegalArgumentException rid provided is not a 4 character string or is equal to 0.0.0.0
     * @throws NumberFormatException rid provided cannot be parsed into a number (contains illegal characters)
     */
    public Node(IPAddressString rid) {
        setRID(rid);
    }

    /**<p><h1>Set Router ID</h1></p>
     * <p>Set Router ID from a 4 byte dotted decimal string. Cannot be 0.0.0.0.</p>
     * <p></p>
     * <p>e.g. 0.0.0.1, 1.1.1.1, 200.200.200.200</p>
     * @param rid Router ID, 4 byte dotted decimal string, cannot be 0.0.0.0
     * @throws IllegalArgumentException rid provided is not a 4 character string or is equal to 0.0.0.0
     * @throws NumberFormatException rid provided cannot be parsed into a number (contains illegal characters)
     */
    public void setRID(IPAddressString rid) {
        if (!rid.isValid())
            throw new IllegalArgumentException("RID isn't valid");
        if (!rid.isIPv4())
            throw new IllegalArgumentException("RID must be IPv4");
        if (rid.isEmpty())
            throw new IllegalArgumentException("RID cannot be an empty IPAddressString");
        if (rid.isPrefixed())
            throw new IllegalArgumentException("RID should not be a prefix string");
        if (rid.isZero())
            throw new IllegalArgumentException("RID cannot be 0.0.0.0");

        this.rid = rid;
    }

    /**<p><h1>Get Router ID</h1></p>
     * <p>Return the Router ID as stored string</p>
     * @return The node's router ID
     */
    public IPAddressString getRID() {
        return this.rid;
    }

    /**<p><h1>Get RID as int</h1></p>
     * <p>Converts the RID IPAddressString to a binary string, which is parsed to an integer, with preserved MSB and LSB.
     * The bigger the RID, the bigger the number. The method is useful for comparing which RID is bigger.</p>
     * @return an integer representing the RID, from a binary string
     * @throws NumberFormatException if the node rid is not a number.
     */
    public int getRIDAsInt() {
        return Integer.parseInt(rid.getAddress().toBinaryString(), 2);
    }

    /**<p><h1>Get Known Neighbours String</h1></p>
     * <p>Returns a list of known neighbours as a comma separated string list. If no known neighbours exist, an empty
     * string is returned</p>
     * @return List of known neighbours, as comma separated list.
     */
    public String getKnownNeighboursString() {
        if (knownNeighbours.size() == 0)
            return "";

        StringBuilder neighbours = new StringBuilder();
        for (IPAddressString neighbour: knownNeighbours)
        {
            neighbours.append(neighbour.toString()).append(",");
        }
        neighbours = new StringBuilder(neighbours.substring(0, neighbours.length() - 1));
        return neighbours.toString();
    }

    /**<p><h1>Get RID as Bytes</h1></p>
     * <p>Returns a 4 byte array containing the node rid in bytes</p>
     * @return 4 byte array of the Router ID in Big Endian format
     * @throws NumberFormatException if the node rid is not a number.
     */
    public byte[] getRIDBytes() {
        return rid.getAddress().getBytes();
    }
    //endregion
}
