package com.mws.ospf;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Shorts;

import javax.crypto.KeyAgreement;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**<p><h1>Diffie-Hellman Exchange</h1></p>
 * <p>Class for storing and progressing the Diffie-Hellman key exchange process for a router interface. It contains
 * variables related to generating a key, using a Diffie-Hellman key pair for the public and private key, and a
 * key agreement to generate a shared secret from exchanged public and private keys.</p>
 * <p>The current iteration of the class is intended only for use with p2p links, not multi-access. A flag, flagComplete,
 * is present to identify when the p2p exchange is complete.</p>
 */
class DHExchange {
    //region OBJECT PROPERTIES
    private final RouterInterface rIntOwner;
    private final int keySize = 2048;
    private KeyPair thisNodeKeyPair;
    private KeyAgreement keyAgreement;
    boolean flagComplete = false;
    boolean flagProcessingKey = false;
    //endregion OBJECT PROPERTIES

    //region OBJECT METHODS
    /**<p><h1>Diffie-Hellman Exchange</h1></p>
     * <p>Construct a Diffie-Hellman exchange object, initialising keys and exchange parameters, per RouterInterface.</p>
     * <p>Manually handles exceptions.</p>
     * @param rIntOwner owning router interface, primarily for debugging
     */
    public DHExchange(RouterInterface rIntOwner) {
        this.rIntOwner = rIntOwner;

        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
            keyAgreement = KeyAgreement.getInstance("DH");

            kpg.initialize(this.keySize);
            thisNodeKeyPair = kpg.generateKeyPair();
            keyAgreement.init(thisNodeKeyPair.getPrivate());
        } catch (NoSuchAlgorithmException ex) {
            // Ex on code:  KeyPairGenerator.getInstance("DH");
            // Ex on code:  KeyAgreement.getInstance("DH");
            StdDaemon.handleDaemonError("UNLIKELY EXCEPTION: NoSuchAlgorithm \"DH\"", ex);
        } catch (InvalidKeyException ex) {
            // Ex on code:  ka.init(thisNodeKeyPair.getPrivate());
            StdDaemon.handleDaemonError("The generated keypair private key for interface \"" + rIntOwner.getName() + "\" was invalid", ex);
        } catch (Exception ex) {
            StdDaemon.handleDaemonError("DHExchange: Unexpected exception", ex);
        }
    }

    /**<p><h1>Receive Diffie-Hellman PubKey packet</h1></p>
     * <p>From the receiveMulticastThread method, provide a packet, containing the DH PubKey of a neighbour. The expected
     * packet still contains a header, which will be stripped.</p>
     * <p>Manually handles exceptions.</p>
     * @param neighbour the neighbour node sending the packet, used to set the EncryptionParameters of that node
     * @param receiveBuffer the raw OSPFv4 type 6 packet (DH PubKey) containing the public key
     */
    void receiveDHKey(NeighbourNode neighbour, byte[] receiveBuffer) {
        flagProcessingKey = true;
        byte[] encPubKey = Arrays.copyOfRange(receiveBuffer, 26, receiveBuffer.length);

        //Match the reported key size to the key used. For the artefact, is a sanity check, as size is hard coded 20 2048
        int receiveKeySize = Short.toUnsignedInt(Shorts.fromByteArray(Arrays.copyOfRange(receiveBuffer, 24, 26)));
        if (receiveKeySize != this.keySize)
            return;

        PublicKey neighbourPubKey = decodeX509PubKey(encPubKey);

        //Try to create a shared secret byte array, and a final SecretKey for AES. Store it in the neighbour's enParam
        byte[] secretKey = new byte[0];//Defined before scope for exception handle.
        try {
            keyAgreement.doPhase(neighbourPubKey, true);
            secretKey = keyAgreement.generateSecret();
            SecretKeySpec finalKey = new SecretKeySpec(secretKey, 0, 16, "AES");
            neighbour.enParam = new EncryptionParameters(finalKey);
        } catch (InvalidKeyException ex) {
            flagProcessingKey = false;//Reset flag, as no longer processing a key.
            // Ex on code:  keyAgreement.doPhase(neighbourPubKey,true);
            Launcher.printToUser("Received DH PubKey packet " + neighbour.getRID() + " on interface " +
                    this.rIntOwner.getName() + "that was invalid:");
            Launcher.printBuffer(secretKey);//Print key for debug.
        } catch (Exception ex) {
            StdDaemon.handleDaemonError("DHExchange: Unexpected exception", ex);
        }
    }

    /**<p><h1>Make Diffie-Hellman PubKey Packet</h1></p>
     * <p>Constructs and returns a packet buffer for a DH PubKey (ospfv4 type 6, new packet type) packet. Contains an
     * OSPF header, key size and the public key, encoded in x509 for aided complexity</p>
     * @return an OSPF buffer, for message type DH PubKey.
     */
    byte[] makeDHPubKey() {
        byte[] ospfBuffer = {
                //GENERIC OSPF HEADER
                0x04,//version //v 4 for encrypted OSPF
                0x06,//message type//type 6, special new type for DH PubKey.
                0x00, 0x00,//packet length //MUST BE REAL VALUE
                0x00, 0x00, 0x00, 0x00,//source router rid (dotted decimal) //MUST BE REAL VALUE
                0x00, 0x00, 0x00, 0x00,//area id (dotted decimal) //MUST BE ALL 0s
                0x00, 0x00,//checksum
                0x00, 0x00,//Auth type//0x00, 0x01
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,//Auth Data//0x63, 0x69, 0x73, 0x63, 0x6f, 0x00, 0x00, 0x00//"Cisco"

                //Data
                0x00, 0x00,//KeySize
                //PubKey
        };

        //Add PubKey to buffer
        ospfBuffer = Bytes.concat(ospfBuffer, thisNodeKeyPair.getPublic().getEncoded());

        //Update KeySize (24, 25)
        ByteBuffer keySizeBuffer = ByteBuffer.allocate(4);
        keySizeBuffer.putInt(this.keySize);
        ospfBuffer[24] = keySizeBuffer.array()[2];
        ospfBuffer[25] = keySizeBuffer.array()[3];

        //Update router ID (4, 5, 6, 7)
        try {
            byte[] rid = Config.thisNode.getRIDBytes();
            ospfBuffer[4] = rid[0];
            ospfBuffer[5] = rid[1];
            ospfBuffer[6] = rid[2];
            ospfBuffer[7] = rid[3];
        } catch (Exception ex)  {
            StdDaemon.handleDaemonError("Error when creating an OSPF packet: Substituting in router ID.", ex);
        }

        //Update packet length (2, 3), Update Checksum (12, 13)
        return StdDaemon.updateChecksumAndLength(ospfBuffer);
    }

    /**<p><h1>Decode x509 Public Key</h1></p>
     * <p>Decode a provided x509 byte buffer, and convert it to a Diffie-Hellman public key. The use of x509 encoding is
     * recommended by the encryption java documentation.</p>
     * <p>Manually handles exceptions.</p>
     * @param encPubKeyBuffer a x509 encoded public key buffer
     * @return decoded public key in usable format
     */
    public static PublicKey decodeX509PubKey(byte[] encPubKeyBuffer) {
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(encPubKeyBuffer);

        try {
            return KeyFactory.getInstance("DH").generatePublic(x509KeySpec);
        } catch (InvalidKeySpecException ex) {
            StdDaemon.handleDaemonError("Received key could not be decoded because it was invalid", ex);
            //Null never returned, as DaemonErrorHandle always exists.
        } catch (NoSuchAlgorithmException ex) {
            StdDaemon.handleDaemonError("UNLIKELY EXCEPTION: NoSuchAlgorithm \"DH\"", ex);
        } catch (Exception ex) {
            StdDaemon.handleDaemonError("DHExchange: Unexpected exception", ex);
        }
        return null;
    }
    //endregion OBJECT METHODS
}
