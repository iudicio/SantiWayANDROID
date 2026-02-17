package com.example.santiway.upload_data;

import android.content.Context;
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
        // ВСЕГДА используем только strings.xml для IP сервера
        String serverIp = defaultServerIp;
        Log.d(TAG, "Using server IP from strings.xml: " + serverIp);

        // Формируем базовый URL
        apiBaseUrl = buildBaseUrl(serverIp);

        // --- Инициализация API Key ---
        // ВСЕГДА используем только strings.xml для API ключа
        apiKey = defaultApiKey;
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            Log.d(TAG, "Using API key from strings.xml");
        } else {
            Log.e(TAG, "API Key is not configured in strings.xml");
        }

        // --- Инициализация MAC адреса телефона ---
        phoneMac = getDeviceMacAddress(context);
        Log.d(TAG, "Phone MAC: " + phoneMac);

        Log.d(TAG, "✅ API Base URL: " + apiBaseUrl);
        Log.d(TAG, "✅ API Key: " + (apiKey != null ? "configured" : "null"));
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
     * НОВЫЙ МЕТОД: Получить базовый URL (аналог getApiBaseUrl для обратной совместимости)
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
        // Убедимся, что нет дублирования "api/"
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
     * НОВЫЙ МЕТОД: Получить MAC-адрес телефона
     */
    public static String getPhoneMac(Context context) {
        if (phoneMac == null && context != null) {
            phoneMac = getDeviceMacAddress(context);
        }
        return phoneMac;
    }

    /**
     * НОВЫЙ МЕТОД: Получить уникальный идентификатор устройства
     */
    private static String getDeviceMacAddress(Context context) {
        try {
            // Используем Android ID как уникальный идентификатор
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId != null && !androidId.isEmpty()) {
                return "android-" + androidId;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting Android ID: " + e.getMessage());
        }

        // Fallback - генерируем случайный ID
        return "android-" + System.currentTimeMillis();
    }
}