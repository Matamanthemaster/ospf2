package com.mws.ospf2;

import com.mws.ospf2.storage.InterfaceType;
import inet.ipaddr.IPAddress;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Store for information about a specific router interface.
 */
public class RouterInterface {

    public String getName() {
        return name;
    }

    private final String name;
    public IPAddress addrIPv4;
    public List<IPAddress> addrIPv6;//List of addresses assigned to a router interface.
    public InterfaceType type; //Interface type identifier. Used by code to determine what type of interface it is. Uses enum
    public Boolean isEnabled; //Interface on?
    public Boolean isPassive; //Does the interface send routing updates?
    public long bandwidth; //BW used by default OSPF calculation, derived from interface type in constructor.

    /**
     * A storage class for interface parameters. Stores IP addresses as classes that can store prefixes, and general parameters.
     * @param name the name of the interface. Cisco e.g. GigabitEthernet0/0/0. Linux e.g. eth0 or enp5s0
     * @param ipv4 an IPv4 address assigned to an interface. Stores prefix information
     * @param ipv6 a list of IPv6 addresses assigned to an interface. Stores prefix information
     * @param type an enum value defining a type of interface, derives static values such as bandwidth.
     * @param enabled interface status, is it up
     * @throws IllegalArgumentException name contains invalid characters
     */
    public RouterInterface(@NotNull String name, IPAddress ipv4, List<IPAddress> ipv6, InterfaceType type, Boolean enabled) {
        if (name.contains(" "))
            throw new IllegalArgumentException();

        this.name = name;
        this.addrIPv4 = ipv4;
        this.addrIPv6 = ipv6;
        this.type = type;
        this.bandwidth = type.getBandwidth();
        this.isEnabled = enabled;
    }
}