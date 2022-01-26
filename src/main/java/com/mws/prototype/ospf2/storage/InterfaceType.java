package com.mws.prototype.ospf2.storage;


/**
 * An enumerable list of common interface types. Can be used to derive interface information
 */
public enum InterfaceType {
    /**
     * A T1 serial interface operating at 1.544Mbps.
     * BW: 1.544Mbps
     */
    T1(1544000L),
    /**
     * An E1 serial interface operating at 2.048Mbps
     * BW: 2.048Mbps
     */
    E1(2048000L),
    /**
     * A 10Base-T interface. Ethernet running at 10Mbps
     * BW: 10Mbps
     */
    E10BASET(10000000L),
    /**
     * A 100Base-T interface. Ethernet running at 100Mbps
     * BW: 100Mbps
     */
    E100BASET(100000000L),
    /**
     * A 1000Base-T interface. Ethernet running at 1Gbps
     * BW: 1Gbps
     */
    E1000BASET(1000000000L),
    /**
     * A 2.5GBase-T interface. Ethernet running at 2.5Gbps
     * BW: 2.5Gbps
     */
    E2_5GBASET(2500000000L),
    /**
     * A 5GBase-T interface. Ethernet running at 5Gbps
     * BW: 5Gbps
     */
    E5GBASET(5000000000L),
    /**
     * A 10GBase-T interface. Ethernet running at 10Gbps
     * BW: 10Gbps
     */
    E10GBASET(10000000000L);

    /**
     * Constructor for enum InterfaceType for mapping each enum to a bandwidth value
     * @param bandwidth the standard bandwidth, in bits per second, of an interface type
     */
    InterfaceType(long bandwidth)
    {
        this.bandwidth = bandwidth;
    }

    //Internal storage for the raw bandwidth value
    private final long bandwidth;

    /**
     * Get the bandwidth associated with a specific enum of InterfaceType
     * @return Interface Bandwidth
     */
    public long getBandwidth()
    {
        return this.bandwidth;
    }
}