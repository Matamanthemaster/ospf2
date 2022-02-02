package com.mws.ospf2;

import com.mws.ospf2.ui.Main;

public class Launcher {

    private final static String commandUsage =
            "Usage: java -jar ospf2.jar [arguments] <Operation Mode Flag>" + System.lineSeparator() +
                    "Arguments:" + System.lineSeparator() +
                    "   --help:                     Prints this help message"+ System.lineSeparator() +
                    "   -g, --with-gui:             Runs the program with the GUI frontend" + System.lineSeparator() +
                    "   -c, --config-file <Path>    Specify an alternate config file (Default ./ospf.conf.xml)" + System.lineSeparator() +
                    "Operation Mode Flags:" + System.lineSeparator() +
                    "   --Standard-OSPF" + System.lineSeparator() +
                    "   --Encrypted-OSPF" + System.lineSeparator();
    private static Thread uiThread;
    private static String operationMode;

    public static void main(String[] args) {
        uiThread = new Thread(() -> Main.main(args));

        //Check args if they exist
        if (args.length > 0) {
            SearchFlags(args);
        }

        //Setup config if it wasn't made from args.
        if (!Config.ConfigExists())
        {
            Config.SetConfig();
        }

        //Entry point for CLI daemon.
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
                case "--Standard-OSPF" -> {
                    if (!operationMode.equals(""))
                    {
                        System.err.println("Cannot use multiple operation modes.");
                        System.out.println(commandUsage);
                        System.exit(-1);
                    }
                    operationMode = "standard";
                }
                case "--Encrypted-OSPF" -> {
                    if (!operationMode.equals(""))
                    {
                        System.err.println("Cannot use multiple operation modes.");
                        System.out.println(commandUsage);
                        System.exit(-1);
                    }
                    operationMode = "encrypted";
                }
                case "--help", "-help" -> { //Request to see command usage
                    System.out.println(commandUsage);
                    System.exit(0);
                }
                case "-g", "--with-gui" -> uiThread.start();//Argument to launch GUI
                case "-c", "--config-file" -> {//Argument to specify a config file path
                    flagSkipFlag = true;
                    Config.SetConfig(args[i+1]);
                }
                case "--remove-config" -> Config.flagFileConfRemove = true;//Argument useful for testing, will remove the config file.
                default -> { //Arg not found. Invalid use of program.
                    System.out.println("Argument not recognised: '" + arg + "'.");
                    System.out.println(commandUsage);
                    System.exit(-1);
                }
            }
        }
    }
}
