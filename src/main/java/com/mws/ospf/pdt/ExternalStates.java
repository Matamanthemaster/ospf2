package com.mws.ospf.pdt;

/**
 * A state of either a neighbour node or network state. Not all are applicable to both. They are transmitted in EH-I
 * packets, as part of a byte, 3 bits of which are ever specified, to match the 8 states. The next 3 bits are reserved,
 * and two bits used as flags.
 */
public enum ExternalStates {
    DOWN(0),
    INIT(1),
    TWOWAY(2),
    EXSTART(3),
    EXCHANCE(4),
    LOADING(5),
    FULL(6);

    public byte value;

    ExternalStates(int value) {
        this.value = (byte) value;
    }
}
