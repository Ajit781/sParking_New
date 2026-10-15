package com.innovus.sparkingnew.models;

public class ParkingSummaryByAgentBean {
    private int VehicleTypeID;
    private String VehicleTypeName;
    private int AgentID;
    private String AgentName;
    private int ParkingCount;
    private int CollectionAmount;
    private int viewtype;

    public int getVehicleTypeID() { return VehicleTypeID; }
    public void setVehicleTypeID(int vehicleTypeID) { VehicleTypeID = vehicleTypeID; }

    public String getVehicleTypeName() { return VehicleTypeName; }
    public void setVehicleTypeName(String vehicleTypeName) { VehicleTypeName = vehicleTypeName; }

    public int getAgentID() { return AgentID; }
    public void setAgentID(int agentID) { AgentID = agentID; }

    public String getAgentName() { return AgentName; }
    public void setAgentName(String agentName) { AgentName = agentName; }

    public int getParkingCount() { return ParkingCount; }
    public void setParkingCount(int parkingCount) { ParkingCount = parkingCount; }

    public int getCollectionAmount() { return CollectionAmount; }
    public void setCollectionAmount(int collectionAmount) { CollectionAmount = collectionAmount; }

    public int getViewtype() { return viewtype; }
    public void setViewtype(int viewtype) { this.viewtype = viewtype; }
}
