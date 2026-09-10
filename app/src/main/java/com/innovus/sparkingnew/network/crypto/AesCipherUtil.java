package com.innovus.sparkingnew.network.crypto;

import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Robust, production-grade AES cipher utility for payload encryption & decryption.
 * Uses AES/CBC/PKCS5Padding (Enterprise Standard).
 *
 * Keys and IVs can be updated at runtime or dynamically set during authentication handshake.
 */
public final class AesCipherUtil {

    private static final String TAG = "AesCipherUtil";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";

    // Default configuration (Replace with production keys from backend when available)
    private static String secretKey = "sParking@Sec2026"; // 16 bytes for AES-128
    private static String initVector = "sParkingIV#@2026"; // 16 bytes IV

    private AesCipherUtil() {
        // Utility class
    }

    /**
     * Updates the AES Secret Key dynamically (e.g., from server handshake or environment config).
     * @param key 16-character (128-bit) or 32-character (256-bit) secret key
     */
    public static synchronized void setSecretKey(String key) {
        if (key != null && (key.length() == 16 || key.length() == 24 || key.length() == 32)) {
            secretKey = key;
            Log.i(TAG, "AES Secret Key updated dynamically.");
        } else {
            Log.w(TAG, "Invalid AES key length. Key must be 16, 24, or 32 bytes.");
        }
    }

    /**
     * Updates the AES Initialization Vector (IV) dynamically.
     * @param iv 16-character (128-bit) IV
     */
    public static synchronized void setInitVector(String iv) {
        if (iv != null && iv.length() == 16) {
            initVector = iv;
            Log.i(TAG, "AES IV updated dynamically.");
        } else {
            Log.w(TAG, "Invalid AES IV length. IV must be exactly 16 bytes.");
        }
    }

    /**
     * Encrypts plain text string using AES-CBC-PKCS5Padding and returns Base64 encoded cipher string.
     */
    public static String encrypt(String plainText) throws Exception {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }

        try {
            byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            byte[] ivBytes = initVector.getBytes(StandardCharsets.UTF_8);

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return encodeBase64(encryptedBytes);
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed: " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Decrypts Base64 encoded AES cipher string and returns original plain text.
     */
    public static String decrypt(String base64CipherText) throws Exception {
        if (base64CipherText == null || base64CipherText.isEmpty()) {
            return "";
        }

        try {
            byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            byte[] ivBytes = initVector.getBytes(StandardCharsets.UTF_8);

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            byte[] decodedBytes = decodeBase64(base64CipherText);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);

            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Decryption failed: " + e.getMessage(), e);
            throw e;
        }
    }

    private static String encodeBase64(byte[] data) {
        try {
            String encoded = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
            if (encoded != null) {
                return encoded;
            }
        } catch (Throwable ignored) {
        }
        return java.util.Base64.getEncoder().encodeToString(data);
    }

    private static byte[] decodeBase64(String base64Str) {
        try {
            byte[] decoded = android.util.Base64.decode(base64Str, android.util.Base64.NO_WRAP);
            if (decoded != null && decoded.length > 0) {
                return decoded;
            }
        } catch (Throwable ignored) {
        }
        return java.util.Base64.getDecoder().decode(base64Str);
    }


}
