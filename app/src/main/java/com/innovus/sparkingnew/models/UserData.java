package com.innovus.sparkingnew.models;

import org.json.JSONObject;

import java.io.Serializable;

/**
 * Data model for logged-in agent / user.
 */
public class UserData implements Serializable {

    private int agentId;
    private String agentName;
    private String location;
    private String areaCode;
    private int deviceId;
    private String deviceUid;
    private String agencyName;
    private int parkingAreaId;
    private double twoWheelerRate;
    private double fourWheelerRate;
    private int freeParkingFacility;
    private int isSpecialPassAvailable;
    private String agencyGstNo;

    public UserData() {}

    public static UserData fromJson(JSONObject json) {
        UserData user = new UserData();
        if (json == null) return user;

        user.agentId = json.optInt("agent_id", 0);
        user.agentName = json.optString("agent_name", "");
        user.location = json.optString("location", "");
        user.areaCode = json.optString("area_code", "");
        user.deviceId = json.optInt("device_id", 0);
        user.deviceUid = json.optString("device_uid", "");
        user.agencyName = json.optString("agency_name", "");
        user.parkingAreaId = json.optInt("parking_area_id", 0);
        user.twoWheelerRate = json.optDouble("two_wheeler_rate", 0.0);
        user.fourWheelerRate = json.optDouble("four_wheeler_rate", 0.0);
        user.freeParkingFacility = json.optInt("free_parking_facility", 0);
        user.isSpecialPassAvailable = json.optInt("is_special_pass_available", 0);
        user.agencyGstNo = json.optString("AgencyGSTNo", json.optString("agency_gst_no", json.optString("gstn", json.optString("GSTN", ""))));

        return user;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("agent_id", agentId);
            json.put("agent_name", agentName);
            json.put("location", location);
            json.put("area_code", areaCode);
            json.put("device_id", deviceId);
            json.put("device_uid", deviceUid);
            json.put("agency_name", agencyName);
            json.put("parking_area_id", parkingAreaId);
            json.put("two_wheeler_rate", twoWheelerRate);
            json.put("four_wheeler_rate", fourWheelerRate);
            json.put("free_parking_facility", freeParkingFacility);
            json.put("is_special_pass_available", isSpecialPassAvailable);
            json.put("agency_gst_no", agencyGstNo);
        } catch (Exception ignored) {}
        return json;
    }

    public int getAgentId() { return agentId; }
    public String getAgentName() { return agentName; }
    public String getLocation() { return location; }
    public String getAreaCode() { return areaCode; }
    public int getDeviceId() { return deviceId; }
    public String getDeviceUid() { return deviceUid; }
    public String getAgencyName() { return agencyName; }
    public int getParkingAreaId() { return parkingAreaId; }
    public double getTwoWheelerRate() { return twoWheelerRate; }
    public double getFourWheelerRate() { return fourWheelerRate; }
    public int getFreeParkingFacility() { return freeParkingFacility; }
    public int getIsSpecialPassAvailable() { return isSpecialPassAvailable; }
    public String getAgencyGstNo() { return agencyGstNo != null ? agencyGstNo : ""; }
}
