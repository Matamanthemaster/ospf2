package com.mws.dplp;

import com.mws.dplp.pdt.ExternalStates;
import inet.ipaddr.IPAddress;
import inet.ipaddr.ipv4.IPv4Address;

import java.util.Timer;
import java.util.TimerTask;

public class NeighbourNode extends Node {

    //Information about a neighbour, mostly defined by RFC2328
    public ExternalStates state;
    public Timer timerInactivity;
    public int priority;
    public IPAddress ipAddress;

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
     * @param NID the neighbour's advertised router ID
     * @param priority the neighbour's advertised priority (MA election)
     * @param ipAddress the neighbour's interface IP address, for unicast messaging
     */
    public NeighbourNode(short NID, int priority, IPAddress ipAddress) {
        super(NID);
        this.priority = priority;
        this.ipAddress = ipAddress;

        //Assume if we have a neighbour, we have a neighbour packet that has passed challange-response,
        //and so they are in Hello-A state.
        state = ExternalStates.HELLO_A;

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
        timerInactivity.schedule(resetTask, Config.deadInterval);
    }

    /**
     * Trigger on expiring the inactive timer.
     * Sets the neighbour node to the down state, resetting variables.
     */
    private void TimerExpire() {
        state = ExternalStates.DOWN;
        timerInactivity.cancel();
    }
}
