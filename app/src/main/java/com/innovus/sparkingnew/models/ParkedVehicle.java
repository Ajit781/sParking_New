package com.innovus.sparkingnew.models;

import org.json.JSONObject;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Model representing a vehicle checked into the parking lot.
 */
public class ParkedVehicle implements Serializable {

    private int slotId;
    private int ownerId;
    private int agencyId;
    private String slotName;
    private int bookingId;
    private String bookingNo;
    private String ownerName;
    private int vehicleId;
    private String agencyCode;
    private String agencyName;
    private double fineAmount;
    private String checkinTime;
    private String paymentMode;
    private double promoAmount;
    private String vehicleType;
    private String vehicleNumber;
    private int parkingAreaId;
    private int paymentModeId;
    private int vehicleTypeId;
    private String ownerContactNo;
    private String parkingAreaCode;
    private String vehicleTypeIcon;
    private String alternateMobileNo;
    private String status = "PARKED";

    public ParkedVehicle() {}

    public static ParkedVehicle fromJson(JSONObject json) {
        ParkedVehicle v = new ParkedVehicle();
        if (json == null) return v;

        v.slotId = json.optInt("slot_id", 0);
        v.ownerId = json.optInt("owner_id", 0);
        v.agencyId = json.optInt("agency_id", 0);
        v.slotName = json.optString("slot_name", "");
        v.bookingId = json.optInt("booking_id", 0);
        v.bookingNo = json.optString("booking_no", "");
        v.ownerName = json.optString("owner_name", "");
        v.vehicleId = json.optInt("vehicle_id", 0);
        v.agencyCode = json.optString("agency_code", "");
        v.agencyName = json.optString("agency_name", "");
        v.fineAmount = json.optDouble("fine_amount", 0.0);
        v.checkinTime = json.optString("checkin_time", "");
        v.paymentMode = json.optString("payment_mode", "");
        v.promoAmount = json.optDouble("promo_amount", 0.0);
        v.vehicleType = json.optString("vehicle_type", "Four Wheeler");
        v.vehicleNumber = json.optString("vehicle_number", "");
        v.parkingAreaId = json.optInt("parking_area_id", 0);
        v.paymentModeId = json.optInt("payment_mode_id", 1);
        v.vehicleTypeId = json.optInt("vehicle_type_id", 0);
        v.ownerContactNo = json.optString("owner_contact_no", "");
        v.parkingAreaCode = json.optString("parking_area_code", "");
        v.vehicleTypeIcon = json.optString("vehicle_type_icon", "");
        v.alternateMobileNo = json.optString("alternate_mobile_no", "");
        v.status = "PARKED";

        return v;
    }

    public int getSlotId() { return slotId; }
    public String getSlotName() { return slotName != null ? slotName : ""; }
    public int getBookingId() { return bookingId; }
    public String getBookingNo() { return bookingNo != null ? bookingNo : ""; }
    public String getOwnerName() { return ownerName != null ? ownerName : ""; }
    public int getVehicleId() { return vehicleId; }
    public String getCheckinTime() { return checkinTime != null ? checkinTime : ""; }
    public String getVehicleType() { return vehicleType != null ? vehicleType : "Vehicle"; }
    public String getVehicleNumber() { return vehicleNumber != null ? vehicleNumber : ""; }
    public int getParkingAreaId() { return parkingAreaId; }
    public int getPaymentModeId() { return paymentModeId; }
    public String getOwnerContactNo() { 
        if (ownerContactNo != null && !ownerContactNo.trim().isEmpty()) return ownerContactNo.trim();
        if (alternateMobileNo != null && !alternateMobileNo.trim().isEmpty()) return alternateMobileNo.trim();
        return ""; 
    }
    public String getOwnerMobile() {
        return getOwnerContactNo();
    }
    public String getParkingAreaCode() { return parkingAreaCode != null ? parkingAreaCode : ""; }
    public String getAlternateMobileNo() { return alternateMobileNo != null ? alternateMobileNo : ""; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isTwoWheeler() {
        if (vehicleType == null) return false;
        String lower = vehicleType.toLowerCase(Locale.ROOT);
        return lower.contains("two") || lower.contains("2") || vehicleTypeId == 1;
    }

    /**
     * Formats ISO timestamp (e.g. 2026-09-07T13:04:32.416523) to clean readable time.
     */
    public String getFormattedTime() {
        if (checkinTime == null || checkinTime.isEmpty()) return "";
        try {
            String clean = checkinTime;
            if (clean.contains(".")) {
                clean = clean.substring(0, clean.indexOf('.'));
            }
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date date = parser.parse(clean);
            if (date != null) {
                return new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date);
            }
        } catch (Exception ignored) {}
        return checkinTime;
    }
}
