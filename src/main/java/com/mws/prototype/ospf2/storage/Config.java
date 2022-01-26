package com.mws.prototype.ospf2.storage;

import java.io.File;
import java.net.Inet4Address;

/**
* Class to store the OSPF config file reference, and values in java data types.
*/
public class Config {

    File fileConfig; //File reference, for IO Operations
    Node thisNode;


    /**
     * Constructor for the OSPF config, that uses the default config file path.
     * Constructor creates a file if it doesn't already exist.
     * Constructor also populates local variables with values from the config.
     * Default path: ./ospf.conf
     */
    public Config() {
        CommonConstructor(System.getProperty("user.dir") + "ospf.conf");
    }

    /**
     * Constructor for OSPF config that takes a specific config file path.
     * Constructor creates a file if it doesn't already exist.
     * Constructor also populates local variables with values from the config.
     *
     * @param path config file path
     */
    public Config(String path) {
        CommonConstructor(path);
    }

    /**
     * Constructor method that performs the common constructor jobs, instead of copying code between constructor methods.
     *
     * @param path Config file path
     */
    private void CommonConstructor(String path) {
        fileConfig = new File(path);
        System.out.println(fileConfig.getPath());
        if (!fileConfig.exists()) {
            MakeConfig();
        }

        ReadConfig();
    }

    /**
     * Create an ospf.conf file at the desired location, with sensible default values.
     */
    private void MakeConfig() {
        //thisNode = new Node();
        WriteConfig();
    }

    public void ReadConfig() {

    }

    public void WriteConfig() {

    }
}
