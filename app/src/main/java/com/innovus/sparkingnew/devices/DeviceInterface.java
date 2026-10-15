package com.innovus.sparkingnew.devices;

import android.app.Activity;

import com.innovus.sparkingnew.payment.PosPaymentManager;
import com.innovus.sparkingnew.printer.ReceiptPrinter;

import com.innovus.sparkingnew.models.ParkingSummaryByAgentBean;
import java.util.List;

public interface DeviceInterface {

    void startPayment(Activity activity, 
                      double amount, 
                      String bookingNo, 
                      String vehicleNo, 
                      String checkinTime, 
                      String customerMobile, 
                      String agencyLocation, 
                      String slotName, 
                      int paymentModeId, 
                      PosPaymentManager.PaymentCallback callback);

    void printCheckInTicket(Activity activity, 
                            String vehicleNumber, 
                            String checkInTime, 
                            String bookingNumber, 
                            String vehicleType, 
                            String parkingLocation, 
                            String slotName, 
                            ReceiptPrinter.PrintCallback callback);

    void printCheckOutTicket(Activity activity, 
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
                             ReceiptPrinter.PrintCallback callback);

    void printBookingSummary(Activity activity,
                             String fromDate,
                             String toDate,
                             List<ParkingSummaryByAgentBean> summaryList,
                             int totalCount,
                             int grandTotal,
                             ReceiptPrinter.PrintCallback callback);
}
