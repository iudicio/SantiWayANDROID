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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import java.util.concurrent.TimeUnit;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class DeviceUploadManager {
    private static final String TAG = "DeviceUploadManager";
    private static final String PREFS_NAME = "DeviceUploadPrefs";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_LAST_UPLOAD_TIME = "last_upload_time";
    private static final int BATCH_SIZE = 20;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private Context context;
    private MainDatabaseHelper databaseHelper;
    private SharedPreferences prefs;
    private String androidDeviceId;
    private ApiService apiService;

    // ДОБАВЛЕННЫЕ ПОЛЯ
    private String apiKey;
    private String baseUrl;
    private String phoneMac;
    private OkHttpClient client;

    public DeviceUploadManager(Context context) {
        this.context = context;
        this.databaseHelper = new MainDatabaseHelper(context);
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.androidDeviceId = getOrCreateDeviceId();

        // ИНИЦИАЛИЗАЦИЯ НОВЫХ ПОЛЕЙ
        ApiConfig.initialize(context);
        this.apiKey = ApiConfig.getApiKey(context);

        // ИСПРАВЛЕНИЕ: используем getApiBaseUrl() вместо getDevicesUrl()
        this.baseUrl = ApiConfig.getApiBaseUrl();  // <- ИЗМЕНЕНО!
        this.phoneMac = ApiConfig.getPhoneMac(context);

        Log.d(TAG, "=== DeviceUploadManager INIT ===");
        Log.d(TAG, "API Key: " + (apiKey != null ? "configured" : "null"));
        Log.d(TAG, "Base URL: " + baseUrl);
        Log.d(TAG, "Devices URL would be: " + ApiConfig.getDevicesUrl());
        Log.d(TAG, "Phone MAC: " + phoneMac);

        // ИНИЦИАЛИЗАЦИЯ HTTP КЛИЕНТА
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        // Не вызываем initializeApiService() если используем прямой OkHttp
        // initializeApiService(); // <- ЗАКОММЕНТИРУЙТЕ ЭТО
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
            List<ApiDevice> tableDevices = getDevicesFromTable(table, BATCH_SIZE - devices.size());
            devices.addAll(tableDevices);
        }

        Log.d(TAG, "Found " + devices.size() + " pending devices to upload");
        return devices;
    }

    private List<ApiDevice> getDevicesFromTable(String tableName, int limit) {
        List<ApiDevice> devices = new ArrayList<>();
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

            String type = getStringFromCursor(cursor, "type");
            String deviceId = getStringFromCursor(cursor, "bssid");
            if (deviceId == null) {
                Integer cellId = getIntFromCursor(cursor, "cell_id");
                deviceId = cellId != null ? cellId.toString() : null;
            }

            // ИСПОЛЬЗУЕМ СЕТТЕРЫ
            device.setDevice_id(deviceId);
            device.setLatitude(getDoubleFromCursor(cursor, "latitude"));
            device.setLongitude(getDoubleFromCursor(cursor, "longitude"));
            device.setSignal_strength(getIntFromCursor(cursor, "signal_strength"));

            String networkType = "Unknown";
            if ("Wi-Fi".equals(type)) networkType = "WiFi";
            else if ("Cell".equals(type)) networkType = "LTE";
            else if ("Bluetooth".equals(type)) networkType = "Bluetooth";
            device.setNetwork_type(networkType);

            device.setIs_ignored(false);
            device.setIs_alert(false);
            device.setUser_api(ApiConfig.getApiKey(context));
            device.setUser_phone_mac(this.androidDeviceId);

            Long timestamp = getLongFromCursor(cursor, "timestamp");
            if (timestamp != null) {
                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
                isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                String isoDate = isoFormat.format(new Date(timestamp));
                device.setDetected_at(isoDate);
            }

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

    // ИЗМЕНЕНО: сделано публичным и возвращает boolean
    public boolean uploadBatch(List<ApiDevice> devices, String tableName) {
        if (devices == null || devices.isEmpty()) return false;

        Log.d(TAG, "=== UPLOAD BATCH START ===");
        Log.d(TAG, "Devices count: " + devices.size());
        Log.d(TAG, "Table name: " + tableName);
        Log.d(TAG, "Base URL from config: " + baseUrl);

        // ПРАВИЛЬНОЕ ФОРМИРОВАНИЕ URL
        String endpoint;
        if (baseUrl.contains("/api/")) {
            // Если baseUrl уже содержит /api/, используем только "devices/"
            endpoint = baseUrl + "devices/";
        } else {
            // Иначе добавляем полный путь
            endpoint = baseUrl + "api/devices/";
        }

        // Убираем возможные двойные слэши
        endpoint = endpoint.replaceAll("(?<!(http:|https:))//", "/");

        Log.d(TAG, "Final endpoint URL: " + endpoint);

        int attempt = 0;
        int maxAttempts = 3;
        long backoff = 1000;

        while (attempt < maxAttempts) {
            attempt++;
            Log.d(TAG, "Upload attempt " + attempt + " to " + endpoint);

            try {
                List<JsonObject> jsonDevices = new ArrayList<>();
                for (ApiDevice device : devices) {
                    JsonObject json = new JsonObject();
                    json.addProperty("device_id", device.getDevice_id());
                    json.addProperty("network_type", device.getNetwork_type());
                    json.addProperty("signal_strength", device.getSignal_strength());
                    json.addProperty("latitude", device.getLatitude());
                    json.addProperty("longitude", device.getLongitude());
                    json.addProperty("detected_at", device.getDetected_at());
                    json.addProperty("folder_name", tableName);
                    json.addProperty("system_folder_name", tableName);
                    json.addProperty("user_api", apiKey);
                    json.addProperty("user_phone_mac", phoneMac);
                    json.addProperty("is_alert", false);
                    json.addProperty("is_ignored", false);
                    jsonDevices.add(json);
                }

                JsonArray jsonArray = new JsonArray();
                for (JsonObject json : jsonDevices) {
                    jsonArray.add(json);
                }

                Log.d(TAG, "Request body sample: " + (jsonArray.size() > 0 ? jsonArray.get(0).toString() : "empty"));

                Request request = new Request.Builder()
                        .url(endpoint)  // ИСПОЛЬЗУЕМ ПРАВИЛЬНЫЙ endpoint
                        .post(RequestBody.create(
                                MediaType.parse("application/json"),
                                jsonArray.toString()
                        ))
                        .addHeader("Authorization", "Api-Key " + apiKey)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("X-Device-MAC", phoneMac)
                        .build();

                Response response = client.newCall(request).execute();
                Log.d(TAG, "Response code: " + response.code());

                String responseBody = response.body() != null ? response.body().string() : "null";
                Log.d(TAG, "Response body: " + responseBody);

                if (response.isSuccessful()) {
                    Log.i(TAG, "✅ SUCCESS: Uploaded " + devices.size() + " devices");
                    markDevicesAsUploaded(jsonDevices, tableName);
                    saveLastUploadTime();
                    return true;
                } else {
                    Log.e(TAG, "❌ Upload failed with code: " + response.code() + ", body: " + responseBody);

                    if (response.code() == 404) {
                        Log.e(TAG, "🔴 404 ERROR: Check if endpoint exists: " + endpoint);
                        Log.e(TAG, "🔴 Also check if baseUrl is correct: " + baseUrl);
                        Log.e(TAG, "🔴 Try accessing in browser: " + endpoint.replace("api/devices/", ""));
                    }

                    if (attempt < maxAttempts) {
                        try {
                            Thread.sleep(backoff);
                            backoff *= 2;
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Upload error (attempt " + attempt + "): " + e.getMessage());
                e.printStackTrace();

                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(backoff);
                        backoff *= 2;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        Log.e(TAG, "❌ Failed to upload after " + maxAttempts + " attempts");
        return false;
    }

    private void logDeviceDetails(List<ApiDevice> devices) {
        if (devices == null || devices.isEmpty()) return;

        Log.d(TAG, "=== DEVICE DETAILS ===");
        for (int i = 0; i < Math.min(devices.size(), 3); i++) {
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

    private void markDevicesAsUploaded(List<JsonObject> devices, String tableName) {
        if (devices == null || devices.isEmpty()) return;

        SQLiteDatabase db = null;
        try {
            db = databaseHelper.getWritableDatabase();
            db.beginTransaction();

            int successCount = 0;
            int cellCount = 0;
            int wifiCount = 0;

            for (JsonObject device : devices) {
                try {
                    String deviceId = device.get("device_id").getAsString();
                    String detectedAt = device.get("detected_at").getAsString();
                    String networkType = device.get("network_type").getAsString();

                    long timestamp = isoToTimestamp(detectedAt);

                    ContentValues values = new ContentValues();
                    values.put("is_uploaded", 1);

                    int updated = 0;

                    // Для Wi-Fi и Bluetooth - ищем по bssid
                    if (networkType.equals("WiFi") || networkType.equals("Bluetooth")) {
                        // Сначала пробуем с точным временем
                        updated = db.update(
                                "\"" + tableName + "\"",
                                values,
                                "bssid = ? AND type IN ('Wi-Fi', 'Bluetooth') AND timestamp = ? AND is_uploaded = 0",
                                new String[]{deviceId, String.valueOf(timestamp)}
                        );

                        // Если не нашли, пробуем без времени (любую непомеченную запись с таким bssid)
                        if (updated == 0) {
                            updated = db.update(
                                    "\"" + tableName + "\"",
                                    values,
                                    "bssid = ? AND type IN ('Wi-Fi', 'Bluetooth') AND is_uploaded = 0",
                                    new String[]{deviceId}
                            );
                            if (updated > 0) {
                                Log.d(TAG, "✓ Wi-Fi/Bluetooth marked (any timestamp): " + deviceId);
                                wifiCount++;
                            }
                        } else {
                            Log.d(TAG, "✓ Wi-Fi/Bluetooth marked (exact timestamp): " + deviceId);
                            wifiCount++;
                        }
                    }
                    // Для Cell Tower - ищем по cell_id
                    else if (networkType.equals("LTE") || networkType.equals("GSM") || networkType.equals("UMTS") || networkType.equals("CDMA")) {
                        // Пробуем с точным временем
                        updated = db.update(
                                "\"" + tableName + "\"",
                                values,
                                "cell_id = ? AND type = 'Cell' AND timestamp = ? AND is_uploaded = 0",
                                new String[]{deviceId, String.valueOf(timestamp)}
                        );

                        // Если не нашли, пробуем без времени
                        if (updated == 0) {
                            updated = db.update(
                                    "\"" + tableName + "\"",
                                    values,
                                    "cell_id = ? AND type = 'Cell' AND is_uploaded = 0",
                                    new String[]{deviceId}
                            );
                            if (updated > 0) {
                                Log.d(TAG, "✓ Cell tower marked (any timestamp): " + deviceId);
                                cellCount++;
                            }
                        } else {
                            Log.d(TAG, "✓ Cell tower marked (exact timestamp): " + deviceId);
                            cellCount++;
                        }
                    }

                    if (updated > 0) {
                        successCount++;
                    } else {
                        Log.w(TAG, "✗ Could not mark device: " + deviceId +
                                ", type: " + networkType +
                                ", time: " + detectedAt);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error marking device: " + e.getMessage());
                }
            }

            db.setTransactionSuccessful();
            Log.i(TAG, String.format("✅ Marked %d devices as uploaded in %s (WiFi/BT: %d, Cell: %d)",
                    successCount, tableName, wifiCount, cellCount));

        } catch (Exception e) {
            Log.e(TAG, "Error in markDevicesAsUploaded: " + e.getMessage());
        } finally {
            if (db != null) {
                try {
                    db.endTransaction();
                } catch (Exception e) {
                    Log.e(TAG, "Error ending transaction: " + e.getMessage());
                }
                db.close();
            }
        }
    }

    private long isoToTimestamp(String isoDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(isoDate);
            return date != null ? date.getTime() : System.currentTimeMillis();
        } catch (Exception e) {
            Log.e(TAG, "Error parsing date: " + isoDate + " - " + e.getMessage());
            return System.currentTimeMillis();
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

    private void sendUploadSuccessNotification(Context context, int count) {
        String channelId = "upload_notifications";
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

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

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd.MM.yyyy", Locale.getDefault());
        String currentTime = sdf.format(new Date());

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

    /**
     * Получить количество ожидающих отправки устройств
     */
    public int getPendingDevicesCount() {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        int count = 0;

        try {
            Cursor cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM \"unified_data\" WHERE is_uploaded = 0",
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                count = cursor.getInt(0);
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting pending count: " + e.getMessage());
        } finally {
            db.close();
        }

        return count;
    }

    /**
     * Получить статистику по типам устройств в очереди
     */
    public String getQueueStats() {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        StringBuilder stats = new StringBuilder();

        try {
            Cursor cursor = db.rawQuery(
                    "SELECT type, COUNT(*) as count FROM \"unified_data\" WHERE is_uploaded = 0 GROUP BY type",
                    null
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String type = cursor.getString(0);
                    int count = cursor.getInt(1);
                    stats.append(type).append(": ").append(count).append(", ");
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting stats: " + e.getMessage());
        } finally {
            db.close();
        }

        return stats.length() > 0 ? stats.toString() : "empty";
    }

    /**
     * Получить время до следующей плановой отправки
     */
    public static long getTimeToNextUpload() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLast = currentTime - DeviceUploadWorker.lastUploadTime;
        long timeToNext = 60000 - timeSinceLast;
        return timeToNext > 0 ? timeToNext : 0;
    }


    public void cleanup() {
        // Не нужно закрывать соединения
    }
}