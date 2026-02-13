package com.example.santiway.host_database;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.List;

public class AppSettingsRepository {
    private ScannerSettings settings;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "AppSettings";

    private static final String KEY_GEO_PROTOCOL = "geo_protocol";
    private static final String KEY_IS_SCANNING = "is_scanning";
    private static final String KEY_DEVICE_NAME = "device_name";
    private static final String KEY_MIN_SIGNAL_PREFIX = "_min_signal";

    public AppSettingsRepository(Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Метод для сохранения протокола геолокации
    public void setGeoProtocol(String protocol) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_GEO_PROTOCOL, protocol);
        editor.apply();
    }

    // Метод для получения протокола геолокации
    public String getGeoProtocol() {
        return sharedPreferences.getString(KEY_GEO_PROTOCOL, "GSM"); // значение по умолчанию
    }

    // Метод для установки статуса сканирования
    public void setScanning(boolean isScanning) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_IS_SCANNING, isScanning);
        editor.apply();
    }

    public String getDeviceName() {
        return sharedPreferences.getString(KEY_DEVICE_NAME, "Telephone"); // значение по умолчанию
    }

    // Метод для сохранения имени устройства
    public void setDeviceName(String deviceName) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_DEVICE_NAME, deviceName);
        editor.apply();
    }

    // Метод для получения статуса сканирования
    public boolean isScanning() {
        return sharedPreferences.getBoolean(KEY_IS_SCANNING, false);
    }

    // Метод для получения всех сканеров
    public List<String> getAllScanners() {
        return Arrays.asList("WiFi", "Bluetooth", "Cell");
    }

    // Метод для получения настроек сканера
    public ScannerSettings getScannerSettings(String scannerName) {
        boolean enabled = sharedPreferences.getBoolean(scannerName + "_enabled", true);
        float interval = sharedPreferences.getFloat(scannerName + "_interval", 5.0f);
        float signalStrength = sharedPreferences.getFloat(scannerName + "_signal_strength", -100.0f);

        return new ScannerSettings(scannerName, enabled, interval, signalStrength);
    }

    // Метод для обновления настроек сканера
    public boolean updateScannerSettings(ScannerSettings settings) {
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            // ИСПРАВЛЕНИЕ: используем getName() вместо getScannerName()
            editor.putBoolean(settings.getName() + "_enabled", settings.isEnabled());
            editor.putFloat(settings.getName() + "_interval", settings.getScanInterval());
            editor.putFloat(settings.getName() + "_signal_strength", settings.getSignalStrength());
            editor.apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
