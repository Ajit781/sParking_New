package com.innovus.sparkingnew.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.innovus.sparkingnew.models.UserData;
import com.innovus.sparkingnew.network.ApiConfig;
import com.innovus.sparkingnew.network.crypto.CryptoManager;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

/**
 * Separate network service dedicated to User Login authentication.
 * Strictly forwards the exact error messages returned by the backend API.
 */
public class LoginNetworkService {

    private static final String TAG = "sParking_LoginAPI";

    public interface LoginCallback {
        void onSuccess(UserData userData, String rawData, String message);
        void onFailure(String backendErrorMessage);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Executes the login POST API call asynchronously.
     *
     * @param username User username/ID
     * @param password User password
     * @param deviceId Device identifier
     * @param bearerToken Optional access token obtained from generate_token
     * @param callback Callback delivering results to Main/UI thread
     */
    public static void executeLogin(String username,
                                    String password,
                                    String deviceId,
                                    String bearerToken,
                                    LoginCallback callback) {
        executor.execute(() -> {
            HttpsURLConnection connection = null;
            String loginUrl = ApiConfig.getUrlLogin();
            Log.i(TAG, "==================== [LOGIN REQUEST START] ====================");
            Log.i(TAG, "URL: " + loginUrl);

            try {
                // STEP 1: Construct JSON Body
                JSONObject innerObj = new JSONObject();
                innerObj.put("username", username != null ? username : "");
                innerObj.put("password", password != null ? password : "");
                innerObj.put("device_id", deviceId != null ? deviceId : "");

                String payload = CryptoManager.prepareEncryptedPayload(innerObj);
                Log.i(TAG, "[REQUEST PAYLOAD] " + payload);

                // STEP 2: Configure Connection
                URL url = new URL(loginUrl);
                connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");

                if (bearerToken != null && !bearerToken.trim().isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + bearerToken.trim());
                    Log.d(TAG, "[AUTH HEADER] Bearer token attached");
                }

                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setDoOutput(true);
                connection.setDoInput(true);

                // STEP 3: Write Request Payload
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                    os.flush();
                }

                // STEP 4: Read Response Code & Body
                int responseCode = connection.getResponseCode();
                String responseMessage = connection.getResponseMessage();
                Log.i(TAG, "[RESPONSE CODE] " + responseCode + " (" + responseMessage + ")");

                boolean isHttpOk = (responseCode >= 200 && responseCode < 300);
                InputStream stream = isHttpOk ? connection.getInputStream() : connection.getErrorStream();

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
                Log.i(TAG, "[RAW SERVER RESPONSE] " + responseStr);

                // Parse Backend Response JSON (auto decrypt and check all known message keys)
                JSONObject responseJson = ApiResponseHelper.parseAndDecryptResponse(responseStr);
                if (responseJson != null) {
                    String status = responseJson.optString("status", "");
                    String backendMessage = ApiResponseHelper.extractServerMessage(responseJson, "Invalid credentials or login rejected by server.");

                    if ("GEN_000".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status)) {
                        Log.i(TAG, "[LOGIN SUCCESS] " + backendMessage);
                        String rawData = responseJson.optString("data", "");
                        UserData userData = null;

                        if (!rawData.isEmpty() && !rawData.equals("null")) {
                            JSONObject userJson = new JSONObject(rawData);
                            userData = UserData.fromJson(userJson);
                        }

                        UserData finalUserData = userData;
                        mainHandler.post(() -> callback.onSuccess(finalUserData, rawData, backendMessage));
                        Log.i(TAG, "==================== [LOGIN REQUEST COMPLETE: OK] ====================");
                        return;
                    } else {
                        // Backend returned explicit failure status (e.g., AUTH_001, GEN_003)
                        // Deliver EXACT backend message directly
                        Log.e(TAG, "[LOGIN REJECTED BY SERVER] Status: " + status + " | Message: " + backendMessage);
                        Log.i(TAG, "==================== [LOGIN REQUEST FAILED] ====================");
                        mainHandler.post(() -> callback.onFailure(backendMessage));
                        return;
                    }
                }

                // If not JSON or unexpected HTTP status
                String cleanError = ApiResponseHelper.cleanHttpError(responseStr, responseCode, responseMessage);
                Log.e(TAG, "[HTTP/RAW ERROR] " + cleanError);
                Log.i(TAG, "==================== [LOGIN REQUEST FAILED] ====================");
                mainHandler.post(() -> callback.onFailure(cleanError));

            } catch (UnknownHostException uhe) {
                String error = "Unable to connect to server. Please check your internet connection.";
                Log.e(TAG, "[CONNECTION ERROR] " + error, uhe);
                Log.i(TAG, "==================== [LOGIN REQUEST FAILED] ====================");
                mainHandler.post(() -> callback.onFailure(error));
            } catch (java.net.SocketTimeoutException ste) {
                String error = "Connection timed out. Server took too long to respond.";
                Log.e(TAG, "[TIMEOUT ERROR] " + error, ste);
                Log.i(TAG, "==================== [LOGIN REQUEST FAILED] ====================");
                mainHandler.post(() -> callback.onFailure(error));
            } catch (Exception e) {
                String error = (e.getMessage() != null && !e.getMessage().isEmpty()) ? e.getMessage() : "An unexpected error occurred.";
                Log.e(TAG, "[EXCEPTION] " + error, e);
                Log.i(TAG, "==================== [LOGIN REQUEST FAILED] ====================");
                mainHandler.post(() -> callback.onFailure(error));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }
}
