package com.example.santiway.wifi_scanner;

import android.net.wifi.ScanResult;
import java.util.Date;

public class WifiDevice {
    private String ssid;
    private String bssid;
    private int signalStrength;
    private int frequency;
    private String capabilities;
    private String vendor;
    private double latitude;
    private double longitude;
    private double altitude;
    private float locationAccuracy;
    private long timestamp;

    public WifiDevice() {
    }

    // ИСПРАВЛЕНО: Добавлен конструктор, который принимает все необходимые данные
    public WifiDevice(ScanResult result, double latitude, double longitude, double altitude, float locationAccuracy) {
        this.ssid = result.SSID;
        this.bssid = result.BSSID;
        this.signalStrength = result.level;
        this.frequency = result.frequency;
        this.capabilities = result.capabilities;
        this.vendor = "Unknown"; // Установите значение по умолчанию
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.locationAccuracy = locationAccuracy;
        this.timestamp = new Date().getTime();
    }

    // Геттеры и сеттеры
    public String getSsid() {
        return ssid;
    }

    public void setSsid(String ssid) {
        this.ssid = ssid;
    }

    public String getBssid() {
        return bssid;
    }

    public void setBssid(String bssid) {
        this.bssid = bssid;
    }

    public int getSignalStrength() {
        return signalStrength;
    }

    public void setSignalStrength(int signalStrength) {
        this.signalStrength = signalStrength;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public String getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(String capabilities) {
        this.capabilities = capabilities;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
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
}