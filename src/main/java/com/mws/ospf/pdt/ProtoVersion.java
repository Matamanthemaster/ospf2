package com.mws.ospf.pdt;

/**
 * Protocol Version. Standard OSPF is 2, encrypted not defined in the original specification. Take 4 to refer to
 * encryption. It forms the first top half nibble of all packets, with message type following.
 */
public enum ProtoVersion {
    Standard(2),
    Encrypted(4);

    private final byte value;

    ProtoVersion(int value) {
        this.value = (byte) value;
    }

    public byte getValue() {
        return value;
    }
}

