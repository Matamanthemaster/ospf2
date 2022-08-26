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
    static File fileStats =  new File(System.getProperty("user.dir") + System.getProperty("file.separator") + "ospf.stat.csv");
    private static long tsStart;
    private static long tsConvergence;
    private static final LinkedHashMap<Long, Integer> mapListTime = new LinkedHashMap<>();
    private static final List<Long> cpuTime = new ArrayList<>();
    private static final List<Long> memUsage = new ArrayList<>();
    private static final Timer timerStatUpdate = new Timer("Timer-Stat");
    private static OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    static int endNoAdjacencies = -1;

    /**<p><h1>Setup Statistic Gathering</h1></p>
     * <p>Records the first set of statistics as a base point, especially for timings. Also starts the statistics timer
     * scheduled to record periodically. The statistic poll time is a static value in code.</p>
     */
    public static void SetupStats() {
        //Change poleIntervalMs to change how frequently resource stats are polled
        int pollIntervalMs = 10;
        System.out.println("STATS: Statistics collection started");
        tsStart = System.nanoTime();
        _RecordStat(tsStart);

        //Setup timer, delay and interval of pollIntervalMs. Every tick, call _RecordStat with the current nanotime.
        timerStatUpdate.schedule(new TimerTask() {
            @Override
            public void run() {
                _RecordStat(System.nanoTime());
            }
        }, pollIntervalMs, pollIntervalMs);
    }

    /**<p><h1>End Statistic Gathering</h1></p>
     * <p>Call at the end of processing, when endNoAdjacencies condition has been met. Records a final statistic, and
     * works towards saving all data. Creates the stats csv file fileStats, and populates it with experimental data</p>
     */
    public static void EndStats() {
        //get endpoint of statistics
        tsConvergence = System.nanoTime();
        _RecordStat(tsConvergence);

        //Stop new statistics being recorded
        timerStatUpdate.cancel();
        System.out.println("STATS: Statistics collection ended");

        //Setup stats file, output all stats to the file.
        try {
            if (fileStats.exists())
                fileStats.delete();
            fileStats.createNewFile();

            FileWriter fwStats = new FileWriter(fileStats, true);

            fwStats.write("timestamp (ns), cpu time (ms), memory usage (KB)" + System.lineSeparator());//Header
            //Store all values. Uses the fact the LinkedHashMap is ordered to make sure data is in the correct output order
            for (Long ts: mapListTime.keySet()) {
                int tsV = mapListTime.get(ts);
                long curCPUT = cpuTime.get(tsV);
                long curMemU = memUsage.get(tsV);
                fwStats.write(ts + ", " + curCPUT + ", " + (curMemU / 1000) + System.lineSeparator());
            }

            fwStats.write(System.lineSeparator() +  System.lineSeparator() +", End, Start, Difference" + System.lineSeparator());
            fwStats.write("Time (ns), " + tsConvergence + ", " + tsStart + ", " + (tsConvergence - tsStart) + System.lineSeparator());

            long cpuTimeStart = cpuTime.get(mapListTime.get(tsStart));
            long cpuTimeConvergence = cpuTime.get(mapListTime.get(tsConvergence));
            fwStats.write("CPU Time (ms), " + cpuTimeConvergence + ", " + cpuTimeStart + ", " + (cpuTimeConvergence - cpuTimeStart) + System.lineSeparator());

            long memUStart = memUsage.get(mapListTime.get(tsStart));
            long memUConvergence = memUsage.get(mapListTime.get(tsConvergence));
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
     * @param timestamp System.nanoTime() at the time of recording statistics. Index in storage for statistics
     */
    private static void _RecordStat(long timestamp) {
        int curStatIndex = mapListTime.size();
        cpuTime.add(curStatIndex, (osBean.getProcessCpuTime() / 1000000));// div by 1000000 to convert to MS, more accurate to scale of returned value
        mapListTime.put(timestamp, curStatIndex);
        Runtime runtime = Runtime.getRuntime();
        memUsage.add(curStatIndex, runtime.totalMemory() - runtime.freeMemory());
    }
}
