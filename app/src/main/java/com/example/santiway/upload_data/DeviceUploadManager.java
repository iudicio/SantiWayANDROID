package com.example.santiway.upload_data;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Log;

import com.example.santiway.upload_data.MainDatabaseHelper;
import com.example.santiway.host_database.AppSettingsRepository;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class DeviceUploadManager {
    private static final String TAG = "DeviceUploadManager";
    private static final String PREFS_NAME = "DeviceUploadPrefs";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final int BATCH_SIZE = 200;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private Context context;
    private MainDatabaseHelper databaseHelper;
    private SharedPreferences prefs;
    private String androidDeviceId;
    private ApiService apiService;

    public DeviceUploadManager(Context context) {
        this.context = context;
        this.databaseHelper = new MainDatabaseHelper(context);
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.androidDeviceId = getOrCreateDeviceId();
        ApiConfig.initialize(context);
        initializeApiService();
    }

    private String getOrCreateDeviceId() {
        String id = prefs.getString(KEY_DEVICE_ID, null);
        if (id == null) {
            id = "android-" + UUID.randomUUID().toString();
            prefs.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    private void initializeApiService() {
        try {
            Log.d(TAG, "Initializing API service...");

            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .connectTimeout(60, TimeUnit.SECONDS) // увеличиваем таймаут
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build();

            // ЯВНО указываем порт 8000
            String baseUrl = ApiConfig.getApiBaseUrl();
            Log.d(TAG, "Using base URL: " + baseUrl);

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            apiService = retrofit.create(ApiService.class);
            Log.d(TAG, "API Service initialized successfully with port 8000");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize API service: " + e.getMessage(), e);
        }
    }

    public List<ApiDevice> getPendingDevicesBatch() {
        List<ApiDevice> devices = new ArrayList<>();

        List<String> tables = databaseHelper.getAllTables();
        for (String table : tables) {
            if (devices.size() >= BATCH_SIZE) break;

            List<ApiDevice> tableDevices = getDevicesFromTable(table, BATCH_SIZE - devices.size());
            devices.addAll(tableDevices);
        }

        return devices;
    }

    private List<ApiDevice> getDevicesFromTable(String tableName, int limit) {
        List<ApiDevice> devices = new ArrayList<>();

        String query = "SELECT * FROM \"" + tableName + "\" WHERE is_uploaded = 0 ORDER BY timestamp DESC LIMIT " + limit;

        Cursor cursor = null;
        try {
            cursor = databaseHelper.getReadableDatabase().rawQuery(query, null);

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    ApiDevice device = cursorToApiDevice(cursor, tableName);
                    if (device != null) {
                        devices.add(device);
                    }
                } while (cursor.moveToNext() && devices.size() < limit);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting devices from table " + tableName + ": " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return devices;
    }

    private ApiDevice cursorToApiDevice(Cursor cursor, String tableName) {
        try {
            ApiDevice device = new ApiDevice();

            // Базовые поля
            String type = getStringFromCursor(cursor, "type");
            String deviceId = getStringFromCursor(cursor, "bssid");
            if (deviceId == null) {
                Integer cellId = getIntFromCursor(cursor, "cell_id");
                deviceId = cellId != null ? cellId.toString() : null;
            }

            device.setDevice_id(deviceId);
            device.setLatitude(getDoubleFromCursor(cursor, "latitude"));
            device.setLongitude(getDoubleFromCursor(cursor, "longitude"));
            device.setSignal_strength(getIntFromCursor(cursor, "signal_strength"));

            // Преобразуем тип устройства
            String networkType = "Unknown";
            if ("Wi-Fi".equals(type)) networkType = "WiFi";
            else if ("Cell".equals(type)) networkType = "LTE"; // или другой тип сотовой сети
            else if ("Bluetooth".equals(type)) networkType = "Bluetooth";
            device.setNetwork_type(networkType);

            // Поля по умолчанию
            device.setIs_ignored(false);
            device.setIs_alert(false);
            device.setUser_api(ApiConfig.API_KEY);
            device.setUser_phone_mac(this.androidDeviceId);

            // Форматируем timestamp в ISO 8601
            Long timestamp = getLongFromCursor(cursor, "timestamp");
            if (timestamp != null) {
                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
                isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                String isoDate = isoFormat.format(new Date(timestamp));
                device.setDetected_at(isoDate);
            }

            // Папки (можно настроить логику)
            device.setFolder_name(tableName);
            device.setSystem_folder_name(tableName.toLowerCase().replace(" ", "_"));

            return device;

        } catch (Exception e) {
            Log.e(TAG, "Error converting cursor to ApiDevice: " + e.getMessage(), e);
            return null;
        }
    }

    // Вспомогательные методы для работы с Cursor (оставить без изменений)
    private String getStringFromCursor(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index != -1 ? cursor.getString(index) : null;
    }

    private Integer getIntFromCursor(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index != -1 && !cursor.isNull(index) ? cursor.getInt(index) : null;
    }

    private Double getDoubleFromCursor(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index != -1 && !cursor.isNull(index) ? cursor.getDouble(index) : null;
    }

    private Long getLongFromCursor(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index != -1 && !cursor.isNull(index) ? cursor.getLong(index) : null;
    }

    public boolean uploadBatch(List<ApiDevice> devices) {
        Log.d(TAG, "=== STARTING UPLOAD BATCH ===");
        Log.d(TAG, "Devices count: " + devices.size());

        if (devices == null || devices.isEmpty()) {
            Log.d(TAG, "No devices to upload");
            return true;
        }

        if (apiService == null) {
            Log.e(TAG, "API Service is not available");
            return false;
        }

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                Log.d(TAG, "Upload attempt " + attempt);

                String apiKey = ApiConfig.getApiKey(context);
                Log.d(TAG, "API Key: " + (apiKey != null ? "configured" : "null"));

                if (apiKey == null || apiKey.isEmpty()) {
                    Log.e(TAG, "API Key is not configured");
                    return false;
                }

                // Формируем заголовки согласно документации
                String authHeader = "Api-Key " + apiKey; // "Api-Key 0028e040-db1f-4144-b711-7011d71fbbcf"
                String contentTypeHeader = "application/json";

                Log.d(TAG, "Auth Header: " + authHeader);
                Log.d(TAG, "Content-Type: " + contentTypeHeader);
                Log.d(TAG, "Sending request to: " + ApiConfig.getDevicesUrl());

                // Передаем оба заголовка в метод
                Call<ApiResponse> call = apiService.uploadDevices(authHeader, contentTypeHeader, devices);
                Response<ApiResponse> response = call.execute();

                Log.d(TAG, "Response code: " + response.code());
                Log.d(TAG, "Response message: " + response.message());

                if (response.isSuccessful()) {
                    ApiResponse apiResponse = response.body();
                    Log.i(TAG, "✅ SUCCESS: Uploaded " + devices.size() + " devices");

                    // Логируем ответ если есть
                    if (apiResponse != null) {
                        Log.d(TAG, "Response body: " + apiResponse.toString());
                    }
                    return true;
                } else {
                    // Логируем ошибку подробнее
                    String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                    Log.w(TAG, "Upload failed with status: " + response.code() + " - " + response.message());
                    Log.w(TAG, "Error response: " + errorBody);

                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        Thread.sleep(2000 * attempt);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Error uploading batch (attempt " + attempt + "): " + e.getMessage(), e);
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(2000 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        Log.e(TAG, "All upload attempts failed for " + devices.size() + " devices");
        return false;
    }

    public void markDevicesAsUploaded(List<ApiDevice> devices) {
        // TODO: Реализовать обновление поля is_uploaded в базе данных
        Log.d(TAG, "Marking " + devices.size() + " devices as uploaded in database");
    }

    public void cleanup() {
        // Не нужно закрывать соединения как в RabbitMQ
    }
}