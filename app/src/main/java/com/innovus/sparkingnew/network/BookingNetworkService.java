package com.innovus.sparkingnew.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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
 * Fully integrated with CryptoManager for seamless Staging / Production encrypted payloads.
 */
public class BookingNetworkService {

    private static final String TAG = "BookingNetworkService";

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
     * Executes checkinRegisteredVehicle API asynchronously.
     */
    public static void checkinRegisteredVehicle(int agentId,
                                                String vehicleNo,
                                                int paymentModeId,
                                                String alternateMobileNo,
                                                String bearerToken,
                                                CheckInCallback callback) {
        executor.execute(() -> {
            HttpsURLConnection connection = null;
            String checkinUrl = ApiConfig.getUrlCheckinRegistered();
            Log.i(TAG, "==================== [CHECK-IN REQUEST START] ====================");
            Log.i(TAG, "URL: " + checkinUrl);

            try {
                JSONObject innerObj = new JSONObject();
                innerObj.put("agent_id", agentId);
                innerObj.put("vehicle_no", vehicleNo != null ? vehicleNo.trim().toUpperCase() : "");
                innerObj.put("payment_mode_id", paymentModeId);
                innerObj.put("alternate_mobile_no", (alternateMobileNo != null && !alternateMobileNo.trim().isEmpty())
                        ? alternateMobileNo.trim()
                        : "0000000000");

                String payload = CryptoManager.prepareEncryptedPayload(innerObj);
                Log.i(TAG, "[CHECK-IN PAYLOAD] " + payload);

                URL url = new URL(checkinUrl);
                connection = (HttpsURLConnection) url.openConnection();
                configureConnection(connection, payload, bearerToken);

                int responseCode = connection.getResponseCode();
                String responseStr = readResponse(connection, responseCode);
                Log.i(TAG, "[RAW SERVER RESPONSE] " + responseStr);

                JSONObject json = ApiResponseHelper.parseAndDecryptResponse(responseStr);
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
        });
    }

    /**
     * Fetches all currently checked-in vehicles from backend.
     */
    public static void getAllCheckinVehicle(int userId,
                                            String vehicleNo,
                                            String bearerToken,
                                            FetchVehiclesCallback callback) {
        executor.execute(() -> {
            HttpsURLConnection connection = null;
            String getVehiclesUrl = ApiConfig.getUrlGetAllCheckinVehicles();
            Log.i(TAG, "==================== [GET ALL CHECKIN VEHICLE START] ====================");
            Log.i(TAG, "URL: " + getVehiclesUrl);

            try {
                String queryVehicle = (vehicleNo != null && !vehicleNo.trim().isEmpty())
                        ? vehicleNo.trim().toUpperCase()
                        : "%";

                int finalUserId = userId > 0 ? userId : 159;
                JSONObject innerObj = new JSONObject();
                innerObj.put("user_id", finalUserId);
                innerObj.put("agent_id", finalUserId);
                innerObj.put("vehicle_no", queryVehicle);

                String payload = CryptoManager.prepareEncryptedPayload(innerObj);
                Log.i(TAG, "[FETCH PAYLOAD] " + payload);

                URL url = new URL(getVehiclesUrl);
                connection = (HttpsURLConnection) url.openConnection();
                configureConnection(connection, payload, bearerToken);

                int responseCode = connection.getResponseCode();
                String responseStr = readResponse(connection, responseCode);
                Log.i(TAG, "[FETCH RESPONSE] " + responseStr);

                JSONObject json = ApiResponseHelper.parseAndDecryptResponse(responseStr);
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
        });
    }

    /**
     * Calculates checkout bill and total payable amount before completing checkout.
     */
    public static void getCheckoutAmount(int bookingId,
                                         String bearerToken,
                                         CheckoutAmountCallback callback) {
        executor.execute(() -> {
            HttpsURLConnection connection = null;
            String amountUrl = ApiConfig.getUrlGetCheckoutAmount();
            Log.i(TAG, "==================== [GET CHECKOUT AMOUNT START] ====================");
            Log.i(TAG, "URL: " + amountUrl);

            try {
                JSONObject innerObj = new JSONObject();
                innerObj.put("booking_id", bookingId);

                String payload = CryptoManager.prepareEncryptedPayload(innerObj);
                Log.i(TAG, "[GET CHECKOUT AMOUNT PAYLOAD] " + payload);

                URL url = new URL(amountUrl);
                connection = (HttpsURLConnection) url.openConnection();
                configureConnection(connection, payload, bearerToken);

                int responseCode = connection.getResponseCode();
                String responseStr = readResponse(connection, responseCode);
                Log.i(TAG, "[GET CHECKOUT AMOUNT RAW RESPONSE] " + responseStr);

                JSONObject json = ApiResponseHelper.parseAndDecryptResponse(responseStr);
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
        });
    }

    /**
     * Executes vehicleCheckout API asynchronously with complete payment and transaction details.
     */
    public static void checkoutVehicle(int bookingId,
                                       int userId,
                                       String vehicleNo,
                                       int paymentModeId,
                                       String transactionId,
                                       int paymentStatus,
                                       double amount,
                                       String bearerToken,
                                       CheckoutCallback callback) {
        executor.execute(() -> {
            HttpsURLConnection connection = null;
            String checkoutUrl = ApiConfig.getUrlVehicleCheckout();
            Log.i(TAG, "==================== [CHECKOUT REQUEST START] ====================");
            Log.i(TAG, "URL: " + checkoutUrl);
            Log.i(TAG, "PaymentModeId: " + paymentModeId + " | TxnId: " + transactionId + " | Status: " + paymentStatus + " | Amount: " + amount);

            try {
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
                Log.i(TAG, "[CHECKOUT PAYLOAD] " + payload);

                URL url = new URL(checkoutUrl);
                connection = (HttpsURLConnection) url.openConnection();
                configureConnection(connection, payload, bearerToken);

                int responseCode = connection.getResponseCode();
                String responseStr = readResponse(connection, responseCode);
                Log.i(TAG, "[CHECKOUT RAW RESPONSE] " + responseStr);

                JSONObject json = ApiResponseHelper.parseAndDecryptResponse(responseStr);
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
        });
    }

    /**
     * Executes vehicleCheckout API asynchronously with required booking_id (defaults to Cash).
     */
    public static void checkoutVehicle(int bookingId,
                                       int userId,
                                       String vehicleNo,
                                       String bearerToken,
                                       CheckoutCallback callback) {
        checkoutVehicle(bookingId, userId, vehicleNo, 1, "0", 1, 0.0, bearerToken, callback);
    }

    /**
     * Backwards-compatible checkoutVehicle overload without bookingId.
     */
    public static void checkoutVehicle(int userId,
                                       String vehicleNo,
                                       String bearerToken,
                                       CheckoutCallback callback) {
        checkoutVehicle(0, userId, vehicleNo, 1, "0", 1, 0.0, bearerToken, callback);
    }

    /**
     * Fetches all vehicle categories (Two Wheeler, Four Wheeler, etc.)
     */
    public static void getAllVehicleTypes(String bearerToken, VehicleTypesCallback callback) {
        executor.execute(() -> {
            HttpsURLConnection connection = null;
            String typesUrl = ApiConfig.getUrlGetAllVehicleTypes();
            Log.i(TAG, "==================== [GET ALL VEHICLE TYPES START] ====================");
            Log.i(TAG, "URL: " + typesUrl);

            try {
                URL url = new URL(typesUrl);
                connection = (HttpsURLConnection) url.openConnection();
                String emptyPayload = CryptoManager.prepareEmptyPayload();
                configureConnection(connection, emptyPayload, bearerToken);

                int responseCode = connection.getResponseCode();
                String responseStr = readResponse(connection, responseCode);
                Log.i(TAG, "[VEHICLE TYPES RESPONSE] " + responseStr);

                JSONObject json = ApiResponseHelper.parseAndDecryptResponse(responseStr);
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
        });
    }

    /**
     * Executes checkinUnregisteredVehicle API asynchronously.
     */
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
        executor.execute(() -> {
            HttpsURLConnection connection = null;
            String checkinUnregUrl = ApiConfig.getUrlCheckinUnregistered();
            Log.i(TAG, "==================== [CHECK-IN UNREGISTERED VEHICLE START] ====================");
            Log.i(TAG, "URL: " + checkinUnregUrl);

            try {
                int finalAgentId = agentId > 0 ? agentId : 159;
                JSONObject innerObj = new JSONObject();
                innerObj.put("agent_id", finalAgentId);
                innerObj.put("vehicle_no", vehicleNo != null ? vehicleNo.trim().toUpperCase() : "");
                innerObj.put("vehicle_type_id", vehicleTypeId);
                innerObj.put("owner_name", (ownerName != null && !ownerName.trim().isEmpty()) ? ownerName.trim() : "");
                innerObj.put("mobile_no", (mobileNo != null && !mobileNo.trim().isEmpty()) ? mobileNo.trim() : "");
                innerObj.put("email_id", (emailId != null && !emailId.trim().isEmpty()) ? emailId.trim() : "deep@gmail.com");
                innerObj.put("address", (address != null && !address.trim().isEmpty()) ? address.trim() : "634");
                innerObj.put("payment_mode_id", paymentModeId);
                innerObj.put("alternate_mobile_no", (alternateMobileNo != null && !alternateMobileNo.trim().isEmpty())
                        ? alternateMobileNo.trim()
                        : "0000000000");

                String payload = CryptoManager.prepareEncryptedPayload(innerObj);
                Log.i(TAG, "[UNREGISTERED CHECK-IN PAYLOAD] " + payload);

                URL url = new URL(checkinUnregUrl);
                connection = (HttpsURLConnection) url.openConnection();
                configureConnection(connection, payload, bearerToken);

                int responseCode = connection.getResponseCode();
                String responseStr = readResponse(connection, responseCode);
                Log.i(TAG, "[UNREGISTERED CHECK-IN RESPONSE] " + responseStr);

                JSONObject json = ApiResponseHelper.parseAndDecryptResponse(responseStr);
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
        });
    }

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
}
