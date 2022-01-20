package com.mws.prototype.ospf2;

import java.io.File;

/**
* Class to store the OSPF config file reference, and values in java data types.
*/
public class Config {
    File fileConfig;

    /**
     * Constructor for the OSPF config, that uses the default config file path.
     * Default path: ./ospf.conf
     * If the file does not exist, it is attempted to be initialised.
     */
    public Config()
    {
        fileConfig = new File(System.getProperty("user.dir") + "ospf.conf");
        System.out.println(fileConfig.getPath());
        System.exit(0);
        if (!fileConfig.exists())
        {
            MakeConfig(fileConfig.getPath());
        }
    }

    /**
     * Constructor for OSPF config that takes a specific config file path.
     * If the file does not exist, it is attempted to be initialised.
     * @param path Specify a path for a specific config file
     */
    public Config(String path)
    {

    }

    /**
     * Create an ospf.conf file at the desired location, with sensible default values.
     * @param path Specify the path to the config file.
     */
    private static void MakeConfig(String path)
    {

    }
}
