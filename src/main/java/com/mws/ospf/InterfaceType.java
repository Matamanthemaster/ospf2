package com.mws.ospf;


/**<p><h1>Interface Types</h1></p>
 * <p>An enumerable list of common interface types. Can be used to derive interface information</p>
 */
enum InterfaceType {
    /**<p><h1>T1</h1></p>
     * <p>A T1 serial interface operating at 1.544Mbps.</p>
     * <p><b>BW: 1.544Mbps</b></p>
     */
    T1(1544000L),
    /**<p><h1>E1</h1></p>
     * <p>An E1 serial interface operating at 2.048Mbps</p>
     * <p><b>BW: 2.048Mbps</b></p>
     */
    E1(2048000L),
    /**<p><h1>10Base-T</h1></p>
     * <p>A 10Base-T interface. Ethernet running at 10Mbps</p>
     * <p><b>BW: 10Mbps</b></p>
     */
    E10BASET(10000000L),
    /**<p><h1>100Base-T</h1></p>
     * <p>A 100Base-T interface. Ethernet running at 100Mbps</p>
     * <p><b>BW: 100Mbps</b></p>
     */
    E100BASET(100000000L),
    /**<p><h1>1000Base-T</h1></p>
     * <p>A 1000Base-T interface. Ethernet running at 1Gbps</p>
     * <p><b>BW: 1Gbps</b></p>
     */
    E1000BASET(1000000000L),
    /**<p><h1>2.5GBase-T</h1></p>
     * <p>A 2.5GBase-T interface. Ethernet running at 2.5Gbps</p>
     * <p><b>BW: 2.5Gbps</b></p>
     */
    E2_5GBASET(2500000000L),
    /**<p><h1>5GBase-T</h1></p>
     * <p>A 5GBase-T interface. Ethernet running at 5Gbps</p>
     * <p><b>BW: 5Gbps</b></p>
     */
    E5GBASET(5000000000L),
    /**<p><h1>10GBase-T</h1></p>
     * <p>A 10GBase-T interface. Ethernet running at 10Gbps</p>
     * <p><b>BW: 10Gbps</b></p>
     */
    E10GBASET(10000000000L);

    //Internal storage for the raw bandwidth value
    private final long bandwidth;

    /**<p><h1>Interface Type Constructor</h1></p>
     * <p>Constructor for enum InterfaceType for mapping each enum to a bandwidth value</p>
     * @param bandwidth the standard bandwidth, in bits per second, of an interface type
     */
    InterfaceType(long bandwidth)
    {
        this.bandwidth = bandwidth;
    }

    /**<p><h1>Get Bandwidth</h1></p>
     * <p>Get the bandwidth associated with a specific enum of InterfaceType</p>
     * @return Interface Bandwidth
     */
    public long getBandwidth()
    {
        return this.bandwidth;
    }

    /**<p><h1>Convert ENUM to String</h1></p>
     * <p>Convert enum type to string, based on the specific enum. Useful for storage in a file.</p>
     * @return a text string that represents the string value.
     */
    @Override
    public String toString() {
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


    /**<p><h1>From String</h1></p>
     * <p>Map of a string value to an interface type ENUM. Values identical to that returned by toString()</p>
     * @param type String value to map to an InterfaceType ENUM
     * @return the associated ENUM InterfaceType
     * @throws IllegalArgumentException parameter type is not valid
     */
    public static InterfaceType fromString(String type) {
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
            default -> throw new IllegalArgumentException("Unexpected value: " + type);
        }
    }
}