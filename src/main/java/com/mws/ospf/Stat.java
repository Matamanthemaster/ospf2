package com.mws.ospf;

import com.sun.management.OperatingSystemMXBean;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Stat {
    private static File fileStats;
    private static long tsStart;
    private static long tsConvergence;
    private static HashMap<Long, Integer> mapListTime = new HashMap<>();
    private static List<Double> cpuPercent;
    private static List<Long> memUsage;
    private static Timer timerStatUpdate = new Timer("Timer-Stat");

    public static void SetupStats() {
        //Change poleIntervalMs to change how frequently resource stats are polled
        int pollIntervalMs = 10;
        tsStart = System.nanoTime();
        _RecordStat(tsStart);

        timerStatUpdate.schedule(new TimerTask() {
            @Override
            public void run() {
                _RecordStat(System.nanoTime());
            }
        }, pollIntervalMs, pollIntervalMs);
    }

    public static void EndStats() {
        tsConvergence = System.nanoTime();
        _RecordStat(tsConvergence);

        for (Long ts: mapListTime.keySet()) {
            int tsV = mapListTime.get(ts);
            double curCPUP = cpuPercent.get(tsV);
            double curMemU = memUsage.get(tsV);
            System.out.println(ts + ": " + curCPUP + "%, " + (curMemU / 1000000) + "MB");
        }
    }

    private static void _RecordStat(long timestamp) {
        ThreadMXBean thBean = ManagementFactory.getThreadMXBean();
        int curSIn = mapListTime.size();
        cpuPercent.add(curSIn, thBean.getThreadCpuTime(Launcher.daemonThread.getId()));
        //TODO: Add CPU Percentage
        mapListTime.put(timestamp, curSIn);
        Runtime r = Runtime.getRuntime();
        memUsage.add(curSIn, r.totalMemory() - r.freeMemory());
    }
}
