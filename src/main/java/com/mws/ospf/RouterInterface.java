package com.mws.ospf;

import com.mws.ospf.pdt.InterfaceType;
import inet.ipaddr.IPAddress;
import org.jetbrains.annotations.NotNull;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

/**
 * Store for information about a specific router interface.
 */
public class RouterInterface {

    //STATIC COMPONENTS
    private static List<RouterInterface> _RouterInterfaces = new ArrayList<>();

    public static RouterInterface GetInterfaceByIP(IPAddress ip) {
        for(RouterInterface r: _RouterInterfaces)
        {
            if (r.addrIPv4.toInetAddress().equals(ip.toInetAddress()))
                return r;
        }
        return null;
    }

    //OBJECT COMPONENTS
    public String getName() {
        return name;
    }

    private final String name;
    public IPAddress addrIPv4;
    public List<IPAddress> addrIPv6;//List of addresses assigned to a router interface.
    public InterfaceType type; //Interface type identifier. Used by code to determine what type of interface it is. Uses enum
    public Boolean isEnabled; //Interface on?
    public long bandwidth; //BW used by default OSPF calculation, derived from interface type in constructor.

    /**
     * A storage class for interface parameters. Stores IP addresses as classes that can store prefixes, and general parameters.
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


    /**
     * @return NetworkInterface object for the interface.
     */
    public NetworkInterface ToNetworkInterface() throws SocketException {
        return NetworkInterface.getByName(this.name);
    }

    /**
     * Testing method to be thrown into constructor.
     */
    private void PrintAllValuesToSTDOut() {
        System.out.println(this.name);
        System.out.println("    " + this.addrIPv4.toString());
        for (IPAddress a: this.addrIPv6) {
            System.out.println("    " + a.toString());
        }
        System.out.println("    " + this.type.toString());
        System.out.println("    " + this.isEnabled.toString());
    }
}
