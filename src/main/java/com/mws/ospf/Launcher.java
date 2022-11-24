package com.mws.ospf;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**<p><h1>Application Launcher</h1></p>
 * <p>Entrypoint into the application. Contains process flow to start application, via checking flags, setting up
 * the configuration class and creating a thread for the main process flow daemon being tested</p>
 */
class Launcher {
    //region STATIC PROPERTIES
    private final static String commandUsage =
            "Usage: java -jar ospf.jar [arguments] <Operation Mode Flag>" + System.lineSeparator() +
                    "Arguments:" + System.lineSeparator() +
                    "   --help                              Prints this help message" + System.lineSeparator() +
                    "   -g, --with-gui                      Runs the program with the GUI frontend" + System.lineSeparator() +
                    "   -c, --config-file </Path/To/File>   Specify an alternate config file (Default ./ospf.conf.xml)" + System.lineSeparator() +
                    "   -l, --log-file </Path/To/File>      Specify a log file to write program messages to " + System.lineSeparator() +
                    "   -W, --wait                          Tell the application to wait for a start signal from an adjacent node" + System.lineSeparator() +
                    "   -S, --start-exp                     Tell the application to send a start signal to all connected nodes" + System.lineSeparator() +
                    "   -s, --stats-file </Path/To/File>    Specify an alternative statistic file path (Default ./ospf.stats.csv)" + System.lineSeparator() +
                    "   -n  --adjacency-no <#>              Specify how many " + System.lineSeparator() +
                    "Operation Mode Flags:" + System.lineSeparator() +
                    "   --Standard-OSPF" + System.lineSeparator() +
                    "   --Encrypted-OSPF" + System.lineSeparator();
    private static Thread uiThread;
    private static Thread daemonThread;
    static byte operationMode;
    static boolean flagWait;
    static boolean flagStart;
    private static File logFile;
    private static FileWriter logFileWriter;
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
            searchFlags(args);

        //Setup config if it wasn't made from args.
        if (!Config.isConfigExists())
            Config.setConfig();

        //Entry point for CLI daemon.
        if (operationMode == 0)
            handleLauncherError("Operation mode not specified.");

        //Check operation mode from flags, choose appropriate implementation for the PF model.
        if (operationMode == 0x02)
            daemonThread = new Thread(StdDaemon::main, "Thread-Daemon-StandardOSPF");
        else if (operationMode == 0x04)
            daemonThread = new Thread(EncDaemon::main, "Thread-Daemon-EncryptedOSPF");
        else
            handleLauncherError("Could not create a daemon thread. Launcher.operationMode is null.");

        //If the wait logic doesn't apply to this program run, start the chosen daemon thread as usual.
        if (!flagWait && !flagStart) {
            daemonThread.start();
            return;
        }

        //region WAIT LOGIC
        if (flagWait & flagStart)
            handleLauncherError("wait and start-exp flags cannot be both set");

        //Set up epoch time for execution. On wait node, this will just be overridden. On send node, will be sent
        //to connected nodes, and used to synchronise with them. This logic is all oneshot.
        long startTimeEpoch = (System.currentTimeMillis()/1000) + 2;

        //Create a broadcast socket, create a datagram packet to send. Uses try-with-resources.
        try (MulticastSocket socketExperimentWait = new MulticastSocket(25566)) {

            socketExperimentWait.setBroadcast(true);

            String message = String.valueOf(startTimeEpoch);
            DatagramPacket packetWait = new DatagramPacket(
                    message.getBytes(StandardCharsets.UTF_8),
                    message.length(),
                    InetAddress.getByName("255.255.255.255"),
                    25566);

            //if sending, send the packet. If waiting, block the thread with receive, then get the epoch timestamp
            //sent, convert it to a long for use later.
            if (flagStart) {
                printToUser("Wait: Sending start epoch");
                for (RouterInterface r: Config.thisNode.interfaceList) {
                    //skip over enabled interfaces, mainly want to send to the NAT bridge interface, to all nodes.
                    //Does not really matter, all nodes will get the packets anyway.
                    if (r.isEnabled)
                        continue;

                    packetWait.setAddress(r.addrIPv4.toMaxHost().toInetAddress());
                    socketExperimentWait.setNetworkInterface(r.toNetworkInterface());
                    socketExperimentWait.send(packetWait);
                }
            } else {
                printToUser("Wait: Waiting for start epoch");
                socketExperimentWait.receive(packetWait);
                String epochString = new String(packetWait.getData(), StandardCharsets.UTF_8);
                startTimeEpoch = Long.parseLong(epochString);
                printToUser("Wait: Epoch received from " + packetWait.getAddress().toString() + ": " + epochString);
            }
        } catch (SocketException ex) {
            handleLauncherError("Socket exception when setting up wait socket. " + ex.getMessage());
        } catch (UnknownHostException ex) {
            handleLauncherError("Could not get IP address 255.255.255.255. Not likely to occur in runtime.");
        } catch (IOException ex) {
            handleLauncherError("IOException when sending or receiving a datagram packet.");
        } catch (NumberFormatException ex) {
            handleLauncherError("Exception: Could not convert received data from sending node to an epoch long.");
        }

