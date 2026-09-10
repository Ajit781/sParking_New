package com.innovus.sparkingnew.network;

import android.util.Log;

import com.innovus.sparkingnew.network.crypto.CryptoManager;

import org.json.JSONObject;

/**
 * Universal helper for extracting exact, authentic server error and response messages
 * from the backend sParking REST API across all endpoints.
 */
public class ApiResponseHelper {

    private static final String TAG = "ApiResponseHelper";

    private static final String[] MESSAGE_KEYS = {
            "message",
            "response_message",
            "response_desc",
            "msg",
            "error_message",
            "error",
            "status_message",
            "status_desc",
            "detail",
            "details",
            "Description",
            "Message"
    };

    /**
     * Extracts the real, authentic message returned by the server API without loss or alteration.
     *
     * @param json Parsed server JSON response
     * @param defaultFallback Fallback message if no message field exists in JSON
     * @return Authentic message from API
     */
    public static String extractServerMessage(JSONObject json, String defaultFallback) {
        if (json == null) {
            return (defaultFallback != null) ? defaultFallback : "";
        }

        // 1. Check primary message keys in top-level JSON
        for (String key : MESSAGE_KEYS) {
            if (json.has(key)) {
                String val = json.optString(key, "").trim();
                if (!val.isEmpty() && !"null".equalsIgnoreCase(val)) {
                    return val;
                }
            }
        }

        // 2. Check if "data" is a JSONObject containing a message key
        if (json.has("data")) {
            Object dataObj = json.opt("data");
            if (dataObj instanceof JSONObject) {
                JSONObject inner = (JSONObject) dataObj;
                for (String key : MESSAGE_KEYS) {
                    if (inner.has(key)) {
                        String val = inner.optString(key, "").trim();
                        if (!val.isEmpty() && !"null".equalsIgnoreCase(val)) {
                            return val;
                        }
                    }
                }
            } else if (dataObj instanceof String) {
                String dataStr = ((String) dataObj).trim();
                if (!dataStr.isEmpty() && !dataStr.startsWith("{") && !dataStr.startsWith("[") && !"null".equalsIgnoreCase(dataStr)) {
                    return dataStr;
                }
            }
        }

        // 3. Check "decrypted_data" if decrypted from enc_data
        if (json.has("decrypted_data")) {
            String decStr = json.optString("decrypted_data", "").trim();
            if (!decStr.isEmpty() && !decStr.startsWith("{") && !decStr.startsWith("[")) {
                return decStr;
            }
        }

        return (defaultFallback != null) ? defaultFallback : "";
    }

    /**
     * Safely decrypts and parses response string into a JSONObject.
     * If CryptoManager fails or throws an exception, attempts raw JSON parsing as fallback.
     */
    public static JSONObject parseAndDecryptResponse(String responseStr) {
        if (responseStr == null || responseStr.trim().isEmpty()) {
            return null;
        }

        String trimmed = responseStr.trim();
        try {
            return CryptoManager.decryptResponse(trimmed);
        } catch (Exception e) {
            Log.w(TAG, "CryptoManager decrypt failed, attempting direct JSON parse: " + e.getMessage());
            if (trimmed.startsWith("{")) {
                try {
                    return new JSONObject(trimmed);
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /**
     * Cleans non-JSON responses without showing 'HTTP' or status codes to the user.
     */
    public static String cleanHttpError(String responseStr, int responseCode, String httpMessage) {
        if (responseStr == null || responseStr.trim().isEmpty()) {
            return "Unable to connect to server. Please check internet connection.";
        }

        String trimmed = responseStr.trim();
        if (trimmed.startsWith("<") || trimmed.toLowerCase().contains("<html")) {
            return "Unable to process request. Please try again.";
        }

        return trimmed;
    }
}
