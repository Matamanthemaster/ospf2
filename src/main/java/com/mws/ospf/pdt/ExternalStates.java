package com.mws.ospf.pdt;

/**
 * A state of either a neighbour node or network state. Not all are applicable to both. They are transmitted in EH-I
 * packets, as part of a byte, 3 bits of which are ever specified, to match the 8 states. The next 3 bits are reserved,
 * and two bits used as flags.
 */
public enum ExternalStates {
    DOWN(0),//There is no relationship with any neighbour or network. Not a state that can be found, only the logical conclusion to there being no neighbour and no network.
    HELLO_A(1),//Both parties have authenticated one another using challenge response. For a network status, this state is not applicable.
    HELLO_E(2),//For a neighbour, it has been discovered through the hello protocol. The current node is aware of this neighbour’s existence, though no exchange has begun. There is a secure channel, and the challenge response protocol has been accepted. Protocol details are also accepted. For a network status, this state is not applicable.
    INCOMPATIBLE(3),//For a neighbour, discovery has concluded, and flags in the hello packets indicate this neighbour node is incompatible with this node. This could be a version incompatibility, or flags that are incompatible, including K values, or failed encryption. Nodes incompatible get soft locked from forming adjacency, setting a 1 hour timer, which neither nodes will attempt to form an adjacency with each other. For a network status, this state is not applicable.
    MA(4),//A neighbour that is participating in an election for LC or BLC or is not a LC or BLC. For a network status, this state is not applicable.
    EXCHANGE(5),//For a node status, this neighbour is actively sending or receiving information required for the weighted graph setup. For a network status, the network has been discovered through the exchange process, but calculation has not started yet.
    CALCULATING(6),//Exchange between this neighbour is complete. Currently this node is trying to solve the weighted graph problem. A network with this status is currently being calculated. Either in node internal state phase one or phase two.
    FULL(7);//The topology has converged for this neighbour. The network metric has been calculated and installed.

    public byte value;

    ExternalStates(int value) {
        this.value = (byte) value;
    }
}
