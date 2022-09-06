package com.mws.ospf;

import inet.ipaddr.IPAddressString;

import java.util.List;

/**<p><h1>Node ThisNode</h1></p>
 * <p>An instance of Node that represents the specific node the code is running on. Contains a list of all router
 * interfaces, which this node doesn't need to know about neighbour nodes.</p>
 */
public class ThisNode extends Node {
    //region OBJECT PROPERTIES
    List<RouterInterface> interfaceList;
    String hostname;
    //endregion

    //region OBJECT METHODS
    /**<p><h1>This Node</h1></p>
     * <p>Construct a This Node object with the specified RID, hostname and list of router interfaces</p>
     * @param rid Router ID of this node
     * @param hostname Hostname of this node
     * @param interfaceList List of interfaces for this node
     */
    public ThisNode(IPAddressString rid, String hostname, List<RouterInterface> interfaceList) {
        super(rid);
        this.interfaceList = interfaceList;
        this.hostname = hostname;
    }
    //endregion
}
