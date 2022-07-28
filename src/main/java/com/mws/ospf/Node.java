package com.mws.ospf;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for NeighbourNode and ThisNode. stores the common variables between both extend classes.
 */
public abstract class Node {
    /*
     * OBJECT PROPERTIES
     */
    private String rID; //Router ID, a 32-bit integer represented in dotted decimal, identically to an IPv4 address.
    public List<String> knownNeighbours = new ArrayList<>();

    /*
     * OBJECT METHODS
     */
    /**<p><h1>Node</h1></p>
     * <p>Construct a generic node with a specified rID that matches constraints of dotted decimal</p>
     * <p></p>
     * <p>e.g. 0.0.0.1, 1.1.1.1, 200.200.200.200</p>
     * @param rID Router ID, 4 byte dotted decimal string, cannot be 0.0.0.0
     * @throws IllegalArgumentException rID provided is not a 4 character string or is equal to 0.0.0.0
     * @throws NumberFormatException rID provided cannot be parsed into a number (contains illegal characters)
     */
    public Node(String rID) {
        SetRID(rID);
    }

    /**<p><h1>Set Router ID</h1></p>
     * <p>Set Router ID from a 4 byte dotted decimal string. Cannot be 0.0.0.0.</p>
     * <p></p>
     * <p>e.g. 0.0.0.1, 1.1.1.1, 200.200.200.200</p>
     * @param rID Router ID, 4 byte dotted decimal string, cannot be 0.0.0.0
     * @throws IllegalArgumentException rID provided is not a 4 character string or is equal to 0.0.0.0
     * @throws NumberFormatException rID provided cannot be parsed into a number (contains illegal characters)
     */
    public void SetRID(String rID) {
        String[] rIDByte = rID.split("\\.");
        if (rIDByte.length != 4)
            throw new IllegalArgumentException("rID provided must have 4 octets (e.g. 0.0.0.1)");

        //Look for alpha characters, and check rID is not 0.0.0.0
        try {
            int zeroByteCount = 0;
            for (int i = 0; i < 4; i++)
            {
                if (Integer.parseInt(rIDByte[i]) == 0)
                    zeroByteCount++;
            }
            if (zeroByteCount == 4)
                throw new IllegalArgumentException("rID provided cannot be 0.0.0.0");

        } catch (NumberFormatException ex) {
            throw new NumberFormatException("rID provided is not a number");
        }
        this.rID = rID;
    }

    /**<p><h1>Get Router ID</h1></p>
     * <p>Return the Router ID as stored string</p>
     * @return The node's router ID
     */
    public String GetRID() {
        return this.rID;
    }

    public String GetKnownNeighboursString() {
        String neighbours = "";
        for (String neighbour: knownNeighbours)
        {
            neighbours += neighbour + ",";
        }
        neighbours = neighbours.substring(0, neighbours.length()-1);
        System.out.println(neighbours);
        return neighbours;
    }

    /**<p><h1>Get RID as Bytes</h1></p>
     * <p>Returns a 4 byte array containing the node RID in bytes</p>
     * @return 4 byte array of the Router ID in Big Endian format
     * @throws NumberFormatException if the node rID is not a number.
     */
    public byte[] GetRIDBytes() {
        byte[] rIDB = new byte[4];
        String rIDS = this.rID.replaceAll("\\.", "");
        for (int i = 0; i < 4; i++)
        {
            rIDB[i] = (byte) Integer.parseInt(String.valueOf(rIDS.charAt(i)));
        }
        return rIDB;
    }
}
