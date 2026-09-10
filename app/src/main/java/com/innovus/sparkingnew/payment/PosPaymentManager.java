package com.innovus.sparkingnew.payment;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.eze.api.EzeAPI;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Centralized POS Payment Manager for PineLabs / EzeTap EzeAPI integration.
 * Manages initiation of Card and UPI transactions, builds options payload,
 * and robustly handles onActivityResult with full null-safety and error classification.
 */
public final class PosPaymentManager {

    private static final String TAG = "PosPaymentManager";

    // Standard Request Code for EzeAPI Payment (matching reference app)
    public static final int REQUEST_CODE_PAY = 10015;

    // Payment Mode Constants
    public static final int PAYMENT_MODE_CASH = 1;
    public static final int PAYMENT_MODE_CARD = 2;
    public static final int PAYMENT_MODE_FASTAG = 3;
    public static final int PAYMENT_MODE_PASS = 4;
    public static final int PAYMENT_MODE_UPI = 6;

    public interface PaymentCallback {
        void onPaymentSuccess(String txnId, String rawResponse);
        void onPaymentCancelled(String errorCode, String errorMessage);
        void onPaymentFailed(String errorCode, String errorMessage);
    }

    private static PaymentCallback activeCallback;
    private static int activePaymentModeId = PAYMENT_MODE_CARD;

    private PosPaymentManager() {
        // Prevent instantiation
    }

    /**
     * Initiates a POS payment request (Card or UPI) via EzeAPI.
     *
     * @param activity       Calling activity context
     * @param amount         Payable amount in INR
     * @param bookingNo      Booking reference number
     * @param vehicleNo      Vehicle registration number
     * @param checkinTime    Check-in timestamp string
     * @param customerMobile Customer phone number (optional)
     * @param agencyLocation Parking location / Agency name
     * @param slotName       Parking slot name
     * @param paymentModeId  Payment mode (PAYMENT_MODE_CARD = 2, PAYMENT_MODE_UPI = 6)
     * @param callback       Result callback
     */
    public static void initiatePayment(Activity activity,
                                       double amount,
                                       String bookingNo,
                                       String vehicleNo,
                                       String checkinTime,
                                       String customerMobile,
                                       String agencyLocation,
                                       String slotName,
                                       int paymentModeId,
                                       PaymentCallback callback) {
        activeCallback = callback;
        activePaymentModeId = paymentModeId;

        if (activity == null || activity.isFinishing()) {
            Log.e(TAG, "Cannot initiate payment: Activity is null or finishing");
            if (callback != null) {
                callback.onPaymentFailed("ACTIVITY_INVALID", "Application context invalid");
            }
            return;
        }

        if (amount <= 0) {
            Log.e(TAG, "Invalid payment amount: " + amount);
            if (callback != null) {
                callback.onPaymentFailed("INVALID_AMOUNT", "Payment amount must be greater than zero");
            }
            return;
        }

        try {
            JSONObject jsonRequest = new JSONObject();
            JSONObject jsonOptionalParams = new JSONObject();
            JSONObject jsonReferences = new JSONObject();
            JSONObject jsonCustomer = new JSONObject();

            // References
            jsonReferences.put("reference1", (bookingNo != null && !bookingNo.trim().isEmpty()) ? bookingNo.trim() : "NA");
            jsonReferences.put("reference2", (vehicleNo != null && !vehicleNo.trim().isEmpty()) ? vehicleNo.trim().toUpperCase() : "NA");
            jsonReferences.put("reference3", (checkinTime != null && !checkinTime.trim().isEmpty()) ? checkinTime.trim() : "NA");

            String agency = (agencyLocation != null && !agencyLocation.trim().isEmpty()) ? agencyLocation.trim() : "sParking Station";
            if (agency.length() > 40) {
                agency = agency.substring(0, 40);
            }

            JSONArray additionalRefs = new JSONArray();
            additionalRefs.put(agency);
            additionalRefs.put((slotName != null && !slotName.trim().isEmpty()) ? slotName.trim() : "General Slot");
            jsonReferences.put("additionalReferences", additionalRefs);

            // Customer details
            String mobile = (customerMobile != null && !customerMobile.trim().isEmpty()) ? customerMobile.trim() : "0000000000";
            jsonCustomer.put("mobileNo", mobile);

            // Optional Params
            jsonOptionalParams.put("references", jsonReferences);
            jsonOptionalParams.put("customer", jsonCustomer);

            // Mode hint if UPI
            if (paymentModeId == PAYMENT_MODE_UPI) {
                JSONObject jsonMode = new JSONObject();
                jsonMode.put("type", "UPI");
                jsonOptionalParams.put("mode", jsonMode);
            }

            // Final Request Object (amount formatted to 2 decimals)
            double formattedAmount = Math.round(amount * 100.0) / 100.0;
            jsonRequest.put("amount", formattedAmount);
            jsonRequest.put("options", jsonOptionalParams);

            Log.i(TAG, "==================== [EZEAPI PAYMENT INITIATED] ====================");
            Log.i(TAG, "Mode: " + (paymentModeId == PAYMENT_MODE_UPI ? "UPI" : "CARD") + " | Amount: Rs." + formattedAmount);
            Log.i(TAG, "Payload: " + jsonRequest);

            EzeAPI.pay(activity, REQUEST_CODE_PAY, jsonRequest);

        } catch (JSONException e) {
            Log.e(TAG, "Error constructing EzeAPI JSON payload: " + e.getMessage(), e);
            if (callback != null) {
                callback.onPaymentFailed("JSON_ERROR", "Error constructing payment request: " + e.getMessage());
            }
        } catch (Throwable t) {
            Log.e(TAG, "Fatal error invoking EzeAPI.pay: " + t.getMessage(), t);
            if (callback != null) {
                callback.onPaymentFailed("SDK_ERROR", "Error invoking POS payment: " + t.getMessage());
            }
        }
    }

