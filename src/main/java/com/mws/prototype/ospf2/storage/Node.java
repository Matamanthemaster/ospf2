package com.mws.prototype.ospf2.storage;

import inet.ipaddr.IPAddress;

import java.net.Inet4Address;
import java.util.List;

/**
 * Base class for NeighbourNode and ThisNode. stores the common variables between both extend classes.
 */
public class Node {
    public IPAddress rID; //Router ID, a 32-bit integer represented in dotted decimal, identically to an IPv4 address.

    public Node(IPAddress rID)
    {
        this.rID = rID;
    }
}
