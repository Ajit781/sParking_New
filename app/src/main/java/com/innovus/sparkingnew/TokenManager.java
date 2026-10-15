package com.innovus.sparkingnew;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Centralized singleton manager for saving, accessing, and validating authentication tokens.
 * Features thread-safe silent auto-refresh (getValidTokenSync) and reactive invalidation (invalidateToken).
 */
public class TokenManager {

    private static final String TAG = "sParking_API";
    private static final String PREF_NAME = "sParking_auth_prefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_CREATED_AT = "created_at";
    private static final String KEY_EXPIRES_AT = "expires_at";

    private static final Object REFRESH_LOCK = new Object();
    private static TokenManager instance;
    private final SharedPreferences prefs;

    // In-memory cache for fast access
    private static volatile String cachedToken = null;

    public interface TokenFetchCallback {
        void onTokenReady(String token);
        void onError(String error);
    }

    private TokenManager(Context context) {
        Context ctx = context != null ? context.getApplicationContext() : SParkingApp.getAppContext();
        prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        cachedToken = prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public static synchronized TokenManager getInstance() {
        if (instance == null) {
            instance = new TokenManager(SParkingApp.getAppContext());
        }
        return instance;
    }

    public static synchronized TokenManager getInstance(Context context) {
        if (instance == null) {
            Context ctx = (context != null) ? context.getApplicationContext() : SParkingApp.getAppContext();
            instance = new TokenManager(ctx);
        }
        return instance;
    }

    /**
     * Save newly generated token to SharedPreferences and in-memory cache.
     */
    public void saveToken(String accessToken, String createdAt, String expiresAt) {
        cachedToken = accessToken;
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_CREATED_AT, createdAt)
                .putString(KEY_EXPIRES_AT, expiresAt)
                .apply();
        Log.i(TAG, "Token saved. Valid until: " + expiresAt);
    }

    /**
     * Returns the access token. Checks memory cache first, then SharedPreferences.
     */
    public String getToken() {
        if (cachedToken != null && !cachedToken.isEmpty()) {
            return cachedToken;
        }
        cachedToken = prefs.getString(KEY_ACCESS_TOKEN, null);
        return cachedToken;
    }

    /**
     * Thread-safe method to get a guaranteed-valid token.
     * If valid, returns immediately.
     * If expired or missing, silently refreshes the token synchronously via AuthNetworkService.
     * MUST be called from a background thread.
     */
    public String getValidTokenSync() {
        if (isTokenValid()) {
            return getToken();
        }

        synchronized (REFRESH_LOCK) {
            // Double-checked locking
            if (isTokenValid()) {
                return getToken();
            }

            Log.i(TAG, "[SILENT TOKEN REFRESH] Token expired or missing. Fetching fresh token synchronously...");
            AuthNetworkService.AuthResult result = AuthNetworkService.generateTokenSync();
            if (result.isSuccess()) {
                saveToken(result.getAccessToken(), result.getCreatedAt(), result.getExpiresAt());
                Log.i(TAG, "[SILENT TOKEN REFRESH SUCCESS] New token saved. Valid until: " + result.getExpiresAt());
                return result.getAccessToken();
            } else {
                Log.e(TAG, "[SILENT TOKEN REFRESH FAILED] " + result.getMessage());
                return getToken(); // Return existing token as fallback
            }
        }
    }

    /**
     * Asynchronously guarantees a valid token, refreshing if needed.
     */
    public void ensureValidToken(TokenFetchCallback callback) {
        if (callback == null) return;
        if (isTokenValid()) {
            callback.onTokenReady(getToken());
            return;
        }

        Log.i(TAG, "[ASYNC TOKEN REFRESH] Fetching fresh token asynchronously...");
        AuthNetworkService.generateToken(new AuthNetworkService.TokenCallback() {
            @Override
            public void onSuccess(String accessToken, String createdAt, String expiresAt, String message) {
                saveToken(accessToken, createdAt, expiresAt);
                callback.onTokenReady(accessToken);
            }

            @Override
            public void onFailure(String errorMessage) {
                String existing = getToken();
                if (existing != null && !existing.trim().isEmpty()) {
                    callback.onTokenReady(existing);
                } else {
                    callback.onError(errorMessage);
                }
            }
        });
    }

    /**
     * Marks the current token as invalidated / expired (e.g. after a 401 Unauthorized from server).
     */
    public void invalidateToken() {
        cachedToken = null;
        prefs.edit().remove(KEY_ACCESS_TOKEN).remove(KEY_EXPIRES_AT).apply();
        Log.i(TAG, "Token explicitly invalidated.");
    }

    /**
     * Helper to get full Authorization header string: "Bearer <token>"
     */
    public String getAuthHeader() {
        String token = getToken();
        if (token != null && !token.trim().isEmpty()) {
            return "Bearer " + token.trim();
        }
        return "";
    }

    public static String getAuthHeader(Context context) {
        return getInstance(context).getAuthHeader();
    }

    public static String getToken(Context context) {
        return getInstance(context).getToken();
    }

    public String getCreatedAt() {
        return prefs.getString(KEY_CREATED_AT, "");
    }

    public String getExpiresAt() {
        return prefs.getString(KEY_EXPIRES_AT, "");
    }

    public boolean hasToken() {
        String token = getToken();
        return token != null && !token.trim().isEmpty();
    }

    /**
     * Checks if the stored token exists and is NOT yet expired.
     * Uses 1-minute buffer before expiration.
     */
    public boolean isTokenValid() {
        if (!hasToken()) {
            Log.d(TAG, "isTokenValid: No token stored");
            return false;
        }

        String expiresAtStr = getExpiresAt();
        if (expiresAtStr != null && !expiresAtStr.trim().isEmpty()) {
            try {
                // Expected format: "2026-09-08 12:17:05"
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                Date expiryDate = sdf.parse(expiresAtStr.trim());
                if (expiryDate != null) {
                    long now = System.currentTimeMillis();
                    long expiryTime = expiryDate.getTime();
                    // 1 minute (60,000 ms) safety buffer
                    boolean isValid = now < (expiryTime - 60000);
                    Log.i(TAG, "Token expiry check -> Current: " + new Date(now) + ", Expires: " + expiryDate + ", IsValid: " + isValid);
                    return isValid;
                }
            } catch (Exception e) {
                Log.w(TAG, "Error parsing expires_at date: " + e.getMessage());
            }
        }

        // Fallback: decode JWT exp timestamp
        return isJwtExpiryValid(getToken());
    }

    private boolean isJwtExpiryValid(String jwt) {
        if (jwt == null || !jwt.contains(".")) return false;
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length >= 2) {
                byte[] decodedBytes = Base64.decode(parts[1], Base64.URL_SAFE);
                String payload = new String(decodedBytes, "UTF-8");
                JSONObject json = new JSONObject(payload);
                if (json.has("exp")) {
                    long expSeconds = json.getLong("exp");
                    long nowSeconds = System.currentTimeMillis() / 1000;
                    return nowSeconds < (expSeconds - 60);
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Clear token from SharedPreferences and memory.
     */
    public void clear() {
        cachedToken = null;
        prefs.edit().clear().apply();
    }
}
