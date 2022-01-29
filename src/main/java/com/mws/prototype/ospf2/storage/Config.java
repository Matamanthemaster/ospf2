package com.mws.prototype.ospf2.storage;

import inet.ipaddr.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import inet.ipaddr.IPAddressNetwork;
/**
* Class to store the OSPF config file reference, and values in java data types.
*/
public class Config {

    static File fileConfig = null; //File reference, for IO Operations
    static String hostname;
    static ThisNode thisNode;
    static int deadInterval;
    static int helloInterval;

    //Not part of the config, part of the daemon. Accessible to all here.
    static NeighboursTable neighboursTable;
    static LSDB lsdb;

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
        if (!ConfigExists()) {
            try {
                MakeConfig();
            } catch (SocketException | UnknownHostException | AddressStringException e) {
                e.printStackTrace();
                ConfigErrorHandle("An exception occurred while trying to make the config. See the above stack trace" +
                        "for details.");
            }
        }

        ReadConfig();
    }

    /**
     * Create an ospf.conf file at the with sensible first-time default values. Uses file path in 'Config.fileConfig'.
     */
    private static void MakeConfig() throws SocketException, UnknownHostException, AddressStringException {
        hostname = "Router";
        helloInterval = 10;
        deadInterval = 40;
        List<RouterInterface> routerInterfaces = new ArrayList<>(Collections.emptyList());

        //Loop over each network interface, building a default RouterInterface object
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        for (NetworkInterface networkInterface: Collections.list(interfaces)) {
            //IP addresses
            IPAddress ipv4Addr = null;
            List<IPAddress> ipv6Addrs = new ArrayList<>();

            //loop over all addresses, if the address is ipv4, add it to the Ipv4 local variable, if ipv6, add to the
            // list of IPv6 addresses.
            for (InterfaceAddress inetAddress : networkInterface.getInterfaceAddresses()) {
                if (inetAddress.getAddress() instanceof Inet4Address)
                {
                    ipv4Addr = new IPAddressNetwork.IPAddressGenerator().from(inetAddress.getAddress());
                }
                else if (inetAddress.getAddress() instanceof Inet6Address)
                {
                    ipv6Addrs.add(new IPAddressNetwork.IPAddressGenerator().from(inetAddress.getAddress()));
                }
            }

            routerInterfaces.add(new RouterInterface(networkInterface.getDisplayName(),
                    ipv4Addr,
                    ipv6Addrs,
                    InterfaceType.E1,
                    networkInterface.isUp()
            ));
        }

        thisNode = new ThisNode(new IPAddressString("1.1.1.1").toAddress(), routerInterfaces);

        WriteConfig();
        System.out.println("The config file has been made for the first time. Please change the config file at '" +
                fileConfig.getPath() + "', then run the program again.");
        System.exit(0);
    }

    /*E.g.
    <Hostname>R1</Hostname>
    <RID>1.1.1.1</RID>
    <HelloInterval>10</HelloInterval>
    <DeadInterval>40</DeadInterval>
    <Interfaces>
        <enp5s0>
            <IPv4>192.168.1.20/255.255.255.0</IPv4>
            <IPv6>fe80::9656:d028:8652:66b6/64</IPv6>
            <IPv6>2001:db8:acad::1/96</IPv6>
            <Type>T1</Type>
            <Enabled>True</Enabled>
        </enp5s0>
        <eth0>
            <IPv4>192.168.0.1/255.255.255.0</IPv4>
            <IPv6>fe80::9656:d028:8652:66b6/64</IPv6>
            <IPv6>2001:db8:acad:a::1/96</IPv6>
            <Type>100Base-T</Type>
            <Enabled>True</Enabled>
        </enp5s0>
    </Interfaces>
     */
    /**
     * Populates Config class static values from the config file stored in 'Config.fileConfig'.
     */
    public static void ReadConfig() {
        if (!ConfigExists())
        {
            try {
                MakeConfig();
            } catch (SocketException | UnknownHostException | AddressStringException e) {
                e.printStackTrace();
                ConfigErrorHandle("An exception occurred while creating a new config. See the above stack trace for" +
                        "details.");
            }
        }

        DocumentBuilder builder;
        try {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document configDocument = builder.parse(fileConfig);

            //get global config information, hostname
            hostname = configDocument.getElementsByTagName("Hostname").item(0).getTextContent();
            try {
                deadInterval = Integer.parseInt(configDocument.getElementsByTagName("DeadInterval")
                        .item(0).getTextContent());
                helloInterval = Integer.parseInt(configDocument.getElementsByTagName("HelloInterval")
                        .item(0).getTextContent());
            } catch (NumberFormatException ex) {
                ConfigErrorHandle("The dead interval and hello interval in the config are not both integers, and so the" +
                        "config file is invalid.\n\rPlease change DeadInterval or HelloInterval.");
            }



            //variables required to create ThisNode. RID is easy to make, Interfaces list is more complex, requiring
            // looping over XML elements.
            IPAddress rid = new IPAddressString(configDocument.getElementsByTagName("RID").item(0)
                    .getTextContent()).toAddress();
            List<RouterInterface> confInterfaces = new ArrayList<>();

            //Go into the interfaces element, loop over each child, getting all children of each interface and storing
            // them in the RouterInterface object 'confInterfaces'.
            NodeList confIntRoot = configDocument.getElementsByTagName("Interfaces");
            for (int i = 0; i < confIntRoot.getLength(); i++) {
                Node curInt = confIntRoot.item(i);
                NodeList curIntVars = curInt.getChildNodes();

                //Vars to store values before the loop, so they always have some value.
                String curIntName = curInt.getNodeName();
                IPAddress curIntIPv4 = null;
                List<IPAddress> curIntIPv6s = new ArrayList<>();
                InterfaceType curIntType = null;
                boolean curIntEnabled = false;

                for (int v = 0; v < curIntVars.getLength(); v++) {
                    //Get the inner text of this child, and switch on the child's name. Use the inner-text to populate
                    //the desired variables if the correct element is found.
                    String curIntVarValue = curIntVars.item(v).getTextContent();
                    switch (curIntVars.item(v).getNodeName()) {
                        case "IPv4" -> curIntIPv4 = new IPAddressString(curIntVarValue).toAddress();
                        case "IPv6" -> curIntIPv6s.add(new IPAddressString(curIntVarValue).toAddress());
                        case "Type" -> curIntType = InterfaceType.fromString(curIntVarValue);
                        case "Enabled" -> {
                            if (curIntVarValue.equals("True"))
                                curIntEnabled = true;
                        }
                        //don't care if extra values exist (default branch).
                    }
                }
                //Check before building an interface that invalid values are not found. Otherwise, this is an illegal
                //state.
                if (curIntIPv4 == null)
                    ConfigErrorHandle("Unexpected value: interface \"" + curIntName + "\"'s IPv4 address doesn't exist" +
                            " in the config file. IPv4 is required for OSPFv2. Please add 'IPv4' child with a valid" +
                            "value");
                if (curIntType == null)
                    ConfigErrorHandle("Unexpected value: interface \"" + curIntName + "\"'s interface type doesn't" +
                            " exist in the config file. Please add 'Type' child with a valid value.");


                //Build an individual interface, and add it to the interfaces list.
                confInterfaces.add(new RouterInterface(curIntName, curIntIPv4, curIntIPv6s, curIntType, curIntEnabled));
            }
            //Finally, take all the work we've done, create this node from RID and the interfaces in the config file.
            thisNode = new ThisNode(rid, confInterfaces);

        } catch (ParserConfigurationException | SAXException | IOException | AddressStringException e) {
            e.printStackTrace();
            ConfigErrorHandle("An exception occurred while reading the config file. See the stack trace above.");
        }
    }

    public static void WriteConfig() {
        if (!ConfigExists()) {
            try {
                fileConfig.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }

        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            StringBuilder xmlStringBuilder = new StringBuilder().append("<?xml version=\"1.0\"?> <config></config>");
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

    private static void ConfigErrorHandle(String message)
    {
        System.err.println(message);
        System.exit(-2);
    }
}
