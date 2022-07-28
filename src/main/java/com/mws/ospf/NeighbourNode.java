package com.mws.ospf;

import com.mws.ospf.pdt.ExternalStates;
import inet.ipaddr.IPAddress;

import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class NeighbourNode extends Node {

    //STATIC COMPONENTS
    private static List<NeighbourNode> _NeighbourNodes = new ArrayList<>();

    public static NeighbourNode GetNeighbourNodeByRID(String rid) {
        for (NeighbourNode n : _NeighbourNodes) {
            if (n.GetRID().equals(rid)) {
                return n;
            }
        }
        return null;
    }

    //OBJECT COMPONENTS

    //Information about a neighbour, mostly defined by RFC2328
    public ExternalStates state;

    //Start the inactivity timer, as the neighbour is now known and could become inactive.
    public Timer timerInactivity;
    private boolean flagTimerInactRunning = false;
    public int priority;
    public IPAddress ipAddress;

    //Variable used for unicast datagram communication with this specific neighbour. Used in DBD packet and SLR/U
    public DatagramSocket unicastSocket;

    /**
     * Create an instance of Neighbour Node with provided parameters.
     * Used to store information about a specific neighbour.
     * @param rID the neighbour's advertised router ID
     * @param priority the neighbour's advertised priority (MA election)
     * @param ipAddress the neighbour's interface IP address, for unicast messaging
     */
    public NeighbourNode(String rID, int priority, IPAddress ipAddress) {
        super(rID);
        this.priority = priority;
        this.ipAddress = ipAddress;

        //Neighbour just added to neighbours, init state.
        this.state = ExternalStates.INIT;

        ResetInactiveTimer();

        _NeighbourNodes.add(this);
    }

    /**<p>Reset Inactivity</p>
     * <p>Refreshes the activity timer on this neighbour. Clears any existing inactivity timer already running and
     * schedules a new timer with the delay of the inactivity timer</p>
     */
    void ResetInactiveTimer() {
        if (flagTimerInactRunning) {
            timerInactivity.cancel();
        }

        timerInactivity = new Timer(this.GetRID() + "-ResetTimer");
        timerInactivity.schedule(new TimerTask() {
            @Override
            public void run() {
                TimerExpire();
            }
        }, 40*1000);
        System.out.println("Timer Reset: " + this.GetRID());//aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa

        flagTimerInactRunning = true;
    }

    /**
     * Trigger on expiring the inactive timer.
     * Sets the neighbour node to the down state, resetting variables.
     */
    private void TimerExpire() {
        state = ExternalStates.DOWN;
        try {
            timerInactivity.cancel();
        } catch (IllegalStateException ignored) {}//IllegalStateException: Timer already cancelled.
        System.err.println("Timer expired: " + this.GetRID());//aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
        if (unicastSocket != null)
            if (!unicastSocket.isClosed())
               unicastSocket.close();
    }
}
