package com.example.santiway.host_database;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.List;

public class AppSettingsRepository {
    private ScannerSettings settings;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "AppSettings";

    // Ключи для SharedPreferences
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_GEO_PROTOCOL = "geo_protocol";
    private static final String KEY_IS_SCANNING = "is_scanning";
    private static final String KEY_SERVER_IP = "server_ip";

    public AppSettingsRepository(Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Метод для сохранения IPv4 адреса сервера
    public void setServerIp(String serverIp) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_SERVER_IP, serverIp);
        editor.apply();
    }

    // Метод для получения IPv4 адреса сервера
    public String getServerIp() {
        return sharedPreferences.getString(KEY_SERVER_IP, null);
    }

    // Метод для сохранения API Key
    public void setApiKey(String apiKey) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_API_KEY, apiKey);
        editor.apply();
    }

    // Метод для получения API Key
    public String getApiKey() {
        return sharedPreferences.getString(KEY_API_KEY, null);
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
        float signalStrength = sharedPreferences.getFloat(scannerName + "_signal_strength", -80.0f);

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