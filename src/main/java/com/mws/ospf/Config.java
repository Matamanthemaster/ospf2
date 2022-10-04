package com.mws.ospf;

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

/**<p><h1>Application Configuration</h1></p>
* <p>Class to store the OSPF config file reference, and values in java data types.</p>
*/
public class Config {
    //region STATIC PROPERTIES
    private static File fileConfig = null; //File reference, for IO Operations
    static boolean flagFileConfRemove;
    static ThisNode thisNode;//Accessible to all here.
    static List<NeighbourNode> neighboursTable = new ArrayList<>();
    static LSDB lsdb = new LSDB();
    //endregion

    //region STATIC METHODS
    /**<p><h1>Set Config File</h1></p>
     * <p>Set method for the OSPF config, that uses the default config file path. Creates a file if it doesn't already exist. Also populates local variables with values from the config</p>
     * <p></p>
     * <p>Uses default path: ./ospf.conf.xml</p>
     */
    static void setConfig() {
        setConfig(System.getProperty("user.dir") + System.getProperty("file.separator") + "ospf.conf.xml");
    }

    /**<p><h1>Config Main Method</h1></p>
     * <p>Set method for OSPF config that takes a specific config file path. Creates a config file if it doesn't
     * already exist. Also populates local variables with values from the config.</p>
     * @param path Config file path
     */
    static void setConfig(String path) {
        fileConfig = new File(path);
        if (flagFileConfRemove)
            fileConfig.delete();

        if (!isConfigExists()) {
            try {
                makeConfig();
            } catch (SocketException | UnknownHostException | AddressStringException e) {
                e.printStackTrace();
                handleConfigError("An exception occurred while trying to make the config. See the above stack trace" +
                        "for details.");
            }
        }

        readConfig();
    }

