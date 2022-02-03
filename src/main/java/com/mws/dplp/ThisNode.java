package com.mws.dplp;

import inet.ipaddr.ipv4.IPv4Address;

import java.util.List;

public class ThisNode extends Node {

    List<RouterInterface> interfaceList;
    String hostname;

    public ThisNode(short NID, String hostname, List<RouterInterface> interfaceList) {
        super(NID);
        this.interfaceList = interfaceList;
        this.hostname = hostname;
    }
}
