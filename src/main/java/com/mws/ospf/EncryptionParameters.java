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
public class EncryptionParameters {
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
     * <p>Method converts cleartext to AES ciphertext, using a key and a generated IV parameter.</p>
     * <p>The method handles its own exceptions.</p>
     * @param data data to be encrypted with the AES secret key
     * @return the original data encrypted with AES and the secret key. Appended in the first 18 bytes is also the IV
     * parameter for decrypting the message
     */
    byte[] encrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, sharedSecret);

            byte[] aesParams = cipher.getParameters().getEncoded();
            byte[] ciphertext = cipher.doFinal(data);
            return Bytes.concat(aesParams, ciphertext);
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
     * @param data data + IV parameters to be decrypted with the AES secret key
     * @return the original data decrypted, with standard OSPF header and trailing decrypted data
     */
    byte[] decrypt(byte[] data) {
        try {
            AlgorithmParameters aesParams = AlgorithmParameters.getInstance("AES");
            //Split data into predefined blocks of data. Blocks include OSPF header, IV params and encrypted data
            //0-23 (24 bytes) inclusive, header. 24-41 (18 bytes) inclusive, IV params. 42-end, encrypted data.
            byte[] ospfHeader = Arrays.copyOfRange(data, 0, 24);
            aesParams.init(Arrays.copyOfRange(data, 24, 42));
            byte[] encryptedData = Arrays.copyOfRange(data, 42, data.length);

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
            // Ex on code:  cipherDecrypt.doFinal(data);
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
