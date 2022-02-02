package com.mws.ospf2.storage;


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

    /**
     * Convert enum type to string, based on the specific enum. Useful for storage in a file.
     * @return a text string that represents the string value.
     */
    @Override
   public String toString()
   {
       switch (this)
       {
           case T1 -> {
               return "T1";
           }
           case E1 -> {
               return "E1";
           }
           case E10BASET -> {
                return "10Base-T";
           }
           case E100BASET -> {
               return  "100Base-T";
           }
           case E1000BASET -> {
               return "1000Base-T";
           }
           case E2_5GBASET -> {
               return "2.5GBase-T";
           }
           case E5GBASET -> {
               return "5GBase-T";
           }
           case E10GBASET -> {
               return "10GBase-T";
           }
           default -> throw new IllegalStateException("Unexpected value: " + this);
       }
   }

    public static InterfaceType fromString(String type)
    {
        switch (type)
        {
            case "T1" -> {
                return InterfaceType.T1;
            }
            case "E1" -> {
                return InterfaceType.E1;
            }
            case "10Base-T" -> {
                return InterfaceType.E10BASET;
            }
            case "100Base-T" -> {
                return InterfaceType.E100BASET;
            }
            case "1000Base-T" -> {
                return InterfaceType.E1000BASET;
            }
            case "2.5GBase-T" -> {
                return  InterfaceType.E2_5GBASET;
            }
            case "5GBase-T" -> {
                return InterfaceType.E5GBASET;
            }
            case "10GBase-T" -> {
                return InterfaceType.E10GBASET;
            }
            default -> throw new IllegalStateException("Unexpected value: " + type);
        }
    }
}