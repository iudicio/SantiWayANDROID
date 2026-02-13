package com.example.santiway.upload_data;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Log;
import com.example.santiway.R;

import androidx.core.app.NotificationCompat;

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
    private static final String KEY_LAST_UPLOAD_TIME = "last_upload_time";
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

    // метод для получения времени последней отправки
    public static long getLastUploadTime(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_LAST_UPLOAD_TIME, 0);
    }

    // метод для сохранения времени последней отправки
    private void saveLastUploadTime() {
        long currentTime = System.currentTimeMillis();
        prefs.edit().putLong(KEY_LAST_UPLOAD_TIME, currentTime).apply();
        Log.d(TAG, "Last upload time saved: " + currentTime);
    }

    public List<ApiDevice> getPendingDevicesBatch() {
        List<ApiDevice> devices = new ArrayList<>();
        List<String> tables = databaseHelper.getAllTables();

        for (String table : tables) {
            if (devices.size() >= BATCH_SIZE) break;
            // Добавляем условие is_uploaded = 0
            List<ApiDevice> tableDevices = getDevicesFromTable(table, BATCH_SIZE - devices.size());
            devices.addAll(tableDevices);
        }

        Log.d(TAG, "Found " + devices.size() + " pending devices to upload");
        return devices;
    }

    private List<ApiDevice> getDevicesFromTable(String tableName, int limit) {
        List<ApiDevice> devices = new ArrayList<>();
        // ИЗМЕНЕНО: ORDER BY timestamp ASC (старые записи первыми)
        String query = "SELECT * FROM \"" + tableName + "\" WHERE is_uploaded = 0 ORDER BY timestamp ASC LIMIT " + limit;

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

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                Log.d(TAG, "Upload attempt " + attempt);

                String apiKey = ApiConfig.getApiKey(context);
                if (apiKey == null || apiKey.isEmpty()) {
                    Log.e(TAG, "API Key is not configured");
                    return false;
                }

                String authHeader = "Api-Key " + apiKey;
                String contentTypeHeader = "application/json";

                Call<ApiResponse> call = apiService.uploadDevices(authHeader, contentTypeHeader, devices);
                Response<ApiResponse> response = call.execute();

                Log.d(TAG, "Response code: " + response.code());

                if (response.isSuccessful()) {
                    ApiResponse apiResponse = response.body();
                    Log.i(TAG, "✅ SUCCESS: Uploaded " + devices.size() + " devices");

                    // УСПЕХ - отмечаем устройства как отправленные
                    markDevicesAsUploaded(devices);

                    return true;
                } else {
                    String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                    Log.w(TAG, "Upload failed with status: " + response.code());

                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        Thread.sleep(2000 * attempt);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Error uploading batch (attempt " + attempt + "): " + e.getMessage());
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
        if (devices == null || devices.isEmpty()) {
            Log.d(TAG, "No devices to mark as uploaded");
            return;
        }

        Log.d(TAG, "Marking " + devices.size() + " devices as uploaded in database");

        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        try {
            db.beginTransaction();

            for (ApiDevice device : devices) {
                String deviceId = device.getDevice_id();
                String detectedAt = device.getDetected_at();
                String folderName = device.getFolder_name();

                // ИЗМЕНЕНО: отмечаем конкретную запись по device_id, folder_name и detected_at
                // чтобы отметить только отправленные, а не все одинаковые MAC-адреса
                ContentValues values = new ContentValues();
                values.put("is_uploaded", 1);

                String whereClause;
                String[] whereArgs;

                if (deviceId != null && deviceId.contains(":")) {
                    // Wi-Fi или Bluetooth - отмечаем по точной дате и времени
                    whereClause = "bssid = ? AND folder_name = ? AND timestamp = ?";
                    whereArgs = new String[]{
                            deviceId,
                            folderName,
                            String.valueOf(timestampFromIsoDate(detectedAt))
                    };
                } else {
                    // Cell tower - отмечаем по cell_id и timestamp
                    whereClause = "cell_id = ? AND folder_name = ? AND timestamp = ?";
                    whereArgs = new String[]{
                            deviceId,
                            folderName,
                            String.valueOf(timestampFromIsoDate(detectedAt))
                    };
                }

                int updated = db.update("\"" + folderName + "\"", values, whereClause, whereArgs);
                if (updated == 0) {
                    Log.w(TAG, "Could not mark device as uploaded: " + deviceId + " @ " + detectedAt);
                }
            }

            db.setTransactionSuccessful();
            Log.d(TAG, "Successfully marked " + devices.size() + " devices as uploaded");

            // Сохраняем время последней успешной отправки
            saveLastUploadTime();

            // ОТПРАВЛЯЕМ УВЕДОМЛЕНИЕ ОБ УСПЕШНОЙ ОТПРАВКЕ
            sendUploadSuccessNotification(context, devices.size());

        } catch (Exception e) {
            Log.e(TAG, "Error marking devices as uploaded: " + e.getMessage(), e);
        } finally {
            try {
                db.endTransaction();
            } catch (Exception e) {
                Log.e(TAG, "Error ending transaction: " + e.getMessage());
            }
            db.close();
        }
    }

    private long timestampFromIsoDate(String isoDate) {
        try {
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = isoFormat.parse(isoDate);
            return date != null ? date.getTime() : 0;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing ISO date: " + e.getMessage());
            return 0;
        }
    }

    // НОВЫЙ МЕТОД: отправка уведомления об успешной отправке
    private void sendUploadSuccessNotification(Context context, int count) {
        String channelId = "upload_notifications";
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Создаем канал для Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Отправка данных",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Уведомления об отправке данных на сервер");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        // Форматируем время
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd.MM.yyyy", Locale.getDefault());
        String currentTime = sdf.format(new Date());

        // Создаем уведомление
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_pause)
                .setContentTitle("📤 Данные отправлены")
                .setContentText("Отправлено " + count + " устройств в " + currentTime)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Отправлено " + count + " устройств\nВремя: " + currentTime))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    public void cleanup() {
        // Не нужно закрывать соединения
    }
}