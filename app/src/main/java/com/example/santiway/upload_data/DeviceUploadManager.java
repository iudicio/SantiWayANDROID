package com.example.santiway.upload_data;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Log;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
public class DeviceUploadManager {
    private static final String TAG = "DeviceUploadManager";
    private static final String PREFS_NAME = "DeviceUploadPrefs";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final int BATCH_SIZE = 200;

    private Context context;
    private MainDatabaseHelper databaseHelper;
    private SharedPreferences prefs;
    private String androidDeviceId;
    private Connection rabbitMQConnection;
    private Channel rabbitMQChannel;

    public DeviceUploadManager(Context context) {
        this.context = context;
        this.databaseHelper = new MainDatabaseHelper(context);
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.androidDeviceId = getOrCreateDeviceId();
        initializeRabbitMQ();
    }

    private String getOrCreateDeviceId() {
        String id = prefs.getString(KEY_DEVICE_ID, null);
        if (id == null) {
            id = "android-" + UUID.randomUUID().toString();
            prefs.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    private void initializeRabbitMQ() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(RabbitMQConfig.getConnectionString());
            factory.setAutomaticRecoveryEnabled(true);
            factory.setNetworkRecoveryInterval(10000); // 10 seconds

            rabbitMQConnection = factory.newConnection();
            rabbitMQChannel = rabbitMQConnection.createChannel();

            // Declare queues (idempotent - will only create if doesn't exist)
            rabbitMQChannel.queueDeclare(RabbitMQConfig.VENDOR_QUEUE, true, false, false, null);
            rabbitMQChannel.queueDeclare(RabbitMQConfig.ES_WRITER_QUEUE, true, false, false, null);

            Log.d(TAG, "RabbitMQ connection established");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize RabbitMQ connection: " + e.getMessage(), e);
        }
    }

    public List<RabbitMQDevice> getPendingDevicesBatch() {
        List<RabbitMQDevice> devices = new ArrayList<>();

        // Получаем устройства из всех таблиц, которые еще не были отправлены
        List<String> tables = databaseHelper.getAllTables();
        for (String table : tables) {
            if (devices.size() >= BATCH_SIZE) break;

            List<RabbitMQDevice> tableDevices = getDevicesFromTable(table, BATCH_SIZE - devices.size());
            devices.addAll(tableDevices);
        }

        return devices;
    }

