package com.innovus.sparkingnew.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.innovus.sparkingnew.TokenManager;
import com.innovus.sparkingnew.models.ParkedVehicle;
import com.innovus.sparkingnew.models.VehicleType;
import com.innovus.sparkingnew.network.crypto.CryptoManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

/**
 * Network service for Vehicle Booking, Check-In, Active Vehicle Listing, and Check-Out APIs.
 * Fully integrated with CryptoManager for encrypted payloads, Silent Auto-Refresh (Tareeqa 1),
 * and 401 Unauthorized / Token Expired Auto-Retry (Tareeqa 2).
 */
public class BookingNetworkService {

    private static final String TAG = "sParking_API";
    private static final int MAX_AUTH_RETRIES = 1;

    public interface CheckInCallback {
        void onResponse(String status, String message, String rawData, boolean isSuccess);
        void onError(String errorMessage);
    }

    public interface VehicleTypesCallback {
        void onResponse(List<VehicleType> vehicleTypes, String status, String message, boolean isSuccess);
        void onError(String errorMessage);
    }

    public interface FetchVehiclesCallback {
        void onResponse(List<ParkedVehicle> vehicles, String status, String message, boolean isSuccess);
        void onError(String errorMessage);
    }

    public interface CheckoutAmountCallback {
        void onResponse(JSONObject amountData, String status, String message, boolean isSuccess);
        void onError(String errorMessage);
    }

