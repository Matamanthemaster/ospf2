package com.mws.prototype.ospf2.storage;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
* Class to store the OSPF config file reference, and values in java data types.
*/
public class Config {

    private static File fileConfig = null; //File reference, for IO Operations
    public static String hostname;
    public static ThisNode thisNode;
    public static NeighboursTable neighboursTable;
    public static LSDB lsdb;
    public static int inactiveTimerDelay;


    /**
     * Set method for the OSPF config, that uses the default config file path.
     * Creates a file if it doesn't already exist.
     * Also populates local variables with values from the config.
     * Default path: ./ospf.conf
     */
    public static void SetConfig() {
        CommonMain(System.getProperty("user.dir") + "ospf.conf");
    }

    /**
     * Set method for OSPF config that takes a specific config file path.
     * Creates a config file if it doesn't already exist.
     * Also populates local variables with values from the config.
     *
     * @param path config file path
     */
    public static void SetConfig(String path) {
        CommonMain(path);
    }

    /**
     * Main start method that performs the common jobs, instead of copying code between set methods.
     *
     * @param path Config file path
     */
    private static void CommonMain(String path) {
        fileConfig = new File(path);
        System.out.println(fileConfig.getPath());
        if (!fileConfig.exists()) {
            try {
                MakeConfig();
            } catch (SocketException | UnknownHostException e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }

        ReadConfig();
    }

    /**
     * Create an ospf.conf file at the desired location, with sensible default values.
     */
    private static void MakeConfig() throws SocketException, UnknownHostException {
        hostname = "Router";
        List<RouterInterface> routerInterfaces = new ArrayList<>(Collections.emptyList());

        //Loop over each network interface, building a default RouterInterface object
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        for (NetworkInterface networkInterface: Collections.list(interfaces)) {
            //IP addresses
            InterfaceAddress ipv4Addr = null;
            List<InterfaceAddress> ipv6Addrs = new ArrayList<>();

            //loop over all addresses, if the address is ipv4, add it to the Ipv4 local variable, if ipv6, add to the list of IPv6 addresses.
            for (InterfaceAddress inetAddress : networkInterface.getInterfaceAddresses()) {
                if (inetAddress.getAddress() instanceof Inet4Address)
                {
                    ipv4Addr = inetAddress;
                }
                else if (inetAddress.getAddress() instanceof Inet6Address)
                {
                    ipv6Addrs.add(inetAddress);
                }
            }



            routerInterfaces.add(new RouterInterface(networkInterface.getDisplayName(),
                    ipv4Addr,
                    ipv6Addrs,
                    InterfaceType.E1,
                    networkInterface.isUp()
            ));
        }

        thisNode = new ThisNode((Inet4Address) InetAddress.getByName("1.1.1.1"), routerInterfaces);

        WriteConfig();
    }

    public static void ReadConfig() {
        DocumentBuilder builder;
        try {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            StringBuilder xmlStringBuilder = new StringBuilder();
            Document configDocument = builder.parse(fileConfig);

            hostname = configDocument.getElementsByTagName("Hostname").item(0).getTextContent();

        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public static void WriteConfig() {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            StringBuilder xmlStringBuilder = new StringBuilder().append("<?xml version=\"1.0\"?> <config></config>");;
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
    }

    public static boolean ConfigExists() {
        if (fileConfig == null)
        {
            return false;
        }

        return fileConfig.exists();
    }
}