        //Packets have been sent or received. For each node, now set a oneshot timer based on the epoch start time.
        //Used to synchronise all nodes start times. On timer expire, start timer thread, as the program would without
        //wait logic
        Timer timerWait = new Timer("Wait-Timer");
        timerWait.schedule(new TimerTask() {
            @Override
            public void run() {
                daemonThread.start();
            }}, new Date(startTimeEpoch * 1000));
        //endregion WAIT LOGIC
    }

    /**<p><h1>Search Flags</h1></p>
     * <p>Method looks through each argument provided to the program, and runs actions on hitting specific flags</p>
     * @param args the program args to search through
     */
    private static void searchFlags(String[] args) {
        //for (args: arg)
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--Standard-OSPF" -> {
                    if (!(operationMode == 0))
                        handleLauncherError("Cannot use multiple operation modes");

                    operationMode = 0x02;
                }
                case "--Encrypted-OSPF" -> {
                    if (!(operationMode == 0))
                        handleLauncherError("Cannot use multiple operation modes");

                    operationMode = 0x04;
                }
                case "--help", "-help" -> { //Request to see command usage
                    System.out.println(commandUsage);
                    System.exit(0);
                }
                case "-g", "--with-gui" -> uiThread.start();//Argument to launch GUI
                case "-c", "--config-file" -> {//Argument to specify a config file path
                    try {
                        Config.setConfig(args[i+1]);
                    } catch (ArrayIndexOutOfBoundsException ex) {
                        handleLauncherError("Path for the --config-file flag was missing");
                    }
                    i++;
                }
                case "-l", "--log-file" -> {
                    try {
                        logFile = new File(args[i+1]);
                        logFile.delete();
                        logFile.createNewFile();
                        logFileWriter = new FileWriter(logFile);
                    } catch (ArrayIndexOutOfBoundsException ex) {
                        handleLauncherError("Path for the --log-file flag was missing");
                    } catch (IOException e) {
                        handleLauncherError("IOException when creating a log file.");
                    }
                    i++;
                }
                case "-W", "--wait" -> flagWait = true;
                case "-S", "--start-exp" -> flagStart = true;
                case "-s", "--stats-file" -> {
                    try {
                        Stat.fileStats = new File(args[i+1]);
                    } catch (NullPointerException | ArrayIndexOutOfBoundsException ex) {
                        handleLauncherError("Path for the --stats-file flag was invalid or missing");
                    }
                    i++;
                }
                case "-n", "--adjacency-no" -> {
                    try {
                        Stat.endNoAdjacencies = Integer.parseInt(args[i+1]);
                    } catch (NumberFormatException | ArrayIndexOutOfBoundsException ex) {
                        handleLauncherError("the number of adjacencies for the --adjacency-no flag was either missing or not an integer");
                    }
                    i++;
                }
                case "--remove-config" -> Config.flagFileConfRemove = true;//Argument useful for testing, will remove the config file.
                default -> handleLauncherError("Argument not recognised: '" + args[i] + "'.");//Arg not found. Invalid use of program.
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
    public static long ipChecksum(byte[] buffer) {
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
    public static void printBuffer(byte[] buffer) {
        System.out.print("Buffer[" + buffer.length + "]: ");
        for (byte b: buffer)
        {
            System.out.print(String.format("%02X", b) + " ");
        }
        System.out.println();
    }

    /**<p><h1>Print to User</h1></p>
     * <p>Print a message to the user. The message is manipulated to add a prompt style front tag.</p>
     * <p>The method exists to allow further processing of the message in the future, and differentiate with standard
     * debugging and error messages.</p>
     * @param message message to be displayed
     */
    static void printToUser(String message) {
        String line = "[OSPFv" + operationMode + "@" + Config.thisNode.hostname + "-" +
                System.currentTimeMillis() + "]: " + message;
        System.out.println(line);
        if (logFile == null)
            return;
        if (!logFile.exists())
            return;

        try {
            logFileWriter.write(line + System.lineSeparator());
            logFileWriter.flush();
        } catch (IOException ex) {
            handleLauncherError("Could not write message to log file due to IOException: " + ex.getMessage());
        }
    }

    /**<p><h1>Error Handle (Launcher)</h1></p>
     * <p>On exception (runtime error) or unexpected condition (logical error), graceful terminate.</p>
     * <p>Because the error is in the launch process, specify the command usage</p>
     * @param message message to display to the end user, to indicate what the issue was
     */
    private static void handleLauncherError(String message) {
        System.err.println(message);
        System.out.println(commandUsage);
        System.exit(-1);
    }
    //endregion
}