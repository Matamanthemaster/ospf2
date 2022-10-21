package com.mws.ospf;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**<p><h1>Neighbour Node</h1></p>
 * <p>A variation of node which is a neighbour to thisNode. Contains specifics of known information about this node, and
 * specifics for direct communication with this node.</p>
 * <p>Static methods exist as helpers</p>
 */
public class NeighbourNode extends Node {
    //region STATIC METHODS
    /**<p><h1>Get Reference of Neighbour from RID</h1></p>
     * <p>Searches neighbours table list to find a specific instance of a neighbour that has been previously
     * created, and returns it to be referenced</p>
     * @param rid Router ID to use as an index
     * @return Related instance of neighbour node
     */
    public static NeighbourNode getNeighbourNodeByRID(IPAddressString rid) {
        for (NeighbourNode n : Config.neighboursTable) {
            if (n.getRID().equals(rid)) {
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
        return getNeighbourNodeByRID(rid) != null;
    }
    //endregion

    //region OBJECT PROPERTIES
    int priority = -1;
    private ExternalStates state = ExternalStates.DOWN;
    IPAddress ipAddress;
    final RouterInterface rIntOwner;
    EncryptionParameters  enParam;
    Timer timerInactivity;
    DBDPacket lastSentDBD;
    DBDPacket lastReceivedDBD;
    List<RLSA> lsaRequestList = new ArrayList<>();
    int lastSentLSAIndex = 0;
    boolean isMaster = false;
    private boolean flagTimerInactRunning = false;
    Tab tab;
    //endregion

    //region OBJECT METHODS
    /**<p><h1>Neighbour Node Constructor</h1></p>
     * <p>Create an instance of Neighbour Node with provided parameters. Used to store information about a specific
     * neighbour.</p>
     * <p>Will also add the neighbour to a list of all neighbour nodes, for easy searching</p>
     * @param rid the neighbour's advertised router ID
     * @param ipAddress the neighbour's interface IP address, for unicast messaging
     */
    public NeighbourNode(IPAddressString rid, IPAddress ipAddress) {
        super(rid);
        this.ipAddress = ipAddress;
        this.rIntOwner = RouterInterface.getInterfaceByIPNetwork(ipAddress);
    }

    /**<p><h1>Reset Inactivity</h1></p>
     * <p>Refreshes the activity timer on this neighbour. Clears any existing inactivity timer already running and
     * schedules a new timer with the delay of the inactivity timer</p>
     */
    void resetInactiveTimer() {
        if (flagTimerInactRunning) {
            timerInactivity.cancel();
        }

        timerInactivity = new Timer(this.getRID() + "-DeadTimer");
        timerInactivity.schedule(new TimerTask() {
            @Override
            public void run() {
                expireDeadTimer();
            }
        }, 40*1000);

        flagTimerInactRunning = true;
    }

    /**<p><h1>Get Neighbour State</h1></p>
     * <p>Getter for neighbour node state</p>
     * @return the ExternalState of the node
     */
    public ExternalStates getState() {
        return this.state;
    }

    /**<p><h1>Set Neighbour State</h1></p>
     * <p>Set the neighbour node to a specified state</p>
     * @param newState State to set node to
     */
    void setState(ExternalStates newState) {
        Launcher.printToUser("Neighbour " + this.getRID() + " Statechange:  " +
                this.state.toString() +
                " -> " +
                newState.toString());
        this.state = newState;

    }

    /**<p><h1>Dead Timer Expire</h1></p>
     * <p>Trigger on expiring the inactive timer. Sets the neighbour node to the down state, resetting variables</p>
     */
    private void expireDeadTimer() {

        try {
            timerInactivity.cancel();
        } catch (IllegalStateException ignored) {}//IllegalStateException: Timer already cancelled.

        this.knownNeighbours.clear();
        this.setState(ExternalStates.DOWN);
        this.enParam = null;
        this.lastSentDBD = null;
        this.lastReceivedDBD = null;
        this.lastSentLSAIndex = 0;
        this.lsaRequestList.clear();
        Launcher.printToUser("Dead timer expired: " + this.getRID());

        //Update neighbours on topology change
        if (Launcher.operationMode.equals("standard"))
            StdDaemon.sendHelloPackets();
        if (Launcher.operationMode.equals("encrypted")) {
            EncDaemon.sendHelloPackets();

            //Also for encrypted nodes, begin DHKeyExchange again (Assumes p2p network)
            this.rIntOwner.dhExchange = new DHExchange(rIntOwner);
        }

        //Refresh the local LSA.
        Config.lsdb.setupLocalRLSA();
    }
    //endregion
}
