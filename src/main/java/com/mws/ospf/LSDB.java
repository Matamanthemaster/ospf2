package com.mws.ospf;

import com.google.common.primitives.Ints;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

class LSDB {

    //region OBJECT PROPERTIES
    /**<p><h1>Router LSA List</h1></p>
     *<p>Stores a list of router LSAs known to this node. The first item (index 0) will always be the local LSA data</p>
     */
    List<RLSA> routerLSAs = new ArrayList<>();
    Timer ageTimer = new Timer("LSDB-Age-Timer");
    //endregion OBJECT PROPERTIES

    //region OBJECT METHODS

    /**<p><h1>Construct Link-State DataBase</h1></p>
     * <p>Construct a LSDB object. Sets up the first local LSA, and sets up the aging timer.</p>
     */
    public LSDB() {
        setupLocalRLSA();

        //Setup timer, age all LSAs every second, starting in 1 second
        ageTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                for (RLSA lsa: routerLSAs) {
                    lsa.ageLSA();
                }
            }
        },1000,1000);
    }

    void setupLocalRLSA() {
        //Set sequence number. For first time local R-LSA, this is the initial sequence number. If the local R-LSA already
        //exists, use that incremented. Also remove the old localRLSA, which is about to be overridden.
        int lsSeqNumber = RLSA.INITIAL_SEQUENCE_NUMBER;
        if (!routerLSAs.isEmpty()) {
            lsSeqNumber = routerLSAs.get(0).lsSeqNumber;
            lsSeqNumber++;
            routerLSAs.remove(0);
        }

        List<LinkData> linkData = new ArrayList<>();
        //Construct LinkData per neighbour that is not down.
        //For future LSAs, the RouterInterface object should be modified to store a list of neighbours. This loop can
        //then iterate over each neighbour for each router interface. For now this works for RLSAs.
        for (NeighbourNode neighbour: Config.neighboursTable) {
            if (neighbour.getState().value <= ExternalStates.INIT.value)
                continue;

            //Calculate interface cost, store int in metric buffer. Remake buffer as a subset of the original, containing
            //the last two octets, only copying the short from the int.
            byte[] metric = Ints.toByteArray(neighbour.rIntOwner.getCost());
            metric = new byte[] {metric[2], metric[3]};

            LinkData localLinkData = new LinkData(
                neighbour.getRID(), neighbour.ipAddress.toAddressString(), metric
            );
            linkData.add(localLinkData);
        }

        routerLSAs.add(0, new RLSA(lsSeqNumber, linkData));
    }

    void removeRLSA(RLSA lsa) {
        routerLSAs.remove(lsa);
        if (lsa.advertisingRouter.equals(Config.thisNode.getRID())) {
            setupLocalRLSA();
        }
    }
    //endregion OBJECT METHODS
}


