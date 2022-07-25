package com.mws.ospf;

public class Launcher {

    private final static String commandUsage =
            "Usage: java -jar ospf.jar [arguments] <Operation Mode Flag>" + System.lineSeparator() +
                    "Arguments:" + System.lineSeparator() +
                    "   --help:                     Prints this help message"+ System.lineSeparator() +
                    "   -g, --with-gui:             Runs the program with the GUI frontend" + System.lineSeparator() +
                    "   -c, --config-file <Path>    Specify an alternate config file (Default ./ospf.conf.xml)" + System.lineSeparator() +
                    "Operation Mode Flags:" + System.lineSeparator() +
                    "   --Standard-OSPF" + System.lineSeparator() +
                    "   --Encrypted-OSPF" + System.lineSeparator();
    private static Thread uiThread;
    private static Thread daemonThread;
    private static String operationMode;

    public static void main(String[] args) {
        uiThread = new Thread(() -> UIEntry.main(args));

        //Check args if they exist
        if (args.length > 0)
            SearchFlags(args);

        //Setup config if it wasn't made from args.
        if (!Config.ConfigExists())
            Config.SetConfig();

        //Entry point for CLI daemon.
        if (operationMode == null)
            LauncherErrorHandle("Operation mode not specified.");

        //Check operation mode from flags, choose appropriate implementation for the PF model.
        if (operationMode.equals("standard"))
            daemonThread = new Thread(StdDaemon::Main);
        else if (operationMode.equals("encrypted"))
            daemonThread = new Thread(EncDaemon::Main);
        else
            LauncherErrorHandle("Could not create a daemon thread. Launcher.operationMode is null.");

        //Create thread, run thread.
        System.out.println("Daemon Program Run");
        daemonThread.start();
    }

    /**
     * Method looks through each argument provided to the program, and runs actions on hitting specific flags.
     * @param  args the program args to search through
     */
    private static void SearchFlags(String[] args) {
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
                    if (!(operationMode == null))
                        LauncherErrorHandle("Cannot use multiple operation modes.");

                    operationMode = "standard";
                }
                case "--Encrypted-OSPF" -> {
                    if (!(operationMode == null))
                        LauncherErrorHandle("Cannot use multiple operation modes.");

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
                default -> LauncherErrorHandle("Argument not recognised: '" + arg + "'.");//Arg not found. Invalid use of program.
            }
        }
    }

    //https://stackoverflow.com/questions/4113890/how-to-calculate-the-internet-checksum-from-a-byte-in-java
    public static long IpChecksum(byte[] buffer)
    {
        int length = buffer.length;
        int i = 0;
        long sum = 0;
        while (length > 0) {
            sum += (buffer[i++]&0xff) << 8;
            if ((--length)==0) break;
            sum += (buffer[i++]&0xff);
            --length;
        }

        return (~((sum & 0xFFFF)+(sum >> 16)))&0xFFFF;
    }

    private static void LauncherErrorHandle(String message) {
        System.err.println(message);
        System.out.println(commandUsage);
        System.exit(-1);
    }
}
