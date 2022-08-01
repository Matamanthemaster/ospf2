package com.mws.ospf;

import inet.ipaddr.IPAddressString;

import java.util.List;

public class ThisNode extends Node {

    List<RouterInterface> interfaceList;
    String hostname;

    public ThisNode(IPAddressString rID, String hostname, List<RouterInterface> interfaceList) {
        super(rID);
        this.interfaceList = interfaceList;
        this.hostname = hostname;
    }
}
