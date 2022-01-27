package com.mws.prototype.ospf2.storage;


/**
 * From RFC2328 A.3.3
 *
 */

public enum DDFlags {
    MS,//0x01 Master flag
    M, //0x02 More flag
    I,  //0x04 Initialize flag
    //0x08, 0x10, 0x20, 0x40, 0x80 unused
};



