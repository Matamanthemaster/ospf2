package com.mws.ospf.pdt;

/**
 * Internal states, used by this node to track its current state, and help the machine know what role it is performing.
 */
public enum InternalStates {
    DOWN,//No routing protocol process has started. Likely the protocol is not configured, but the node is capable of running DPLP.
    INIT,//The current node is discovering neighbours through the hello protocol. The node could also be in a multi-access election.
    DISCOVERY,//All neighbours are known, and the current node is using the exchange protocol to discover remote networks through exchanging database information.
    PHASE1,//All raw data is now known. The data will be turned into basic routing information, that all nodes must share for a quick, initial loop free path. Generate the simple graph using Dijkstra’s algorithm, starting from the node with the lowest router ID. While in phase 1, there is no path yet.
    PHASE2,//The simple graph in phase 1 has been created, and this graph has convergence over the network. The node will now work on phase 2 calculations. At this point, the network is at least workable. All endpoint networks will be calculated a second time, starting at the endpoint network and working towards the current node, using Dijkstra’s algorithm again. This process is then repeated, removing the link directly connected to the current node, if possible.
    FULL//All networks on this node have been recalculated with the phase 2 calculation, with at most two paths to the network, and the phase 1 backup path in the event a catastrophic issue occurs.
}
