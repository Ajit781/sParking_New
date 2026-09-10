package com.innovus.sparkingnew.utils;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Helper to fetch device serial number and identifier from POS hardware (Pax A910s / Razorpay POS).
 */
public class PosDeviceHelper {

    private static final String TAG = "sParking_Device";
    public static final String DEFAULT_FALLBACK_DEVICE_ID = "27";

    private static String cachedDeviceId = null;

    /**
     * Get the device identifier for POS API requests.
     * Automatically retrieves serial number on Pax A910s, with fallback to default "27".
     */
    public static synchronized String getDeviceId(Context context) {
        if (cachedDeviceId != null && !cachedDeviceId.trim().isEmpty()) {
            return cachedDeviceId;
        }

        String detectedId = "";

        // 1. Try Pax / Android System Properties
        detectedId = getSystemProperty("ro.serialno");
        if (isValidId(detectedId)) {
            Log.i(TAG, "Detected Device ID from ro.serialno: " + detectedId);
            cachedDeviceId = detectedId;
            return cachedDeviceId;
        }

        detectedId = getSystemProperty("ro.boot.serialno");
        if (isValidId(detectedId)) {
            Log.i(TAG, "Detected Device ID from ro.boot.serialno: " + detectedId);
            cachedDeviceId = detectedId;
            return cachedDeviceId;
        }

        detectedId = getSystemProperty("ro.pax.serialno");
        if (isValidId(detectedId)) {
            Log.i(TAG, "Detected Device ID from ro.pax.serialno: " + detectedId);
            cachedDeviceId = detectedId;
            return cachedDeviceId;
        }

        detectedId = getSystemProperty("ro.hardware.sn");
        if (isValidId(detectedId)) {
            Log.i(TAG, "Detected Device ID from ro.hardware.sn: " + detectedId);
            cachedDeviceId = detectedId;
            return cachedDeviceId;
        }

        // 2. Try Build.SERIAL
        try {
            if (Build.SERIAL != null && isValidId(Build.SERIAL)) {
                Log.i(TAG, "Detected Device ID from Build.SERIAL: " + Build.SERIAL);
                cachedDeviceId = Build.SERIAL;
                return cachedDeviceId;
            }
        } catch (Exception ignored) {}

        // 3. Try Android ID if context is available
        if (context != null) {
            try {
                String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                if (isValidId(androidId)) {
                    Log.i(TAG, "Detected Device ID from Android ID: " + androidId);
                }
            } catch (Exception ignored) {}
        }

        // 4. Default Fallback
        Log.w(TAG, "Hardware serial unavailable (emulator/phone). Using default Device ID: " + DEFAULT_FALLBACK_DEVICE_ID);
        cachedDeviceId = DEFAULT_FALLBACK_DEVICE_ID;
        return cachedDeviceId;
    }

    /**
     * Get display description of device for UI indication.
     */
    public static String getDeviceDisplayInfo(Context context) {
        String id = getDeviceId(context);
        String model = Build.MODEL != null ? Build.MODEL : "POS Terminal";
        return model + " • ID: " + id;
    }

    private static boolean isValidId(String str) {
        return str != null && !str.trim().isEmpty() && !str.equalsIgnoreCase("unknown") && !str.equalsIgnoreCase("null");
    }

    private static String getSystemProperty(String propName) {
        try {
            Class<?> systemPropertiesClass = Class.forName("android.os.SystemProperties");
            Method getMethod = systemPropertiesClass.getMethod("get", String.class);
            Object result = getMethod.invoke(null, propName);
            if (result instanceof String) {
                return ((String) result).trim();
            }
        } catch (Exception ignored) {}
        return "";
    }
}
