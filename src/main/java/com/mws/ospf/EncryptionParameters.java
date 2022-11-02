package com.mws.ospf;

import com.google.common.primitives.Bytes;

import javax.crypto.*;
import java.io.IOException;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static com.mws.ospf.StdDaemon.handleDaemonError;

/**<p><h1>Encryption Parameters</h1></p>
 * <p>Class to store a secret key derived from Diffie-Hellman. Provides methods to use the key in encryption and
 * decryption of byte buffers, for each individual NeighbourNode.</p>
 */
class EncryptionParameters {
    //region OBJECT PROPERTIES
    private final SecretKey sharedSecret;
    //endregion OBJECT PROPERTIES

    //region OBJECT METHODS
    /**<p><h1>Encryption Parameters</h1></p>
     * <p>Constructs an encryption parameters object with the specified SecretKey. The key is locked down, being private
     * and final, only exposed to class methods.</p>
     * @param aesKey the shared AES secret
     */
    public EncryptionParameters(SecretKey aesKey) {
        this.sharedSecret = aesKey;
    }

    /**<p><h1>Encrypt Data with Key</h1></p>
     * <p>Method converts a packets data from cleartext to AES ciphertext, using a key and a generated IV parameter.
     * The method expects there to be an OSPF header, and will try to encrypt only the data payload section.</p>
     * <p>The method handles its own exceptions.</p>
     * @param packet a packet budder containing the OSPF header and data to be encrypted with the AES secret key
     * @return the original packet encrypted with AES and the secret key. Appended in the first 18 bytes is also the IV
     * parameter for decrypting the message
     */
    byte[] encrypt(byte[] packet) {
        try {
            //Split packet into predefined blocks of packet. Blocks include OSPF header and packet
            byte[] ospfHeader = Arrays.copyOf(packet, StdDaemon.HEADER_LENGTH);
            byte[] data = Arrays.copyOfRange(packet, StdDaemon.HEADER_LENGTH, packet.length);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, sharedSecret);

            byte[] aesParams = cipher.getParameters().getEncoded();
            data = cipher.doFinal(data);
            return Bytes.concat(ospfHeader, aesParams, data);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException ex) {
            // Ex on code:  Cipher.getInstance("AES/CBC/PKCS5Padding");
            StdDaemon.handleDaemonError("UNLIKELY EXCEPTION: Encryption: NoSuchAlgorithm / NoSuchPadding  \"AES/CBC/PKCS5Padding\"", ex);
        } catch (InvalidKeyException ex) {
            // Ex on code:  cipherEncrypt.init(Cipher.ENCRYPT_MODE, aesKey);
            StdDaemon.handleDaemonError("Encryption: Invalid Key provided to cipher", ex);
        } catch (IllegalBlockSizeException | BadPaddingException ex) {
            //Data encrypting is bad.
            StdDaemon.handleDaemonError("Encryption: Illegal block size or bad padding", ex);
        } catch (IOException ex) {
            // Ex on code:  cipher.getParameters().getEncoded();
            StdDaemon.handleDaemonError("Encryption: Exception on encoding AES parameters", ex);
        } catch (Exception ex) {
            StdDaemon.handleDaemonError("Encryption: Unexpected Exception", ex);
        }
        //Return null as the compiler can't see error handle calls exit.
        return null;
    }

    /**<p><h1>Decrypt Data with Key</h1></p>
     * <p>Method converts AES ciphertext to plaintext, using a key. The method expects the standard OSPF header, and the
     * first 18 bits to include an encoded IV parameter, from the encryption method of the neighbour node, to point at
     * where to start decryption.</p>
     * <p>The method handles its own exceptions.</p>
     * @param packet a packet buffer containing an OSPF header, IV parameters and encrypted packet to be decrypted with
     *               the AES secret key
     * @return the original packet decrypted, with standard OSPF header and trailing decrypted packet
     */
    byte[] decrypt(byte[] packet) {
        try {
            AlgorithmParameters aesParams = AlgorithmParameters.getInstance("AES");
            //Split packet into predefined blocks of packet. Blocks include OSPF header, IV params and encrypted packet
            byte[] ospfHeader = Arrays.copyOf(packet, StdDaemon.HEADER_LENGTH);
            aesParams.init(Arrays.copyOfRange(packet, StdDaemon.HEADER_LENGTH, 42));
            byte[] encryptedData = Arrays.copyOfRange(packet, 42, packet.length);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, sharedSecret, aesParams);

            //return buffer as if no encryption took place.
            return Bytes.concat(ospfHeader, cipher.doFinal(encryptedData));
        } catch (NoSuchAlgorithmException ex) {
            // Ex on code:  AlgorithmParameters.getInstance("AES");
            // Ex on code:  Cipher.getInstance("AES/CBC/PKCS5Padding");
            handleDaemonError("UNLIKELY EXCEPTION: Decryption: NoSuchAlgorithm \"AES\"", ex);
        } catch (NoSuchPaddingException ex) {
            // Ex on code:  Cipher.getInstance("AES/CBC/PKCS5Padding");
            handleDaemonError("UNLIKELY EXCEPTION: Decryption: NoSuchPadding \"PKCS5Padding\"", ex);
        } catch (IllegalBlockSizeException | BadPaddingException ex) {
            // Ex on code:  cipherDecrypt.doFinal(packet);
            handleDaemonError("Decryption: Illegal block size or bad padding when decrypting buffer", ex);
        } catch (IOException ex) {
            // Ex on code:  aesParams.init()
            StdDaemon.handleDaemonError("Decryption: Exception on decoding AES parameters", ex);
        } catch (InvalidAlgorithmParameterException | InvalidKeyException ex) {
            // Ex on code:  cipher.init()
            StdDaemon.handleDaemonError("Decryption: init cipher", ex);
        } catch (Exception ex) {
            StdDaemon.handleDaemonError("Decryption: Unexpected Exception", ex);
        }
        //Return null as the compiler can't see error handle calls exit.
        return null;
    }
    //endregion OBJECT METHODS
}
