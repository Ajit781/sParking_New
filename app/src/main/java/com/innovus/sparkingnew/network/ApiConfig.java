package com.innovus.sparkingnew.network;

import android.util.Log;

/**
 * =========================================================================================
 * ⚙️ CENTRALIZED API CONFIGURATION & MASTER ENVIRONMENT SWITCH
 * =========================================================================================
 *
 * Bhai, aapko sirf NEECHE WALE EK VARIABLE ko change karna hai:
 *
 *   IS_PRODUCTION = false;  --> STAGING MODE (Plain enc_data, staging vigpl.com URL)
 *   IS_PRODUCTION = true;   --> PRODUCTION MODE (Automatic AES Encryption ON + Production URL)
 *
 * Kisi aur file ya method ko chhedne ki bilkul zaroorat nahi hai!
 * =========================================================================================
 */
public final class ApiConfig {

    private static final String TAG = "ApiConfig";

    // =====================================================================================
    //  [MASTER SWITCH] CHANGE ONLY THIS ONE BOOLEAN VALUE:
    // =====================================================================================
    public static boolean IS_PRODUCTION = false; // false = STAGING | true = PRODUCTION
    // =====================================================================================

    // URLs
    public static final String STAGING_URL    = "https://vigpl.com/SParkingRestAPI/api/";
    public static final String PRODUCTION_URL = "https://vigpl.com/SParkingRestAPI/api/";

    private ApiConfig() {
        // Private constructor to prevent instantiation
    }

    /**
     * Active Base URL automatically selected based on IS_PRODUCTION switch.
     */
    public static String getBaseUrl() {
        return IS_PRODUCTION ? PRODUCTION_URL : STAGING_URL;
    }

    /**
     * Payload Encryption:
     * In PRODUCTION (true): Automatic AES-128/256-CBC Encryption ON for request & response.
     * In STAGING (false): Plain stringified enc_data (as working currently).
     */
    public static boolean isEncryptionEnabled() {
        return IS_PRODUCTION;
    }

    public static boolean isProduction() {
        return IS_PRODUCTION;
    }

    // =========================================================================
    // Dynamic Endpoint Getters (Always reflect active Base URL)
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
    //           Backward-Compatible Static Constants
    // =========================================================================
    public static final String URL_GENERATE_TOKEN = getUrlGenerateToken();
    public static final String URL_LOGIN = getUrlLogin();
    public static final String URL_GET_ALL_VEHICLE_TYPES = getUrlGetAllVehicleTypes();
    public static final String URL_CHECKIN_REGISTERED = getUrlCheckinRegistered();
    public static final String URL_CHECKIN_UNREGISTERED = getUrlCheckinUnregistered();
    public static final String URL_GET_ALL_CHECKIN_VEHICLES = getUrlGetAllCheckinVehicles();
    public static final String URL_GET_CHECKOUT_AMOUNT = getUrlGetCheckoutAmount();
    public static final String URL_VEHICLE_CHECKOUT = getUrlVehicleCheckout();
}
