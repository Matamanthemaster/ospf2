package com.mws.ospf;

import com.mws.ospf.pdt.ExternalStates;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import javafx.scene.control.Tab;

import java.net.DatagramSocket;
import java.util.Timer;
import java.util.TimerTask;

/**<p><h1>Neighbour Node</h1></p>
 * <p>A variation of node which is a neighbour to thisNode. Contains specifics of known information about this node, and
 * specifics for direct communication with this node.</p>
 * <p>Also contains a cheat sheet of all created neighbour nodes to allow program code to easily find a neighbour node
 * by RID, without </p>
 */
public class NeighbourNode extends Node {
    //region STATIC METHODS
    /**<p><h1>Get Reference of Neighbour from RID</h1></p>
     * <p>Searches neighbours table list to find a specific instance of a neighbour that has been previously
     * created, and returns it to be referenced</p>
     * @param rid Router ID to use as an index
     * @return Related instance of neighbour node
     */
    public static NeighbourNode GetNeighbourNodeByRID(IPAddressString rid) {
        for (NeighbourNode n : Config.neighboursTable) {
            if (n.GetRID().equals(rid)) {
                return n;
            }
        }
        return null;
    }

    /**<p><h1>Does Neighbour Node Exist</h1></p>
     * <p>Method checks if neighbour node exists in the neighbours table list.</p>
     * @param rid Router ID to check
     * @return true if it exists, false if not
     */
    public static boolean isNeighbourNodeExists(IPAddressString rid) {
        return GetNeighbourNodeByRID(rid) != null;
    }
    //endregion

    //region OBJECT PROPERTIES
    private ExternalStates state = ExternalStates.DOWN;
    public Timer timerInactivity;
    private boolean flagTimerInactRunning = false;
    public int priority;
    public IPAddress ipAddress;

    //Variable used for unicast datagram communication with this specific neighbour. Used in DBD packet and SLR/U
    public DatagramSocket unicastSocket;
    public final RouterInterface rIntOwner;
    public Tab tab;
    //endregion

    //region OBJECT METHODS
    /**<p><h1>Neighbour Node Constructor</h1></p>
     * <p>Create an instance of Neighbour Node with provided parameters. Used to store information about a specific
     * neighbour.</p>
     * <p>Will also add the neighbour to a list of all neighbour nodes, for easy searching</p>
     * @param rID the neighbour's advertised router ID
     * @param priority the neighbour's advertised priority (MA election)
     * @param ipAddress the neighbour's interface IP address, for unicast messaging
     */
    public NeighbourNode(IPAddressString rID, int priority, IPAddress ipAddress) {
        super(rID);
        this.priority = priority;
        this.ipAddress = ipAddress;
        this.rIntOwner = RouterInterface.GetInterfaceByIPNetwork(ipAddress);

        //Neighbour just added to neighbours, init state.
        SetState(ExternalStates.INIT);

        ResetInactiveTimer();
    }

    /**<p><h1>Reset Inactivity</h1></p>
     * <p>Refreshes the activity timer on this neighbour. Clears any existing inactivity timer already running and
     * schedules a new timer with the delay of the inactivity timer</p>
     */
    void ResetInactiveTimer() {
        if (flagTimerInactRunning) {
            timerInactivity.cancel();
        }

        timerInactivity = new Timer(this.GetRID() + "-DeadTimer");
        timerInactivity.schedule(new TimerTask() {
            @Override
            public void run() {
                TimerExpire();
            }
        }, 40*1000);

        flagTimerInactRunning = true;
    }

    /**<p><h1>Get Neighbour State</h1></p>
     * <p>Getter for neighbour node state</p>
     * @return the ExternalState of the node
     */
    public ExternalStates GetState() {
        return this.state;
    }

    /**<p><h1>Set Neighbour State</h1></p>
     * <p>Set the neighbour node to a specified state</p>
     * @param newState State to set node to
     */
    void SetState(ExternalStates newState) {
        System.out.println("Neighbour " + this.GetRID() + " Statechange:  " +
                this.state.toString() +
                " -> " +
                newState.toString());
        this.state = newState;

    }

    /**<p><h1>Dead Timer Expire</h1></p>
     * <p>Trigger on expiring the inactive timer. Sets the neighbour node to the down state, resetting variables</p>
     */
    private void TimerExpire() {
        this.knownNeighbours.clear();
        if (!Config.neighboursTable.remove(this))
            System.err.println("Unexpected Condition:" + System.lineSeparator() +
                    "Neighbours table didn't include the neighbour " + this.GetRID() + ", and so could not" +
                    " remove it from the neighbours table on timer expire");

        try {
            timerInactivity.cancel();
        } catch (IllegalStateException ignored) {}//IllegalStateException: Timer already cancelled.

        System.out.println("Dead timer expired: " + this.GetRID());
        this.SetState(ExternalStates.DOWN);

        if (unicastSocket != null)
            if (!unicastSocket.isClosed())
               unicastSocket.close();
    }
    //endregion
}
