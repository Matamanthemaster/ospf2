package com.mws.ospf.pdt;

/**
 * Field used to specify what role a packet has. Can be used as a sanity check, and status of hello procedure. It forms
 * the bottom half nibble of a byte, in conjunction with protocol version in all packets.
 */
public enum MessageType {
    UNDEFINED(0),//0 – Undefined. Likely an error.
    UNENC_HELLO_SOLICIT(1),//1 – Unencrypted Hello, Solicitation (Unsolicited)
    UNENC_HELLO_CR(2),//2 – Unencrypted Hello, Challenge-response (Solicited)
    UNENC_HELLO_ENC(3),//3 – Unencrypted Hello, Encryption (Solicited)
    //3* RESERVED
    ENC_HELLO_I(7),//7 – Encrypted Hello, Information
    DBD(8),//8 – Database Description
    REQUEST(9),//9 – Request
    UPDATE(10)//10 – Update
    //5* RESERVED
    ;

    private final byte value;

    MessageType(int value) {
        this.value = (byte) value;
    }

    public byte getValue() {
        return value;
    }

}
