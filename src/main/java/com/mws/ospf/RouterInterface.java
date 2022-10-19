package com.mws.ospf;

import inet.ipaddr.IPAddress;
import org.jetbrains.annotations.NotNull;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

/**<p><h1>Router Interface</h1></p>
 * <p>Store for information about a specific router interface. A router interface is one used for routing packets,
 * in which data is not switched. It is the interface of a host. In this protocol, it is an interface an IGP works on.</p>
 * <p>This node has several router interfaces OSPF will work over. An interface has a name, that is used in the OS.
 * each node also has an IP that is also used in the OS.</p>
 */
public class RouterInterface {
    //region STATIC PROPERTIES
    private static final List<RouterInterface> _RouterInterfaces = new ArrayList<>();
    private static final long REFERENCE_BANDWIDTH = InterfaceType.E100BASET.getBandwidth();
    //endregion

    //region STATIC METHODS
    /**<p><h1>Get Interface Object by IP</h1></p>
     * <p>Searches the internal list of all created Router Interfaces for a specific interface that
     * matches in IP.</p>
     * @param ip an IP address to search for
     * @return First RouterInterface associated with the IP, or null
     */
    public static RouterInterface getInterfaceByIP(IPAddress ip) {
        for(RouterInterface r: _RouterInterfaces) {
            if (r.addrIPv4.toIPv4().equals(ip.toIPv4()))
                return r;
        }
        return null;
    }

    /**<p><h1>Get Interface Object by Network Address</h1></p>
     * <p>Searches the internal list of all created Router Interfaces for a specific interface that shares the same
     * network address. Network addresses is a host address zeroed after CIDR prefix length. Returned interface will
     * only be an interface that shares the same network address as the specified interface.</p>
     * <p>The provided ip address' prefix length is not taken into account, only the CIDR prefix of the checking
     * interface.</p>
     * @param ip ip address to check against all interfaces for a match in network address
     * @return a router interface object that shares the same network address as ip
     */
    public static RouterInterface getInterfaceByIPNetwork(IPAddress ip) {
        for (RouterInterface r: _RouterInterfaces) {
            if (r.isAddressInNetwork(ip)) {
                return r;
            }
        }
        return null;
    }
    //endregion

    //region OBJECT PROPERTIES
    private final String name;
    public IPAddress addrIPv4;
    public List<IPAddress> addrIPv6;//List of addresses assigned to a router interface.
    public InterfaceType type; //Interface type identifier. Used by code to determine what type of interface it is. Uses enum
    public Boolean isEnabled; //Interface on?
    public long bandwidth; //BW used by default OSPF calculation, derived from interface type in constructor.
    int cost = -1;
    DHExchange dhExchange;
    //endregion

    //region OBJECT METHODS
    /**<p><h1>RouterInterface</h1></p>
     * <p>Construct a router interface object with specified parameters.</p>
     * <p>A storage class for interface parameters. Stores IP addresses as classes that can store prefixes, and general parameters.</p>
     * @param name the name of the interface. Cisco e.g. GigabitEthernet0/0/0. Linux e.g. eth0 or enp5s0
     * @param ipv4 an IPv4 address assigned to an interface. Stores prefix information
     * @param ipv6 a list of IPv6 addresses assigned to an interface. Stores prefix information
     * @param type an enum value defining a type of interface, derives static values such as bandwidth
     * @param enabled interface status, is it up
     * @throws IllegalArgumentException name contains invalid characters
     */
    public RouterInterface(@NotNull String name, IPAddress ipv4, List<IPAddress> ipv6, InterfaceType type, boolean enabled) {
        if (name.contains(" "))
            throw new IllegalArgumentException();

        this.name = name;
        this.addrIPv4 = ipv4;
        this.addrIPv6 = ipv6;
        this.type = type;
        this.bandwidth = type.getBandwidth();
        this.isEnabled = enabled;

        _RouterInterfaces.add(this);

        //PrintAllValuesToSTDOut();//Debug Line, verify data is correct.
    }

    /**<p><h1>Get Name</h1></p>
     * <p>Getter for the RouterInterface name property</p>
     * @return interface name
     */
    public String getName() {
        return name;
    }

    /**<p><h1>Get Cost</h1></p>
     * <p>Returns a value for cost, using multiple industry techniques to derive a value.</p>
     * <p>First, if cost is statically defined, use this value. Second, calculate a cost from the reference bandwidth
     * and interface bandwidth. If the interface bandwidth is greater than the reference (as in, calculation value is
     * between 0 and 1), treat the cost as 1.</p>
     * @return interface cost calculated
     */
    public int getCost() {
        //Use static cost if defined.
        if (cost != -1)
            return cost;

        //if cost < 1, set cost = 1 (bandwidth = reference; reference/reference = 1 = cost)
        long bandwidth = this.bandwidth;
        if (bandwidth > REFERENCE_BANDWIDTH)
            bandwidth = REFERENCE_BANDWIDTH;

        //Perform cost calculation, as longs, which is bandwidth. Reference will scale down number a lot. End value
        //needs to be an unsigned short, so return it as an int, only returning the first 16 bits.
        bandwidth = REFERENCE_BANDWIDTH / bandwidth;
        return (int) bandwidth & 0x0000ffff;
    }

    /**<p><h1>ToNetworkInterface</h1></p>
     * <p>Map the RouterInterface name to a NetworkInterface linked to the interface in the OS. For this, the name
     * property must be identical to one in the OS.</p>
     * @return NetworkInterface object for the interface.
     * @throws SocketException if an I/O error occurs
     */
    public NetworkInterface toNetworkInterface() throws SocketException {
        return NetworkInterface.getByName(this.name);
    }

    /**<p><h1>Is Address in Network</h1></p>
     * <p>Returns whether provided IP is within the network address range of the current interface. That is, if the
     * provided IP uses the same prefix length mask as the current interfaces IPv4, they are in the same network.</p>
     * @param ip ip to check against the current interface's addrIPv4
     * @return true if the network addresses match
     */
    public boolean isAddressInNetwork(IPAddress ip) {
        ip = ip.setPrefixLength(addrIPv4.getNetworkPrefixLength());
        return addrIPv4.toZeroHost().equals(ip.toZeroHost());
    }

    /**<p><h1>Print Properties Test</h1></p>
     * <p>Testing method to be thrown into constructor.</p>
     */
    private void printAllValuesToSTDOut() {
        System.out.println(this.name);
        System.out.println("    " + this.addrIPv4.toString());
        for (IPAddress a: this.addrIPv6) {
            System.out.println("    " + a.toString());
        }
        System.out.println("    " + this.type.toString());
        System.out.println("    " + this.isEnabled.toString());
    }
    //endregion
}
