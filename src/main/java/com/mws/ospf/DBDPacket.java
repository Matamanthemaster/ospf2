package com.mws.ospf;

import java.util.ArrayList;
import java.util.List;

public class DBDPacket {
    public int ddSeqNo;
    public byte dbdFlags;
    public List<RLSA> listLSAs;

    public DBDPacket(int ddSeqNo, byte flags, List<RLSA> listLSAs) {
        if (ddSeqNo == -1)
            this.ddSeqNo = java.util.concurrent.ThreadLocalRandom.current().nextInt();
        else
            this.ddSeqNo = ddSeqNo;
        this.dbdFlags = flags;

        if (listLSAs.equals(null))
            this.listLSAs = new ArrayList<>();
        else
            this.listLSAs = listLSAs;
    }

    public boolean IsMSBitSet() {
        return (dbdFlags & 0x01) > 0;
    }

    public boolean ISMoreBitSet() {
        return (dbdFlags & 0x02) > 0;
    }

    public boolean IsInitBitSet() {
        return (dbdFlags & 0x04) > 0;
    }
}
