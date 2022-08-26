package com.mws.ospf;

/**<p><h1>Encryption Daemon</h1></p>
 * <p>Class contains methods that are executed when the application is in encrypted daemon mode. Behaviour of
 * application process flow is controlled by this class</p>
 * <p>This class is the antithesis of StdDaemon</p>
 */
public class EncDaemon{
    //region STATIC PROPERTIES
    //endregion

    //region STATIC METHODS
    public static void Main() {
        System.out.println("Encrypted Daemon Program Run");

        //Start stat process if conditions set
        if (Stat.endNoAdjacencies != -1)
            Stat.SetupStats();
    }
    //endregion
}
