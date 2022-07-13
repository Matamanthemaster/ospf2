package com.mws.ospf;

/**
 * Base class for NeighbourNode and ThisNode. stores the common variables between both extend classes.
 */
public abstract class Node {
    public short NID; //Router ID, a 32-bit integer represented in dotted decimal, identically to an IPv4 address.
    public Node(short NID)
    {
        this.NID = NID;
    }
}
