package com.example.santiway.cell_scanner;

import java.util.Locale;

public class CellTower {
    private long cellId;
    private int lac; // Location Area Code
    private int mcc; // Mobile Country Code
    private int mnc; // Mobile Network Code
    private int psc; // Primary Scrambling Code (для UMTS)
    private int pci; // Physical Cell ID (для LTE)
    private long tac; // Tracking Area Code (для LTE)
    private int earfcn; // E-UTRA Absolute Radio Frequency Channel Number (для LTE)
    private int arfcn; // Absolute Radio Frequency Channel Number (для GSM/UMTS)
    private int signalStrength;
    private int signalQuality;
    private String networkType;
    private String operatorName;
    private boolean isRegistered;
    private boolean isNeighbor;
    private double latitude;
    private double longitude;
    private double altitude;
    private float locationAccuracy;
    private long timestamp;

    public CellTower() {
    }

    public long getCellId() {
        return cellId;
    }

    public void setCellId(long cellId) {
        this.cellId = cellId;
    }

    public int getLac() {
        return lac;
    }

    public void setLac(int lac) {
        this.lac = lac;
    }

    public int getMcc() {
        return mcc;
    }

    public void setMcc(int mcc) {
        this.mcc = mcc;
    }

    public int getMnc() {
        return mnc;
    }

    public void setMnc(int mnc) {
        this.mnc = mnc;
    }

    public int getPsc() {
        return psc;
    }

    public void setPsc(int psc) {
        this.psc = psc;
    }

    public int getPci() {
        return pci;
    }

    public void setPci(int pci) {
        this.pci = pci;
    }

    public long getTac() {
        return tac;
    }

    public void setTac(long tac) {
        this.tac = tac;
    }

    public int getEarfcn() {
        return earfcn;
    }

    public void setEarfcn(int earfcn) {
        this.earfcn = earfcn;
    }

    public int getArfcn() {
        return arfcn;
    }

    public void setArfcn(int arfcn) {
        this.arfcn = arfcn;
    }

    public int getSignalStrength() {
        return signalStrength;
    }

    public void setSignalStrength(int signalStrength) {
        this.signalStrength = signalStrength;
    }

    public int getSignalQuality() {
        return signalQuality;
    }

    public void setSignalQuality(int signalQuality) {
        this.signalQuality = signalQuality;
    }

    public String getNetworkType() {
        return networkType;
    }

    public void setNetworkType(String networkType) {
        this.networkType = networkType;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public boolean isRegistered() {
        return isRegistered;
    }

    public void setRegistered(boolean registered) {
        isRegistered = registered;
    }

    public boolean isNeighbor() {
        return isNeighbor;
    }

    public void setNeighbor(boolean neighbor) {
        isNeighbor = neighbor;
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

    // Уникальный идентификатор с учетом всех параметров
    public String getUniqueId() {
        if (mcc > 0 && mnc > 0) {
            if ("LTE".equals(networkType) || "5G".equals(networkType)) {
                return String.format(Locale.US, "%d_%d_%d_%d", mcc, mnc, tac, cellId);
            } else {
                return String.format(Locale.US, "%d_%d_%d_%d", mcc, mnc, lac, cellId);
            }
        }
        return String.valueOf(cellId);
    }


    public String getDescription() {
        return "Cell ID: " + cellId + 
               ", LAC: " + lac + 
               ", MCC: " + mcc + 
               ", MNC: " + mnc + 
               ", Type: " + networkType + 
               ", Signal: " + signalStrength + " dBm";
    }
}
