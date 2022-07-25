package com.mws.ospf;

/**
 * Base class for NeighbourNode and ThisNode. stores the common variables between both extend classes.
 */
public abstract class Node {
    public String rID; //Router ID, a 32-bit integer represented in dotted decimal, identically to an IPv4 address.
    public Node(String rID)
    {
        this.rID = rID;
    }
}
