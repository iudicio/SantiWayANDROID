package com.example.santiway.host_database;

public class ScannerSettings {
    private String name;
    private boolean enabled;
    private float scanInterval;
    private float signalStrength;

    public ScannerSettings(String name, boolean enabled, float scanInterval, float signalStrength) {
        this.name = name;
        this.enabled = enabled;
        this.scanInterval = scanInterval;
        this.signalStrength = signalStrength;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public float getScanInterval() {
        return scanInterval;
    }

    public void setScanInterval(float scanInterval) {
        this.scanInterval = scanInterval;
    }

    public float getSignalStrength() {
        return signalStrength;
    }

    public void setSignalStrength(float signalStrength) {
        this.signalStrength = signalStrength;
    }
}