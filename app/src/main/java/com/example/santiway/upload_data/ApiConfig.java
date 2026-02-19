package com.example.santiway.upload_data;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.util.Log;

import com.example.santiway.R;

public class ApiConfig {
    private static final String TAG = "ApiConfig";

    private static String apiBaseUrl;
    private static String apiKey;
    private static String phoneMac;

    /**
     * Инициализация конфигурации API только из strings.xml
     */
    public static void initialize(Context context) {
        // Получаем значения только из strings.xml
        String defaultApiKey = context.getString(R.string.default_api_key);
        String defaultServerIp = context.getString(R.string.default_server_ip);

        // --- Инициализация IP сервера ---
        String serverIp = defaultServerIp;
        Log.d(TAG, "Using server IP from strings.xml: " + serverIp);

        // Формируем базовый URL
        apiBaseUrl = buildBaseUrl(serverIp);

        // --- Инициализация API Key ---
        apiKey = defaultApiKey;
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            Log.d(TAG, "Using API key from strings.xml");
        } else {
            Log.e(TAG, "API Key is not configured in strings.xml");
        }

        // --- Инициализация MAC адреса телефона (НОВЫЙ МЕТОД) ---
        phoneMac = getSimpleMacAddress(context);
        Log.d(TAG, "Phone MAC: " + phoneMac);

        Log.d(TAG, "✅ API Base URL: " + apiBaseUrl);
        Log.d(TAG, "✅ API Key: " + (apiKey != null ? "configured" : "null"));
        Log.d(TAG, "✅ Phone MAC: " + phoneMac);
    }

    /**
     * НОВЫЙ МЕТОД: Получение MAC-адреса устройства простым способом
     */
    private static String getSimpleMacAddress(Context context) {
        try {
            // Пытаемся получить MAC через WiFi
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) {
                Log.w(TAG, "WifiManager is null");
                return getFallbackId(context);
            }

            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo == null) {
                Log.w(TAG, "WifiInfo is null");
                return getFallbackId(context);
            }

            String mac = wifiInfo.getMacAddress();
            Log.d(TAG, "Raw MAC from WifiInfo: " + mac);

            // Проверяем, что MAC валидный (не заглушка)
            if (mac != null && !mac.isEmpty() && !"02:00:00:00:00:00".equals(mac)) {
                // Приводим к верхнему регистру для единообразия
                String upperMac = mac.toUpperCase();
                Log.d(TAG, "Valid MAC address found: " + upperMac);
                return upperMac;
            } else {
                Log.w(TAG, "Invalid MAC address (probably emulator or restricted): " + mac);
                return getFallbackId(context);
            }

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception getting MAC: " + e.getMessage());
            return getFallbackId(context);
        } catch (Exception e) {
            Log.e(TAG, "Error getting MAC address: " + e.getMessage());
            return getFallbackId(context);
        }
    }

    /**
     * Запасной вариант - Android ID
     */
    private static String getFallbackId(Context context) {
        try {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId != null && !androidId.isEmpty()) {
                String fallback = "android-" + androidId;
                Log.d(TAG, "Using fallback Android ID: " + fallback);
                return fallback;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting Android ID: " + e.getMessage());
        }

        // Последний запасной вариант
        String lastResort = "android-" + System.currentTimeMillis();
        Log.d(TAG, "Using last resort ID: " + lastResort);
        return lastResort;
    }

    /**
     * Строит базовый URL для порта 80 (без явного указания порта)
     */
    private static String buildBaseUrl(String serverIp) {
        if (serverIp == null || serverIp.trim().isEmpty()) {
            Log.e(TAG, "Server IP is null or empty, using fallback");
            return "http://192.168.1.67/"; // fallback
        }

        String result = serverIp.trim();

        // Если IP без http, добавляем схему (только http)
        if (!result.startsWith("http://") && !result.startsWith("https://")) {
            result = "http://" + result;
        }

        // Для порта 80 убираем явное указание порта из URL
        result = result.replaceFirst(":80/", "/");
        result = result.replaceFirst(":80$", "");

        // Если нет завершающего "/", добавляем
        if (!result.endsWith("/")) {
            result = result + "/";
        }

        Log.d(TAG, "Built base URL: " + result);
        return result;
    }

    /**
     * Получить базовый URL API
     */
    public static String getApiBaseUrl() {
        return apiBaseUrl;
    }

    /**
     * Получить базовый URL (аналог getApiBaseUrl для обратной совместимости)
     */
    public static String getBaseUrl(Context context) {
        if (apiBaseUrl == null && context != null) {
            initialize(context);
        }
        return apiBaseUrl;
    }

    /**
     * Получить полный URL для списка устройств
     */
    public static String getDevicesUrl() {
        String base = getApiBaseUrl();
        if (base.endsWith("/")) {
            if (base.contains("/api/")) {
                return base + "devices/";  // base уже содержит api/
            } else {
                return base + "api/devices/";  // base не содержит api/
            }
        } else {
            if (base.contains("/api")) {
                return base + "/devices/";
            } else {
                return base + "/api/devices/";
            }
        }
    }

    /**
     * Получить API ключ (глобально) - только из strings.xml
     */
    public static String getApiKey(Context context) {
        String apiKey = context.getString(R.string.default_api_key);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            Log.e(TAG, "API Key is not configured in strings.xml");
            return null;
        }
        return apiKey.trim();
    }

    /**
     * Получить API ключ (из статической переменной)
     */
    public static String getApiKey() {
        return apiKey;
    }

    /**
     * Получить MAC-адрес телефона (использует новый метод)
     */
    public static String getPhoneMac(Context context) {
        if (phoneMac == null && context != null) {
            phoneMac = getSimpleMacAddress(context);
        }
        return phoneMac;
    }

    // Старый метод больше не нужен, можно удалить или оставить для совместимости
    // private static String getDeviceMacAddress(Context context) {
    //     return getSimpleMacAddress(context);
    // }
}