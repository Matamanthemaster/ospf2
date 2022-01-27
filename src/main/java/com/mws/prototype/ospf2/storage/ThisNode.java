package com.mws.prototype.ospf2.storage;

import java.net.Inet4Address;
import java.util.List;

public class ThisNode extends Node {

    List<RouterInterface> interfaceList;

    public ThisNode(Inet4Address rID, List<RouterInterface> interfaceList) {
        super(rID);
        this.interfaceList = interfaceList;
    }
}
