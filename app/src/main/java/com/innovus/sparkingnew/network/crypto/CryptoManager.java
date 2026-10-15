package com.innovus.sparkingnew.network.crypto;

import android.util.Log;

import com.innovus.sparkingnew.network.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Centralized Crypto and Payload Security Manager for sParking.
 *
 * Staging vs Production:
 * - STAGING: Sends plain real data payload (no AES encryption).
 * - PRODUCTION: Automatically encrypts and decrypts payloads via AES-256-GCM.
 *
 * NOTE: Sensitive real data is NEVER printed to Android logs. Only status and ciphers are logged.
 */
public final class CryptoManager {

    private static final String TAG = "sParking_API";
    public static final String KEY_ENC_DATA = "enc_data";

    private CryptoManager() {
        // Prevent instantiation
    }

    /**
     * Prepares the outbound HTTP JSON request payload.
     * In Staging: Sends plain real data in enc_data: {"enc_data": "{\"field\":\"value\"}"}.
     * In Production: Encrypts inner JSON using AES-256-GCM and wraps in {"enc_data": "<base64_ciphertext>"}.
     */
    public static String prepareEncryptedPayload(JSONObject innerJson) throws Exception {
        if (innerJson == null) {
            innerJson = new JSONObject();
        }

        JSONObject outerObj = new JSONObject();

        if (ApiConfig.isEncryptionEnabled()) {
            // PRODUCTION MODE: Real AES-256-GCM encryption
            String plainText = innerJson.toString();
            String cipherText = AesCipherUtil.encrypt(plainText);
            outerObj.put(KEY_ENC_DATA, cipherText);

            Log.i(TAG, "🔒 [CryptoManager:ENCRYPT] PRODUCTION MODE (AES-256-GCM) - Encrypted payload generated");
            Log.i(TAG, "📤 Encrypted Cipher (On-The-Wire): " + cipherText);
        } else {
            // STAGING MODE: Plain real data payload
            outerObj.put(KEY_ENC_DATA, innerJson.toString());

            Log.i(TAG, "🧪 [CryptoManager:STAGING] STAGING MODE - Outbound payload prepared (Plain enc_data)");
        }

        return outerObj.toString();
    }

    /**
     * Prepares an empty request payload.
     */
    public static String prepareEmptyPayload() throws Exception {
        return prepareEncryptedPayload(new JSONObject());
    }

    /**
     * Decrypts and parses the raw server response body into a clean JSONObject.
     *
     * In Staging: Parses and returns the standard response directly without decryption.
     * In Production: Decrypts AES-256-GCM encrypted "enc_data" or "data" fields without exposing plaintext in logs.
     */
    public static JSONObject decryptResponse(String rawResponse) throws Exception {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw new IllegalArgumentException("Server response is empty");
        }

        String trimmed = rawResponse.trim();

        // Check if raw response is already a JSON object
        if (trimmed.startsWith("{")) {
            JSONObject responseJson = new JSONObject(trimmed);

            // STAGING MODE: Return standard response as-is (no decryption required)
            if (!ApiConfig.isEncryptionEnabled()) {
                Log.i(TAG, "🧪 [CryptoManager:DECRYPT] STAGING MODE - Response processed directly.");
                return responseJson;
            }

            // PRODUCTION MODE: Decrypt "enc_data" if present
            if (responseJson.has(KEY_ENC_DATA)) {
                String encData = responseJson.optString(KEY_ENC_DATA, "");
                if (!encData.isEmpty() && !encData.startsWith("{") && !encData.startsWith("[")) {
                    Log.i(TAG, "🔒 [CryptoManager:DECRYPT] Decrypting 'enc_data' cipher via AES-256-GCM...");
                    try {
                        String decrypted = AesCipherUtil.decrypt(encData);
                        Log.i(TAG, "🔓 [CryptoManager:DECRYPT] 'enc_data' decrypted successfully.");

                        if (decrypted.trim().startsWith("{")) {
                            return new JSONObject(decrypted);
                        } else {
                            responseJson.put("decrypted_data", decrypted);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "⚠️ [CryptoManager:DECRYPT] Could not decrypt 'enc_data': " + e.getMessage());
                    }
                }
            }

            // PRODUCTION MODE: Decrypt "data" field if it contains Base64 cipher text
            if (responseJson.has("data")) {
                String dataStr = responseJson.optString("data", "");
                if (!dataStr.isEmpty() && !dataStr.equals("null")
                        && !dataStr.startsWith("{") && !dataStr.startsWith("[")) {
                    try {
                        String decryptedData = AesCipherUtil.decrypt(dataStr);
                        Log.i(TAG, "🔓 [CryptoManager:DECRYPT] 'data' cipher decrypted successfully via AES-256-GCM.");
                        if (decryptedData.startsWith("{")) {
                            responseJson.put("data", new JSONObject(decryptedData));
                        } else if (decryptedData.startsWith("[")) {
                            responseJson.put("data", new JSONArray(decryptedData));
                        } else {
                            responseJson.put("data", decryptedData);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "⚠️ [CryptoManager:DECRYPT] 'data' decryption skipped/plain: " + e.getMessage());
                    }
                }
            }

            return responseJson;
        }

        // If the entire HTTP body itself is a raw encrypted Base64 string
        if (ApiConfig.isEncryptionEnabled()) {
            Log.i(TAG, "🔒 [CryptoManager:DECRYPT] Entire response body is encrypted cipher. Decrypting via AES-256-GCM...");
            String decryptedStr = AesCipherUtil.decrypt(trimmed);
            Log.i(TAG, "🔓 [CryptoManager:DECRYPT] Response body decrypted successfully.");
            if (decryptedStr.trim().startsWith("{")) {
                return new JSONObject(decryptedStr);
            } else {
                JSONObject fallback = new JSONObject();
                fallback.put("data", decryptedStr);
                return fallback;
            }
        }

        return new JSONObject(trimmed);
    }
}
