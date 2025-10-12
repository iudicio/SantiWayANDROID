package com.example.santiway.upload_data;

import android.content.Context;
import android.util.Log;

import com.example.santiway.host_database.AppSettingsRepository;

public class ApiConfig {
    private static String apiBaseUrl = "http://192.168.1.67/"; // значение по умолчанию
    private static String apiKey = "3415fab8-fa0c-47b1-a111-43facaa7dde0"; // значение по умолчанию
    public static String API_KEY = "3415fab8-fa0c-47b1-a111-43facaa7dde0";

    /**
     * Инициализация конфигурации API из базы (AppSettingsRepository)
     */
    public static void initialize(Context context) {
        AppSettingsRepository repository = new AppSettingsRepository(context);

        // --- Инициализация IP сервера ---
        String serverIp = repository.getServerIp();
        if (serverIp != null && !serverIp.isEmpty()) {
            // Если IP без http/https, добавляем схему
            if (!serverIp.startsWith("http://") && !serverIp.startsWith("https://")) {
                serverIp = "http://" + serverIp;
            }

            // Если нет завершающего "/", добавляем
            if (!serverIp.endsWith("/")) {
                serverIp = serverIp + "/";
            }

            apiBaseUrl = serverIp;
        }

        // --- Инициализация API Key ---
        String savedApiKey = repository.getApiKey();
        if (savedApiKey != null && !savedApiKey.isEmpty()) {
            apiKey = savedApiKey;
        }

        Log.d("ApiConfig", "✅ API Base URL: " + apiBaseUrl);
        Log.d("ApiConfig", "✅ API Key: " + apiKey);
    }

    /**
     * Получить базовый URL API
     */
    public static String getApiBaseUrl() {
        return apiBaseUrl;
    }

    /**
     * Получить полный URL для списка устройств
     * Пример: http://192.168.1.67/api/devices/
     */
    public static String getDevicesUrl() {
        return getApiBaseUrl() + "api/devices/";
    }

    /**
     * Получить API ключ (глобально)
     */
    public static String getApiKey() {
        return apiKey;
    }

    /**
     * Получить API ключ напрямую из базы (если нужно обновить)
     */
    public static String getApiKey(Context context) {
        AppSettingsRepository repository = new AppSettingsRepository(context);
        String key = repository.getApiKey();
        return (key != null && !key.isEmpty()) ? key : apiKey;
    }
}
