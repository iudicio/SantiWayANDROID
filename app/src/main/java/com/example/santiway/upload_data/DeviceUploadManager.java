package com.example.santiway.upload_data;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DeviceUploadManager {
    private static final String TAG = "DeviceUploadManager";
    private static final String PREFS_NAME = "DeviceUploadPrefs";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_LAST_UPLOAD_TIME = "last_upload_time";
    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private Context context;
    private MainDatabaseHelper databaseHelper;
    private SharedPreferences prefs;
    private String androidDeviceId;
    private String apiKey;
    private String baseUrl;
    private String phoneMac;
    private OkHttpClient client;

    public DeviceUploadManager(Context context) {
        this.context = context;
        this.databaseHelper = new MainDatabaseHelper(context);
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.androidDeviceId = getOrCreateDeviceId();

        ApiConfig.initialize(context);
        this.apiKey = ApiConfig.getApiKey(context);
        this.baseUrl = ApiConfig.getApiBaseUrl();
        this.phoneMac = ApiConfig.getPhoneMac(context);

        Log.d(TAG, "=== DeviceUploadManager INIT ===");
        Log.d(TAG, "API Key: " + (apiKey != null ? "configured" : "null"));
        Log.d(TAG, "Base URL: " + baseUrl);
        Log.d(TAG, "Phone MAC: " + phoneMac);

        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private String getOrCreateDeviceId() {
        String id = prefs.getString(KEY_DEVICE_ID, null);
        if (id == null) {
            id = "android-" + UUID.randomUUID().toString();
            prefs.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    public static long getLastUploadTime(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_LAST_UPLOAD_TIME, 0);
    }

    private void saveLastUploadTime() {
        long currentTime = System.currentTimeMillis();
        prefs.edit().putLong(KEY_LAST_UPLOAD_TIME, currentTime).apply();
        Log.d(TAG, "Last upload time saved: " + currentTime);
    }

    /**
     * ПОЛУЧАЕТ ДАННЫЕ ДЛЯ ОТПРАВКИ ИЗ Основная
     * Важно: получаем записи с is_uploaded = 0
     */
    public List<PendingUpload> getPendingUploadsBatch() {
        List<PendingUpload> items = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = databaseHelper.getReadableDatabase();

            List<String> tables = databaseHelper.getAllTables(); // уже без *_unique
            if (tables == null || tables.isEmpty()) {
                Log.d(TAG, "No regular tables found for upload");
                return items;
            }

            for (String tableName : tables) {
                if (items.size() >= BATCH_SIZE) break;

                if (tableName == null || tableName.trim().isEmpty()) {
                    continue;
                }

                int remaining = BATCH_SIZE - items.size();

                String query =
                        "SELECT id, * FROM \"" + tableName + "\" " +
                                "WHERE is_uploaded = 0 " +
                                "ORDER BY timestamp ASC LIMIT " + remaining;

                try {
                    cursor = db.rawQuery(query, null);

                    if (cursor != null && cursor.moveToFirst()) {
                        int idCol = cursor.getColumnIndexOrThrow("id");

                        do {
                            long rowId = cursor.getLong(idCol);
                            ApiDevice device = cursorToApiDevice(cursor, tableName);

                            if (device != null) {
                                items.add(new PendingUpload(rowId, tableName, device));
                                Log.d(TAG,
                                        "Pending rowId=" + rowId +
                                                ", table=" + tableName +
                                                ", device=" + device.getDevice_id() +
                                                ", detected_at=" + device.getDetected_at());
                            }
                        } while (cursor.moveToNext());
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                        cursor = null;
                    }
                }
            }

            Log.d(TAG, "Found " + items.size() + " pending uploads from regular tables");

        } catch (Exception e) {
            Log.e(TAG, "Error getting pending uploads: " + e.getMessage(), e);
        } finally {
            if (cursor != null) cursor.close();
            if (db != null && db.isOpen()) db.close();
        }

        return items;
    }

    /**
     * Конвертация курсора в ApiDevice для отправки
     * ВАЖНО: берем данные из Основная, но для MAC и времени используем точные значения
     */
    private ApiDevice cursorToApiDevice(Cursor cursor, String sourceTableName) {
        try {
            ApiDevice device = new ApiDevice();

            String type = getStringFromCursor(cursor, "type");
            String bssid = getStringFromCursor(cursor, "bssid");
            Integer cellId = getIntFromCursor(cursor, "cell_id");

            // Уникальный идентификатор: для Wi-Fi/Bluetooth - MAC, для Cell - cell_id
            String deviceId;
            if ("Wi-Fi".equals(type) || "Bluetooth".equals(type)) {
                deviceId = bssid;
            } else {
                deviceId = cellId != null ? cellId.toString() : null;
            }

            device.setDevice_id(deviceId);
            device.setLatitude(getDoubleFromCursor(cursor, "latitude"));
            device.setLongitude(getDoubleFromCursor(cursor, "longitude"));
            device.setSignal_strength(getIntFromCursor(cursor, "signal_strength"));

            String networkType = "Unknown";
            if ("Wi-Fi".equals(type)) networkType = "WiFi";
            else if ("Cell".equals(type)) {
                String netType = getStringFromCursor(cursor, "network_type");
                networkType = netType != null ? netType : "LTE";
            }
            else if ("Bluetooth".equals(type)) networkType = "Bluetooth";
            device.setNetwork_type(networkType);

            device.setIs_ignored(false);
            device.setIs_alert(false);
            device.setUser_api(apiKey);
            device.setUser_phone_mac(phoneMac);
            String name = getStringFromCursor(cursor, "name");
            device.setDevice_name(name);

            Long timestamp = getLongFromCursor(cursor, "timestamp");
            if (timestamp != null) {
                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
                isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                String isoDate = isoFormat.format(new Date(timestamp));
                device.setDetected_at(isoDate);
            }

            String systemFolderName = getStringFromCursor(cursor, "folder_name");
            if (systemFolderName == null || systemFolderName.trim().isEmpty()) {
                systemFolderName = sourceTableName;
            }
            device.setSystem_folder_name(systemFolderName);
            device.setFolder_name(getDisplayFolderName(systemFolderName));


            return device;

        } catch (Exception e) {
            Log.e(TAG, "Error converting cursor to ApiDevice: " + e.getMessage(), e);
            return null;
        }
    }

    // Вспомогательные методы
    private String getStringFromCursor(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index != -1 ? cursor.getString(index) : null;
    }

    private String getDisplayFolderName(String systemFolderName) {
        if ("Основная".equals(systemFolderName)) {
            return "Основная";
        }
        return systemFolderName;
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

    /**
     * ОТПРАВКА БАТЧА ДАННЫХ НА СЕРВЕР
     */
    public boolean uploadBatch(List<PendingUpload> items) {
        if (items == null || items.isEmpty()) return false;

        Log.d(TAG, "=== UPLOAD BATCH START ===");
        Log.d(TAG, "Items count: " + items.size());

        String endpoint;
        if (baseUrl.contains("/api/")) {
            endpoint = baseUrl + "devices/";
        } else {
            endpoint = baseUrl + "api/devices/";
        }
        endpoint = endpoint.replaceAll("(?<!(http:|https:))//", "/");

        Log.d(TAG, "Final endpoint URL: " + endpoint);

        int attempt = 0;
        long backoff = 1000;

        while (attempt < MAX_RETRY_ATTEMPTS) {
            attempt++;
            Log.d(TAG, "Upload attempt " + attempt);

            try {
                JsonArray jsonArray = new JsonArray();

                for (PendingUpload item : items) {
                    ApiDevice device = item.device;

                    JsonObject json = new JsonObject();
                    json.addProperty("device_id", device.getDevice_id());
                    json.addProperty("device_name", device.getDevice_name());
                    json.addProperty("network_type", device.getNetwork_type());
                    json.addProperty("signal_strength", device.getSignal_strength());
                    json.addProperty("latitude", device.getLatitude());
                    json.addProperty("longitude", device.getLongitude());
                    json.addProperty("detected_at", device.getDetected_at());
                    json.addProperty("folder_name", device.getFolder_name());
                    json.addProperty("system_folder_name", device.getSystem_folder_name());
                    json.addProperty("user_api", apiKey);
                    json.addProperty("user_phone_mac", phoneMac);
                    json.addProperty("is_alert", false);
                    json.addProperty("is_ignored", false);
                    jsonArray.add(json);
                }

                Log.d(TAG, "UPLOAD JSON: " + jsonArray.toString());

                Request request = new Request.Builder()
                        .url(endpoint)
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

                if (response.isSuccessful()) {
                    Log.i(TAG, "✅ SUCCESS: Uploaded " + items.size() + " devices");

                    markRowsAsUploaded(items);

                    saveLastUploadTime();

                    android.content.Intent intent =
                            new android.content.Intent("com.example.santiway.UPLOAD_COMPLETED");
                    intent.putExtra("device_count", items.size());
                    intent.putExtra("timestamp", System.currentTimeMillis());
                    androidx.localbroadcastmanager.content.LocalBroadcastManager
                            .getInstance(context).sendBroadcast(intent);

                    return true;
                } else {
                    String responseBody = response.body() != null ? response.body().string() : "null";
                    Log.e(TAG, "❌ Upload failed with code: " + response.code() + ", body: " + responseBody);

                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        Thread.sleep(backoff);
                        backoff *= 2;
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Upload error (attempt " + attempt + "): " + e.getMessage());

                if (attempt < MAX_RETRY_ATTEMPTS) {
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

        Log.e(TAG, "❌ Failed to upload after " + MAX_RETRY_ATTEMPTS + " attempts");
        return false;
    }

    /**
     * ПОМЕЧАЕТ ОТПРАВЛЕННЫЕ УСТРОЙСТВА В Основная
     * Важно: помечаем по связке device_id + timestamp
     */
    private void markRowsAsUploaded(List<PendingUpload> items) {
        if (items == null || items.isEmpty()) return;

        SQLiteDatabase db = null;

        try {
            db = databaseHelper.getWritableDatabase();
            db.beginTransaction();

            Map<String, List<Long>> idsByTable = new HashMap<>();

            for (PendingUpload item : items) {
                if (item == null || item.tableName == null || item.tableName.trim().isEmpty()) {
                    continue;
                }

                List<Long> ids = idsByTable.get(item.tableName);
                if (ids == null) {
                    ids = new ArrayList<>();
                    idsByTable.put(item.tableName, ids);
                }
                ids.add(item.rowId);
            }

            for (Map.Entry<String, List<Long>> entry : idsByTable.entrySet()) {
                String tableName = entry.getKey();
                List<Long> ids = entry.getValue();

                if (ids == null || ids.isEmpty()) continue;

                for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
                    int end = Math.min(start + BATCH_SIZE, ids.size());
                    List<Long> batch = ids.subList(start, end);

                    StringBuilder placeholders = new StringBuilder();
                    String[] args = new String[batch.size()];

                    for (int i = 0; i < batch.size(); i++) {
                        if (i > 0) placeholders.append(",");
                        placeholders.append("?");
                        args[i] = String.valueOf(batch.get(i));
                    }

                    ContentValues values = new ContentValues();
                    values.put("is_uploaded", 1);

                    int updated = db.update(
                            "\"" + tableName + "\"",
                            values,
                            "id IN (" + placeholders + ")",
                            args
                    );

                    Log.d(TAG, "Marked uploaded in table " + tableName + ": " + updated + " rows");
                }
            }

            db.setTransactionSuccessful();

        } catch (Exception e) {
            Log.e(TAG, "Error marking rows as uploaded: " + e.getMessage(), e);
        } finally {
            if (db != null && db.isOpen()) {
                try {
                    if (db.inTransaction()) db.endTransaction();
                } catch (Exception ignored) {}
                db.close();
            }
        }
    }

    // Вспомогательный метод для отладки
    private void checkIfRecordExists(SQLiteDatabase db, String deviceId, long timestamp) {
        try {
            Cursor cursor = null;
            if (deviceId.contains(":")) {
                cursor = db.rawQuery(
                        "SELECT timestamp, is_uploaded FROM \"Основная\" WHERE bssid = ? ORDER BY timestamp DESC LIMIT 5",
                        new String[]{deviceId}
                );
            } else {
                cursor = db.rawQuery(
                        "SELECT timestamp, is_uploaded FROM \"Основная\" WHERE cell_id = ? ORDER BY timestamp DESC LIMIT 5",
                        new String[]{deviceId}
                );
            }

            if (cursor != null) {
                Log.d(TAG, "Recent records for " + deviceId + ":");
                while (cursor.moveToNext()) {
                    long dbTime = cursor.getLong(0);
                    int uploaded = cursor.getInt(1);
                    Log.d(TAG, "  - timestamp: " + dbTime + " (" + new Date(dbTime) +
                            "), is_uploaded: " + uploaded + ", diff: " + (dbTime - timestamp) + "ms");
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking records: " + e.getMessage());
        }
    }

    private long isoToTimestamp(String isoDate) {
        try {
            // Пробуем разные форматы
            String[] formats = {
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",     // UTC
                    "yyyy-MM-dd'T'HH:mm:ss",        // без Z
                    "yyyy-MM-dd HH:mm:ss"            // простой формат
            };

            for (String format : formats) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                    Date date = sdf.parse(isoDate);
                    if (date != null) {
                        long timestamp = date.getTime();
                        Log.d(TAG, "Parsed " + isoDate + " to " + timestamp + " using format: " + format);
                        return timestamp;
                    }
                } catch (Exception e) {
                    // пробуем следующий формат
                }
            }

            // Если ничего не помогло, пробуем как число
            try {
                return Long.parseLong(isoDate);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Could not parse date: " + isoDate);
                return System.currentTimeMillis();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing date: " + isoDate + " - " + e.getMessage());
            return System.currentTimeMillis();
        }
    }

    public int getPendingDevicesCount() {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        int totalCount = 0;

        try {
            db = databaseHelper.getReadableDatabase();
            List<String> tables = databaseHelper.getAllTables();

            if (tables == null || tables.isEmpty()) {
                return 0;
            }

            for (String tableName : tables) {
                if (tableName == null || tableName.trim().isEmpty()) {
                    continue;
                }

                try {
                    cursor = db.rawQuery(
                            "SELECT COUNT(*) FROM \"" + tableName + "\" WHERE is_uploaded = 0",
                            null
                    );

                    if (cursor != null && cursor.moveToFirst()) {
                        totalCount += cursor.getInt(0);
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                        cursor = null;
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting pending count: " + e.getMessage(), e);
        } finally {
            if (cursor != null) cursor.close();
            if (db != null && db.isOpen()) db.close();
        }

        return totalCount;
    }

    public void cleanup() {
        // Ничего не делаем
    }

    public static class PendingUpload {
        public final long rowId;
        public final String tableName;
        public final ApiDevice device;

        public PendingUpload(long rowId, String tableName, ApiDevice device) {
            this.rowId = rowId;
            this.tableName = tableName;
            this.device = device;
        }
    }
}

