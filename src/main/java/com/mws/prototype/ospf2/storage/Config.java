package com.mws.prototype.ospf2.storage;

import java.io.File;
import java.net.Inet4Address;

/**
* Class to store the OSPF config file reference, and values in java data types.
*/
public class Config {

    protected static File fileConfig; //File reference, for IO Operations
    protected static ThisNode thisNode;
    protected static NeighboursTable neighboursTable;
    protected static LSDB lsdb;
    protected static int inactiveTimerDelay;


    /**
     * Set method for the OSPF config, that uses the default config file path.
     * Creates a file if it doesn't already exist.
     * Also populates local variables with values from the config.
     * Default path: ./ospf.conf
     */
    protected static void SetConfig() {
        CommonMain(System.getProperty("user.dir") + "ospf.conf");
    }

    /**
     * Set method for OSPF config that takes a specific config file path.
     * Creates a config file if it doesn't already exist.
     * Also populates local variables with values from the config.
     *
     * @param path config file path
     */
    protected static void SetConfig(String path) {
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
            MakeConfig();
        }

        ReadConfig();
    }

    /**
     * Create an ospf.conf file at the desired location, with sensible default values.
     */
    private static void MakeConfig() {
        //thisNode = new Node();
        WriteConfig();
    }

    protected static void ReadConfig() {

    }

    protected static void WriteConfig() {

    }
}
