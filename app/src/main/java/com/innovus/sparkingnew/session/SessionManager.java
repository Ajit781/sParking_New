package com.innovus.sparkingnew.session;

import android.content.Context;
import android.content.SharedPreferences;

import com.innovus.sparkingnew.models.UserData;

import org.json.JSONObject;

/**
 * Manages user login state and stored profile session.
 */
public class SessionManager {

    private static final String PREF_NAME = "sParking_user_session";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_USER_DATA = "user_data";
    private static final String KEY_SAVED_USERNAME = "saved_username";
    private static final String KEY_SAVED_DEVICE_ID = "saved_device_id";

    private static SessionManager instance;
    private final SharedPreferences prefs;

    private SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    public void saveUserSession(UserData user, String username, String deviceId) {
        prefs.edit()
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putString(KEY_USER_DATA, user != null ? user.toJson().toString() : "")
                .putString(KEY_SAVED_USERNAME, username)
                .putString(KEY_SAVED_DEVICE_ID, deviceId)
                .apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public UserData getUserData() {
        String jsonStr = prefs.getString(KEY_USER_DATA, "");
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return null;
        }
        try {
            return UserData.fromJson(new JSONObject(jsonStr));
        } catch (Exception e) {
            return null;
        }
    }

    public String getSavedUsername() {
        return prefs.getString(KEY_SAVED_USERNAME, "");
    }

    public String getSavedDeviceId() {
        return prefs.getString(KEY_SAVED_DEVICE_ID, "");
    }

    public void logout() {
        prefs.edit()
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .remove(KEY_USER_DATA)
                .apply();
    }
}