    /**
     * Handles onActivityResult from MainActivity when requestCode == REQUEST_CODE_PAY.
     *
     * @param requestCode Activity request code
     * @param resultCode  Activity result code (RESULT_OK, RESULT_CANCELED, etc.)
     * @param data        Returned Intent
     * @return true if handled by this manager, false otherwise
     */
    public static boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE_PAY) {
            return false;
        }

        PaymentCallback callback = activeCallback;
        activeCallback = null;

        Log.i(TAG, "==================== [EZEAPI PAYMENT RESULT RECEIVED] ====================");
        Log.i(TAG, "ResultCode: " + resultCode + " | Data present: " + (data != null));

        if (data == null || !data.hasExtra("response")) {
            Log.w(TAG, "No response extra found in Intent data");
            if (callback != null) {
                if (resultCode == Activity.RESULT_CANCELED) {
                    callback.onPaymentCancelled("USER_CANCELLED", "Payment was cancelled on terminal");
                } else {
                    callback.onPaymentFailed("NULL_RESPONSE", "No transaction response received from POS");
                }
            }
            return true;
        }

        String rawResponse = data.getStringExtra("response");
        Log.i(TAG, "Raw EzeAPI Response: " + rawResponse);

        try {
            JSONObject responseObj = new JSONObject(rawResponse != null ? rawResponse : "{}");

            if (resultCode == Activity.RESULT_OK) {
                // Payment Success: extract transaction ID
                String txnId = "";
                JSONObject resultObj = responseObj.optJSONObject("result");
                if (resultObj != null) {
                    JSONObject txnObj = resultObj.optJSONObject("txn");
                    if (txnObj != null) {
                        txnId = txnObj.optString("txnId", "");
                        if (txnId.isEmpty()) {
                            txnId = txnObj.optString("paymentId", "");
                        }
                    }
                }

                if (txnId.isEmpty()) {
                    txnId = "TXN_" + System.currentTimeMillis();
                }

                Log.i(TAG, "Payment SUCCESSFUL -> TxnId: " + txnId);
                if (callback != null) {
                    callback.onPaymentSuccess(txnId, rawResponse);
                }

            } else if (resultCode == Activity.RESULT_CANCELED) {
                // Payment Cancelled / User Aborted
                JSONObject errorObj = responseObj.optJSONObject("error");
                String errorCode = (errorObj != null) ? errorObj.optString("code", "CANCELLED") : "CANCELLED";
                String errorMessage = (errorObj != null) ? errorObj.optString("message", "Payment cancelled by operator or customer") : "Payment cancelled";

                Log.w(TAG, "Payment CANCELLED -> Code: " + errorCode + " | Message: " + errorMessage);
                if (callback != null) {
                    callback.onPaymentCancelled(errorCode, errorMessage);
                }

            } else {
                // Payment Error / Declined / Technical Failure
                JSONObject errorObj = responseObj.optJSONObject("error");
                String errorCode = (errorObj != null) ? errorObj.optString("code", "PAYMENT_FAILED") : "PAYMENT_FAILED";
                String errorMessage = (errorObj != null) ? errorObj.optString("message", "Payment could not be completed") : "Payment failed";

                Log.e(TAG, "Payment FAILED -> Code: " + errorCode + " | Message: " + errorMessage);
                if (callback != null) {
                    callback.onPaymentFailed(errorCode, errorMessage);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing payment response: " + e.getMessage(), e);
            if (callback != null) {
                callback.onPaymentFailed("PARSE_ERROR", "Failed to parse POS response: " + e.getMessage());
            }
        }

        return true;
    }

    public static int getActivePaymentModeId() {
        return activePaymentModeId;
    }
}
