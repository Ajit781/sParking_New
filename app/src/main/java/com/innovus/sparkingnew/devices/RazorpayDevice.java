package com.innovus.sparkingnew.devices;

import android.app.Activity;
import android.util.Log;

import com.innovus.sparkingnew.payment.PosPaymentManager;
import com.innovus.sparkingnew.printer.ReceiptPrinter;

public class RazorpayDevice implements DeviceInterface {

    private static final String TAG = "RazorpayDevice";

    @Override
    public void startPayment(Activity activity, 
                             double amount, 
                             String bookingNo, 
                             String vehicleNo, 
                             String checkinTime, 
                             String customerMobile, 
                             String agencyLocation, 
                             String slotName, 
                             int paymentModeId, 
                             PosPaymentManager.PaymentCallback callback) {
        Log.i(TAG, "Starting Razorpay Payment for amount: " + amount);
        // TODO: Apna Razorpay Payment ka code yaha daaliye
    }

    @Override
    public void printCheckInTicket(Activity activity, 
                                   String vehicleNumber, 
                                   String checkInTime, 
                                   String bookingNumber, 
                                   String vehicleType, 
                                   String parkingLocation, 
                                   String slotName, 
                                   ReceiptPrinter.PrintCallback callback) {
        Log.i(TAG, "Printing CheckIn Ticket via Razorpay (EzeAPI)");
        try {
            ReceiptPrinter.setPendingEzeCallback(callback);
            ReceiptPrinter.printOnEzeApi(activity, vehicleNumber, checkInTime, bookingNumber, vehicleType, parkingLocation, slotName, callback);
        } catch (Exception e) {
            Log.e(TAG, "EzeAPI print error: " + e.getMessage());
            if (callback != null) callback.onComplete(false, "EzeAPI print error: " + e.getMessage());
        }
    }

    @Override
    public void printCheckOutTicket(Activity activity, 
                                    String vehicleNumber, 
                                    String checkInTime, 
                                    String checkOutTime, 
                                    String bookingNumber, 
                                    String vehicleType, 
                                    String parkingLocation, 
                                    String slotName, 
                                    int totalHours, 
                                    double hourlyRate, 
                                    double totalAmount, 
                                    String paymentModeName, 
                                    String transactionId, 
                                    ReceiptPrinter.PrintCallback callback) {
        Log.i(TAG, "Printing CheckOut Ticket via Razorpay (EzeAPI)");
        try {
            ReceiptPrinter.setPendingEzeCallback(callback);
            ReceiptPrinter.printCheckOutOnEzeApi(activity, vehicleNumber, checkInTime, checkOutTime, bookingNumber, vehicleType, parkingLocation, slotName, totalHours, hourlyRate, totalAmount, paymentModeName, transactionId, callback);
        } catch (Exception e) {
            Log.e(TAG, "EzeAPI print error: " + e.getMessage());
            if (callback != null) callback.onComplete(false, "EzeAPI print error: " + e.getMessage());
        }
    }

    @Override
    public void printBookingSummary(Activity activity,
                                    String fromDate,
                                    String toDate,
                                    java.util.List<com.innovus.sparkingnew.models.ParkingSummaryByAgentBean> summaryList,
                                    int totalCount,
                                    int grandTotal,
                                    ReceiptPrinter.PrintCallback callback) {
        Log.i(TAG, "Printing Booking Summary via Razorpay");
        // TODO: Apna Razorpay Booking Summary Print ka code yaha daaliye
    }
}