    public interface CheckoutCallback {
        void onResponse(String status, String message, String rawData, boolean isSuccess);
        void onError(String errorMessage);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Resolves a guaranteed valid token. Uses TokenManager.getValidTokenSync() (Tareeqa 1).
     */
    private static String resolveActiveToken(String passedToken) {
        try {
            String validToken = TokenManager.getInstance().getValidTokenSync();
            if (validToken != null && !validToken.trim().isEmpty()) {
                return validToken.trim();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error resolving token from TokenManager: " + e.getMessage());
        }
        return passedToken != null ? passedToken.trim() : "";
    }

    /**
     * Checks if a server response indicates token expiration or unauthorized access (Tareeqa 2).
     */
    private static boolean isAuthFailure(int responseCode, JSONObject json, String responseStr) {
        if (responseCode == 401 || responseCode == 403) {
            return true;
        }
        if (json != null) {
            String status = json.optString("status", "");
            String message = ApiResponseHelper.extractServerMessage(json, "").toLowerCase();
            if ("TOKEN_EXPIRED".equalsIgnoreCase(status)
                    || "AUTH_001".equalsIgnoreCase(status)
                    || "GEN_401".equalsIgnoreCase(status)
                    || "UNAUTHORIZED".equalsIgnoreCase(status)) {
                return true;
            }
            if (message.contains("token expired") || message.contains("invalid token") || message.contains("unauthorized") || message.contains("token not found")) {
                return true;
            }
        }
        if (responseStr != null) {
            String lower = responseStr.toLowerCase();
            if (lower.contains("token expired") || lower.contains("unauthorized") || lower.contains("invalid token")) {
                return true;
            }
        }
        return false;
    }

    // ============================================================================================
    // 1. CHECK-IN REGISTERED VEHICLE
    // ============================================================================================

    public static void checkinRegisteredVehicle(int agentId,
                                                String vehicleNo,
                                                int paymentModeId,
                                                String alternateMobileNo,
                                                String bearerToken,
                                                CheckInCallback callback) {
        executor.execute(() -> executeCheckinRegisteredVehicle(agentId, vehicleNo, paymentModeId, alternateMobileNo, bearerToken, callback, 0));
    }

    private static void executeCheckinRegisteredVehicle(int agentId,
                                                        String vehicleNo,
                                                        int paymentModeId,
                                                        String alternateMobileNo,
                                                        String bearerToken,
                                                        CheckInCallback callback,
                                                        int retryCount) {
        HttpsURLConnection connection = null;
        String checkinUrl = ApiConfig.getUrlCheckinRegistered();
        Log.i(TAG, "==================== [CHECK-IN REQUEST START" + (retryCount > 0 ? " (RETRY)" : "") + "] ====================");
        Log.i(TAG, "URL: " + checkinUrl);

        try {
            String activeToken = resolveActiveToken(bearerToken);

            JSONObject innerObj = new JSONObject();
            innerObj.put("agent_id", agentId);
            innerObj.put("vehicle_no", vehicleNo != null ? vehicleNo.trim().toUpperCase() : "");
            innerObj.put("payment_mode_id", paymentModeId);
            innerObj.put("alternate_mobile_no", (alternateMobileNo != null && !alternateMobileNo.trim().isEmpty())
                    ? alternateMobileNo.trim()
                    : "0000000000");

            String payload = CryptoManager.prepareEncryptedPayload(innerObj);
            logOutboundPayload("CHECK-IN PAYLOAD", payload);

            URL url = new URL(checkinUrl);
            connection = (HttpsURLConnection) url.openConnection();
            configureConnection(connection, payload, activeToken);

            int responseCode = connection.getResponseCode();
            String responseStr = readResponse(connection, responseCode);
            logInboundResponse("CHECK-IN RESPONSE", responseCode, responseStr);

            JSONObject json = ApiResponseHelper.parseAndDecryptResponse(responseStr);

            // Check for Auth Failure and Auto-Retry (Tareeqa 2)
            if (isAuthFailure(responseCode, json, responseStr) && retryCount < MAX_AUTH_RETRIES) {
                Log.w(TAG, "[AUTH RETRY 1/1] Token rejected. Invalidating and retrying checkinRegisteredVehicle...");
                TokenManager.getInstance().invalidateToken();
                String freshToken = TokenManager.getInstance().getValidTokenSync();
                executeCheckinRegisteredVehicle(agentId, vehicleNo, paymentModeId, alternateMobileNo, freshToken, callback, retryCount + 1);
                return;
            }

            if (json != null) {
                String status = json.optString("status", "");
                String message = ApiResponseHelper.extractServerMessage(json, "Check-in request processed.");
                String data = json.optString("data", "null");

                boolean isSuccess = "GEN_000".equalsIgnoreCase(status)
                        || "VEH_000".equalsIgnoreCase(status)
                        || "CHKIN_001".equalsIgnoreCase(status)
                        || "CHKIN_000".equalsIgnoreCase(status)
                        || (message != null && message.toLowerCase().contains("success"));
                mainHandler.post(() -> callback.onResponse(status, message, data, isSuccess));
            } else {
                String cleanErr = ApiResponseHelper.cleanHttpError(responseStr, responseCode, connection.getResponseMessage());
                mainHandler.post(() -> callback.onError(cleanErr));
            }

        } catch (UnknownHostException uhe) {
            mainHandler.post(() -> callback.onError("Unable to connect to server. Please check internet connection."));
        } catch (Exception e) {
            mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Check-in error"));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    // ============================================================================================
    // 2. GET ALL CHECKIN VEHICLES
    // ============================================================================================

    public static void getAllCheckinVehicle(int userId,
                                            String vehicleNo,
                                            String bearerToken,
                                            FetchVehiclesCallback callback) {
        executor.execute(() -> executeGetAllCheckinVehicle(userId, vehicleNo, bearerToken, callback, 0));
    }

    private static void executeGetAllCheckinVehicle(int userId,
                                                    String vehicleNo,
                                                    String bearerToken,
                                                    FetchVehiclesCallback callback,
                                                    int retryCount) {
        HttpsURLConnection connection = null;
        String getVehiclesUrl = ApiConfig.getUrlGetAllCheckinVehicles();
        Log.i(TAG, "==================== [GET ALL CHECKIN VEHICLE START" + (retryCount > 0 ? " (RETRY)" : "") + "] ====================");
        Log.i(TAG, "URL: " + getVehiclesUrl);

        try {
            String activeToken = resolveActiveToken(bearerToken);

            String queryVehicle = (vehicleNo != null && !vehicleNo.trim().isEmpty())
                    ? vehicleNo.trim().toUpperCase()
                    : "%";

            int finalUserId = userId > 0 ? userId : 159;
            JSONObject innerObj = new JSONObject();
            innerObj.put("user_id", finalUserId);
            innerObj.put("agent_id", finalUserId);
            innerObj.put("vehicle_no", queryVehicle);

            String payload = CryptoManager.prepareEncryptedPayload(innerObj);
            logOutboundPayload("FETCH PAYLOAD", payload);

            URL url = new URL(getVehiclesUrl);
            connection = (HttpsURLConnection) url.openConnection();
            configureConnection(connection, payload, activeToken);

            int responseCode = connection.getResponseCode();
            String responseStr = readResponse(connection, responseCode);
            logInboundResponse("FETCH RESPONSE", responseCode, responseStr);

            JSONObject json = ApiResponseHelper.parseAndDecryptResponse(responseStr);

            // Check for Auth Failure and Auto-Retry (Tareeqa 2)
            if (isAuthFailure(responseCode, json, responseStr) && retryCount < MAX_AUTH_RETRIES) {
                Log.w(TAG, "[AUTH RETRY 1/1] Token rejected. Invalidating and retrying getAllCheckinVehicle...");
                TokenManager.getInstance().invalidateToken();
                String freshToken = TokenManager.getInstance().getValidTokenSync();
                executeGetAllCheckinVehicle(userId, vehicleNo, freshToken, callback, retryCount + 1);
                return;
            }

            if (json != null) {
                String status = json.optString("status", "");
                String message = ApiResponseHelper.extractServerMessage(json, "Vehicle list loaded.");
                boolean isSuccess = "GEN_000".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status);

                List<ParkedVehicle> vehicles = new ArrayList<>();
                if (isSuccess) {
                    String dataStr = json.optString("data", "");
                    JSONArray array = null;
                    if (dataStr.startsWith("[")) {
                        array = new JSONArray(dataStr);
                    } else {
                        array = json.optJSONArray("data");
                    }

                    if (array != null) {
                        for (int i = 0; i < array.length(); i++) {
                            vehicles.add(ParkedVehicle.fromJson(array.getJSONObject(i)));
                        }
                    }
                }

                mainHandler.post(() -> callback.onResponse(vehicles, status, message, isSuccess));
            } else {
                String cleanErr = ApiResponseHelper.cleanHttpError(responseStr, responseCode, connection.getResponseMessage());
                mainHandler.post(() -> callback.onError(cleanErr));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error fetching checked in vehicles", e);
            mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Error loading vehicle list"));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    // ============================================================================================
    // 3. GET CHECKOUT AMOUNT
    // ============================================================================================

    public static void getCheckoutAmount(int bookingId,
                                         String bearerToken,
                                         CheckoutAmountCallback callback) {
        executor.execute(() -> executeGetCheckoutAmount(bookingId, bearerToken, callback, 0));
    }

    private static void executeGetCheckoutAmount(int bookingId,
                                                 String bearerToken,
                                                 CheckoutAmountCallback callback,
                                                 int retryCount) {
        HttpsURLConnection connection = null;
        String amountUrl = ApiConfig.getUrlGetCheckoutAmount();
        Log.i(TAG, "==================== [GET CHECKOUT AMOUNT START" + (retryCount > 0 ? " (RETRY)" : "") + "] ====================");
        Log.i(TAG, "URL: " + amountUrl);

        try {
            String activeToken = resolveActiveToken(bearerToken);

            JSONObject innerObj = new JSONObject();
            innerObj.put("booking_id", bookingId);

            String payload = CryptoManager.prepareEncryptedPayload(innerObj);
            logOutboundPayload("GET CHECKOUT AMOUNT PAYLOAD", payload);

            URL url = new URL(amountUrl);
            connection = (HttpsURLConnection) url.openConnection();
            configureConnection(connection, payload, activeToken);

            int responseCode = connection.getResponseCode();
            String responseStr = readResponse(connection, responseCode);
            logInboundResponse("GET CHECKOUT AMOUNT RESPONSE", responseCode, responseStr);

            JSONObject json = ApiResponseHelper.parseAndDecryptResponse(responseStr);

            // Check for Auth Failure and Auto-Retry (Tareeqa 2)
            if (isAuthFailure(responseCode, json, responseStr) && retryCount < MAX_AUTH_RETRIES) {
                Log.w(TAG, "[AUTH RETRY 1/1] Token rejected. Invalidating and retrying getCheckoutAmount...");
                TokenManager.getInstance().invalidateToken();
                String freshToken = TokenManager.getInstance().getValidTokenSync();
                executeGetCheckoutAmount(bookingId, freshToken, callback, retryCount + 1);
                return;
            }

            if (json != null) {
                String status = json.optString("status", "");
                String message = ApiResponseHelper.extractServerMessage(json, "Checkout amount calculated.");
                boolean isSuccess = "GEN_000".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status);

                JSONObject amountObj = null;
                if (isSuccess) {
                    String dataStr = json.optString("data", "");
                    if (dataStr.startsWith("{")) {
                        amountObj = new JSONObject(dataStr);
                    } else {
                        amountObj = json.optJSONObject("data");
                    }
                }

                final JSONObject finalAmount = amountObj;
                mainHandler.post(() -> callback.onResponse(finalAmount, status, message, isSuccess));
            } else {
                String cleanErr = ApiResponseHelper.cleanHttpError(responseStr, responseCode, connection.getResponseMessage());
                mainHandler.post(() -> callback.onError(cleanErr));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error fetching checkout amount", e);
            mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Error getting checkout amount"));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    // ============================================================================================
    // 4. CHECKOUT VEHICLE
    // ============================================================================================

    public static void checkoutVehicle(int bookingId,
                                       int userId,
                                       String vehicleNo,
                                       int paymentModeId,
                                       String transactionId,
                                       int paymentStatus,
                                       double amount,
                                       String bearerToken,
                                       CheckoutCallback callback) {
        executor.execute(() -> executeCheckoutVehicle(bookingId, userId, vehicleNo, paymentModeId, transactionId, paymentStatus, amount, bearerToken, callback, 0));
    }

    private static void executeCheckoutVehicle(int bookingId,
                                               int userId,
                                               String vehicleNo,
                                               int paymentModeId,
                                               String transactionId,
                                               int paymentStatus,
                                               double amount,
                                               String bearerToken,
                                               CheckoutCallback callback,
                                               int retryCount) {
        HttpsURLConnection connection = null;
        String checkoutUrl = ApiConfig.getUrlVehicleCheckout();
        Log.i(TAG, "==================== [CHECKOUT REQUEST START" + (retryCount > 0 ? " (RETRY)" : "") + "] ====================");
        Log.i(TAG, "URL: " + checkoutUrl);
        Log.i(TAG, "PaymentModeId: " + paymentModeId + " | TxnId: " + transactionId + " | Status: " + paymentStatus + " | Amount: " + amount);

        try {
            String activeToken = resolveActiveToken(bearerToken);

            JSONObject innerObj = new JSONObject();
            if (bookingId > 0) {
                innerObj.put("booking_id", bookingId);
            }
            innerObj.put("user_id", userId);
            if (vehicleNo != null && !vehicleNo.trim().isEmpty()) {
                innerObj.put("vehicle_no", vehicleNo.trim().toUpperCase());
            }
            innerObj.put("payment_mode_id", paymentModeId);
            innerObj.put("transaction_id", (transactionId != null && !transactionId.trim().isEmpty()) ? transactionId.trim() : "0");
            innerObj.put("payment_status", paymentStatus);
            if (amount > 0) {
                innerObj.put("amount", amount);
            }

            String payload = CryptoManager.prepareEncryptedPayload(innerObj);
            logOutboundPayload("CHECKOUT PAYLOAD", payload);

            URL url = new URL(checkoutUrl);
            connection = (HttpsURLConnection) url.openConnection();
            configureConnection(connection, payload, activeToken);

            int responseCode = connection.getResponseCode();
            String responseStr = readResponse(connection, responseCode);
            logInboundResponse("CHECKOUT RESPONSE", responseCode, responseStr);

            JSONObject json = ApiResponseHelper.parseAndDecryptResponse(responseStr);

            // Check for Auth Failure and Auto-Retry (Tareeqa 2)
            if (isAuthFailure(responseCode, json, responseStr) && retryCount < MAX_AUTH_RETRIES) {
                Log.w(TAG, "[AUTH RETRY 1/1] Token rejected. Invalidating and retrying checkoutVehicle...");
                TokenManager.getInstance().invalidateToken();
                String freshToken = TokenManager.getInstance().getValidTokenSync();
                executeCheckoutVehicle(bookingId, userId, vehicleNo, paymentModeId, transactionId, paymentStatus, amount, freshToken, callback, retryCount + 1);
                return;
            }

            if (json != null) {
                String status = json.optString("status", "");
                String message = ApiResponseHelper.extractServerMessage(json, "Vehicle check-out completed.");
                String data = json.optString("data", "");

                boolean isSuccess = "CHKOUT_001".equalsIgnoreCase(status)
                        || "GEN_000".equalsIgnoreCase(status)
                        || (message != null && message.toLowerCase().contains("success"));

                mainHandler.post(() -> callback.onResponse(status, message, data, isSuccess));
            } else {
                String cleanErr = ApiResponseHelper.cleanHttpError(responseStr, responseCode, connection.getResponseMessage());
                mainHandler.post(() -> callback.onError(cleanErr));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error performing checkout", e);
            mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Error during checkout"));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static void checkoutVehicle(int bookingId,
                                       int userId,
                                       String vehicleNo,
                                       String bearerToken,
                                       CheckoutCallback callback) {
        checkoutVehicle(bookingId, userId, vehicleNo, 1, "0", 1, 0.0, bearerToken, callback);
    }

    public static void checkoutVehicle(int userId,
                                       String vehicleNo,
                                       String bearerToken,
                                       CheckoutCallback callback) {
        checkoutVehicle(0, userId, vehicleNo, 1, "0", 1, 0.0, bearerToken, callback);
    }

    // ============================================================================================
    // 5. GET ALL VEHICLE TYPES
    // ============================================================================================

    public static void getAllVehicleTypes(String bearerToken, VehicleTypesCallback callback) {
        executor.execute(() -> executeGetAllVehicleTypes(bearerToken, callback, 0));
    }

    private static void executeGetAllVehicleTypes(String bearerToken, VehicleTypesCallback callback, int retryCount) {
        HttpsURLConnection connection = null;
        String typesUrl = ApiConfig.getUrlGetAllVehicleTypes();
        Log.i(TAG, "==================== [GET ALL VEHICLE TYPES START" + (retryCount > 0 ? " (RETRY)" : "") + "] ====================");
        Log.i(TAG, "URL: " + typesUrl);

        try {
            String activeToken = resolveActiveToken(bearerToken);

            URL url = new URL(typesUrl);
            connection = (HttpsURLConnection) url.openConnection();
            String emptyPayload = CryptoManager.prepareEmptyPayload();
            configureConnection(connection, emptyPayload, activeToken);

            int responseCode = connection.getResponseCode();
            String responseStr = readResponse(connection, responseCode);
            logInboundResponse("VEHICLE TYPES RESPONSE", responseCode, responseStr);

            JSONObject json = ApiResponseHelper.parseAndDecryptResponse(responseStr);

            // Check for Auth Failure and Auto-Retry (Tareeqa 2)
            if (isAuthFailure(responseCode, json, responseStr) && retryCount < MAX_AUTH_RETRIES) {
                Log.w(TAG, "[AUTH RETRY 1/1] Token rejected. Invalidating and retrying getAllVehicleTypes...");
                TokenManager.getInstance().invalidateToken();
                String freshToken = TokenManager.getInstance().getValidTokenSync();
                executeGetAllVehicleTypes(freshToken, callback, retryCount + 1);
                return;
            }

            if (json != null) {
                String status = json.optString("status", "");
                String message = ApiResponseHelper.extractServerMessage(json, "Vehicle types loaded.");
                boolean isSuccess = "GEN_000".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status);

                List<VehicleType> types = new ArrayList<>();
                if (isSuccess) {
                    String dataStr = json.optString("data", "");
                    JSONArray array = null;
                    if (dataStr.startsWith("[")) {
                        array = new JSONArray(dataStr);
                    } else {
                        array = json.optJSONArray("data");
                    }

                    if (array != null) {
                        for (int i = 0; i < array.length(); i++) {
                            types.add(VehicleType.fromJson(array.getJSONObject(i)));
                        }
                    }
                }

                mainHandler.post(() -> callback.onResponse(types, status, message, isSuccess));
            } else {
                String cleanErr = ApiResponseHelper.cleanHttpError(responseStr, responseCode, connection.getResponseMessage());
                mainHandler.post(() -> callback.onError(cleanErr));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error fetching vehicle types", e);
            mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Error loading vehicle types"));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    // ============================================================================================
    // 6. CHECK-IN UNREGISTERED VEHICLE
    // ============================================================================================

    public static void checkinUnregisteredVehicle(int agentId,
                                                String vehicleNo,
                                                int vehicleTypeId,
                                                String ownerName,
                                                String mobileNo,
                                                String emailId,
                                                String address,
                                                int paymentModeId,
                                                String alternateMobileNo,
                                                String bearerToken,
                                                CheckInCallback callback) {
        executor.execute(() -> executeCheckinUnregisteredVehicle(agentId, vehicleNo, vehicleTypeId, ownerName, mobileNo, emailId, address, paymentModeId, alternateMobileNo, bearerToken, callback, 0));
    }

    private static void executeCheckinUnregisteredVehicle(int agentId,
                                                        String vehicleNo,
                                                        int vehicleTypeId,
                                                        String ownerName,
                                                        String mobileNo,
                                                        String emailId,
                                                        String address,
                                                        int paymentModeId,
                                                        String alternateMobileNo,
                                                        String bearerToken,
                                                        CheckInCallback callback,
                                                        int retryCount) {
        HttpsURLConnection connection = null;
        String checkinUnregUrl = ApiConfig.getUrlCheckinUnregistered();
        Log.i(TAG, "==================== [CHECK-IN UNREGISTERED VEHICLE START" + (retryCount > 0 ? " (RETRY)" : "") + "] ====================");
        Log.i(TAG, "URL: " + checkinUnregUrl);

        try {
            String activeToken = resolveActiveToken(bearerToken);

            int finalAgentId = agentId > 0 ? agentId : 159;
            JSONObject innerObj = new JSONObject();
            innerObj.put("agent_id", finalAgentId);
            innerObj.put("vehicle_no", vehicleNo != null ? vehicleNo.trim().toUpperCase() : "");
            innerObj.put("vehicle_type_id", vehicleTypeId);
            innerObj.put("owner_name", (ownerName != null && !ownerName.trim().isEmpty()) ? ownerName.trim() : "12345");
            innerObj.put("mobile_no", (mobileNo != null && !mobileNo.trim().isEmpty()) ? mobileNo.trim() : "");
            innerObj.put("email_id", (emailId != null && !emailId.trim().isEmpty()) ? emailId.trim() : "deep@gmail.com");
            innerObj.put("address", (address != null && !address.trim().isEmpty()) ? address.trim() : "634");
            innerObj.put("payment_mode_id", paymentModeId);
            innerObj.put("alternate_mobile_no", (alternateMobileNo != null && !alternateMobileNo.trim().isEmpty())
                    ? alternateMobileNo.trim()
                    : "0000000000");

            String payload = CryptoManager.prepareEncryptedPayload(innerObj);
            logOutboundPayload("UNREGISTERED CHECK-IN PAYLOAD", payload);

            URL url = new URL(checkinUnregUrl);
            connection = (HttpsURLConnection) url.openConnection();
            configureConnection(connection, payload, activeToken);

            int responseCode = connection.getResponseCode();
            String responseStr = readResponse(connection, responseCode);
            logInboundResponse("UNREGISTERED CHECK-IN RESPONSE", responseCode, responseStr);

            JSONObject json = ApiResponseHelper.parseAndDecryptResponse(responseStr);

            // Check for Auth Failure and Auto-Retry (Tareeqa 2)
            if (isAuthFailure(responseCode, json, responseStr) && retryCount < MAX_AUTH_RETRIES) {
                Log.w(TAG, "[AUTH RETRY 1/1] Token rejected. Invalidating and retrying checkinUnregisteredVehicle...");
                TokenManager.getInstance().invalidateToken();
                String freshToken = TokenManager.getInstance().getValidTokenSync();
                executeCheckinUnregisteredVehicle(agentId, vehicleNo, vehicleTypeId, ownerName, mobileNo, emailId, address, paymentModeId, alternateMobileNo, freshToken, callback, retryCount + 1);
                return;
            }

            if (json != null) {
                String status = json.optString("status", "");
                String message = ApiResponseHelper.extractServerMessage(json, "Vehicle registered & checked in.");
                String data = json.optString("data", "null");

                boolean isSuccess = "CHKIN_001".equalsIgnoreCase(status)
                        || "GEN_000".equalsIgnoreCase(status)
                        || (message != null && message.toLowerCase().contains("success"));

                mainHandler.post(() -> callback.onResponse(status, message, data, isSuccess));
            } else {
                String cleanErr = ApiResponseHelper.cleanHttpError(responseStr, responseCode, connection.getResponseMessage());
                mainHandler.post(() -> callback.onError(cleanErr));
            }

        } catch (UnknownHostException uhe) {
            mainHandler.post(() -> callback.onError("Unable to connect to server. Please check internet connection."));
        } catch (Exception e) {
            Log.e(TAG, "Error in checkinUnregisteredVehicle", e);
            mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Error during registration"));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    // ============================================================================================
    // HTTP CONNECTION & RESPONSE HELPERS
    // ============================================================================================

    private static void configureConnection(HttpsURLConnection connection, String payload, String bearerToken) throws Exception {
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");

        if (bearerToken != null && !bearerToken.trim().isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken.trim());
        }

        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setDoOutput(true);
        connection.setDoInput(true);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = payload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
            os.flush();
        }
    }

    private static String readResponse(HttpsURLConnection connection, int responseCode) throws Exception {
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
        return responseBuilder.toString();
    }

    private static void logOutboundPayload(String label, String payload) {
        if (ApiConfig.isEncryptionEnabled()) {
            Log.i(TAG, "[" + label + "] Encrypted AES-256-GCM cipher (" + (payload != null ? payload.length() : 0) + " bytes)");
        } else {
            Log.i(TAG, "[" + label + "] Staging payload transmitted (" + (payload != null ? payload.length() : 0) + " bytes)");
        }
    }

    private static void logInboundResponse(String label, int responseCode, String responseStr) {
        Log.i(TAG, "[" + label + "] HTTP Status " + responseCode + " received (" + (responseStr != null ? responseStr.length() : 0) + " bytes)");
    }
}
