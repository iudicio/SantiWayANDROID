package com.example.santiway.upload_data;

import android.content.Context;
import com.example.santiway.host_database.AppSettingsRepository;

public class ApiConfig {
    private static String apiBaseUrl = "http://localhost/"; // значение по умолчанию
    private static String apiKey = ""; // Добавляем поле для API Key

    public static void initialize(Context context) {
        AppSettingsRepository repository = new AppSettingsRepository(context);

         //Инициализация IP сервера
        String serverIp = repository.getServerIp();
        if (serverIp != null && !serverIp.isEmpty()) {
            apiBaseUrl = "https://" + serverIp + ":8000/";
        }

        // Инициализация API Key
        String savedApiKey = repository.getApiKey();
        if (savedApiKey != null && !savedApiKey.isEmpty()) {
            apiKey = savedApiKey;
        }
    }

    public static String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public static String API_BASE_URL = getApiBaseUrl();

    // Добавляем статическое поле API_KEY
    public static String API_KEY = apiKey;

    public static String getDevicesUrl() {
        return getApiBaseUrl() + "api/devices/";
    }

    public static String getApiKey(Context context) {
        AppSettingsRepository repository = new AppSettingsRepository(context);
        return repository.getApiKey();
    }

    // Метод для получения API Key (без контекста, если уже инициализирован)
    public static String getApiKey() {
        return apiKey;
    }
}