    /**<p><h1>Generate Config</h1></p>
     * <p>Populates the config file static class properties with starting data. Data is derived from the machine details,
     * such as interface name and assigned IP addresses. Calls static void WriteConfig() to save sensible defaults to
     * a config file with path specified to CommonMain</p>
     * <p>>Uses file path in static File property 'Config.fileConfig'.</p>
     */
    private static void makeConfig() throws SocketException, UnknownHostException, AddressStringException {

        List<RouterInterface> routerInterfaces = new ArrayList<>();

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
                    false
            ));
        }

        thisNode = new ThisNode(new IPAddressString("0.0.0.1"), "Router", routerInterfaces);

        writeConfig();
        System.out.println("The config file has been made for the first time. Please change the config file at '" +
                fileConfig.getPath() + "', then run the program again.");
        System.exit(0);
    }

    //CONFIG XML FILE FORMAT
    /*Desired format of XML document.
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
    </config>*/
    /**<p><h1>Read Config File</h1></p>
     * <p>Reads data from the config file stored in static File 'Config.fileConfig'. Populates application config class
     * properties with data from the config xml document file.</p>
     */
    static void readConfig() {
        if (!isConfigExists())
        {
            try {
                makeConfig();
            } catch (SocketException | UnknownHostException | AddressStringException e) {
                e.printStackTrace();
                handleConfigError("An exception occurred while creating a new config. See the above stack trace for" +
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
            IPAddressString rid = new IPAddressString(configDocument.getElementsByTagName("RID").item(0).getTextContent());

            List<RouterInterface> confInterfaces = new ArrayList<>();

            //Go into the interfaces element, loop over each child, getting all children of each interface and storing
            // them in the RouterInterface object 'confInterfaces'.
            Node confIntRoot = configDocument.getElementsByTagName("interfaces").item(0);
            NodeList confIntRootChildren = confIntRoot.getChildNodes();
            for (int i = 0; i < confIntRootChildren.getLength(); i++) {
                Node curInt = confIntRootChildren.item(i);
                if (curInt.getNodeName().equals("#text"))
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
                    handleConfigError("Unexpected value: interface \"" + curIntName + "\"'s IPv4 address doesn't exist" +
                            " in the config file. IPv4 is required for ipv4. Please add 'IPv4' child with a valid" +
                            " value");
                if (curIntType == null)
                    handleConfigError("Unexpected value: interface \"" + curIntName + "\"'s interface type doesn't" +
                            " exist in the config file. Please add 'Type' child with a valid value.");


                //Build an individual interface, and add it to the interfaces list.
                confInterfaces.add(new RouterInterface(curIntName, curIntIPv4, curIntIPv6s, curIntType, curIntEnabled));
            }
            //Finally, take all the work we've done, create this node from rid and the interfaces in the config file.
            thisNode = new ThisNode(rid, hostname, confInterfaces);

        } catch (ParserConfigurationException | SAXException | IOException | AddressStringException e) {
            e.printStackTrace();
            handleConfigError("An exception occurred while reading the config file. See the stack trace above.");
        }
    }

    /**<p><h1>Write Config File</h1></p>
     * <p>Takes static variables defined in this class and writes them to the config.xml file specified in static File
     * 'Config.fileConfig'. Datastructures stored statically in the config class are formatted with tags in the specific
     * structure, pictured above ReadConfig().</p>
     */
    static void writeConfig() {
        try {
            if (isConfigExists()) {
                fileConfig.delete();
                fileConfig.createNewFile();
            }

            //Recreate config document.
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document confDoc = builder.newDocument();



            //Setup document root
            Element confRoot;
            if (confDoc.getElementsByTagName("config").getLength() == 0) {
                confRoot = confDoc.createElement("config");
                confDoc.appendChild(confRoot);
            } else
                confRoot = (Element) confDoc.getElementsByTagName("config").item(0);

            //Setup hostname element. Set hostname text content to the stored config.
            Element confHostname = getConfigElementFromRoot(confDoc, confRoot, "Hostname");
            confHostname.setTextContent(thisNode.hostname);

            //setup nid element. Set nid text content to the stored config.
            Element configRID = getConfigElementFromRoot(confDoc, confRoot, "RID");
            configRID.setTextContent(String.valueOf(thisNode.getRID()));

            //Create interfaces root.
            Element confInterfacesRoot = getConfigElementFromRoot(confDoc, confRoot, "interfaces");

            //Now the harder part. Create interfaces.
            for (RouterInterface curRInt: thisNode.interfaceList)
            {
                //Current interface element (e.g. <enp5s0></enp5s0>)
                Element confCurRInt = getConfigElementFromRoot(confDoc, confInterfacesRoot, curRInt.getName());

                //IPv4 Address (e.g. 192.168.1.20/24)
                Element confCurRIntIPv4 = getConfigElementFromRoot(confDoc, confCurRInt, "IPv4");
                confCurRIntIPv4.setTextContent(curRInt.addrIPv4.toPrefixLengthString());

                //IPv6 Address. First remove all existing elements, then recreate elements. This is the simplest way.
                //(E.g. <IPv6>fe80::9656:d028:8652:66b6/64</IPv6>
                //      <IPv6>2001:db8:acad::1/96</IPv6>
                NodeList ip6Tags = confCurRInt.getElementsByTagName("IPv6");
                for (int i = 0; i < ip6Tags.getLength(); i++)
                {
                    confCurRInt.removeChild(ip6Tags.item(i));
                }
                for (IPAddress curIPv6: curRInt.addrIPv6)
                {
                    Element confCurIPv6Tag = confDoc.createElement("IPv6");
                    confCurIPv6Tag.setTextContent(curIPv6.toPrefixLengthString());
                    confCurRInt.appendChild(confCurIPv6Tag);
                }

                //InterfaceType (e.g. T1)
                Element confCurRIntType = getConfigElementFromRoot(confDoc, confCurRInt, "Type");
                confCurRIntType.setTextContent(curRInt.type.toString());

                //Enabled state (e.g. true)
                Element confCurRintEnabled = getConfigElementFromRoot(confDoc, confCurRInt, "Enabled");
                confCurRintEnabled.setTextContent(String.valueOf(curRInt.isEnabled));
            }

            //Write output via transformer factory.
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "yes");
            transformer.transform(new DOMSource(confDoc), new StreamResult(fileConfig));

        } catch (ParserConfigurationException | IOException | TransformerException e) {
            e.printStackTrace();
            handleConfigError("Exception when creating the XML document in Config.WriteConfig(). See the stack trace above");
        }
    }

    /**<p><h1>Get XML Element Helper</h1></p>
     * <p>Get a DOM element for a specified tag, limit one tag. If the tag exists, get that existing tag. Tag will be created if it doesn't exist.</p>
     * <p>Used internally to read and write DOM xml document.</p>
     * @param confDoc the DOM document, used for creating elements
     * @param rootElement the root DOM element where operations are preformed on
     * @param tagName the tag to get from the parent
     * @return the requested child DOM element, from tag name
     */
    private static Element getConfigElementFromRoot(@NotNull Document confDoc, @NotNull Element rootElement, @NotNull String tagName) {
        Element newElement;
        if (rootElement.getElementsByTagName(tagName).getLength() == 0)//Set element
        {
            newElement = confDoc.createElement(tagName);
            rootElement.appendChild(newElement);
        } else//Get element
            newElement = (Element) rootElement.getElementsByTagName(tagName).item(0);
        return newElement;
    }

    /**<p><h1>Check Config Exists</h1></p>
     * <p>Determine the status of the config file</p>
     * @return true if the file is set in the config and exists
     */
    static boolean isConfigExists() {
        if (fileConfig == null)
        {
            return false;
        }

        return fileConfig.exists();
    }

    /**<p><h1>Error Handle (Config)</h1></p>
     * <p>More graceful and standard exit to the Config class, usually during exception handling</p>
     * @param message the message to display to the user
     */
    private static void handleConfigError(String message) {
        System.err.println(message);
        System.exit(-2);
    }
    //endregion
}
