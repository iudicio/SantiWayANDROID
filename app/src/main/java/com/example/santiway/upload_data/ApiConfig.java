package com.example.santiway.upload_data;

import android.content.Context;
import android.util.Log;

import com.example.santiway.host_database.AppSettingsRepository;
import com.example.santiway.R;

public class ApiConfig {
    private static final String TAG = "ApiConfig";

    private static String apiBaseUrl;
    private static String apiKey;

    /**
     * Инициализация конфигурации API из базы (AppSettingsRepository) и ресурсов
     */
    public static void initialize(Context context) {
        AppSettingsRepository repository = new AppSettingsRepository(context);

        // Получаем значения по умолчанию из ресурсов
        String defaultApiKey = context.getString(R.string.default_api_key);
        String defaultServerIp = context.getString(R.string.default_server_ip);

        // --- Инициализация IP сервера ---
        String serverIp = repository.getServerIp();
        if (serverIp == null || serverIp.trim().isEmpty()) {
            serverIp = defaultServerIp;
            Log.d(TAG, "Using default server IP: " + serverIp);
        } else {
            Log.d(TAG, "Using saved server IP: " + serverIp);
        }

        // Формируем базовый URL
        apiBaseUrl = buildBaseUrl(serverIp);

        // --- Инициализация API Key ---
        String savedApiKey = repository.getApiKey();
        if (savedApiKey != null && !savedApiKey.trim().isEmpty()) {
            apiKey = savedApiKey;
            Log.d(TAG, "Using saved API key");
        } else {
            apiKey = defaultApiKey;
            Log.d(TAG, "Using default API key");
        }

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
        return apiBaseUrl != null ? apiBaseUrl : "http://192.168.1.67/";
    }

    /**
     * Получить полный URL для списка устройств
     * Пример: http://192.168.1.67/api/devices/
     */
    public static String getDevicesUrl() {
        return getApiBaseUrl() + "api/devices/";
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


}