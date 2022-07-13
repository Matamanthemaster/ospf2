package com.mws.ospf;

import jnr.ffi.types.u_int8_t;

public interface OspfCLib {
    /**<p><h1>Test JNR</h1></p>
     * <p>Function confirms correct function of the JNR JNI bridge. Takes an input which it prints to COut, and always
     * returns an int of 76</p>
     * <p></p>
     * <p><b>int TestJNR(char *p_output);</b></p>
     * @param outStdMsg Output to be printed to COut
     * @return static value 76
     */
    int TestJNR(String outStdMsg);

    /**<p><h1>Send Packet</h1></p>
     * <p></p>
     * <p></p>
     * <p><b>int SendPacket(char *p_intName, unsigned char *p_intHWAddr, char *p_intIPS, unsigned char *p_ospfBuffer, int ospfBufferSize);</b></p>
     * @param intName name of the interface (e.g. enp5s0, eth0)
     * @param intHWAddr hardware (MAC) address of the interface
     * @param intIPSource source IP address of the interface
     * @param ospfPayload payload to send on the wire
     * @param ospfPayloadSize size in bytes of the ospf payload being sent
     * @return function return status
     */
    int SendPacket(String intName, @u_int8_t byte[] intHWAddr, String intIPSource, @u_int8_t byte[] ospfPayload, int ospfPayloadSize);
}
