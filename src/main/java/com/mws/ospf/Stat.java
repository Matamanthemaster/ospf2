package com.mws.ospf;

import com.sun.management.OperatingSystemMXBean;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.*;

/**<p><h1>Application Statistics</h1></p>
 * <p>Class to store, collect and process statistic information for research. Sets up a timer in the SetupStats method to
 * perform the collection activities at a set interval. At the </p>
 */
public class Stat {
    //region STATIC PROPERTIES
    static File fileStats =  new File(System.getProperty("user.dir") + System.getProperty("file.separator") + "ospf.stat.csv");
    static int endNoAdjacencies = -1;
    private static long tsStart;
    private static long tsConvergence;
    private static final LinkedHashSet<Long> timeOrderedSet = new LinkedHashSet<>();
    private static final HashMap<Long, Long> cpuTime = new HashMap<>();
    private static final HashMap<Long, Long> memUsage = new HashMap<>();
    private static final Timer timerStatUpdate = new Timer("Timer-Stat");
    private static final OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    //endregion STATIC PROPERTIES

    //region STATIC METHODS
    /**<p><h1>Setup Statistic Gathering</h1></p>
     * <p>Records the first set of statistics as a base point, especially for timings. Also starts the statistics timer
     * scheduled to record periodically. The statistic poll time is a static value in code.</p>
     */
    static void setupStats() {
        //Change poleIntervalMs to change how frequently resource stats are polled
        int pollIntervalMs = 10;
        System.out.println("STATS: Statistics collection started");
        tsStart = System.currentTimeMillis();
        recordStat(tsStart);

        //Setup timer, delay and interval of pollIntervalMs. Every tick, call _RecordStat with the current milli time.
        timerStatUpdate.schedule(new TimerTask() {
            @Override
            public void run() {
                recordStat(System.currentTimeMillis());
            }
        }, pollIntervalMs, pollIntervalMs);
    }

    /**<p><h1>End Statistic Gathering</h1></p>
     * <p>Call at the end of processing, when endNoAdjacencies condition has been met. Records a final statistic, and
     * works towards saving all data. Creates the stats csv file fileStats, and populates it with experimental data</p>
     */
    static void endStats() {
        //get endpoint of statistics
        tsConvergence = System.currentTimeMillis();
        recordStat(tsConvergence);

        //Stop new statistics being recorded
        timerStatUpdate.cancel();
        System.out.println("STATS: Statistics collection ended");

        //Setup stats file, output all stats to the file.
        try {
            if (fileStats.exists())
                fileStats.delete();
            fileStats.createNewFile();

            FileWriter fwStats = new FileWriter(fileStats, true);

            fwStats.write("timestamp (ms), cpu time (ms), memory usage (KB)" + System.lineSeparator());//Header
            //Store all values. Uses the fact the LinkedHashMap is ordered to make sure data is in the correct output order
            for (Long ts: timeOrderedSet) {
                fwStats.write(ts + ", " + cpuTime.get(ts) + ", " + (memUsage.get(ts) / 1000) + System.lineSeparator());
            }

            fwStats.write(System.lineSeparator() +  System.lineSeparator() +", End, Start, Difference" + System.lineSeparator());
            fwStats.write("Time (ms), " + tsConvergence + ", " + tsStart + ", " + (tsConvergence - tsStart) + System.lineSeparator());

            long cpuTimeStart = cpuTime.get(tsStart);
            long cpuTimeConvergence = cpuTime.get(tsConvergence);
            fwStats.write("CPU Time (ms), " + cpuTimeConvergence + ", " + cpuTimeStart + ", " + (cpuTimeConvergence - cpuTimeStart) + System.lineSeparator());

            long memUStart = memUsage.get(tsStart);
            long memUConvergence = memUsage.get(tsConvergence);
            fwStats.write("Mem Usage (KB), " + (memUConvergence / 1000) + ", " + (memUStart / 1000) + ", " + ((memUConvergence - memUStart) / 1000) + System.lineSeparator());

            fwStats.close();
        } catch (IOException ex) {
            System.err.println("Saving statistics csv created IOException: " + ex.getMessage() + ": StackTrace:");
            ex.printStackTrace();
            System.exit(-1);//Use same code as launcher exit. Handle message already explicit.
        }
    }

    /**<p><h1>Record Statistics Tick</h1></p>
     * <p>Method called on timerStatUpdate tick. Uses an index value provided to store data collected in arrays. The
     * timestamp index is related to the actual index in the lists via the mapListTime HashMap.</p>
     * <p>Stores the timestamp, CPU time and memory usage</p>
     * @param timestamp System.currentTimeMillis() at the time of recording statistics. Index in storage for statistics
     */
    private static void recordStat(long timestamp) {
        timeOrderedSet.add(timestamp);
        cpuTime.put(timestamp, (osBean.getProcessCpuTime() / 1000000));// div by 1000000 to convert to MS, more accurate to scale of returned value
        Runtime runtime = Runtime.getRuntime();
        memUsage.put(timestamp, runtime.totalMemory() - runtime.freeMemory());
    }
    //endregion STATIC METHODS
}
