package com.mws.ospf;

import com.google.common.primitives.Bytes;

import javax.crypto.*;
import java.io.IOException;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static com.mws.ospf.StdDaemon.DaemonErrorHandle;

public class EncryptionParameters {
    private final SecretKey sharedSecret;

    public EncryptionParameters(SecretKey aesKey) {
        this.sharedSecret = aesKey;
    }

    byte[] Encrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, sharedSecret);

            byte[] aesParams = cipher.getParameters().getEncoded();
            Launcher.PrintBuffer(aesParams);
            byte[] ciphertext = cipher.doFinal(data);
            return Bytes.concat(aesParams, ciphertext);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException ex) {
            // Ex on code:  Cipher.getInstance("AES/CBC/PKCS5Padding");
            StdDaemon.DaemonErrorHandle("UNLIKELY EXCEPTION: Encryption: NoSuchAlgorithm / NoSuchPadding  \"AES/CBC/PKCS5Padding\"", ex);
        } catch (InvalidKeyException ex) {
            // Ex on code:  cipherEncrypt.init(Cipher.ENCRYPT_MODE, aesKey);
            StdDaemon.DaemonErrorHandle("Encryption: Invalid Key provided to cipher", ex);
        } catch (IllegalBlockSizeException | BadPaddingException ex) {
            //Data encrypting is bad.
            StdDaemon.DaemonErrorHandle("Encryption: Illegal block size or bad padding", ex);
        } catch (IOException ex) {
            // Ex on code:  cipher.getParameters().getEncoded();
            StdDaemon.DaemonErrorHandle("Encryption: Exception on encoding AES parameters", ex);
        }
        //Return null as the compiler can't see error handle calls exit.
        return null;
    }

    byte[] Decrypt(byte[] data) {
        //TODO: Test encrypt and decrypt.
        try {
            AlgorithmParameters aesParams = AlgorithmParameters.getInstance("AES");
            aesParams.init(Arrays.copyOfRange(data, 0, 16));

            data = Arrays.copyOfRange(data, 16, data.length);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, sharedSecret, aesParams);

            return cipher.doFinal(data);
        } catch (NoSuchAlgorithmException ex) {
            // Ex on code:  AlgorithmParameters.getInstance("AES");
            // Ex on code:  Cipher.getInstance("AES/CBC/PKCS5Padding");
            DaemonErrorHandle("UNLIKELY EXCEPTION: Decryption: NoSuchAlgorithm \"AES\"", ex);
        } catch (NoSuchPaddingException ex) {
            // Ex on code:  Cipher.getInstance("AES/CBC/PKCS5Padding");
            DaemonErrorHandle("UNLIKELY EXCEPTION: Decryption: NoSuchPadding \"PKCS5Padding\"", ex);
        } catch (IllegalBlockSizeException | BadPaddingException ex) {
            // Ex on code:  cipherDecrypt.doFinal(data);
            DaemonErrorHandle("Decryption: Illegal block size or bad padding when decrypting buffer", ex);
        } catch (IOException ex) {
            // Ex on ocde:  aesParams.init()
            StdDaemon.DaemonErrorHandle("Decryption: Exception on decoding AES parameters", ex);
        } catch (InvalidAlgorithmParameterException | InvalidKeyException ex) {
            // Ex on code:  ipher.init()
            StdDaemon.DaemonErrorHandle("Decryption: init cipher", ex);
        }
        //Return null as the compiler can't see error handle calls exit.
        return null;
    }
}
