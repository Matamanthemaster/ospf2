package com.mws.dplp.pdt;

/**
 * Protocol Version. Not explicitly defined in the original specification, as it was a draft. We will take 0 to mean no
 * encryption, and 1 to mean the encrypted version, specified in the DPLP draft specification. It forms the first top
 * half nibble of all packets, with message type following.
 */
public enum ProtoVersion {
    Standard(0),
    Encrypted(1);

    private final byte value;

    ProtoVersion(int value) {
        this.value = (byte) value;
    }

    public byte getValue() {
        return value;
    }
}

