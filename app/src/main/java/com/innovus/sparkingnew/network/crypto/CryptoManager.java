package com.innovus.sparkingnew.network.crypto;

import android.util.Log;

import com.innovus.sparkingnew.network.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Centralized Crypto and Payload Security Manager for sParking.
 *
 * Seamlessly manages Staging (plain stringified enc_data) vs Production (real AES cipher enc_data)
 * so that business logic and UI activities remain completely decoupled from encryption mechanisms.
 */
public final class CryptoManager {

    private static final String TAG = "CryptoManager";
    public static final String KEY_ENC_DATA = "enc_data";

    private CryptoManager() {
        // Prevent instantiation
    }

    /**
     * Prepares the outbound HTTP JSON request payload.
     * In Staging: Wraps inner JSON string in {"enc_data": "{\"field\":\"value\"}"}.
     * In Production: Encrypts inner JSON using AES and wraps in {"enc_data": "<base64_ciphertext>"}.
     *
     * @param innerJson Inner business data JSONObject
     * @return Complete JSON payload string ready for transmission
     * @throws Exception if encryption fails
     */
    public static String prepareEncryptedPayload(JSONObject innerJson) throws Exception {
        if (innerJson == null) {
            innerJson = new JSONObject();
        }

        JSONObject outerObj = new JSONObject();

        if (ApiConfig.isEncryptionEnabled()) {
            // PRODUCTION MODE: Real AES encryption
            Log.d(TAG, "[ENCRYPT] Production encryption active. Encrypting payload via AES-CBC...");
            String plainText = innerJson.toString();
            String cipherText = AesCipherUtil.encrypt(plainText);
            outerObj.put(KEY_ENC_DATA, cipherText);
            Log.d(TAG, "[ENCRYPT] Payload successfully encrypted into AES Base64 cipher.");
        } else {
            // STAGING / DEBUG MODE: Plain stringified inner JSON
            Log.d(TAG, "[STAGING] Plain enc_data wrapping active (staging mode).");
            outerObj.put(KEY_ENC_DATA, innerJson.toString());
        }

        return outerObj.toString();
    }

    /**
     * Prepares an empty request payload for endpoints that require an empty JSON body.
     */
    public static String prepareEmptyPayload() throws Exception {
        return prepareEncryptedPayload(new JSONObject());
    }

    /**
     * Decrypts and parses the raw server response body into a clean JSONObject.
     *
     * If Encryption is Enabled:
     *  - Automatically identifies and decrypts "enc_data" or encrypted "data" fields.
     *  - Handles fully encrypted responses transparently.
     * If Staging Mode:
     *  - Parses and returns the standard response directly.
     *
     * @param rawResponse Raw HTTP response string from server
     * @return Parsed (and decrypted if applicable) JSONObject
     * @throws Exception if parsing or decryption fails
     */
    public static JSONObject decryptResponse(String rawResponse) throws Exception {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw new IllegalArgumentException("Server response is empty");
        }

        String trimmed = rawResponse.trim();

        // Check if raw response is already a JSON object
        if (trimmed.startsWith("{")) {
            JSONObject responseJson = new JSONObject(trimmed);

            // If encryption is not enabled, return standard response as-is
            if (!ApiConfig.isEncryptionEnabled()) {
                return responseJson;
            }

            // In Production: Check if "enc_data" exists and needs decryption
            if (responseJson.has(KEY_ENC_DATA)) {
                String encData = responseJson.optString(KEY_ENC_DATA, "");
                if (!encData.isEmpty() && !encData.startsWith("{") && !encData.startsWith("[")) {
                    Log.d(TAG, "[DECRYPT] Decrypting 'enc_data' cipher text...");
                    try {
                        String decrypted = AesCipherUtil.decrypt(encData);
                        Log.d(TAG, "[DECRYPT] Decryption successful.");

                        if (decrypted.trim().startsWith("{")) {
                            return new JSONObject(decrypted);
                        } else {
                            responseJson.put("decrypted_data", decrypted);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "[DECRYPT] Could not decrypt 'enc_data' (might be plain server error): " + e.getMessage());
                    }
                }
            }

            // Check if "data" field contains encrypted Base64 cipher text
            if (responseJson.has("data")) {
                String dataStr = responseJson.optString("data", "");
                // If data is a cipher string (not raw JSON object or array)
                if (!dataStr.isEmpty() && !dataStr.equals("null")
                        && !dataStr.startsWith("{") && !dataStr.startsWith("[")) {
                    try {
                        String decryptedData = AesCipherUtil.decrypt(dataStr);
                        if (decryptedData.startsWith("{")) {
                            responseJson.put("data", new JSONObject(decryptedData));
                        } else if (decryptedData.startsWith("[")) {
                            responseJson.put("data", new JSONArray(decryptedData));
                        } else {
                            responseJson.put("data", decryptedData);
                        }
                        Log.d(TAG, "[DECRYPT] 'data' payload decrypted successfully.");
                    } catch (Exception e) {
                        // Fallback: It may already be a plain string (e.g. error message)
                        Log.w(TAG, "[DECRYPT] 'data' was not encrypted or decryption skipped: " + e.getMessage());
                    }
                }
            }

            return responseJson;
        }

        // If the entire HTTP body itself is a raw encrypted Base64 string
        if (ApiConfig.isEncryptionEnabled()) {
            Log.d(TAG, "[DECRYPT] Raw response body appears to be fully encrypted. Decrypting...");
            String decryptedStr = AesCipherUtil.decrypt(trimmed);
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
