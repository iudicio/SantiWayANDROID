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
import java.net.SocketException;
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
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build();

            String baseUrl = ApiConfig.getApiBaseUrl();
            Log.d(TAG, "Using base URL: " + baseUrl);

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            apiService = retrofit.create(ApiService.class);
            Log.d(TAG, "API Service initialized successfully");

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

        try (Cursor cursor = databaseHelper.getReadableDatabase().rawQuery(query, null)) {
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
            else if ("Cell".equals(type)) networkType = "LTE";
            else if ("Bluetooth".equals(type)) networkType = "Bluetooth";
            device.setNetwork_type(networkType);

            // Поля по умолчанию
            device.setIs_ignored(false);
            device.setIs_alert(false);
            device.setUser_api(ApiConfig.getApiKey(context));
            device.setUser_phone_mac(this.androidDeviceId);

            // Форматируем timestamp в ISO 8601
            Long timestamp = getLongFromCursor(cursor, "timestamp");
            if (timestamp != null) {
                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
                isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                String isoDate = isoFormat.format(new Date(timestamp));
                device.setDetected_at(isoDate);
            }

            // Папки
            device.setFolder_name(tableName);
            device.setSystem_folder_name(tableName.toLowerCase().replace(" ", "_"));

            return device;

        } catch (Exception e) {
            Log.e(TAG, "Error converting cursor to ApiDevice: " + e.getMessage(), e);
            return null;
        }
    }

    // Вспомогательные методы для работы с Cursor
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

        // Детальное логирование данных
        logDeviceDetails(devices);

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                Log.d(TAG, "Upload attempt " + attempt);

                String apiKey = ApiConfig.getApiKey(context);
                if (apiKey == null || apiKey.isEmpty()) {
                    Log.e(TAG, "API Key is not configured");
                    return false;
                }

                // Формируем заголовки
                String authHeader = "Api-Key " + apiKey;
                String contentTypeHeader = "application/json";

                Log.d(TAG, "Auth Header: " + authHeader);
                Log.d(TAG, "Content-Type: " + contentTypeHeader);
                Log.d(TAG, "Sending request to: " + ApiConfig.getDevicesUrl());

                // Логируем тело запроса
                String requestBody = devicesToJsonString(devices);
                Log.d(TAG, "Request body (first 1000 chars): " +
                        (requestBody != null ? requestBody.substring(0, Math.min(requestBody.length(), 1000)) : "null"));

                Call<ApiResponse> call = apiService.uploadDevices(authHeader, contentTypeHeader, devices);
                Response<ApiResponse> response = call.execute();

                Log.d(TAG, "Response code: " + response.code());
                Log.d(TAG, "Response message: " + response.message());

                if (response.isSuccessful()) {
                    ApiResponse apiResponse = response.body();
                    Log.i(TAG, "✅ SUCCESS: Uploaded " + devices.size() + " devices");
                    if (apiResponse != null) {
                        Log.d(TAG, "Response body: " + apiResponse.toString());
                    }
                    return true;
                } else {
                    // Детальный анализ ошибки
                    String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                    Log.w(TAG, "Upload failed with status: " + response.code() + " - " + response.message());
                    Log.w(TAG, "Error response: " + errorBody);

                    // Анализ распространенных ошибок
                    analyzeCommonErrors(response.code(), errorBody);

                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        Thread.sleep(2000 * attempt);
                    }
                }

            } catch (SocketException e) {
                Log.e(TAG, "SocketException (attempt " + attempt + "): " + e.getMessage());
                Log.e(TAG, "This usually means the server accepted connection but reset it immediately");
                Log.e(TAG, "Possible causes: wrong endpoint, authentication issues, or server-side problems");

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(2000 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
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

    private void logDeviceDetails(List<ApiDevice> devices) {
        if (devices == null || devices.isEmpty()) return;

        Log.d(TAG, "=== DEVICE DETAILS ===");
        for (int i = 0; i < Math.min(devices.size(), 3); i++) { // Логируем только первые 3 устройства
            ApiDevice device = devices.get(i);
            Log.d(TAG, "Device " + i + ": " +
                    "id=" + device.getDevice_id() + ", " +
                    "type=" + device.getNetwork_type() + ", " +
                    "lat=" + device.getLatitude() + ", " +
                    "lng=" + device.getLongitude() + ", " +
                    "signal=" + device.getSignal_strength() + ", " +
                    "folder=" + device.getFolder_name());
        }
    }

    private String devicesToJsonString(List<ApiDevice> devices) {
        try {
            // Простой способ посмотреть на структуру JSON
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < Math.min(devices.size(), 2); i++) {
                if (i > 0) sb.append(",");
                ApiDevice device = devices.get(i);
                sb.append("{")
                        .append("\"device_id\":\"").append(device.getDevice_id()).append("\",")
                        .append("\"network_type\":\"").append(device.getNetwork_type()).append("\",")
                        .append("\"latitude\":").append(device.getLatitude()).append(",")
                        .append("\"longitude\":").append(device.getLongitude())
                        .append("}");
            }
            sb.append("]");
            return sb.toString();
        } catch (Exception e) {
            return "Error creating JSON preview: " + e.getMessage();
        }
    }

    private void analyzeCommonErrors(int statusCode, String errorBody) {
        switch (statusCode) {
            case 401:
                Log.e(TAG, "❌ AUTHENTICATION ERROR: Invalid API Key");
                break;
            case 403:
                Log.e(TAG, "❌ FORBIDDEN: API Key doesn't have required permissions");
                break;
            case 404:
                Log.e(TAG, "❌ NOT FOUND: Endpoint doesn't exist. Check URL: " + ApiConfig.getDevicesUrl());
                break;
            case 413:
                Log.e(TAG, "❌ PAYLOAD TOO LARGE: Too many devices in one request");
                break;
            case 415:
                Log.e(TAG, "❌ UNSUPPORTED MEDIA TYPE: Check Content-Type header");
                break;
            case 500:
                Log.e(TAG, "❌ SERVER ERROR: Problem on server side");
                break;
            default:
                Log.e(TAG, "❌ UNKNOWN ERROR: Status code " + statusCode);
        }
    }

    public void markDevicesAsUploaded(List<ApiDevice> devices) {
        Log.d(TAG, "Marking " + devices.size() + " devices as uploaded in database");
    }

    public void cleanup() {
        // Не нужно закрывать соединения как в RabbitMQ
    }
}