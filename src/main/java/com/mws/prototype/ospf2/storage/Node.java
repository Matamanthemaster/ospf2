package com.mws.prototype.ospf2.storage;

import java.net.Inet4Address;
import java.util.List;

public class Node {
    public Inet4Address rID; //Router ID, a 32-bit integer represented in dotted decimal, identically to an IPv4 address.
    public List<RouterInterface> interfaces;

    public Node(Inet4Address rID, List<RouterInterface> interfaces)
    {
        this.rID = rID;
        this.interfaces = interfaces;
    }
}
