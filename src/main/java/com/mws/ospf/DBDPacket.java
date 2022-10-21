package com.mws.ospf;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.mws.ospf.RLSA.LSA_HEADER_LENGTH;
/*      Stripped DBD packet
         +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         |         Interface MTU         |    Options    |0|0|0|0|0|I|M|MS
         +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         |                     DD sequence number                        |
         +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         |                                                               |
         +-                                                             -+
         |                                                               |
         +-                      An LSA Header                          -+
         |                                                               |
         +-                                                             -+
         |                                                               |
         +-                                                             -+
         |                                                               |
         +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */

/**
 *
 */
class DBDPacket {
    //region STATIC CONSTANTS
    static final int DBD_HEADER_LENGTH = 8;
    //endregion STATIC CONSTANTS

    //region OBJECT PROPERTIES
    public int ddSeqNo;
    public byte dbdFlags;
    public List<RLSA> listLSAs = new ArrayList<>();
    private int mtu;
    final byte[] packetBuffer;
    //endregion OBJECT PROPERTIES

    //region OBJECT METHODS
    /**<p><h1>Construct DBDPacket from Local Data</h1></p>
     * <p>Construct an OSPF DBD packet from local data stored on this node, using predefined LSAs. This constructor is
     * useful when creating a DBD packet to send, and storing the last sent packet.</p>
     * @param mtu maximum transmission unit to use
     * @param ddSeqNo sequence number to use
     * @param dbdFlags flags on the packet
     * @param listLSAs list of LSAs to
     */
    public DBDPacket(int mtu, int ddSeqNo, byte dbdFlags, @Nullable List<RLSA> listLSAs) {
        this.ddSeqNo = ddSeqNo;
        this.dbdFlags = dbdFlags;
        if (listLSAs != null)
            this.listLSAs = listLSAs;
        this.mtu = mtu;

        this.packetBuffer = makeDBDPacket();
    }

    /**<p><h1>Construct DBDPacket from Buffer</h1></p>
     * <p>Construct an OSPF DBD packet from a buffer received from neighbour, constructing LSAs stored in the process
     * The stored LSAs can form the basis of a request list, if they do not contain link data.</p>
     * <p>This constructor is useful when receiving an OSPF DBD packet form a neighbour, and storing the last received
     * packet</p>
     * @param packetBuffer a full received ospf packet buffer
     */
    public DBDPacket(byte[] packetBuffer) {
        this.packetBuffer = packetBuffer;
        byte[] strippedPacketBuffer = Arrays.copyOfRange(packetBuffer, StdDaemon.HEADER_LENGTH, packetBuffer.length);

        this.mtu = (strippedPacketBuffer[0] << 8) | strippedPacketBuffer[1];
        this.dbdFlags = strippedPacketBuffer[3];
        this.ddSeqNo = (strippedPacketBuffer[4] << 24) | (strippedPacketBuffer[5] << 16) |
                (strippedPacketBuffer[6] << 8) | strippedPacketBuffer[7];

        //Create LSA for each LSA in the DBD packet. Use LSAs for length.
        //" The rest of the packet consists of a (possibly partial) list of the
        //  link-state database's pieces.  Each LSA in the database is described
        //  by its LSA header."
        //The loop self-validates, and while is important.
        int offset = DBD_HEADER_LENGTH;
        try {
            while (offset < strippedPacketBuffer.length) {
                byte[] lsaBuffer = Arrays.copyOfRange(strippedPacketBuffer, offset, offset + LSA_HEADER_LENGTH);
                listLSAs.add(new RLSA(lsaBuffer));
                offset += (lsaBuffer[18] << 8) | lsaBuffer[19];
            }
        } catch (ArithmeticException ex) {
            Launcher.printToUser("An R-LSA from a received DBD packet was malformed at offset " + offset + ": " +
                    ex.getMessage());
            ex.printStackTrace();
            Launcher.printBuffer(strippedPacketBuffer);
        } catch (IllegalArgumentException ex) {
            Launcher.printToUser("An R-LSA in a received DBD packet was invalid at offset " + offset + ": " +
                    ex.getMessage());
            ex.printStackTrace();
            Launcher.printBuffer(strippedPacketBuffer);
        } catch (IndexOutOfBoundsException ex) {
            Launcher.printToUser("DBD packet received from a neighbour was invalid. The offsets in the LSAs did not" +
                    "match up with the packet length: offset=" + offset + ",length=" + strippedPacketBuffer.length);
            ex.printStackTrace();
            Launcher.printBuffer(strippedPacketBuffer);
        }
    }

    /**<p><h1>Is MS Bit Set</h1></p>
     * <p>Is the master bit in this DBD packet set? This flag determines the sending node is declaring itself master.</p>
     * @return true if MS is set
     */
    public boolean isMSBitSet() {
        return (dbdFlags & 0x01) > 0;
    }

    /**<p><h1>Is M Bit Set</h1></p>
     * <p>Is the more bit in this DBD packet set? This flag determines if the sending node has more data to send.</p>
     * @return true if M is set
     */
    public boolean isMoreBitSet() {
        return (dbdFlags & 0x02) > 0;
    }

    /**<p><h1>Is I Bit Set</h1></p>
     * <p>Is the init bit in this DBD packet set? This flag determines the sender is initiating an exchange.</p>
     * @return true if I is set
     */
    public boolean isInitBitSet() {
        return (dbdFlags & 0x04) > 0;
    }

    /**<p><h1>Is This DBD First</h1></p>
     * <p>The first DBD packet has MS, M and I set. Determine if all 3 bits are set.</p>
     * @return true if MS, M and I are set
     */
    public boolean isFirstPacket() {
        return isMSBitSet() && isMoreBitSet() && isInitBitSet();
    }

    /**<p><h1>Make DBD Packet</h1></p>
     * <p>Makes a DBD packet buffer from the data stored in this object. The data can be sent to an adjacent node. This
     * method is only callable by this class, in the constructor for the object, as it populates final byte[]
     * packetBuffer. The DBD data will never change after creation of the packet. It is set in stone.</p>
     * @return a fully complete DBD packet byte buffer
     */
    private byte[] makeDBDPacket() {
        byte[] buffer;

        //GENERIC OSPF HEADER
        if (Launcher.operationMode.equals("encrypted"))
            buffer = new byte[] {0x04};
        else
            buffer = new byte[] {0x02};

        buffer = Bytes.concat(
                buffer,
                new byte[] {
                        //Assume 0x02 or 0x04 is here
                              0x02,//message type
                                    0x00, 0x00,//packet length
                }, Config.thisNode.getRIDBytes(), new byte[] {
                        0x00, 0x00, 0x00, 0x00,//area id (dotted decimal)
                        0x00, 0x00,//checksum
                                    0x00, 0x00,//Auth type//0x00, 0x01
                        0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00,//Auth Data
                });

        //Add in DBD packet data.
        byte[] mtuBuffer = Ints.toByteArray(mtu);
        buffer = Bytes.concat(
                buffer,
                new byte[] {mtuBuffer[2], mtuBuffer[3], 0x00, dbdFlags},//20,21,22,23
                Ints.toByteArray(ddSeqNo)
        );

        //Add in LSA data. Only copy in the header for DBDs.
        for (RLSA lsa: listLSAs) {
            Bytes.concat(buffer, lsa.makeRLSABuffer());
        }

        return StdDaemon.updateChecksumAndLength(buffer);
    }
    //endregion OBJECT METHODS
}
