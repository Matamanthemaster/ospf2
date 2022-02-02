package com.mws.ospf2;

import inet.ipaddr.ipv4.IPv4Address;

/**
 * Base class for NeighbourNode and ThisNode. stores the common variables between both extend classes.
 */
public abstract class Node {
    public IPv4Address rID; //Router ID, a 32-bit integer represented in dotted decimal, identically to an IPv4 address.
    public Node(IPv4Address rID)
    {
        this.rID = rID;
    }
}