    private List<RabbitMQDevice> getDevicesFromTable(String tableName, int limit) {
        List<RabbitMQDevice> devices = new ArrayList<>();

        // TODO: Добавить поле is_uploaded в базу данных для отслеживания отправленных устройств
        String query = "SELECT * FROM \"" + tableName + "\" WHERE is_uploaded = 0 ORDER BY timestamp DESC LIMIT " + limit;

        Cursor cursor = null;
        try {
            cursor = databaseHelper.getReadableDatabase().rawQuery(query, null);

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    RabbitMQDevice device = cursorToRabbitMQDevice(cursor, tableName);
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

    private RabbitMQDevice cursorToRabbitMQDevice(Cursor cursor, String tableName) {
        try {
            RabbitMQDevice device = new RabbitMQDevice();

            // Общие поля
            String type = getStringFromCursor(cursor, "type");
            device.setType(type);
            device.setName(getStringFromCursor(cursor, "name"));
            device.setSignal_strength(getIntFromCursor(cursor, "signal_strength"));
            device.setLatitude(getDoubleFromCursor(cursor, "latitude"));
            device.setLongitude(getDoubleFromCursor(cursor, "longitude"));
            device.setAltitude(getDoubleFromCursor(cursor, "altitude"));
            device.setLocation_accuracy(getFloatFromCursor(cursor, "location_accuracy"));
            device.setTimestamp(getLongFromCursor(cursor, "timestamp"));
            device.setStatus(getStringFromCursor(cursor, "status"));

            // Определяем device_id в зависимости от типа устройства
            if ("Wi-Fi".equals(type) || "Bluetooth".equals(type)) {
                String bssid = getStringFromCursor(cursor, "bssid");
                device.setDevice_id(bssid);
                device.setVendor(getStringFromCursor(cursor, "vendor"));
                device.setFrequency(getIntFromCursor(cursor, "frequency"));
                device.setCapabilities(getStringFromCursor(cursor, "capabilities"));
            } else if ("Cell".equals(type)) {
                Integer cellId = getIntFromCursor(cursor, "cell_id");
                device.setDevice_id(cellId != null ? cellId.toString() : null);
                device.setCell_id(cellId);
                device.setLac(getIntFromCursor(cursor, "lac"));
                device.setMcc(getIntFromCursor(cursor, "mcc"));
                device.setMnc(getIntFromCursor(cursor, "mnc"));
                device.setPsc(getIntFromCursor(cursor, "psc"));
                device.setPci(getIntFromCursor(cursor, "pci"));
                device.setTac(getIntFromCursor(cursor, "tac"));
                device.setEarfcn(getIntFromCursor(cursor, "earfcn"));
                device.setArfcn(getIntFromCursor(cursor, "arfcn"));
                device.setSignal_quality(getIntFromCursor(cursor, "signal_quality"));
                device.setNetwork_type(getStringFromCursor(cursor, "network_type"));
                device.setIs_registered(getBooleanFromCursor(cursor, "is_registered"));
                device.setIs_neighbor(getBooleanFromCursor(cursor, "is_neighbor"));
            }

            return device;

        } catch (Exception e) {
            Log.e(TAG, "Error converting cursor to RabbitMQDevice: " + e.getMessage(), e);
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

    private Float getFloatFromCursor(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index != -1 && !cursor.isNull(index) ? cursor.getFloat(index) : null;
    }

    private Long getLongFromCursor(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index != -1 && !cursor.isNull(index) ? cursor.getLong(index) : null;
    }

    private Boolean getBooleanFromCursor(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index != -1 && !cursor.isNull(index) ? cursor.getInt(index) == 1 : null;
    }

    public boolean uploadBatch(List<RabbitMQDevice> devices) {
        if (devices == null || devices.isEmpty()) {
            Log.d(TAG, "No devices to upload");
            return true;
        }

        if (rabbitMQChannel == null || !rabbitMQChannel.isOpen()) {
            Log.e(TAG, "RabbitMQ channel is not available");
            return false;
        }

        try {
            // Конвертируем устройства в JSON массив
            JSONArray devicesArray = new JSONArray();
            for (RabbitMQDevice device : devices) {
                JSONObject deviceJson = convertDeviceToJson(device);
                if (deviceJson != null) {
                    devicesArray.put(deviceJson);
                }
            }

            if (devicesArray.length() == 0) {
                Log.w(TAG, "No valid devices to upload after JSON conversion");
                return true;
            }

            // Создаем сообщение для RabbitMQ в формате Celery task
            JSONObject taskMessage = new JSONObject();
            taskMessage.put("task", RabbitMQConfig.VENDOR_TASK_NAME);
            taskMessage.put("id", UUID.randomUUID().toString());
            taskMessage.put("args", new JSONArray().put(devicesArray.toString()));
            taskMessage.put("kwargs", new JSONObject());

            // Отправляем в очередь vendor_queue
            rabbitMQChannel.basicPublish(
                    "", // exchange - default exchange
                    RabbitMQConfig.VENDOR_QUEUE, // routing key = queue name
                    null, // properties
                    taskMessage.toString().getBytes("UTF-8")
            );

            Log.d(TAG, "Successfully sent " + devicesArray.length() + " devices to RabbitMQ vendor_queue");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error uploading batch to RabbitMQ: " + e.getMessage(), e);
            return false;
        }
    }

    private JSONObject convertDeviceToJson(RabbitMQDevice device) throws JSONException {
        if (device == null) return null;

        JSONObject json = new JSONObject();

        // Форматирование timestamp в ISO 8601 (удобно для Elasticsearch)
        if (device.getTimestamp() != null) {
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            String isoDate = isoFormat.format(new Date(device.getTimestamp()));
            json.put("timestamp_iso", isoDate);
        }

        // Добавляем только не-null поля
        putIfNotNull(json, "device_id", device.getDevice_id());
        putIfNotNull(json, "type", device.getType());
        putIfNotNull(json, "name", device.getName());
        putIfNotNull(json, "signal_strength", device.getSignal_strength());
        putIfNotNull(json, "frequency", device.getFrequency());
        putIfNotNull(json, "capabilities", device.getCapabilities());
        putIfNotNull(json, "vendor", device.getVendor());
        putIfNotNull(json, "cell_id", device.getCell_id());
        putIfNotNull(json, "lac", device.getLac());
        putIfNotNull(json, "mcc", device.getMcc());
        putIfNotNull(json, "mnc", device.getMnc());
        putIfNotNull(json, "psc", device.getPsc());
        putIfNotNull(json, "pci", device.getPci());
        putIfNotNull(json, "tac", device.getTac());
        putIfNotNull(json, "earfcn", device.getEarfcn());
        putIfNotNull(json, "arfcn", device.getArfcn());
        putIfNotNull(json, "signal_quality", device.getSignal_quality());
        putIfNotNull(json, "network_type", device.getNetwork_type());
        putIfNotNull(json, "is_registered", device.getIs_registered());
        putIfNotNull(json, "is_neighbor", device.getIs_neighbor());
        putIfNotNull(json, "latitude", device.getLatitude());
        putIfNotNull(json, "longitude", device.getLongitude());
        putIfNotNull(json, "altitude", device.getAltitude());
        putIfNotNull(json, "location_accuracy", device.getLocation_accuracy());
        putIfNotNull(json, "timestamp", device.getTimestamp());
        putIfNotNull(json, "status", device.getStatus());

        return json;
    }

    private void putIfNotNull(JSONObject json, String key, Object value) throws JSONException {
        if (value != null) {
            json.put(key, value);
        }
    }

    public void markDevicesAsUploaded(List<RabbitMQDevice> devices) {
        // TODO: Реализовать обновление поля is_uploaded в базе данных
        // Пока просто логируем
        Log.d(TAG, "Marking " + devices.size() + " devices as uploaded in database");
    }

    public void cleanup() {
        try {
            if (rabbitMQChannel != null && rabbitMQChannel.isOpen()) {
                rabbitMQChannel.close();
            }
            if (rabbitMQConnection != null && rabbitMQConnection.isOpen()) {
                rabbitMQConnection.close();
            }
        } catch (IOException | TimeoutException e) {
            Log.e(TAG, "Error cleaning up RabbitMQ connection: " + e.getMessage(), e);
        }
    }
}