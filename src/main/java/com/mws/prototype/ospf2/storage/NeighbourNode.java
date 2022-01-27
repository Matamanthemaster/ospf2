package com.mws.prototype.ospf2.storage;

import java.net.Inet4Address;
import java.net.InterfaceAddress;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class NeighbourNode extends Node {

    //Information about a neighbour, mostly defined by RFC2328
    public NeighbourState state;
    public Timer timerInactivity;
    public MasterSlave exchangeRole;
    public int ddSeqNum;
    public LastReceivedDD lastDD;
    public int priority;
    public InterfaceAddress ipAddress;
    public List<HelloOptions> helloOptions;

    //variable for reset task, identical for each instance of object, but with the correct reference to the instance of TimerExpire() method.
    //Cannot use lambda because there isn't a wrapper for TimerTask setup.
    private final TimerTask resetTask = new TimerTask() {
        @Override
        public void run() {
            TimerExpire();
        }
    };

    /**
     * Create an instance of Neighbour Node with provided parameters.
     * Used to store information about a specific neighbour.
     * @param rID the neighbour's advertised router ID
     * @param priority the neighbour's advertised priority (MA election)
     * @param ipAddress the neighbour's interface IP address, for unicast messaging
     * @param helloOptions the neighbour's advertised options
     */
    public NeighbourNode(Inet4Address rID, int priority, InterfaceAddress ipAddress, List<HelloOptions> helloOptions) {
        super(rID);
        this.priority = priority;
        this.ipAddress = ipAddress;
        this.helloOptions = helloOptions;

        //Assume if we have a neighbour, we have a neighbour packet, and so they are in init state.
        state = NeighbourState.INIT;
        exchangeRole = null;
        ddSeqNum = -1;
        lastDD = null;

        //Start the inactivity timer, as the neighbour is now known and could become inactive.
        timerInactivity = new Timer();
        ResetInactiveTimer();
    }

    /**
     * Reset the inactive timer.
     * First cancels any existing timers, then schedules the next timer.
     */
    private void ResetInactiveTimer() {
        timerInactivity.cancel();
        timerInactivity.schedule(resetTask, Config.inactiveTimerDelay);
    }

    /**
     * Trigger on expiring the inactive timer.
     * Sets the neighbour node to the down state, resetting variables.
     */
    private void TimerExpire() {
        state = NeighbourState.DOWN;
        exchangeRole = null;
        ddSeqNum = -1;
        lastDD = null;
        timerInactivity.cancel();
    }
}
