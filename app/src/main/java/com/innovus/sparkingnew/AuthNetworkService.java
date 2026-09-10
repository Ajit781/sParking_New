package com.innovus.sparkingnew;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.innovus.sparkingnew.network.ApiConfig;
import com.innovus.sparkingnew.network.ApiResponseHelper;
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
 * Service to execute Auth Token generation API request with comprehensive logging.
 */
public class AuthNetworkService {

    private static final String TAG = "sParking_API";

    public interface TokenCallback {
        void onSuccess(String accessToken, String createdAt, String expiresAt, String message);
        void onFailure(String errorMessage);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Request authentication token asynchronously with detailed logging at every step.
     */
    public static void generateToken(TokenCallback callback) {
        executor.execute(() -> {
            HttpsURLConnection connection = null;
            String tokenUrl = ApiConfig.getUrlGenerateToken();
            Log.i(TAG, "==================== [API REQUEST START] ====================");
            Log.i(TAG, "Target Endpoint: " + tokenUrl);
            Log.i(TAG, "HTTP Method: POST");

            try {
                // STEP 1: Build Payload
                Log.d(TAG, "[STEP 1] Constructing JSON Request Body...");
                JSONObject encDataObj = new JSONObject();
                encDataObj.put("username", "0020797790");
                encDataObj.put("password", "Parking@123");

                String payload = CryptoManager.prepareEncryptedPayload(encDataObj);
                Log.i(TAG, "[STEP 1 SUCCESS] Request Body:\n" + payload);

                // STEP 2: Configure HttpsURLConnection
                Log.d(TAG, "[STEP 2] Initializing HTTPS Connection...");
                URL url = new URL(tokenUrl);
                connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setDoOutput(true);
                connection.setDoInput(true);
                Log.d(TAG, "[STEP 2 SUCCESS] Headers set: Content-Type=application/json, Timeouts=15000ms");

                // STEP 3: Write Request Payload to Stream
                Log.d(TAG, "[STEP 3] Writing payload to OutputStream...");
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                    os.flush();
                }
                Log.i(TAG, "[STEP 3 SUCCESS] Request body transmitted successfully (" + payload.length() + " bytes)");

                // STEP 4: Read Response Code
                Log.d(TAG, "[STEP 4] Awaiting server response code...");
                int responseCode = connection.getResponseCode();
                String responseMessage = connection.getResponseMessage();
                Log.i(TAG, "[STEP 4 SUCCESS] Response Received -> HTTP Status: " + responseCode + " (" + responseMessage + ")");

                // STEP 5: Read Response Stream
                Log.d(TAG, "[STEP 5] Reading Response Stream...");
                boolean isSuccessHttp = (responseCode >= 200 && responseCode < 300);
                InputStream stream = isSuccessHttp ? connection.getInputStream() : connection.getErrorStream();

                StringBuilder responseBuilder = new StringBuilder();
                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseBuilder.append(line);
                        }
                    }
                } else {
                    Log.w(TAG, "[STEP 5 WARNING] Stream is null! No response body received from server.");
                }

                String responseStr = responseBuilder.toString();
                Log.i(TAG, "[STEP 5 SUCCESS] Full Raw Server Response Body:\n" + responseStr);

                // STEP 6: Parse JSON Response Body (with auto-decryption)
                Log.d(TAG, "[STEP 6] Parsing and Decrypting JSON Response Body...");
                JSONObject responseJson = ApiResponseHelper.parseAndDecryptResponse(responseStr);
                if (responseJson != null) {
                    String version = responseJson.optString("version", "N/A");
                    String status = responseJson.optString("status", "");
                    String message = ApiResponseHelper.extractServerMessage(responseJson, "Token generation processed.");

                    Log.i(TAG, "[STEP 7 INFO] API Version: " + version + " | Status Code: " + status + " | Message: " + message);

                    if ("GEN_000".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status)) {
                        if (responseJson.has("data")) {
                            JSONObject dataObj = responseJson.getJSONObject("data");
                            String accessToken = dataObj.optString("access_token", "");
                            String createdAt = dataObj.optString("created_at", "");
                            String expiresAt = dataObj.optString("expires_at", "");

                            Log.i(TAG, "[STEP 8 SUCCESS] Token Generated Successfully!");
                            mainHandler.post(() -> callback.onSuccess(accessToken, createdAt, expiresAt, message));
                            return;
                        }
                    }

                    // Token generation rejected by server with explicit message
                    Log.e(TAG, "[ERROR at STEP 8] Server rejected token generation: " + message);
                    mainHandler.post(() -> callback.onFailure(message));
                    return;
                }

                // If non-JSON or HTTP error
                String cleanErr = ApiResponseHelper.cleanHttpError(responseStr, responseCode, responseMessage);
                Log.e(TAG, "[ERROR] Non-JSON server error: " + cleanErr);
                mainHandler.post(() -> callback.onFailure(cleanErr));
                return;

            } catch (UnknownHostException uhe) {
                String errorMsg = "Internet/DNS error: Cannot reach server vigpl.com. Please check device internet connection.";
                Log.e(TAG, "[NETWORK ERROR] " + errorMsg, uhe);
                Log.i(TAG, "==================== [API REQUEST FAILED] ====================");
                mainHandler.post(() -> callback.onFailure(errorMsg));
            } catch (java.net.SocketTimeoutException ste) {
                String errorMsg = "Connection timeout: Server took too long to respond (15s).";
                Log.e(TAG, "[TIMEOUT ERROR] " + errorMsg, ste);
                Log.i(TAG, "==================== [API REQUEST FAILED] ====================");
                mainHandler.post(() -> callback.onFailure(errorMsg));
            } catch (Exception e) {
                String errorMsg = "Unexpected error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                Log.e(TAG, "[EXCEPTION] Error during token request: " + errorMsg, e);
                Log.i(TAG, "==================== [API REQUEST FAILED] ====================");
                mainHandler.post(() -> callback.onFailure(errorMsg));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                    Log.d(TAG, "Connection disconnected.");
                }
            }
        });
    }
}
