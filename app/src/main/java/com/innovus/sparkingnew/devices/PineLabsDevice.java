package com.innovus.sparkingnew.devices;

import android.app.Activity;

import com.innovus.sparkingnew.payment.PosPaymentManager;
import com.innovus.sparkingnew.printer.ReceiptPrinter;

public class PineLabsDevice implements DeviceInterface {

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
        
        org.json.JSONArray printData = new org.json.JSONArray();
        try {
            // Header
            addTextLine(printData, "sParking", true, 24);
            addTextLine(printData, "www.s-parking.com", true, 20);
            
            String[] arrLocName = com.innovus.sparkingnew.printer.PrinterUtils.breakStringToLines(parkingLocation, 35);
            for (String strLocName : arrLocName) {
                if (strLocName != null && !strLocName.trim().isEmpty()) {
                    addTextLine(printData, strLocName, true, 20);
                }
            }
            
            addTextLine(printData, "", true, 20); // Empty line
            addTextLine(printData, "Vehicle No : " + vehicleNumber, true, 20);
            
            String displayTime = checkInTime;
            if (displayTime != null && displayTime.length() > 3) {
                displayTime = displayTime.substring(0, displayTime.length() - 3);
            }
            addTextLine(printData, "CheckIn Time : " + displayTime, true, 20);
            addTextLine(printData, "Booking No : " + bookingNumber, true, 20);
            
            if (slotName != null && !slotName.isEmpty()) {
                addTextLine(printData, "Slot : " + slotName, true, 20);
            }
            
            addTextLine(printData, "", true, 20);
            
            // QR Code
            String qrData = vehicleNumber + "##" + displayTime + "##" + bookingNumber + "##" + vehicleType;
            org.json.JSONObject printQR = new org.json.JSONObject();
            printQR.put("PrintDataType", 4);
            printQR.put("PrinterWidth", 32);
            printQR.put("IsCenterAligned", true); // Required by SDK
            printQR.put("DataToPrint", qrData);
            printData.put(printQR);
            
            addTextLine(printData, "", true, 20);
            addTextLine(printData, "Download s-Parking from Play Store", true, 20);
            addTextLine(printData, "", true, 20);
            addTextLine(printData, "", true, 20);
            addTextLine(printData, "", true, 20);
            addTextLine(printData, "", true, 20);
            
            sendToPineLabsService(activity, printData, callback);

        } catch (Exception e) {
            if (callback != null) callback.onComplete(false, "JSON Error: " + e.getMessage());
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
        
        String gstNo = "";
        try {
            com.innovus.sparkingnew.session.SessionManager session = com.innovus.sparkingnew.session.SessionManager.getInstance(activity);
            if (session != null && session.getUserData() != null) {
                gstNo = session.getUserData().getAgencyGstNo();
            }
        } catch (Exception ignored) {}

        org.json.JSONArray printData = new org.json.JSONArray();
        try {
            addTextLine(printData, "Tax Invoice", true, 24);
            addTextLine(printData, "Parking Maintenance Charge", true, 20);
            
            String[] arrLocName = com.innovus.sparkingnew.printer.PrinterUtils.breakStringToLines(parkingLocation, 35);
            for (String strLocName : arrLocName) {
                if (strLocName != null && !strLocName.trim().isEmpty()) {
                    addTextLine(printData, strLocName, true, 20);
                }
            }
            
            addTextLine(printData, "", true, 20);
            addTextLine(printData, "Vehicle No: "+ vehicleNumber, true, 20);
            addTextLine(printData, "Booking No: "+ bookingNumber, true, 20);
            addTextLine(printData, "In Time:"+ formatReceiptTime(checkInTime), true, 20);
            addTextLine(printData, "Out Time:"+ formatReceiptTime(checkOutTime), true, 20);
            addTextLine(printData, "Duration:"+ totalHours + " Hr(s)", true, 20);
            
            String payAmountStr = formatAmount(totalAmount);
            addTextLine(printData, "Amount: Rs."+ payAmountStr, true, 20);
            addTextLine(printData, "Fine Amount : Rs. 0", true, 20);
            addTextLine(printData, "Discount: Rs. 0", true, 20);
            addTextLine(printData, "--------------------------------", true, 20);
            addTextLine(printData, "Pay " + paymentModeName + "       : Rs. " + payAmountStr, true, 20);
            
            if (transactionId != null && !transactionId.isEmpty() && !"0".equals(transactionId)) {
                addTextLine(printData, "Txn ID: " + transactionId, true, 20);
            }
            
            addTextLine(printData, "", true, 20);
            
            if (gstNo != null && !gstNo.trim().isEmpty()) {
                addTextLine(printData, "Inclusive of GST @ 18%", true, 20);
                addTextLine(printData, "GSTN : " + gstNo.trim(), true, 20);
            }
            
            addTextLine(printData, "Download s-Parking from Play Store", true, 20);
            addTextLine(printData, "", true, 20);
            addTextLine(printData, "", true, 20);
            addTextLine(printData, "", true, 20);
            addTextLine(printData, "", true, 20);
            
            sendToPineLabsService(activity, printData, callback);

        } catch (Exception e) {
            if (callback != null) callback.onComplete(false, "JSON Error: " + e.getMessage());
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
        // Here we would ideally call ReceiptPrinter.printBookingSummary
        // Since ReceiptPrinter doesn't have it yet, we add a TODO
        // TODO: Move the PineLabs printEazytapBillUsingSDK logic from BookingSummaryActivity here
    }

    private void addTextLine(org.json.JSONArray printData, String text, boolean isCenterAligned, int width) throws org.json.JSONException {
        if (text == null || text.isEmpty()) {
            text = " ";
            isCenterAligned = false; 
        }
        int targetWidth = width > 0 ? width : 32;

        org.json.JSONObject lineObj = new org.json.JSONObject();
        lineObj.put("PrintDataType", 0);
        lineObj.put("PrinterWidth", targetWidth);
        lineObj.put("IsCenterAligned", isCenterAligned); 
        lineObj.put("DataToPrint", text);
        printData.put(lineObj);
    }

    private String formatReceiptTime(String datetimeStr) {
        if (datetimeStr == null || datetimeStr.length() < 3) return datetimeStr;
        if (datetimeStr.length() >= 19) {
            return datetimeStr.substring(0, 16);
        }
        return datetimeStr;
    }

    private String formatAmount(double amount) {
        return String.format(java.util.Locale.US, "%.2f", amount);
    }

    private void sendToPineLabsService(Activity activity, org.json.JSONArray printData, ReceiptPrinter.PrintCallback callback) {
        android.content.Intent intent = new android.content.Intent();
        intent.setAction("com.pinelabs.masterapp.SERVER");
        intent.setPackage("com.pinelabs.masterapp");

        class IncomingHandlerPrint extends android.os.Handler {
            public IncomingHandlerPrint(android.os.Looper looper) {
                super(looper);
            }

            @Override
            public void handleMessage(android.os.Message msg) {
                super.handleMessage(msg);
                android.os.Bundle bundle = msg.getData();
                if (bundle != null) {
                    String response = bundle.getString("MASTERAPPRESPONSE");
                    android.util.Log.i("PineLabsDevice", "PineLabs Print Response: " + response);
                }
            }
        }

        android.content.ServiceConnection connection = new android.content.ServiceConnection() {
            @Override
            public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
                android.os.Messenger mService = new android.os.Messenger(service);
                try {
                    org.json.JSONObject printRequest = new org.json.JSONObject();
                    
                    org.json.JSONObject header = new org.json.JSONObject();
                    header.put("ApplicationId", "8754f022bd7f475a9f29284a656d3401"); 
                    header.put("MethodId", "1002"); // PrintData method ID
                    header.put("VersionNo", "1.0");
                    printRequest.put("Header", header);

                    org.json.JSONObject detail = new org.json.JSONObject();
                    detail.put("PrintRefNo", String.valueOf(System.currentTimeMillis()));     
                    detail.put("SavePrintData", true); 
                    
                    detail.put("Data", printData); // Pass the array

                    printRequest.put("Detail", detail);

                    android.os.Message message = android.os.Message.obtain(null, 1001); // PrintData method code
                    android.os.Bundle data = new android.os.Bundle();
                    data.putString("MASTERAPPREQUEST", printRequest.toString());
                    message.setData(data);
                    message.replyTo = new android.os.Messenger(new IncomingHandlerPrint(android.os.Looper.getMainLooper()));

                    mService.send(message);
                    if (callback != null) callback.onComplete(true, "Sent to PineLabs");
                } catch (Exception e) {
                    if (callback != null) callback.onComplete(false, "PineLabs Error: " + e.getMessage());
                } finally {
                    activity.unbindService(this);
                }
            }

            @Override
            public void onServiceDisconnected(android.content.ComponentName name) {
            }
        };

        boolean bindResult = activity.bindService(intent, connection, android.content.Context.BIND_AUTO_CREATE);
        if (!bindResult) {
            if (callback != null) callback.onComplete(false, "Failed to bind to PineLabs MasterApp");
        }
    }
}
