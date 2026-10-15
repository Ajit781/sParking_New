package com.innovus.sparkingnew.devices;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

public class DeviceFactory {

    private static final String TAG = "DeviceFactory";

    public static DeviceInterface getDevice(Activity activity) {
        Log.i(TAG, "Auto-detecting POS device...");

        // 1. Auto-detect based on installed apps - Razorpay/Ezetap
        if (isPackageInstalled(activity, "com.eze.api") || 
            isPackageInstalled(activity, "com.ezetap.service") || 
            isPackageInstalled(activity, "com.razorpay.pos")) {
            Log.i(TAG, "Detected Razorpay (Ezetap) POS via Package Manager");
            return new RazorpayDevice();
        } 
        
        // 2. Pinelabs Master App package
        if (isPackageInstalled(activity, "com.pinelabs.masterapp")) {
            Log.i(TAG, "Detected PineLabs POS via Package Manager");
            return new PineLabsDevice();
        }

        // 3. Check for PAX Neptune Hardware Printer as a last resort for standard PAX terminals
        try {
            com.pax.dal.IDAL paxPrinter = com.pax.neptunelite.api.NeptuneLiteUser.getInstance().getDal(activity);
            if (paxPrinter != null && paxPrinter.getPrinter() != null) {
                Log.i(TAG, "Detected PAX Neptune Hardware Printer");
                return new PaxDevice();
            }
        } catch (Exception | Error e) {
            // Not a Pax device or DAL failed to load
            Log.i(TAG, "PAX DAL check failed: " + e.getMessage());
        }

        // Default fallback
        Log.i(TAG, "Could not confidently auto-detect. Defaulting to PineLabsDevice.");
        return new PineLabsDevice();
    }
    
    private static boolean isPackageInstalled(Context context, String packageName) {
        try {
            if (context == null || context.getPackageManager() == null) return false;
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
