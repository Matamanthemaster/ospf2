package com.mws.ospf;

import com.google.common.primitives.Bytes;

import javax.crypto.KeyAgreement;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

public class DHExchange {
    //region OBJECT PROPERTIES
    private final RouterInterface rIntOwner;
    private final int keySize = 2048;
    private KeyPair thisNodeKeyPair;
    private KeyAgreement keyAgreement;
    boolean flagComplete = false;
    //endregion OBJECT PROPERTIES

    //region OBJECT METHODS
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
            StdDaemon.DaemonErrorHandle("UNLIKELY EXCEPTION: NoSuchAlgorithm \"DH\"", ex);
        } catch (InvalidKeyException ex) {
            // Ex on code:  ka.init(thisNodeKeyPair.getPrivate());
            StdDaemon.DaemonErrorHandle("The generated keypair private key for interface \"" + rIntOwner.GetName() + "\" was invalid", ex);
        }
    }

    void receiveDHKey(NeighbourNode neighbour, byte[] receiveBuffer) {
        byte[] encPubKey = Arrays.copyOfRange(receiveBuffer, 26, receiveBuffer.length);

        PublicKey neighbourPubKey = DecodePubKey(encPubKey);

        try {
            keyAgreement.doPhase(neighbourPubKey,true);
            byte[] secretKey = keyAgreement.generateSecret();
            Launcher.PrintBuffer(secretKey);
            SecretKeySpec finalKey = new SecretKeySpec(secretKey, 0, 16, "AES");
            neighbour.enParam = new EncryptionParameters(finalKey);
            flagComplete = true;
        } catch (InvalidKeyException ex) {
            // Ex on code:  keyAgreement.doPhase(neighbourPubKey,true);
            // Ex on code:  neighbour.aesKey = keyAgreement.generateSecret("AES");
            StdDaemon.DaemonErrorHandle("", ex);
        }
    }

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
            byte[] rid = Config.thisNode.GetRIDBytes();
            ospfBuffer[4] = rid[0];
            ospfBuffer[5] = rid[1];
            ospfBuffer[6] = rid[2];
            ospfBuffer[7] = rid[3];
        } catch (Exception ex)  {
            StdDaemon.DaemonErrorHandle("Error when creating an OSPF packet: Substituting in router ID.", ex);
        }

        //Update packet length (2, 3)
        int packetLength = ospfBuffer.length;
        ByteBuffer lenBuffer = ByteBuffer.allocate(4);
        lenBuffer.putInt(packetLength);
        ospfBuffer[2] = lenBuffer.array()[2];
        ospfBuffer[3] = lenBuffer.array()[3];

        //Update Checksum (12, 13)
        ByteBuffer chkBuffer = ByteBuffer.allocate(8);
        chkBuffer.putLong(Launcher.IpChecksum(ospfBuffer));
        ospfBuffer[12] = chkBuffer.array()[6];
        ospfBuffer[13] = chkBuffer.array()[7];

        return ospfBuffer;
    }

    public static PublicKey DecodePubKey(byte[] encPubKeyBuffer) {
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(encPubKeyBuffer);

        try {
            return KeyFactory.getInstance("DH").generatePublic(x509KeySpec);
        } catch (InvalidKeySpecException ex) {
            StdDaemon.DaemonErrorHandle("Received key could not be decoded because it was invalid", ex);
            //Null never returned, as DaemonErrorHandle always exists.
            return null;
        } catch (NoSuchAlgorithmException ex) {
            StdDaemon.DaemonErrorHandle("UNLIKELY EXCEPTION: NoSuchAlgorithm \"DH\"", ex);
            return null;
        }
    }
    //region OBJECT METHODS
}
