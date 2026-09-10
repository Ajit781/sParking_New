package com.innovus.sparkingnew.models;

import org.json.JSONObject;

import java.io.Serializable;

/**
 * Data model for vehicle categories (Two Wheeler, Four Wheeler, etc.).
 */
public class VehicleType implements Serializable {

    private int vehicleTypeId;
    private String vehicleTypeName;
    private String vehicleTypeIcon;

    public VehicleType() {}

    public VehicleType(int vehicleTypeId, String vehicleTypeName, String vehicleTypeIcon) {
        this.vehicleTypeId = vehicleTypeId;
        this.vehicleTypeName = vehicleTypeName;
        this.vehicleTypeIcon = vehicleTypeIcon;
    }

    public static VehicleType fromJson(JSONObject json) {
        if (json == null) return new VehicleType(0, "", "");
        int id = json.optInt("vehicle_type_id", 0);
        String name = json.optString("vehicle_type_name", "");
        String icon = json.optString("vehicle_type_icon", "");
        return new VehicleType(id, name, icon);
    }

    public int getVehicleTypeId() {
        return vehicleTypeId;
    }

    public String getVehicleTypeName() {
        return vehicleTypeName != null ? vehicleTypeName : "";
    }

    public String getVehicleTypeIcon() {
        return vehicleTypeIcon != null ? vehicleTypeIcon : "";
    }

    @Override
    public String toString() {
        return getVehicleTypeName();
    }
}
