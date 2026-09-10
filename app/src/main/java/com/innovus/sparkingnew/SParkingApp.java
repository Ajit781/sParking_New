package com.innovus.sparkingnew;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.pax.dal.IDAL;
import com.pax.neptunelite.api.NeptuneLiteUser;

/**
 * Application class initializing PAX Neptune DAL hardware bindings.
 */
public class SParkingApp extends Application {

    private static final String TAG = "SParkingApp";
    private static SParkingApp instance;
    private static Context appContext;
    private static IDAL dal;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        appContext = getApplicationContext();

        // Initialize PAX Neptune DAL
        getDal(this);
    }

    public static SParkingApp getInstance() {
        return instance;
    }

    public static Context getAppContext() {
        if (appContext == null && instance != null) {
            appContext = instance.getApplicationContext();
        }
        if (appContext == null) {
            appContext = getApplicationByReflect();
        }
        return appContext;
    }

    public static IDAL getDal() {
        return getDal(getAppContext());
    }

    public static synchronized IDAL getDal(Context context) {
        if (dal == null) {
            Context ctx = context != null ? context.getApplicationContext() : getAppContext();
            if (ctx != null) {
                try {
                    long start = System.currentTimeMillis();
                    dal = NeptuneLiteUser.getInstance().getDal(ctx);
                    Log.i(TAG, "PAX Neptune DAL initialized in " + (System.currentTimeMillis() - start) + " ms | Dal=" + dal);
                } catch (Throwable t) {
                    Log.w(TAG, "PAX Neptune DAL not available on this device: " + t.getMessage(), t);
                }
            }
        }
        return dal;
    }

    private static Application getApplicationByReflect() {
        try {
            @SuppressLint("PrivateApi")
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method method = activityThread.getMethod("currentApplication");
            return (Application) method.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
