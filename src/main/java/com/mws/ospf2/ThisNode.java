package com.mws.ospf2;

import inet.ipaddr.ipv4.IPv4Address;

import java.util.List;

public class ThisNode extends Node {

    List<RouterInterface> interfaceList;
    String hostname;

    public ThisNode(IPv4Address rID, String hostname, List<RouterInterface> interfaceList) {
        super(rID);
        this.interfaceList = interfaceList;
        this.hostname = hostname;
    }
}
