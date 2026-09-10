package com.innovus.sparkingnew.printer;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.eze.api.EzeAPI;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.pax.dal.entity.EFontTypeAscii;
import com.pax.dal.entity.EFontTypeExtCode;
import com.innovus.sparkingnew.session.SessionManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Unified thermal receipt printer supporting both PAX Neptune and PineLabs / EzeAPI POS terminals.
 * Ported directly from SParkingAgent (VehicleInfoScanActivity.java).
 */
public class ReceiptPrinter {

    private static final String TAG = "ReceiptPrinter";
    public static final int REQUEST_CODE_INITIALIZE = 10001;
    public static final int REQUEST_CODE_PRINT_BITMAP = 10029;

    // Stored callback for EzeAPI path — called from onActivityResult, NOT immediately
    private static PrintCallback pendingEzeCallback = null;

    public interface PrintCallback {
        void onComplete(boolean success, String statusMessage);
    }

    /**
     * Call this from Activity.onActivityResult() to deliver EzeAPI print result back to callback.
     * SParkingAgent pattern: result is parsed from intent extra "response".
     */
    public static void handlePrintActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode != REQUEST_CODE_PRINT_BITMAP) return;
        PrintCallback cb = pendingEzeCallback;
        pendingEzeCallback = null;

        String successMsg = "Print successful";
        String failMsg = "Print failed";

        try {
            if (data != null && data.hasExtra("response")) {
                String responseStr = data.getStringExtra("response");
                Log.i(TAG, "EzeAPI result -> code=" + resultCode + " | response=" + responseStr);
                if (resultCode == android.app.Activity.RESULT_OK) {
                    Log.i(TAG, "EzeAPI Print SUCCESS");
                    if (cb != null) cb.onComplete(true, successMsg);
                } else {
                    org.json.JSONObject response = new org.json.JSONObject(responseStr);
                    org.json.JSONObject error = response.optJSONObject("error");
                    String errorCode = (error != null) ? error.optString("code", "UNKNOWN") : "UNKNOWN";
                    String errorMessage = (error != null) ? error.optString("message", failMsg) : failMsg;
                    Log.e(TAG, "EzeAPI Print FAILED -> Code: " + errorCode + " | " + errorMessage);
                    if (cb != null) cb.onComplete(false, "Print Error [" + errorCode + "]: " + errorMessage);
                }
            } else {
                Log.w(TAG, "EzeAPI onActivityResult -> resultCode=" + resultCode + " | no response extra");
                if (cb != null) cb.onComplete(resultCode == android.app.Activity.RESULT_OK, resultCode == android.app.Activity.RESULT_OK ? successMsg : "Print cancelled");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing EzeAPI result: " + e.getMessage());
            if (cb != null) cb.onComplete(false, "Print result parse error: " + e.getMessage());
        }
    }

    /**
     * Prints a check-in ticket. Automatically detects whether PAX Neptune hardware or
     * PineLabs/Verifone EzeAPI is available on the device.
     * Detection runs on a background thread to avoid blocking the UI.
     */
    public static void printCheckInTicket(Activity activity,
                                          String vehicleNumber,
                                          String checkInTime,
                                          String bookingNumber,
                                          String vehicleType,
                                          String parkingLocation,
                                          String slotName,
                                          PrintCallback callback) {

        if (activity == null || activity.isFinishing()) {
            if (callback != null) callback.onComplete(false, "Activity is no longer active");
            return;
        }

        final Handler mainHandler = new Handler(Looper.getMainLooper());

        final String finalTime = (checkInTime != null && !checkInTime.trim().isEmpty())
                ? checkInTime.trim()
                : new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(new Date());

        final String finalLoc = (parkingLocation != null && !parkingLocation.trim().isEmpty())
                ? parkingLocation.trim()
                : "sParking Station";

        final String finalVehicle = vehicleNumber != null ? vehicleNumber.trim().toUpperCase(Locale.getDefault()) : "";
        final String finalBooking = (bookingNumber != null && !bookingNumber.trim().isEmpty()) ? bookingNumber.trim() : "N/A";
        final String finalType = (vehicleType != null && !vehicleType.trim().isEmpty()) ? vehicleType.trim() : "Vehicle";
        final String finalSlot = (slotName != null && !slotName.trim().isEmpty()) ? slotName.trim() : "";

        // Run printer detection on a background thread to avoid blocking UI
        new Thread(() -> {
            try {
                // Check 1: PAX Neptune hardware printer
                PrinterTester paxPrinter = PrinterTester.getInstance(activity);
                if (paxPrinter.isPrinterAvailable()) {
                    Log.i(TAG, "PAX Neptune printer detected. Starting print...");
                    printOnPax(activity, paxPrinter, finalVehicle, finalTime, finalBooking, finalType, finalLoc, finalSlot, callback, mainHandler);
                    return;
                }

                // Check 2: PineLabs / EzeAPI POS terminal
                Log.i(TAG, "PAX not detected. Attempting print via PineLabs / EzeAPI...");
                mainHandler.post(() -> {
                    try {
                        printOnEzeApi(activity, finalVehicle, finalTime, finalBooking, finalType, finalLoc, finalSlot, callback);
                    } catch (Throwable t) {
                        Log.e(TAG, "Error invoking EzeAPI printing: " + t.getMessage(), t);
                        String msg = "Thermal printer not detected on this device";
                        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                        if (callback != null) callback.onComplete(false, msg);
                    }
                });

            } catch (Throwable t) {
                Log.e(TAG, "Printer detection failed: " + t.getMessage(), t);
                mainHandler.post(() -> {
                    String msg = "Print error: " + t.getMessage();
                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                    if (callback != null) callback.onComplete(false, msg);
                });
            }
        }).start();
    }

    /**
     * Prints ticket using PAX Neptune POS Hardware directly.
     * Ported line-by-line from SParkingAgent's VehicleInfoScanActivity.printEazytapBill()
     */
    private static void printOnPax(Activity activity,
                                   PrinterTester printer,
                                   String vehicleNumber,
                                   String checkInTime,
                                   String bookingNumber,
                                   String vehicleType,
                                   String parkingLocation,
                                   String slotName,
                                   PrintCallback callback,
                                   Handler mainHandler) {

        new Thread(() -> {
            try {
                printer.init();
                printer.fontSet(EFontTypeAscii.FONT_16_32, EFontTypeExtCode.FONT_16_16);
                printer.setGray(30);

                // Header lines
                StringBuilder printBill = new StringBuilder();
                printBill.append(PrinterUtils.paddingCenter("sParking", PrinterUtils.PAGE_WIDTH_TWO_INCH)).append("\n");
                printer.printStr(printBill.toString(), null);
                printer.step(2);

                printBill = new StringBuilder();
                printBill.append(PrinterUtils.paddingCenter("www.s-parking.com", PrinterUtils.PAGE_WIDTH_TWO_INCH_SMALL)).append("\n");
                printer.printStr(printBill.toString(), null);

                if (parkingLocation != null && !parkingLocation.isEmpty()) {
                    printBill = new StringBuilder();
                    printBill.append(PrinterUtils.paddingCenter(parkingLocation, PrinterUtils.PAGE_WIDTH_TWO_INCH_SMALL)).append("\n");
                    printer.printStr(printBill.toString(), null);
                }

                // Vehicle details
                printer.leftIndents((short) 10);
                printer.fontSet(EFontTypeAscii.FONT_8_32, EFontTypeExtCode.FONT_16_32);
                printer.printStr("Vehicle No    : " + vehicleNumber + "\n", null);
                printer.printStr("CheckIn Time  : " + checkInTime + "\n", null);
                printer.printStr("Booking No    : " + bookingNumber + "\n", null);
                if (!slotName.isEmpty()) {
                    printer.printStr("Slot No       : " + slotName + "\n", null);
                }
                printer.leftIndents((short) 10);

                // QR Code (Format: VehicleNo##CheckInTime##BookingNo##VehicleType)
                String qrData = vehicleNumber + "##" + checkInTime + "##" + bookingNumber + "##" + vehicleType;
                QRCodeWriter writer = new QRCodeWriter();
                try {
                    BitMatrix bitMatrix = writer.encode(qrData, BarcodeFormat.QR_CODE, 200, 200);
                    int width = bitMatrix.getWidth();
                    int height = bitMatrix.getHeight();
                    Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                    for (int x = 0; x < width; x++) {
                        for (int y = 0; y < height; y++) {
                            bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                        }
                    }

                    Bitmap bitmap = PrinterUtils.pad(bmp, 100, 0);
                    if (bitmap != null) {
                        printer.printBitmap(bitmap);

                        printBill = new StringBuilder();
                        printBill.append(PrinterUtils.paddingCenter("Download s-Parking App from Play Store", PrinterUtils.PAGE_WIDTH_TWO_INCH_SMALL_BOTTOM)).append("\n");
                        printer.printStr(printBill.toString(), null);
                        printer.printStr("\n\n", null);
                        printer.step(60);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "QR generation failed", e);
                }

                printer.step(2);
                final String status = printer.start();
                Log.i(TAG, "PAX Print completed with status: " + status);
                boolean isSuccess = "Success".equalsIgnoreCase(status);

                mainHandler.post(() -> {
                    if (isSuccess) {
                        Toast.makeText(activity, "Print: Success", Toast.LENGTH_SHORT).show();
                        if (callback != null) callback.onComplete(true, status);
                    } else {
                        String errMsg = (status != null && !status.trim().isEmpty()) ? status : "Printer error";
                        Toast.makeText(activity, "Printer: " + errMsg, Toast.LENGTH_SHORT).show();
                        if (callback != null) callback.onComplete(false, errMsg);
                    }
                });

            } catch (Throwable e) {
                Log.e(TAG, "PAX Printing failed: " + e.getMessage(), e);
                if (e instanceof UnsatisfiedLinkError || e instanceof NoClassDefFoundError) {
                    mainHandler.post(() -> {
                        try {
                            Log.i(TAG, "Non-PAX hardware: Switching to EzeAPI fallback...");
                            printOnEzeApi(activity, vehicleNumber, checkInTime, bookingNumber,
                                    vehicleType, parkingLocation, slotName, callback);
                        } catch (Throwable t2) {
                            Log.e(TAG, "EzeAPI fallback also failed: " + t2.getMessage());
                            String msg = "Printer not available on this device";
                            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                            if (callback != null) callback.onComplete(false, msg);
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        String msg = "Printer: " + (e.getMessage() != null ? e.getMessage() : "Print error");
                        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                        if (callback != null) callback.onComplete(false, msg);
                    });
                }
            }
        }).start();
    }

    /**
     * Prints ticket using PineLabs / Verifone EzeAPI by drawing a canvas bitmap and invoking EzeAPI.printBitmap.
     * Ported directly from SParkingAgent's VehicleInfoScanActivity.printEazytapBillNew()
     */
    private static void printOnEzeApi(Activity activity,
                                      String vehicleNumber,
                                      String checkInTime,
                                      String bookingNumber,
                                      String vehicleType,
                                      String parkingLocation,
                                      String slotName,
                                      PrintCallback callback) throws Exception {

        String[] arrLocName = PrinterUtils.breakStringToLines(parkingLocation, 35);
        int baseHeight = 490;
        if (!slotName.isEmpty()) baseHeight += 24;
        int bitmapHeight = baseHeight + (Math.max(0, arrLocName.length - 1) * 24);

        Bitmap bitmap = Bitmap.createBitmap(400, bitmapHeight, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);

        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(0, 0, 0));
        Rect bounds = new Rect();

        // Line 1: Header "sParking"
        paint.setTextSize(24);
        String strText = "sParking";
        paint.getTextBounds(strText, 0, strText.length(), bounds);
        int x = (bitmap.getWidth() - bounds.width()) / 2;
        int y = 30;
        canvas.drawText(strText, x, y, paint);

        // Line 2: URL "www.s-parking.com"
        paint.setTextSize(20);
        strText = "www.s-parking.com";
        paint.getTextBounds(strText, 0, strText.length(), bounds);
        x = (bitmap.getWidth() - bounds.width()) / 2;
        y += 24;
        canvas.drawText(strText, x, y, paint);

        // Line 3: Location Name lines
        paint.setTextSize(20);
        for (String strLocName : arrLocName) {
            if (strLocName != null && !strLocName.trim().isEmpty()) {
                paint.getTextBounds(strLocName, 0, strLocName.length(), bounds);
                x = (bitmap.getWidth() - bounds.width()) / 2;
                y += 24;
                canvas.drawText(strLocName, x, y, paint);
            }
        }

        // Line 4: Vehicle No  (y += 62 — same as SParkingAgent)
        paint.setTextSize(20);
        strText = "Vehicle No : " + vehicleNumber;
        paint.getTextBounds(strText, 0, strText.length(), bounds);
        x = (bitmap.getWidth() - bounds.width()) / 2;
        y += 62;
        canvas.drawText(strText, x, y, paint);

        // Line 5: CheckIn Time — trim seconds like SParkingAgent (remove last 3 chars ":SS")
        String displayTime = checkInTime;
        if (displayTime != null && displayTime.length() > 3) {
            displayTime = displayTime.substring(0, displayTime.length() - 3);
        }
        paint.setTextSize(20);
        strText = "CheckIn Time : " + displayTime;
        paint.getTextBounds(strText, 0, strText.length(), bounds);
        x = (bitmap.getWidth() - bounds.width()) / 2;
        y += 24;
        canvas.drawText(strText, x, y, paint);

        // Line 6: Booking No
        paint.setTextSize(20);
        strText = "Booking No : " + bookingNumber;
        paint.getTextBounds(strText, 0, strText.length(), bounds);
        x = (bitmap.getWidth() - bounds.width()) / 2;
        y += 24;
        canvas.drawText(strText, x, y, paint);

        // Optional Line: Slot No
        if (!slotName.isEmpty()) {
            paint.setTextSize(20);
            strText = "Slot : " + slotName;
            paint.getTextBounds(strText, 0, strText.length(), bounds);
            x = (bitmap.getWidth() - bounds.width()) / 2;
            y += 24;
            canvas.drawText(strText, x, y, paint);
        }

        // Line 7: QR Code — 250x250, xOffset=75 (same as SParkingAgent)
        String qrData = vehicleNumber + "##" + displayTime + "##" + bookingNumber + "##" + vehicleType;
        y += 20;
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(qrData, BarcodeFormat.QR_CODE, 250, 250);
        int qrW = bitMatrix.getWidth();
        int qrH = bitMatrix.getHeight();
        for (int xCount = 0; xCount < qrW; xCount++) {
            for (int yCount = 0; yCount < qrH; yCount++) {
                bitmap.setPixel(xCount + 75, yCount + y, bitMatrix.get(xCount, yCount) ? Color.BLACK : Color.WHITE);
            }
        }

        // Line 8: Footer — y += 275 (same as SParkingAgent)
        paint.setTextSize(20);
        strText = "Download s-Parking from Play Store";
        paint.getTextBounds(strText, 0, strText.length(), bounds);
        x = (bitmap.getWidth() - bounds.width()) / 2;
        y += 275;
        canvas.drawText(strText, x, y, paint);

        // Encode bitmap — Base64.DEFAULT (same as SParkingAgent)
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        String encodedImageData = Base64.encodeToString(byteArray, Base64.DEFAULT);

        JSONObject jsonRequest = new JSONObject();
        JSONObject jsonImageObj = new JSONObject();
        jsonImageObj.put("imageData", encodedImageData);
        jsonImageObj.put("imageType", "JPEG");
        jsonRequest.put("image", jsonImageObj);

        Log.i(TAG, "Sending EzeAPI.printBitmap intent...");

        // Save callback — do NOT call it now!
        // EzeAPI launches a new Activity via startActivityForResult.
        // Result will come back in onActivityResult → call handlePrintActivityResult() → callback fires.
        pendingEzeCallback = callback;

        EzeAPI.printBitmap(activity, REQUEST_CODE_PRINT_BITMAP, jsonRequest);
    }

    /**
     * Prints a check-out receipt ticket with duration, tariff, amount paid, and status.
     */
    public static void printCheckOutTicket(Activity activity,
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
                                           PrintCallback callback) {

        if (activity == null || activity.isFinishing()) {
            if (callback != null) callback.onComplete(false, "Activity is no longer active");
            return;
        }

        final Handler mainHandler = new Handler(Looper.getMainLooper());

        final String finalInTime = (checkInTime != null && !checkInTime.trim().isEmpty())
                ? checkInTime.trim() : "N/A";
        final String finalOutTime = (checkOutTime != null && !checkOutTime.trim().isEmpty())
                ? checkOutTime.trim()
                : new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(new Date());

        final String finalLoc = (parkingLocation != null && !parkingLocation.trim().isEmpty())
                ? parkingLocation.trim() : "sParking Station";

        final String finalVehicle = vehicleNumber != null ? vehicleNumber.trim().toUpperCase(Locale.getDefault()) : "";
        final String finalBooking = (bookingNumber != null && !bookingNumber.trim().isEmpty()) ? bookingNumber.trim() : "N/A";
        final String finalType = (vehicleType != null && !vehicleType.trim().isEmpty()) ? vehicleType.trim() : "Vehicle";
        final String finalSlot = (slotName != null && !slotName.trim().isEmpty()) ? slotName.trim() : "";
        final String finalMode = (paymentModeName != null && !paymentModeName.trim().isEmpty()) ? paymentModeName.trim() : "CASH";
        final String finalTxn = (transactionId != null && !transactionId.trim().isEmpty()) ? transactionId.trim() : "0";

        new Thread(() -> {
            try {
                PrinterTester paxPrinter = PrinterTester.getInstance(activity);
                if (paxPrinter.isPrinterAvailable()) {
                    Log.i(TAG, "PAX Neptune printer detected for checkout print...");
                    printCheckOutOnPax(activity, paxPrinter, finalVehicle, finalInTime, finalOutTime, finalBooking, finalType, finalLoc, finalSlot, totalHours, hourlyRate, totalAmount, finalMode, finalTxn, callback, mainHandler);
                    return;
                }

                Log.i(TAG, "PAX not detected. Attempting checkout print via PineLabs / EzeAPI...");
                mainHandler.post(() -> {
                    try {
                        printCheckOutOnEzeApi(activity, finalVehicle, finalInTime, finalOutTime, finalBooking, finalType, finalLoc, finalSlot, totalHours, hourlyRate, totalAmount, finalMode, finalTxn, callback);
                    } catch (Throwable t) {
                        Log.e(TAG, "Error invoking EzeAPI checkout printing: " + t.getMessage(), t);
                        String msg = "Thermal printer not detected on this device";
                        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                        if (callback != null) callback.onComplete(false, msg);
                    }
                });

            } catch (Throwable t) {
                Log.e(TAG, "Checkout printer detection failed: " + t.getMessage(), t);
                mainHandler.post(() -> {
                    String msg = "Print error: " + t.getMessage();
                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                    if (callback != null) callback.onComplete(false, msg);
                });
            }
        }).start();
    }

    /**
     * Backwards-compatible overload for checkout printing.
     */
    public static void printCheckOutTicket(Activity activity,
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
                                           PrintCallback callback) {
        printCheckOutTicket(activity, vehicleNumber, checkInTime, checkOutTime, bookingNumber, vehicleType,
                parkingLocation, slotName, totalHours, hourlyRate, totalAmount, "CASH", "0", callback);
    }

    private static String formatReceiptTime(String time) {
        if (time == null) return "";
        String t = time.trim().replace("T", " ");
        if (t.contains(".")) {
            t = t.substring(0, t.indexOf("."));
        }
        return t;
    }

    private static String formatAmount(double amount) {
        if (amount == (long) amount) {
            return String.valueOf((long) amount);
        } else {
            return String.format(Locale.getDefault(), "%.2f", amount);
        }
    }

    /**
     * Prints checkout tax invoice ticket via PAX Neptune thermal printer.
     * Formatted line-by-line identical to SParkingAgent (BillGenerateActivity.printEazytapBill).
     */
    private static void printCheckOutOnPax(Activity activity,
                                          PrinterTester printer,
                                          String vehicleNumber,
                                          String inTime,
                                          String outTime,
                                          String bookingNumber,
                                          String vehicleType,
                                          String parkingLocation,
                                          String slotName,
                                          int totalHours,
                                          double hourlyRate,
                                          double totalAmount,
                                          String paymentModeName,
                                          String transactionId,
                                          PrintCallback callback,
                                          Handler mainHandler) {
        new Thread(() -> {
            try {
                String cleanIn = formatReceiptTime(inTime);
                String cleanOut = formatReceiptTime(outTime);
                String payAmountStr = formatAmount(totalAmount);

                String gstNo = "";
                try {
                    SessionManager session = SessionManager.getInstance(activity);
                    if (session != null && session.getUserData() != null) {
                        gstNo = session.getUserData().getAgencyGstNo();
                    }
                } catch (Exception ignored) {}

                printer.init();
                printer.fontSet(EFontTypeAscii.FONT_16_32, EFontTypeExtCode.FONT_16_16);
                printer.setGray(30);

                // 1. Tax Invoice (Center)
                StringBuilder printBill = new StringBuilder();
                printBill.append(PrinterUtils.paddingCenter("Tax Invoice", PrinterUtils.PAGE_WIDTH_TWO_INCH)).append("\n");
                printer.printStr(printBill.toString(), null);
                printer.step(2);

                // 2. Parking Maintenance Charge (Center)
                printBill = new StringBuilder();
                printBill.append(PrinterUtils.paddingCenter("Parking Maintenance Charge", PrinterUtils.PAGE_WIDTH_TWO_INCH_SMALL)).append("\n");
                printer.printStr(printBill.toString(), null);

                // 3. Location (Center)
                if (parkingLocation != null && !parkingLocation.isEmpty()) {
                    printBill = new StringBuilder();
                    printBill.append(PrinterUtils.paddingCenter(parkingLocation, PrinterUtils.PAGE_WIDTH_TWO_INCH_SMALL)).append("\n");
                    printer.printStr(printBill.toString(), null);
                }

                // 4. Vehicle Details (Left Indent 10)
                printer.leftIndents((short) 10);
                printer.fontSet(EFontTypeAscii.FONT_8_32, EFontTypeExtCode.FONT_16_32);
                printer.printStr("Vehicle No   : " + vehicleNumber + "\n", null);
                printer.printStr("Booking No   : " + bookingNumber + "\n", null);
                printer.printStr("In Time      : " + cleanIn + "\n", null);
                printer.printStr("Out Time     : " + cleanOut + "\n", null);
                printer.printStr("Duration     : " + totalHours + " Hr(s)\n", null);
                printer.printStr("Amount       : Rs. " + payAmountStr + "\n", null);
                printer.printStr("Fine Amount  : Rs. 0\n", null);
                printer.printStr("Discount     : Rs. 0\n", null);
                printer.printStr("--------------------------------\n", null);
                printer.printStr("Pay " + paymentModeName + "    Rs. " + payAmountStr + "\n", null);
                if (transactionId != null && !transactionId.isEmpty() && !"0".equals(transactionId)) {
                    printer.printStr("Txn ID       : " + transactionId + "\n", null);
                }

                // 5. GST and Footer (Center)
                printBill = new StringBuilder();
                if (gstNo != null && !gstNo.trim().isEmpty()) {
                    printBill.append(PrinterUtils.paddingCenter("Inclusive of GST @ 18%", PrinterUtils.PAGE_WIDTH_TWO_INCH_SMALL_BOTTOM)).append("\n");
                    printBill.append(PrinterUtils.paddingCenter("GSTIN:" + gstNo.trim(), PrinterUtils.PAGE_WIDTH_TWO_INCH_SMALL_BOTTOM)).append("\n");
                }
                printBill.append(PrinterUtils.paddingCenter("Download s-Parking from Play Store", PrinterUtils.PAGE_WIDTH_TWO_INCH_SMALL_BOTTOM)).append("\n");
                printer.printStr(printBill.toString(), null);
                printer.printStr("\n\n\n", null);
                printer.step(2);

                final String status = printer.start();
                Log.i(TAG, "PAX Checkout print result status: " + status);
                boolean isSuccess = "Success".equalsIgnoreCase(status);

                mainHandler.post(() -> {
                    if (isSuccess) {
                        Toast.makeText(activity, "Print: Success", Toast.LENGTH_SHORT).show();
                        if (callback != null) callback.onComplete(true, "Checkout receipt printed successfully");
                    } else {
                        String errMsg = (status != null && !status.trim().isEmpty()) ? status : "Printer error";
                        Toast.makeText(activity, "Printer: " + errMsg, Toast.LENGTH_SHORT).show();
                        if (callback != null) callback.onComplete(false, "Printer error: " + errMsg);
                    }
                });

            } catch (Throwable t) {
                Log.e(TAG, "PAX Checkout printing failed: " + t.getMessage(), t);
                if (t instanceof UnsatisfiedLinkError || t instanceof NoClassDefFoundError) {
                    mainHandler.post(() -> {
                        try {
                            Log.i(TAG, "Non-PAX hardware: Switching to EzeAPI checkout fallback...");
                            printCheckOutOnEzeApi(activity, vehicleNumber, inTime, outTime, bookingNumber, vehicleType, parkingLocation, slotName, totalHours, hourlyRate, totalAmount, paymentModeName, transactionId, callback);
                        } catch (Throwable ezeErr) {
                            if (callback != null) callback.onComplete(false, "Printing error: " + t.getMessage());
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        String msg = "Printer: " + (t.getMessage() != null ? t.getMessage() : "Print error");
                        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                        if (callback != null) callback.onComplete(false, msg);
                    });
                }
            }
        }).start();
    }

    /**
     * Prints checkout tax invoice ticket via PineLabs / EzeAPI POS terminal bitmap printing.
     * Formatted line-by-line identical to SParkingAgent (BillGenerateActivity.printEazytapBillNew).
     */
    private static void printCheckOutOnEzeApi(Activity activity,
                                             String vehicleNumber,
                                             String inTime,
                                             String outTime,
                                             String bookingNumber,
                                             String vehicleType,
                                             String parkingLocation,
                                             String slotName,
                                             int totalHours,
                                             double hourlyRate,
                                             double totalAmount,
                                             String paymentModeName,
                                             String transactionId,
                                             PrintCallback callback) throws Exception {

        String cleanIn = formatReceiptTime(inTime);
        String cleanOut = formatReceiptTime(outTime);
        String payAmountStr = formatAmount(totalAmount);

        String gstNo = "";
        try {
            SessionManager session = SessionManager.getInstance(activity);
            if (session != null && session.getUserData() != null) {
                gstNo = session.getUserData().getAgencyGstNo();
            }
        } catch (Exception ignored) {}

        Integer iBitmapBaseHeight = 500;
        if (gstNo == null || gstNo.trim().isEmpty()) {
            iBitmapBaseHeight -= 24;
        }

        String[] arrLocName = PrinterUtils.breakStringToLines(parkingLocation, 35);
        Bitmap bitmap;
        if (arrLocName.length <= 1) {
            bitmap = Bitmap.createBitmap(400, iBitmapBaseHeight, Bitmap.Config.ARGB_8888);
        } else {
            Integer bitmapHeight = iBitmapBaseHeight + ((arrLocName.length - 1) * 24);
            bitmap = Bitmap.createBitmap(400, bitmapHeight, Bitmap.Config.ARGB_8888);
        }
        bitmap.eraseColor(Color.WHITE);

        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(0, 0, 0));
        Rect bounds = new Rect();

        // Line 1: Tax Invoice (size 24, Center)
        paint.setTextSize(24);
        String strText = "Tax Invoice";
        paint.getTextBounds(strText, 0, strText.length(), bounds);
        int x = (bitmap.getWidth() - bounds.width()) / 2;
        int y = 30;
        canvas.drawText(strText, x, y, paint);

        // Line 2: Parking Maintenance Charge (size 20, Center)
        paint.setTextSize(20);
        strText = "Parking Maintenance Charge";
        paint.getTextBounds(strText, 0, strText.length(), bounds);
        x = (bitmap.getWidth() - bounds.width()) / 2;
        y += 24;
        canvas.drawText(strText, x, y, paint);

        // Line 3: Location lines (size 20, Center)
        paint.setTextSize(20);
        for (String strLocName : arrLocName) {
            if (strLocName != null && !strLocName.trim().isEmpty()) {
                paint.getTextBounds(strLocName, 0, strLocName.length(), bounds);
                x = (bitmap.getWidth() - bounds.width()) / 2;
                y += 24;
                canvas.drawText(strLocName, x, y, paint);
            }
        }

        // Line 4: Vehicle No (size 20, x=35, y+=62)
        paint.setTextSize(20);
        strText = "Vehicle No    : " + vehicleNumber;
        paint.getTextBounds(strText, 0, strText.length(), bounds);
        x = 35;
        y += 62;
        canvas.drawText(strText, x, y, paint);

        // Line 5: Booking No (size 20, x=35, y+=24)
        paint.setTextSize(20);
        strText = "Booking No   : " + bookingNumber;
        paint.getTextBounds(strText, 0, strText.length(), bounds);
        x = 35;
        y += 24;
        canvas.drawText(strText, x, y, paint);

        // Line 6: In Time (size 20, x=35, y+=24)
        paint.setTextSize(20);
        strText = "In Time          : " + cleanIn;
        paint.getTextBounds(strText, 0, strText.length(), bounds);
        x = 35;
        y += 24;
        canvas.drawText(strText, x, y, paint);

        // Line 7: Out Time (size 20, x=35, y+=24)
        paint.setTextSize(20);
        strText = "Out Time       : " + cleanOut;
        paint.getTextBounds(strText, 0, strText.length(), bounds);
        x = 35;
        y += 24;
        canvas.drawText(strText, x, y, paint);

        // Line 8: Duration (size 20, x=35, y+=24)
        paint.setTextSize(20);
        strText = "Duration        : " + totalHours + " Hr(s)";
        paint.getTextBounds(strText, 0, strText.length(), bounds);
        x = 35;
        y += 24;
        canvas.drawText(strText, x, y, paint);

        // Line 9: Amount (size 20, x=35, y+=24)
        paint.setTextSize(20);
        strText = "Amount         : Rs. " + payAmountStr;
        paint.getTextBounds(strText, 0, strText.length(), bounds);
        x = 35;
        y += 24;
        canvas.drawText(strText, x, y, paint);

        // Line 10: Fine Amount (size 20, x=35, y+=24)
        paint.setTextSize(20);
        strText = "Fine Amount : Rs. 0";
        paint.getTextBounds(strText, 0, strText.length(), bounds);
        x = 35;
        y += 24;
        canvas.drawText(strText, x, y, paint);

        // Line 11: Discount (size 20, x=35, y+=24)
        paint.setTextSize(20);
        strText = "Discount        : Rs. 0";
        paint.getTextBounds(strText, 0, strText.length(), bounds);
        x = 35;
        y += 24;
        canvas.drawText(strText, x, y, paint);

        // Line 12: Separator line (size 20, x=35, y+=24)
        paint.setTextSize(20);
        strText = "-------------------------------------------------------";
        paint.getTextBounds(strText, 0, strText.length(), bounds);
        x = 35;
        y += 24;
        canvas.drawText(strText, x, y, paint);

        // Line 13: Pay PaymentMode : Rs. Amount (size 20, x=35, y+=24)
        paint.setTextSize(20);
        strText = "Pay " + paymentModeName + "       : Rs. " + payAmountStr;
        paint.getTextBounds(strText, 0, strText.length(), bounds);
        x = 35;
        y += 24;
        canvas.drawText(strText, x, y, paint);

        // Optional Txn ID
        if (transactionId != null && !transactionId.isEmpty() && !"0".equals(transactionId)) {
            paint.setTextSize(18);
            strText = "Txn ID          : " + transactionId;
            paint.getTextBounds(strText, 0, strText.length(), bounds);
            x = 35;
            y += 22;
            canvas.drawText(strText, x, y, paint);
        }

        // GST lines (if GSTN available)
        if (gstNo != null && !gstNo.trim().isEmpty()) {
            paint.setTextSize(20);
            strText = "Inclusive of GST @ 18%";
            paint.getTextBounds(strText, 0, strText.length(), bounds);
            x = (bitmap.getWidth() - bounds.width()) / 2;
            y += 62;
            canvas.drawText(strText, x, y, paint);

            paint.setTextSize(20);
            strText = "GSTN : " + gstNo.trim();
            paint.getTextBounds(strText, 0, strText.length(), bounds);
            x = (bitmap.getWidth() - bounds.width()) / 2;
            y += 24;
            canvas.drawText(strText, x, y, paint);
        }

        // Footer line
        paint.setTextSize(20);
        strText = "Download s-Parking from Play Store";
        paint.getTextBounds(strText, 0, strText.length(), bounds);
        x = (bitmap.getWidth() - bounds.width()) / 2;
        y += 24;
        canvas.drawText(strText, x, y, paint);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        String encodedImageData = Base64.encodeToString(byteArray, Base64.DEFAULT);

        JSONObject jsonRequest = new JSONObject();
        JSONObject jsonImageObj = new JSONObject();
        jsonImageObj.put("imageData", encodedImageData);
        jsonImageObj.put("imageType", "JPEG");
        jsonRequest.put("image", jsonImageObj);

        Log.i(TAG, "Sending EzeAPI.printBitmap intent for checkout (matching SParkingAgent format)...");
        pendingEzeCallback = callback;
        EzeAPI.printBitmap(activity, REQUEST_CODE_PRINT_BITMAP, jsonRequest);
    }
}
