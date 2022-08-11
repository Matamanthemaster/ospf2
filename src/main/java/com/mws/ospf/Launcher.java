package com.mws.ospf;

/**<p><h1>Application Launcher</h1></p>
 * <p>Entrypoint into the application. Contains process flow to start application, via checking flags, setting up
 * the configuration class and creating a thread for the main process flow daemon being tested</p>
 */
public class Launcher {
    //region STATIC PROPERTIES
    private final static String commandUsage =
            "Usage: java -jar ospf.jar [arguments] <Operation Mode Flag>" + System.lineSeparator() +
                    "Arguments:" + System.lineSeparator() +
                    "   --help:                     Prints this help message" + System.lineSeparator() +
                    "   -g, --with-gui:             Runs the program with the GUI frontend" + System.lineSeparator() +
                    "   -c, --config-file <Path>    Specify an alternate config file (Default ./ospf.conf.xml)" + System.lineSeparator() +
                    "Operation Mode Flags:" + System.lineSeparator() +
                    "   --Standard-OSPF" + System.lineSeparator() +
                    "   --Encrypted-OSPF" + System.lineSeparator();
    private static Thread uiThread;
    private static Thread daemonThread;
    static String operationMode;
    //endregion

    //region STATIC METHODS
    /**<p><h1>Application Main Method</h1></p>
     * <p>Main entrypoint into any java application.</p>
     * @param args user args provided by execution
     */
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
            daemonThread = new Thread(StdDaemon::Main, "Thread-Daemon-StandardOSPF");
        else if (operationMode.equals("encrypted"))
            daemonThread = new Thread(EncDaemon::Main, "Thread-Daemon-EncryptedOSPF");
        else
            LauncherErrorHandle("Could not create a daemon thread. Launcher.operationMode is null.");

        //Create thread, run thread.
        System.out.println("Daemon Program Run");
        daemonThread.start();
    }

    /**<p><h1>Search Flags</h1></p>
     * <p>Method looks through each argument provided to the program, and runs actions on hitting specific flags</p>
     * @param args the program args to search through
     */
    private static void SearchFlags(String[] args) {
        //for (args: arg)
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
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
                    Config.SetConfig(args[i+1]);
                    i++;
                }
                case "--remove-config" -> Config.flagFileConfRemove = true;//Argument useful for testing, will remove the config file.
                default -> LauncherErrorHandle("Argument not recognised: '" + args[i] + "'.");//Arg not found. Invalid use of program.
            }
        }
    }

    /**<p><h1>Create IP Checksum from Buffer</h1></p>
     * <p>Creates an internet checksum, as specified by IP, and used in OSPF.</p>
     * <p>Code implemented in java by user 'Ernest Friedman-Hill' on Stackoverflow in answer to
     * <a href="https://stackoverflow.com/questions/4113890/how-to-calculate-the-internet-checksum-from-a-byte-in-java">
     *     this</a> thread</p>
     * @param buffer buffer to calculate the checksum on
     * @return the internet checksum, as a long (8 bytes)
     */
    public static long IpChecksum(byte[] buffer) {
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

    /**<p><h1>Print Buffer</h1></p>
     * <p>Debug method, print a buffer (e.g. from neighbour) to stdout, formatted as hex, each byte separated by space</p>
     * @param buffer Buffer to print to stdout
     */
    public static void PrintBuffer(byte[] buffer) {
        System.out.print("Packet Buffer: ");
        for (byte b: buffer)
        {
            System.out.print(String.format("%02X", b) + " ");
        }
        System.out.println();
    }

    /**<p><h1>Error Handle (Launcher)</h1></p>
     * <p>On exception (runtime error) or unexpected condition (logical error), graceful terminate.</p>
     * <p>Because the error is in the launch process, specify the command usage</p>
     * @param message message to display to the end user, to indicate what the issue was
     */
    private static void LauncherErrorHandle(String message) {
        System.err.println(message);
        System.out.println(commandUsage);
        System.exit(-1);
    }
    //endregion
}