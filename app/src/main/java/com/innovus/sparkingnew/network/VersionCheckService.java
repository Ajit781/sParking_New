package com.innovus.sparkingnew.network;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

/**
 * Service to execute CheckAppVersion API request.
 * Fetches dynamic Base URL, enforces required app updates, and validates build expiration.
 */
public class VersionCheckService {

    private static final String TAG = "sParking_FLOW";
    public static final String CHECK_VERSION_URL = "https://www.s-parking.com/sParkingAppVersion/CheckAppVersion.php";

    public interface VersionCheckCallback {
        void onSuccess(VersionCheckResult result);
        void onFailure(String errorMessage);
    }

    public static class VersionCheckResult {
        private final int status;
        private final String message;
        private final String serverVersion;
        private final String validUpto;
        private final String baseUrl;

        public VersionCheckResult(int status, String message, String serverVersion, String validUpto, String baseUrl) {
            this.status = status;
            this.message = message != null ? message : "";
            this.serverVersion = serverVersion != null ? serverVersion : "";
            this.validUpto = validUpto != null ? validUpto : "";
            this.baseUrl = baseUrl != null ? baseUrl : "";
        }

        public int getStatus() { return status; }
        public String getMessage() { return message; }
        public String getServerVersion() { return serverVersion; }
        public String getValidUpto() { return validUpto; }
        public String getBaseUrl() { return baseUrl; }

        /**
         * Returns true if backend explicitly demands app update (Installed version is lower).
         */
        public boolean isLowerVersion() {
            return "Lower Version".equalsIgnoreCase(message);
        }

        /**
         * Checks if the app build has expired according to backend valid_upto timestamp.
         */
        public boolean isBuildExpired() {
            if (validUpto == null || validUpto.trim().isEmpty()) {
                return false;
            }
            try {
                // Expected format: "2030-01-01 00:00:00" or ISO format
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                Date expiryDate = sdf.parse(validUpto.trim());
                if (expiryDate != null) {
                    // Ignore historical typo years (e.g. 0207)
                    long year = expiryDate.getYear() + 1900;
                    if (year >= 2024 && System.currentTimeMillis() > expiryDate.getTime()) {
                        return true;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not parse valid_upto date (" + validUpto + "): " + e.getMessage());
            }
            return false;
        }

        public boolean hasBaseUrl() {
            return baseUrl != null && !baseUrl.trim().isEmpty();
        }

        @Override
        public String toString() {
            return "VersionCheckResult{" +
                    "status=" + status +
                    ", message='" + message + '\'' +
                    ", serverVersion='" + serverVersion + '\'' +
                    ", validUpto='" + validUpto + '\'' +
                    ", baseUrl='" + baseUrl + '\'' +
                    '}';
        }
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Executes Version Check asynchronously and delivers result to the callback on Main Looper.
     */
    public static void checkVersion(Context context, VersionCheckCallback callback) {
        final Context appContext = context != null ? context.getApplicationContext() : null;
        executor.execute(() -> {
            try {
                VersionCheckResult result = checkVersionSync(appContext);
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(result));
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ [sParking_FLOW] VersionCheck Error: " + e.getMessage(), e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onFailure(e.getMessage() != null ? e.getMessage() : "Version check failed"));
                }
            }
        });
    }

    /**
     * Executes Version Check synchronously on calling thread.
     */
    public static VersionCheckResult checkVersionSync(Context context) throws Exception {
        HttpsURLConnection connection = null;

        String packageName = "";
        String versionName = "1.0";

        if (context != null) {
            packageName = context.getPackageName();
            try {
                PackageInfo pInfo = context.getPackageManager().getPackageInfo(packageName, 0);
                if (pInfo != null && pInfo.versionName != null) {
                    versionName = pInfo.versionName;
                }
            } catch (Exception ignored) {
            }
        }

        Log.i(TAG, "================================================================================");
        Log.i(TAG, "🚀 [sParking_FLOW] ---> VERSION CHECK REQUEST START");
        Log.i(TAG, "🌐 URL     : " + CHECK_VERSION_URL);
        Log.i(TAG, "📦 Package : " + packageName);
        Log.i(TAG, "🏷️ Version : " + versionName);
        Log.i(TAG, "================================================================================");

        try {
            URL url = new URL(CHECK_VERSION_URL);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);
            connection.setDoInput(true);

            String formBody = "Package=" + URLEncoder.encode(packageName, "UTF-8") +
                    "&Version=" + URLEncoder.encode(versionName, "UTF-8");

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = formBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            InputStream stream = (responseCode >= 200 && responseCode < 300)
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            StringBuilder responseBuilder = new StringBuilder();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                }
            }

            String responseStr = responseBuilder.toString();

            JSONObject json = new JSONObject(responseStr);
            int status = json.optInt("status", 0);
            String message = json.optString("message", "");
            String serverVersion = json.optString("ServerVersion", "");
            String validUpto = json.optString("valid_upto", "");
            String baseUrl = json.optString("base_url", "");

            Log.i(TAG, "================================================================================");
            Log.i(TAG, "📥 [sParking_FLOW] <--- VERSION CHECK RESPONSE (HTTP " + responseCode + ")");
            Log.i(TAG, "📊 Status        : " + status + " (" + message + ")");
            Log.i(TAG, "🔢 Server Version: " + serverVersion);
            Log.i(TAG, "📅 Valid Upto    : " + validUpto);
            Log.i(TAG, "🔗 Base URL      : " + baseUrl);
            Log.i(TAG, "================================================================================");

            return new VersionCheckResult(status, message, serverVersion, validUpto, baseUrl);

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
