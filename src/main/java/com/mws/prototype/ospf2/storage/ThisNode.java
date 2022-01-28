package com.mws.prototype.ospf2.storage;

import inet.ipaddr.IPAddress;
import java.util.List;

public class ThisNode extends Node {

    List<RouterInterface> interfaceList;

    public ThisNode(IPAddress rID, List<RouterInterface> interfaceList) {
        super(rID);
        this.interfaceList = interfaceList;
    }
}
