package com.innovus.sparkingnew.network.crypto;

import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Enterprise-grade AES-256-GCM cipher utility for payload encryption & decryption.
 * Matches backend and citizen app implementation (AES/GCM/NoPadding with 12-byte IV & 128-bit tag).
 */
public final class AesCipherUtil {

    private static final String TAG = "AesCipherUtil";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int MIN_COMBINED_LENGTH = 12 + 16; // 12-byte IV + 16-byte Tag

    // 256-bit AES-GCM Key (64-character hex string)
    public static final String DEFAULT_GCM_KEY_HEX = "710f5a3bbcb3c168409c47774ba11897be76f08e997085377803271c4d42e961";
    private static String activeKeyHex = DEFAULT_GCM_KEY_HEX;

    private AesCipherUtil() {
        // Utility class
    }

    /**
     * Updates the AES Secret Key dynamically if provided as hex string.
     */
    public static synchronized void setSecretKeyHex(String keyHex) {
        if (keyHex != null && (keyHex.length() == 32 || keyHex.length() == 48 || keyHex.length() == 64)) {
            activeKeyHex = keyHex;
            Log.i(TAG, "AES GCM Key updated dynamically.");
        } else {
            Log.w(TAG, "Invalid AES GCM key hex length.");
        }
    }

    public static synchronized String getActiveKeyHex() {
        return activeKeyHex;
    }

    /**
     * Encrypts plain text string using AES-256-GCM and returns Base64 encoded cipher string:
     * Combined format: [12-byte IV] + [Ciphertext + 16-byte Auth Tag]
     */
    public static String encrypt(String plainText) throws Exception {
        return encrypt(plainText, activeKeyHex);
    }

    public static String encrypt(String plainText, String keyHex) throws Exception {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }

        try {
            byte[] keyBytes = hexStringToByteArray(keyHex);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);

            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);

            return encodeBase64(combined);
        } catch (Exception e) {
            Log.e(TAG, "AES-256-GCM Encryption failed: " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Decrypts Base64 encoded AES-256-GCM cipher string and returns original plain text.
     * Expects: [12-byte IV] + [Ciphertext + 16-byte Auth Tag]
     */
    public static String decrypt(String base64CipherText) throws Exception {
        return decrypt(base64CipherText, activeKeyHex);
    }

    public static String decrypt(String base64CipherText, String keyHex) throws Exception {
        if (base64CipherText == null || base64CipherText.trim().isEmpty()) {
            return "";
        }

        String trimmed = base64CipherText.trim();
        // If it's already plaintext JSON (starts with { or [), return as-is
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }

        try {
            byte[] keyBytes = hexStringToByteArray(keyHex);
            byte[] combined = decodeBase64(trimmed);

            if (combined.length < MIN_COMBINED_LENGTH) {
                throw new IllegalArgumentException("Invalid ciphertext length for AES-GCM (minimum 28 bytes required)");
            }

            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES);

            int cipherLength = combined.length - GCM_IV_LENGTH_BYTES;
            byte[] cipherBytes = new byte[cipherLength];
            System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherBytes, 0, cipherLength);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);

            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "AES-256-GCM Decryption failed: " + e.getMessage(), e);
            throw e;
        }
    }

    public static byte[] hexStringToByteArray(String s) {
        if (s == null) {
            return new byte[0];
        }
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
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
