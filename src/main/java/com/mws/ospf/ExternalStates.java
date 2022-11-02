package com.mws.ospf;

/**<p><h1>External States</h1></p>
 * <p>The state of a neighbour node. Based on OSPF states, used in protocol process flow. Each state has an associated
 * value</p>
 */
enum ExternalStates {
    DOWN(0),
    INIT(1),
    TWOWAY(2),
    EXSTART(3),
    EXCHANCE(4),
    LOADING(5),
    FULL(6);

    public final byte value;

    /**<p><h1>Map ExternalState enum to value</h1></p>
     * @param value value of a state, to be translated to one byte
     */
    ExternalStates(int value) {
        this.value = (byte) value;
    }
}
