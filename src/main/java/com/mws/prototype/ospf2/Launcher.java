package com.mws.prototype.ospf2;

import com.mws.prototype.ospf2.storage.Config;

public class Launcher {

    private final static String commandUsage =
            "Usage: java -jar ospf2.jar [arguments]" + System.lineSeparator() +
                    "Arguments:" + System.lineSeparator() +
                    "   --help:                     Prints this help message"+ System.lineSeparator() +
                    "   -g, --with-gui:             Runs the program with the GUI frontend" + System.lineSeparator() +
                    "   -c, --config-file <Path>    Specify an alternate config file (Default ./ospf.cfg)" + System.lineSeparator();
    private static Thread uiThread;
    private static Config config;

    public static void main(String[] args) {
        uiThread = new Thread(() -> OspfUIMain.main(args));

        if (args.length > 0) {
            SearchFlags(args);
        }

        //Entry point for CLI server application.
        System.out.println("CLI Program Run");
    }

    /**
     * Method looks through each argument provided to the program, and runs actions on hitting specific flags.
     * @param  args the program args to search through
     */
    private static void SearchFlags(String[] args)
    {
        //flag to determine if this argument should be skipped when searching for a flag.
        boolean flagSkipFlag = false;

        //for (args: arg)
        for (int i = 0; i < args.length; i++) {
            //Check if argument isn't a flag to check, skip if not checking.
            if (flagSkipFlag) {
                flagSkipFlag = false;
                continue;
            }
            String arg = args[i];

            switch (arg) {
                case "--help", "-help" -> { //Request to see command usage
                    System.out.println(commandUsage);
                    System.exit(0);
                }
                case "-g", "--with-gui" -> uiThread.start();//Argument to launch GUI
                case "-c", "--config-file" -> {//Argument to specify a config file path
                    flagSkipFlag = true;
                    ConfigFile(args[i+1]);
                }
                default -> { //Arg not found. Invalid use of program.
                    System.out.println("Argument not recognised: '" + arg + "'.");
                    System.out.println(commandUsage);
                    System.exit(-1);
                }
            }
        }
    }

    /**
     * Use a specific config file
     */
    private static void ConfigFile(String path)
    {

    }
}
