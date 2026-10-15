package com.innovus.sparkingnew.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.innovus.sparkingnew.SParkingApp;

/**
 * =========================================================================================
 * ⚙️ CENTRALIZED API CONFIGURATION & 100% DYNAMIC BASE URL MANAGEMENT
 * =========================================================================================
 *
 * 1. ZERO HARDCODED BASE URLs:
 *    - Base URL is completely dynamic.
 *    - Fetched dynamically from VersionCheck API (CheckAppVersion.php) on Splash screen.
 *    - Stored in local DB (SharedPreferences 'sParking_api_prefs' -> 'dynamic_base_url').
 *    - Used across all API service calls dynamically.
 *
 * 2. PRODUCTION vs STAGING SWITCH:
 *    - IS_PRODUCTION = false -> STAGING MODE (Plain real data payload, AES encryption OFF).
 *    - IS_PRODUCTION = true  -> PRODUCTION MODE (AES-256-GCM encryption/decryption ON).
 * =========================================================================================
 */
public final class ApiConfig {

    private static final String TAG = "sParking_API";

    // Local DB / SharedPreferences keys
    private static final String PREF_NAME = "sParking_api_prefs";
    private static final String KEY_DYNAMIC_BASE_URL = "dynamic_base_url";

    // =====================================================================================
    //  MASTER SWITCH: Toggle between Staging (Plain) vs Production (Encrypted)
    // =====================================================================================
    public static boolean IS_PRODUCTION = true; // false = STAGING | true = PRODUCTION
    // =====================================================================================

    // Active Base URL in memory (populated dynamically from VersionCheck / Local DB)
    private static String dynamicBaseUrl = "";

    private ApiConfig() {
        // Private constructor
    }

    /**
     * Initializes configuration from local DB (SharedPreferences).
     * Called in SParkingApp.onCreate().
     */
    public static synchronized void init(Context context) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String cached = prefs.getString(KEY_DYNAMIC_BASE_URL, "");
            if (cached != null && !cached.trim().isEmpty()) {
                dynamicBaseUrl = formatUrl(cached.trim());
            }

            Log.i(TAG, "================================================================================");
            Log.i(TAG, "⚙️ [ApiConfig:init] Dynamic Base URL loaded from Local DB: " + dynamicBaseUrl);
            Log.i(TAG, "🌐 Active Mode: " + (IS_PRODUCTION ? "PRODUCTION (AES-256-GCM ON)" : "STAGING (Plain Real Data)"));
            Log.i(TAG, "================================================================================");
        } catch (Exception e) {
            Log.w(TAG, "[ApiConfig:init] Error loading configuration from Local DB: " + e.getMessage());
        }
    }

    /**
     * Updates active Base URL dynamically and persists to Local DB (SharedPreferences).
     */
    public static synchronized void setBaseUrl(Context context, String newUrl) {
        if (newUrl == null || newUrl.trim().isEmpty()) {
            return;
        }

        String formatted = formatUrl(newUrl.trim());
        dynamicBaseUrl = formatted;

        Context ctx = (context != null) ? context.getApplicationContext() : SParkingApp.getAppContext();
        if (ctx != null) {
            try {
                SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                prefs.edit().putString(KEY_DYNAMIC_BASE_URL, formatted).apply();
                Log.i(TAG, "💾 [ApiConfig:setBaseUrl] Dynamic Base URL saved to Local DB: " + formatted);
            } catch (Exception e) {
                Log.w(TAG, "[ApiConfig:setBaseUrl] Failed to save Base URL to Local DB: " + e.getMessage());
            }
        }
    }

    /**
     * Returns the active Dynamic Base URL.
     * Checks in-memory cache first, falls back to Local DB (SharedPreferences).
     */
    public static synchronized String getBaseUrl() {
        if (dynamicBaseUrl == null || dynamicBaseUrl.trim().isEmpty()) {
            Context ctx = SParkingApp.getAppContext();
            if (ctx != null) {
                try {
                    SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                    String cached = prefs.getString(KEY_DYNAMIC_BASE_URL, "");
                    if (cached != null && !cached.trim().isEmpty()) {
                        dynamicBaseUrl = formatUrl(cached.trim());
                    }
                } catch (Exception ignored) {}
            }
        }
        return dynamicBaseUrl != null ? dynamicBaseUrl : "";
    }

    /**
     * Payload Encryption Switch:
     * Returns true ONLY if in Production.
     * In Staging (false), payload goes as plain real data without AES encryption.
     */
    public static boolean isEncryptionEnabled() {
        return IS_PRODUCTION;
    }

    public static boolean isProduction() {
        return IS_PRODUCTION;
    }

    public static void setProduction(boolean isProduction) {
        IS_PRODUCTION = isProduction;
        Log.i(TAG, "🔄 [ApiConfig] Switched Environment -> " + (IS_PRODUCTION ? "PRODUCTION" : "STAGING"));
    }

    // =========================================================================
    // Dynamic Endpoint Getters (Always reflect active Dynamic Base URL)
    // =========================================================================
    public static String getUrlGenerateToken() {
        return getBaseUrl() + "auth/generate_token";
    }

    public static String getUrlLogin() {
        return getBaseUrl() + "auth/login";
    }

    public static String getUrlGetAllVehicleTypes() {
        return getBaseUrl() + "master/getAllVehicleTypes";
    }

    public static String getUrlCheckinRegistered() {
        return getBaseUrl() + "booking/checkinRegisteredVehicle";
    }

    public static String getUrlCheckinUnregistered() {
        return getBaseUrl() + "booking/checkinUnregisteredVehicle";
    }

    public static String getUrlGetAllCheckinVehicles() {
        return getBaseUrl() + "booking/getAllCheckinVehicle";
    }

    public static String getUrlGetCheckoutAmount() {
        return getBaseUrl() + "booking/getCheckoutAmount";
    }

    public static String getUrlVehicleCheckout() {
        return getBaseUrl() + "booking/vehicleCheckout";
    }

    // =========================================================================
    // Helper to ensure trailing slash and canonical API route
    // =========================================================================
    private static String formatUrl(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        String trimmed = url.trim();
        // Route fix: If backend version check returns legacy 'sparking/api' (which is 404),
        // normalize it to the active production route 'SParkingRestAPI/api'
        if (trimmed.contains("vigpl.com/sparking/api")) {
            trimmed = trimmed.replace("vigpl.com/sparking/api", "vigpl.com/SParkingRestAPI/api");
        }
        if (!trimmed.endsWith("/")) {
            trimmed += "/";
        }
        return trimmed;
    }
}
