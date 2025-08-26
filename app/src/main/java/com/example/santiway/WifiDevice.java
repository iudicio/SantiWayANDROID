package com.example.santiway;

public class WifiDevice {
    private int id;
    private String ssid;
    private String bssid;
    private int signalStrength;
    private int frequency;
    private String capabilities;
    private String vendor;
    private long timestamp;

    public WifiDevice() {
    }

    public WifiDevice(String ssid, String bssid, int signalStrength, int frequency,
                      String capabilities, String vendor, long timestamp) {
        this.ssid = ssid;
        this.bssid = bssid;
        this.signalStrength = signalStrength;
        this.frequency = frequency;
        this.capabilities = capabilities;
        this.vendor = vendor;
        this.timestamp = timestamp;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getSsid() { return ssid; }
    public void setSsid(String ssid) { this.ssid = ssid; }

    public String getBssid() { return bssid; }
    public void setBssid(String bssid) { this.bssid = bssid; }

    public int getSignalStrength() { return signalStrength; }
    public void setSignalStrength(int signalStrength) { this.signalStrength = signalStrength; }

    public int getFrequency() { return frequency; }
    public void setFrequency(int frequency) { this.frequency = frequency; }

    public String getCapabilities() { return capabilities; }
    public void setCapabilities(String capabilities) { this.capabilities = capabilities; }

    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
