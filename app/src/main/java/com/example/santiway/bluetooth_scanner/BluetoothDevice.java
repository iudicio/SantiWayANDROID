package com.example.santiway.bluetooth_scanner;

public class BluetoothDevice {
    // Поля устройства
    private String deviceName;
    private String macAddress;
    private int signalStrength; // RSSI
    private String vendor;

    // Геолокация
    private double latitude;
    private double longitude;
    private double altitude;
    private float locationAccuracy;
    private long timestamp;


    public BluetoothDevice() {
    }

    // Геттеры и сеттеры
    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getMacAddress() {
        return macAddress.toUpperCase();
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress.toUpperCase();
    }

    public int getSignalStrength() {
        return signalStrength;
    }

    public void setSignalStrength(int signalStrength) {
        this.signalStrength = signalStrength;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getAltitude() {
        return altitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    public float getLocationAccuracy() {
        return locationAccuracy;
    }

    public void setLocationAccuracy(float locationAccuracy) {
        this.locationAccuracy = locationAccuracy;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
