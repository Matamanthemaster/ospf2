package com.mws.ospf;

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
