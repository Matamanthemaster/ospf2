package com.mws.ospf2.storage;

import java.util.EnumSet;
import java.util.List;

/**
 * From RFC2328#section-10:
 * <p></p>
 * <h3>Last received Database Description packet</h3>
 * <p>
 * The initialize(I), more (M) and master(MS) bits, Options field,
 * and DD sequence number contained in the last Database
 * Description packet received from the neighbor. Used to determine
 * whether the next Database Description packet received from the
 * neighbor is a duplicate.
 * </p>
 */
public class LastReceivedDD {
    int ddSeq;
    EnumSet<DDFlags> ddFlags;
    List<ExchangeOptions> options;

    public LastReceivedDD(int ddSeq, EnumSet<DDFlags> ddFlags, List<ExchangeOptions> options) {
        this.ddSeq = ddSeq;
        this.ddFlags = ddFlags;
        this.options = options;
    }
}
