package com.mws.ospf;

import com.mws.ospf.pdt.InterfaceType;
import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressNetwork;
import inet.ipaddr.IPAddressString;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
* Class to store the OSPF config file reference, and values in java data types.
*/
public class Config {

    private static File fileConfig = null; //File reference, for IO Operations
    static ThisNode thisNode;
    static boolean flagFileConfRemove;

    //Not part of the config, part of the daemons. Accessible to all here.
    static List<NeighbourNode> neighboursTable;
    static LSDB lsdb;

    /**
     * Set method for the OSPF config, that uses the default config file path.
     * Creates a file if it doesn't already exist.
     * Creates a file if it doesn't already exist.
     * Also populates local variables with values from the config.
     * Default path: ./ospf.conf.xml
     */
    static void SetConfig() {
        CommonMain(System.getProperty("user.dir") + System.getProperty("file.separator") + "ospf.conf.xml");
    }

    /**
     * Set method for OSPF config that takes a specific config file path.
     * Set method for OSPF config that takes a specific config file path.
     * Creates a config file if it doesn't already exist.
     * Also populates local variables with values from the config.
     *
     * @param path config file path
     */
    static void SetConfig(String path) {
        CommonMain(path);
    }

    /**
     * Main start method that performs the common jobs, instead of copying code between set methods.
     *
     * @param path Config file path
     */
    private static void CommonMain(String path) {
        fileConfig = new File(path);
        if (flagFileConfRemove)
            fileConfig.delete();

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
     * Create a .conf.xml file with sensible first-time default values. Uses file path in 'Config.fileConfig'.
     */
    private static void MakeConfig() throws SocketException, UnknownHostException, AddressStringException {

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
                    ipv4Addr = ipv4Addr.setPrefixLength(inetAddress.getNetworkPrefixLength());
                }
                else if (inetAddress.getAddress() instanceof Inet6Address)
                {
                    IPAddress newIPv6 = new IPAddressNetwork.IPAddressGenerator().from(inetAddress.getAddress());
                    newIPv6 = newIPv6.setPrefixLength(inetAddress.getNetworkPrefixLength());
                    ipv6Addrs.add(newIPv6);
                }
            }

