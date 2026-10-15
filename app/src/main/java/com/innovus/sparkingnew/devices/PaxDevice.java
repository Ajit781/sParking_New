package com.innovus.sparkingnew.devices;

import android.app.Activity;

import com.innovus.sparkingnew.payment.PosPaymentManager;
import com.innovus.sparkingnew.printer.ReceiptPrinter;

public class PaxDevice implements DeviceInterface {

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
        // PAX doesn't have a specific payment SDK configured in this interface yet,
        // fallback to standard PosPaymentManager
        PosPaymentManager.initiatePayment(
                activity, amount, bookingNo, vehicleNo, checkinTime, 
                customerMobile, agencyLocation, slotName, paymentModeId, callback
        );
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
        ReceiptPrinter.printCheckInTicket(
                activity, vehicleNumber, checkInTime, bookingNumber, 
                vehicleType, parkingLocation, slotName, callback
        );
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
        ReceiptPrinter.printCheckOutTicket(
                activity, vehicleNumber, checkInTime, checkOutTime, 
                bookingNumber, vehicleType, parkingLocation, slotName, 
                totalHours, hourlyRate, totalAmount, paymentModeName, 
                transactionId, callback
        );
    }

    @Override
    public void printBookingSummary(Activity activity,
                                    String fromDate,
                                    String toDate,
                                    java.util.List<com.innovus.sparkingnew.models.ParkingSummaryByAgentBean> summaryList,
                                    int totalCount,
                                    int grandTotal,
                                    ReceiptPrinter.PrintCallback callback) {
    }
}
