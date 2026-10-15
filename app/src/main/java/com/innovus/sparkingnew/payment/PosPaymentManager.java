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

        com.innovus.sparkingnew.devices.DeviceInterface device = com.innovus.sparkingnew.devices.DeviceFactory.getDevice(activity);
        if (device instanceof com.innovus.sparkingnew.devices.PineLabsDevice) {
            initiatePineLabsPayment(activity, amount, bookingNo, paymentModeId, callback);
            return;
        }

        if (amount <= 0) {
            Log.e(TAG, "Invalid payment amount:" + amount);
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

    private static void initiatePineLabsPayment(Activity activity,
                                                double amount,
                                                String bookingNo,
                                                int paymentModeId,
                                                PaymentCallback callback) {
        Intent intent = new Intent();
        intent.setAction("com.pinelabs.masterapp.SERVER");
        intent.setPackage("com.pinelabs.masterapp");

        final android.content.ServiceConnection[] connectionHolder = new android.content.ServiceConnection[1];

        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper()) {
            @Override
            public void handleMessage(android.os.Message msg) {
                super.handleMessage(msg);
                
                if (connectionHolder[0] != null) {
                    try {
                        activity.unbindService(connectionHolder[0]);
                    } catch (Exception e) {
                        Log.w(TAG, "Error unbinding PineLabs service: " + e.getMessage());
                    }
                    connectionHolder[0] = null;
                }

                android.os.Bundle bundle = msg.getData();
                if (bundle != null) {
                    String rawResponse = bundle.getString("MASTERAPPRESPONSE");
                    Log.i(TAG, "PineLabs Payment Response: " + rawResponse);
                    
                    try {
                        JSONObject responseObj = new JSONObject(rawResponse != null ? rawResponse : "{}");
                        JSONObject resultObj = responseObj.optJSONObject("Response");
                        if (resultObj != null) {
                            int responseCode = resultObj.optInt("ResponseCode", -1);
                            String responseMsg = resultObj.optString("ResponseMsg", "");

                            if (responseCode == 0) {
                                String txnId = "TXN_" + System.currentTimeMillis(); 
                                if (callback != null) callback.onPaymentSuccess(txnId, rawResponse);
                            } else if (responseCode == 7) {
                                if (callback != null) callback.onPaymentCancelled(String.valueOf(responseCode), responseMsg);
                            } else {
                                if (callback != null) callback.onPaymentFailed(String.valueOf(responseCode), responseMsg);
                            }
                        } else {
                            if (callback != null) callback.onPaymentFailed("UNKNOWN", "Missing Response in JSON");
                        }
                    } catch (Exception e) {
                        if (callback != null) callback.onPaymentFailed("PARSE_ERROR", e.getMessage());
                    }
                }
            }
        };

        connectionHolder[0] = new android.content.ServiceConnection() {
            @Override
            public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
                android.os.Messenger mService = new android.os.Messenger(service);
                try {
                    JSONObject printRequest = new JSONObject();
                    JSONObject header = new JSONObject();
                    header.put("ApplicationId", "8754f022bd7f475a9f29284a656d3401");
                    header.put("UserId", "user1234");
                    header.put("MethodId", "1001");
                    header.put("VersionNo", "1.0");
                    printRequest.put("Header", header);

                    JSONObject detail = new JSONObject();
                    if (paymentModeId == PAYMENT_MODE_CARD) {
                        detail.put("TransactionType", "4001");
                    } else if (paymentModeId == PAYMENT_MODE_UPI) {
                        detail.put("TransactionType", "5123");
                    } else {
                        detail.put("TransactionType", "4001");
                    }

                    detail.put("BillingRefNo", bookingNo != null ? bookingNo : "TXN" + System.currentTimeMillis());
                    
                    long amountInPaisa = Math.round(amount * 100.0);
                    detail.put("PaymentAmount", String.valueOf(amountInPaisa));

                    printRequest.put("Detail", detail);

                    android.os.Message message = android.os.Message.obtain(null, 1001); 
                    android.os.Bundle data = new android.os.Bundle();
                    data.putString("MASTERAPPREQUEST", printRequest.toString());
                    message.setData(data);
                    message.replyTo = new android.os.Messenger(handler);

                    Log.i(TAG, "Sending PineLabs Payment Request: " + printRequest.toString());
                    mService.send(message);

                } catch (Exception e) {
                    if (callback != null) callback.onPaymentFailed("ERROR", e.getMessage());
                    try {
                        activity.unbindService(this);
                        connectionHolder[0] = null;
                    } catch (Exception ignored) {}
                }
            }

            @Override
            public void onServiceDisconnected(android.content.ComponentName name) {
            }
        };

        boolean bindResult = activity.bindService(intent, connectionHolder[0], android.content.Context.BIND_AUTO_CREATE);
        if (!bindResult) {
            if (callback != null) callback.onPaymentFailed("BIND_ERROR", "Failed to bind PineLabs MasterApp");
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