            routerInterfaces.add(new RouterInterface(networkInterface.getDisplayName(),
                    ipv4Addr,
                    ipv6Addrs,
                    InterfaceType.E1,
                    networkInterface.isUp()
            ));
        }

        thisNode = new ThisNode("0.0.0.1", "Router", routerInterfaces);

        WriteConfig();
        System.out.println("The config file has been made for the first time. Please change the config file at '" +
                fileConfig.getPath() + "', then run the program again.");
        System.exit(0);
    }

    /*
    Desired format of XML document.
    tags used to store tags: lowercase.
    tags used to store information: UpperCammelCase
    E.g.
    <config>
        <Hostname>R1</Hostname>
        <RID>0.0.0.1</RID>
        <interfaces>
            <enp5s0>
                <IPv4>192.168.1.20/24</IPv4>
                <IPv6>fe80::9656:d028:8652:66b6/64</IPv6>
                <IPv6>2001:db8:acad::1/96</IPv6>
                <Type>T1</Type>
                <Enabled>True</Enabled>
            </enp5s0>
            <eth0>
                <IPv4>192.168.0.1/24</IPv4>
                <IPv6>fe80::9656:d028:8652:66b6/64</IPv6>
                <IPv6>2001:db8:acad:a::1/96</IPv6>
                <Type>100Base-T</Type>
                <Enabled>True</Enabled>
            </enp5s0>
        </interfaces>
    </config>
     */
    /**
     * Populates Config class static values from the config file stored in 'Config.fileConfig'.
     */
    static void ReadConfig() {
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
            String hostname = configDocument.getElementsByTagName("Hostname").item(0).getTextContent();




            //variables required to create ThisNode. RID is easy to make, Interfaces list is more complex, requiring
            // looping over XML elements.
            String rid = configDocument.getElementsByTagName("RID").item(0).getTextContent();
            List<RouterInterface> confInterfaces = new ArrayList<>();

            //Go into the interfaces element, loop over each child, getting all children of each interface and storing
            // them in the RouterInterface object 'confInterfaces'.
            Node confIntRoot = configDocument.getElementsByTagName("interfaces").item(0);
            NodeList confIntRootChildren = confIntRoot.getChildNodes();
            for (int i = 0; i < confIntRootChildren.getLength(); i++) {
                Node curInt = confIntRootChildren.item(i);
                if (curInt.getNodeName() == "#text")
                    continue;
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
                            if (curIntVarValue.equals("true"))
                                curIntEnabled = true;
                        }
                        //don't care if extra values exist (default branch).
                    }
                }
                //Check before building an interface that invalid values are not found. Otherwise, this is an illegal
                //state.
                if (curIntIPv4 == null)
                    ConfigErrorHandle("Unexpected value: interface \"" + curIntName + "\"'s IPv4 address doesn't exist" +
                            " in the config file. IPv4 is required for ipv4. Please add 'IPv4' child with a valid" +
                            " value");
                if (curIntType == null)
                    ConfigErrorHandle("Unexpected value: interface \"" + curIntName + "\"'s interface type doesn't" +
                            " exist in the config file. Please add 'Type' child with a valid value.");


                //Build an individual interface, and add it to the interfaces list.
                confInterfaces.add(new RouterInterface(curIntName, curIntIPv4, curIntIPv6s, curIntType, curIntEnabled));
            }
            //Finally, take all the work we've done, create this node from rid and the interfaces in the config file.
            //Also init tables.
            thisNode = new ThisNode(rid, hostname, confInterfaces);
            neighboursTable = Collections.emptyList();
            lsdb = new LSDB();

        } catch (ParserConfigurationException | SAXException | IOException | AddressStringException e) {
            e.printStackTrace();
            ConfigErrorHandle("An exception occurred while reading the config file. See the stack trace above.");
        }
    }

    /**
     * Take static variables stored in Config and write them to the XML file.
     */
    static void WriteConfig() {
        try {
            if (!ConfigExists())
                fileConfig.createNewFile();

            //Recreate config document.
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document confDoc;
            try {
                confDoc = builder.parse(fileConfig);
            } catch (SAXParseException ex) {
                confDoc = builder.newDocument();
            }


            //Setup document root
            Element confRoot;
            if (confDoc.getElementsByTagName("config").getLength() == 0) {
                confRoot = confDoc.createElement("config");
                confDoc.appendChild(confRoot);
            } else
                confRoot = (Element) confDoc.getElementsByTagName("config").item(0);

            //Setup hostname element. Set hostname text content to the stored config.
            Element confHostname = GetConfigElementFromRoot(confDoc, confRoot, "Hostname");
            confHostname.setTextContent(thisNode.hostname);

            //setup nid element. Set nid text content to the stored config.
            Element configRID = GetConfigElementFromRoot(confDoc, confRoot, "RID");
            configRID.setTextContent(String.valueOf(thisNode.GetRID()));

            //Create interfaces root.
            Element confInterfacesRoot = GetConfigElementFromRoot(confDoc, confRoot, "interfaces");

            //Now the harder part. Create interfaces.
            for (RouterInterface curRInt: thisNode.interfaceList)
            {
                //Current interface element (e.g. <enp5s0></enp5s0>)
                Element confCurRInt = GetConfigElementFromRoot(confDoc, confInterfacesRoot, curRInt.getName());

                //IPv4 Address (e.g. 192.168.1.20/24)
                Element confCurRIntIPv4 = GetConfigElementFromRoot(confDoc, confCurRInt, "IPv4");
                confCurRIntIPv4.setTextContent(curRInt.addrIPv4.toPrefixLengthString());

                //IPv6 Address. First remove all existing elements, then recreate elements. This is the simplest way.
                //(E.g. <IPv6>fe80::9656:d028:8652:66b6/64</IPv6>
                //      <IPv6>2001:db8:acad::1/96</IPv6>
                for (int i = 0; i < confCurRInt.getElementsByTagName("IPv6").getLength(); i++)
                {
                    confCurRInt.removeChild(confInterfacesRoot.getElementsByTagName("IPv6").item(i));
                }
                for (IPAddress curIPv6: curRInt.addrIPv6)
                {
                    Element confCurIPv6Tag = confDoc.createElement("IPv6");
                    confCurIPv6Tag.setTextContent(curIPv6.toPrefixLengthString());
                    confCurRInt.appendChild(confCurIPv6Tag);
                }

                //InterfaceType (e.g. T1)
                Element confCurRIntType = GetConfigElementFromRoot(confDoc, confCurRInt, "Type");
                confCurRIntType.setTextContent(curRInt.type.toString());

                //Enabled state (e.g. true)
                Element confCurRintEnabled = GetConfigElementFromRoot(confDoc, confCurRInt, "Enabled");
                confCurRintEnabled.setTextContent(String.valueOf(curRInt.isEnabled));
            }

            //Write output via transformer factory.
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "yes");
            transformer.transform(new DOMSource(confDoc), new StreamResult(fileConfig));

        } catch (ParserConfigurationException | IOException | SAXException | TransformerException e) {
            e.printStackTrace();
            ConfigErrorHandle("Exception when creating the XML document in Config.WriteConfig(). See the stack trace above");
        }
    }

    /**
     * Get a DOM element for a specified tag, limit one tag. If the tag exists, get that existing tag. Tag will be created if it doesn't exist.
     * @param confDoc the DOM document, used for creating elements
     * @param rootElement the root DOM element where operations are preformed on
     * @param tagName the tag to get from the parent
     * @return the requested child DOM element, from tag name
     */
    private static Element GetConfigElementFromRoot(@NotNull Document confDoc, @NotNull Element rootElement, @NotNull String tagName) {
        Element newElement;
        if (rootElement.getElementsByTagName(tagName).getLength() == 0)//Set element
        {
            newElement = confDoc.createElement(tagName);
            rootElement.appendChild(newElement);
        } else//Get element
            newElement = (Element) rootElement.getElementsByTagName(tagName).item(0);
        return newElement;
    }

    /**
     * Determine the status of the config file.
     * @return true if the file is set in the config and exists.
     */
    static boolean ConfigExists() {
        if (fileConfig == null)
        {
            return false;
        }

        return fileConfig.exists();
    }

    /**
     * More graceful and standard exit to the Config class, usually during exception handling.
     * @param message the message to display to the user
     */
    private static void ConfigErrorHandle(String message)
    {
        System.err.println(message);
        System.exit(-2);
    }
}
