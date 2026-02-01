package com.example.santiway.upload_data;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.santiway.NotificationData;
import com.example.santiway.NotificationDatabaseHelper;
import com.example.santiway.NotificationsActivity;
import com.example.santiway.R;
import com.example.santiway.cell_scanner.CellTower;
import com.example.santiway.wifi_scanner.WifiDevice;
import java.util.ArrayList;
import java.util.List;
import com.example.santiway.bluetooth_scanner.BluetoothDevice;
import com.example.santiway.DeviceListActivity;

public class MainDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "MainDatabaseHelper";
    private static final String DATABASE_NAME = "UnifiedScanner.db";
    private static final int DATABASE_VERSION = 7; // УВЕЛИЧЕНО
    private final Context mContext;

    public MainDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Создание единой таблицы для всех данных
        String createUnifiedTable = "CREATE TABLE \"unified_data\" (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "type TEXT NOT NULL," + // 'Wi-Fi' или 'Cell'
                "name TEXT," + // SSID или Operator name
                "bssid TEXT," + // BSSID для Wi-Fi
                "signal_strength INTEGER," +
                "frequency INTEGER," +
                "capabilities TEXT," +
                "vendor TEXT," +
                "cell_id INTEGER," +
                "lac INTEGER," +
                "mcc INTEGER," +
                "mnc INTEGER," +
                "psc INTEGER," +
                "pci INTEGER," +
                "tac INTEGER," +
                "earfcn INTEGER," +
                "arfcn INTEGER," +
                "signal_quality INTEGER," +
                "network_type TEXT," +
                "is_registered INTEGER," +
                "is_neighbor INTEGER," +
                "latitude REAL," +
                "longitude REAL," +
                "altitude REAL," +
                "location_accuracy REAL," +
                "timestamp LONG," +
                "status TEXT DEFAULT 'ignore'," +
                "is_uploaded INTEGER DEFAULT 0" +
                ");";
        db.execSQL(createUnifiedTable);
    }
    public void deleteOldRecordsFromAllTables(long maxAgeMillis) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            // Получаем список таблиц напрямую через один запрос к базе
            Cursor cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
                    null
            );

            if (cursor != null) {
                try {
                    long cutoffTime = System.currentTimeMillis() - maxAgeMillis;

                    while (cursor.moveToNext()) {
                        String table = cursor.getString(0);
                        try {
                            db.delete("\"" + table + "\"", "timestamp < ?", new String[]{String.valueOf(cutoffTime)});
                        } catch (Exception e) {
                            Log.e(TAG, "Error deleting old records from table " + table + ": " + e.getMessage());
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting old records: " + e.getMessage());
        } finally {
            db.close();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 6) {
            // Добавляем поле is_uploaded в существующие таблицы
            // Вместо вызова getAllTables() работаем напрямую с переданной db
            try {
                // Получаем список таблиц напрямую через SQL запрос
                Cursor cursor = db.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
                        null
                );

                if (cursor != null) {
                    try {
                        while (cursor.moveToNext()) {
                            String tableName = cursor.getString(0);
                            try {
                                db.execSQL("ALTER TABLE \"" + tableName + "\" ADD COLUMN is_uploaded INTEGER DEFAULT 0");
                            } catch (Exception e) {
                                Log.e(TAG, "Error adding is_uploaded column to table " + tableName + ": " + e.getMessage());
                            }
                        }
                    } finally {
                        cursor.close();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during database upgrade: " + e.getMessage());
            }
        }
        if (oldVersion < 5) {
            // Удаляем старые таблицы, если они существуют, и создаем новую
            db.execSQL("DROP TABLE IF EXISTS \"wifi_data\"");
            db.execSQL("DROP TABLE IF EXISTS \"cell_data\"");
            onCreate(db);
        }
    }
    public long addBluetoothDevice(BluetoothDevice device, String tableName) {
        ContentValues values = new ContentValues();
        values.put("type", "Bluetooth"); // Тип устройства
        values.put("name", device.getDeviceName());
        values.put("bssid", device.getMacAddress()); // Используем bssid для MAC-адреса Bluetooth
        values.put("signal_strength", device.getSignalStrength());
        values.put("vendor", device.getVendor());

        // Общие поля геолокации и времени
        values.put("latitude", device.getLatitude());
        values.put("longitude", device.getLongitude());
        values.put("altitude", device.getAltitude());
        values.put("location_accuracy", device.getLocationAccuracy());
        values.put("timestamp", device.getTimestamp());
        values.put("status", "scanned"); // Статус

        // Логика поиска/обновления: ищем по MAC-адресу и типу "Bluetooth"
        String selection = "bssid = ? AND type = ?";
        String[] selectionArgs = {device.getMacAddress(), "Bluetooth"};

        // Используем существующий общий метод addOrUpdateUnifiedDevice
        return addOrUpdateUnifiedDevice(tableName, values, selection, selectionArgs, device.getTimestamp());
    }
    public long addWifiDevice(WifiDevice device, String tableName) {
        ContentValues values = new ContentValues();
        values.put("type", "Wi-Fi");
        values.put("name", device.getSsid());
        values.put("bssid", device.getBssid());
        values.put("signal_strength", device.getSignalStrength());
        values.put("frequency", device.getFrequency());
        values.put("capabilities", device.getCapabilities());
        values.put("vendor", device.getVendor());
        values.put("latitude", device.getLatitude());
        values.put("longitude", device.getLongitude());
        values.put("altitude", device.getAltitude());
        values.put("location_accuracy", device.getLocationAccuracy());
        values.put("timestamp", device.getTimestamp());
        values.put("status", "ignore");

        String selection = "bssid = ?";
        String[] selectionArgs = {device.getBssid()};
        return addOrUpdateUnifiedDevice(tableName, values, selection, selectionArgs, device.getTimestamp());
    }

    public long addCellTower(CellTower tower, String tableName) {
        ContentValues values = new ContentValues();
        values.put("type", "Cell");
        values.put("name", tower.getOperatorName());
        values.put("cell_id", tower.getCellId());
        values.put("lac", tower.getLac());
        values.put("mcc", tower.getMcc());
        values.put("mnc", tower.getMnc());
        values.put("psc", tower.getPsc());
        values.put("pci", tower.getPci());
        values.put("tac", tower.getTac());
        values.put("earfcn", tower.getEarfcn());
        values.put("arfcn", tower.getArfcn());
        values.put("signal_strength", tower.getSignalStrength());
        values.put("signal_quality", tower.getSignalQuality());
        values.put("network_type", tower.getNetworkType());
        values.put("is_registered", tower.isRegistered() ? 1 : 0);
        values.put("is_neighbor", tower.isNeighbor() ? 1 : 0);
        values.put("latitude", tower.getLatitude());
        values.put("longitude", tower.getLongitude());
        values.put("altitude", tower.getAltitude());
        values.put("location_accuracy", tower.getLocationAccuracy());
        values.put("timestamp", tower.getTimestamp());
        values.put("status", "ignore");

        String selection = "cell_id = ? AND network_type = ?";
        String[] selectionArgs = {String.valueOf(tower.getCellId()), tower.getNetworkType()};
        return addOrUpdateUnifiedDevice(tableName, values, selection, selectionArgs, tower.getTimestamp());
    }

    private long addOrUpdateUnifiedDevice(String tableName, ContentValues values, String selection, String[] selectionArgs, long newTimestamp) {
        SQLiteDatabase db = this.getWritableDatabase();
        long result = -1;
        Cursor cursor = null;

        try {
            // 1. Определяем уникальный ID устройства (MAC-адрес или Cell ID вышки)
            String uniqueId = values.getAsString("bssid");
            if (uniqueId == null && values.containsKey("cell_id")) {
                uniqueId = String.valueOf(values.getAsInteger("cell_id"));
            }

            if (uniqueId != null) {
                double curLat = values.getAsDouble("latitude");
                double curLon = values.getAsDouble("longitude");

                // 2. Рассчитываем статус (ваша логика движения)
                String calculatedStatus = evaluateTargetStatus(uniqueId, curLat, curLon, newTimestamp, tableName);
                values.put("status", calculatedStatus);

                // 3. Если статус стал "Target", обрабатываем уведомление
                if ("Target".equals(calculatedStatus)) {
                    NotificationDatabaseHelper notifDb = new NotificationDatabaseHelper(mContext);

                    // Проверка на уникальность (чтобы не спамить звуком каждую секунду)
                    if (notifDb.isUniqueAlert(uniqueId)) {
                        String deviceName = values.getAsString("name");
                        String type = values.getAsString("type");

                        // Сохраняем в локальную базу данных для вкладки
                        NotificationData alert = new NotificationData(
                                java.util.UUID.randomUUID().toString(),
                                "Target: " + type,
                                "Устройство " + (deviceName != null ? deviceName : uniqueId) + " в движении!",
                                new java.util.Date(),
                                NotificationData.NotificationType.ALARM,
                                null, null, curLat, curLon
                        );
                        notifDb.addNotification(alert, uniqueId);

                        // 4. ЗАПУСКАЕМ ЗВУК И ВИБРАЦИЮ (Системный Push)
                        sendSystemNotification(
                                "Обнаружен ТАРГЕТ (" + type + ")",
                                "Объект " + (deviceName != null ? deviceName : uniqueId) + " перемещается!"
                        );
                    }
                }
            }

            // 5. Работа с основной базой данных (Update или Insert)
            db.beginTransaction();
            cursor = db.query("\"" + tableName + "\"", new String[]{"id", "timestamp", "status"}, selection, selectionArgs, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                long existingId = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                long existingTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));
                String oldStatus = cursor.getString(cursor.getColumnIndexOrThrow("status"));

                // Если устройство уже было в статусе Target, не позволяем сменить его обратно на scanned
                if ("Target".equals(oldStatus)) {
                    values.put("status", "Target");
                }

                if (newTimestamp >= existingTimestamp) {
                    result = db.update("\"" + tableName + "\"", values, "id = ?", new String[]{String.valueOf(existingId)});
                }
            } else {
                result = db.insert("\"" + tableName + "\"", null, values);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            android.util.Log.e("STATUS_CHECK", "Ошибка в addOrUpdateUnifiedDevice: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
            if (db != null && db.isOpen()) {
                db.endTransaction();
            }
        }
        return result;
    }

    public List<DeviceListActivity.Device> getAllDataFromTable(String tableName) {
        List<DeviceListActivity.Device> deviceList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT type, name, latitude, longitude, timestamp " +
                            "FROM \"" + tableName + "\" " +
                            "ORDER BY timestamp DESC", // НОВЫЕ записи будут первыми
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                    double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
                    double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));

                    String locationStr = String.format("Lat: %.4f, Lon: %.4f", latitude, longitude);
                    String timeStr = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(timestamp));

                    deviceList.add(new DeviceListActivity.Device(name, type, locationStr, timeStr));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting data from table " + tableName + ": " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return deviceList;
    }

    public List<DeviceListActivity.Device> getAllDataFromTableWithPagination(
            String tableName,
            int offset,
            int limit) {

        List<DeviceListActivity.Device> deviceList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            // Добавляем bssid (MAC адрес) в запрос
            cursor = db.rawQuery(
                    "SELECT type, name, bssid, latitude, longitude, timestamp " +
                            "FROM \"" + tableName + "\" " +
                            "WHERE type IN ('Wi-Fi', 'Bluetooth') " + // Только устройства с MAC
                            "ORDER BY timestamp DESC " +
                            "LIMIT ? OFFSET ?",
                    new String[]{String.valueOf(limit), String.valueOf(offset)}
            );

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                    String mac = cursor.getString(cursor.getColumnIndexOrThrow("bssid"));
                    double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
                    double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));

                    String locationStr = String.format("Lat: %.4f, Lon: %.4f", latitude, longitude);
                    String timeStr = new java.text.SimpleDateFormat("HH:mm:ss")
                            .format(new java.util.Date(timestamp));

                    // Теперь создаем Device с MAC адресом
                    deviceList.add(new DeviceListActivity.Device(name, type, locationStr, timeStr, mac, "scanned"));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting paginated data from table " + tableName + ": " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return deviceList;
    }

    public int updateDeviceStatus(String tableName, String mac, String newStatus) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsAffected = 0;

        try {
            ContentValues values = new ContentValues();
            values.put("status", newStatus);

            // Обновляем по MAC-адресу (поле bssid) и типу
            // Для Wi-Fi и Bluetooth используем поле bssid, для Cell - другие поля
            String selection;
            String[] selectionArgs;

            if (mac != null && !mac.isEmpty()) {
                // Если есть MAC, обновляем по bssid
                selection = "bssid = ?";
                selectionArgs = new String[]{mac};
            } else {
                // Если MAC нет, обновляем все записи без фильтра
                selection = null;
                selectionArgs = null;
            }

            rowsAffected = db.update("\"" + tableName + "\"", values, selection, selectionArgs);
            Log.d(TAG, "Updated status for " + rowsAffected + " devices in table: " + tableName);

        } catch (Exception e) {
            Log.e(TAG, "Error updating device status: " + e.getMessage());
        } finally {
            db.close();
        }

        return rowsAffected;
    }

    // Добавлен метод из dev для массового обновления статуса
    public int updateAllDeviceStatusForTable(String tableName, String newStatus) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("status", newStatus);

        int rowsAffected = 0;

        try {
            db.beginTransaction();
            // Обновляем все записи в таблице без условия WHERE
            rowsAffected = db.update("\"" + tableName + "\"", values, null, null);

            if (rowsAffected > 0) {
                db.setTransactionSuccessful();
                Log.d(TAG, "Status updated for all devices in table: " + tableName + ". Affected: " + rowsAffected);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating all device statuses in table " + tableName + ": " + e.getMessage());
        } finally {
            try {
                db.endTransaction();
            } catch (Exception e) {
                Log.e(TAG, "Error ending transaction: " + e.getMessage());
            }
            db.close();
        }
        return rowsAffected;
    }

    public boolean deleteTable(String tableName) {
        if (tableName.equals("unified_data")) return false;

        SQLiteDatabase db = this.getWritableDatabase();
        try {
            String safeName = "\"" + tableName + "\"";
            db.execSQL("DROP TABLE IF EXISTS " + safeName);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            db.close();
        }
    }

    public List<String> getAllTables() {
        List<String> tables = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
                    null
            );
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    tables.add(cursor.getString(0));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting all tables: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return tables;
    }
    public void createTableIfNotExists(String tableName) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            String safeName = "\"" + tableName + "\"";
            String createTableQuery = "CREATE TABLE IF NOT EXISTS " + safeName + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "type TEXT NOT NULL," +
                    "name TEXT," +
                    "bssid TEXT," +
                    "signal_strength INTEGER," +
                    "frequency INTEGER," +
                    "capabilities TEXT," +
                    "vendor TEXT," +
                    "cell_id INTEGER," +
                    "lac INTEGER," +
                    "mcc INTEGER," +
                    "mnc INTEGER," +
                    "psc INTEGER," +
                    "pci INTEGER," +
                    "tac INTEGER," +
                    "earfcn INTEGER," +
                    "arfcn INTEGER," +
                    "signal_quality INTEGER," +
                    "network_type TEXT," +
                    "is_registered INTEGER," +
                    "is_neighbor INTEGER," +
                    "latitude REAL," +
                    "longitude REAL," +
                    "altitude REAL," +
                    "location_accuracy REAL," +
                    "timestamp INTEGER," +
                    "status TEXT DEFAULT 'ignore'," +
                    "is_uploaded INTEGER DEFAULT 0)"; // Добавляем поле is_uploaded
            db.execSQL(createTableQuery);
        } catch (Exception e) {
            Log.e(TAG, "Error creating table " + tableName + ": " + e.getMessage());
        } finally {
            db.close();
        }
    }

    // Метод для получения всех записей устройства по MAC адресу (bssid)
    public List<DeviceLocation> getDeviceHistoryByMac(String tableName, String mac) {
        List<DeviceLocation> history = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            String query = "SELECT * FROM \"" + tableName + "\" " +
                    "WHERE bssid = ? AND type IN ('Wi-Fi', 'Bluetooth') " +
                    "ORDER BY timestamp DESC";
            cursor = db.rawQuery(query, new String[]{mac});

            while (cursor.moveToNext()) {
                double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
                double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));

                DeviceLocation location = new DeviceLocation(name, latitude, longitude,
                        String.valueOf(timestamp), mac);
                history.add(location);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting device history: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return history;
    }

    // Метод для получения устройства с MAC адресом
    public DeviceListActivity.Device getDeviceWithMac(String tableName, int position) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            // Получаем запись по позиции
            String query = "SELECT type, name, bssid, latitude, longitude, timestamp " +
                    "FROM \"" + tableName + "\" " +
                    "WHERE type IN ('Wi-Fi', 'Bluetooth') " +
                    "ORDER BY timestamp DESC " +
                    "LIMIT 1 OFFSET ?";
            cursor = db.rawQuery(query, new String[]{String.valueOf(position)});

            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                String mac = cursor.getString(cursor.getColumnIndexOrThrow("bssid"));
                double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
                double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));

                String locationStr = String.format("Lat: %.4f, Lon: %.4f", latitude, longitude);
                String timeStr = new java.text.SimpleDateFormat("HH:mm:ss")
                        .format(new java.util.Date(timestamp));

                return new DeviceListActivity.Device(name, type, locationStr, timeStr, mac, "scanned");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting device with MAC: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return null;
    }
    // 1. Расчет расстояния (в метрах)
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0];
    }

    // 2. Расчет пути за последние 24 часа (в километрах)
    private double getDistanceLast24h(String uniqueId, String tableName) {
        SQLiteDatabase db = this.getReadableDatabase();
        double totalDistance = 0;

        // Определяем колонку (как мы договорились: если есть ":", это MAC, иначе CellID)
        String column = uniqueId.contains(":") ? "bssid" : "cell_id";
        long twentyFourHoursAgo = System.currentTimeMillis() - 86400000;

        // Выбираем все точки этого устройства за последние 24 часа в хронологическом порядке
        Cursor cursor = db.query("\"" + tableName + "\"",
                new String[]{"latitude", "longitude"},
                column + " = ? AND timestamp > ?",
                new String[]{uniqueId, String.valueOf(twentyFourHoursAgo)},
                null, null, "timestamp ASC");

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                double prevLat = cursor.getDouble(0);
                double prevLon = cursor.getDouble(1);

                while (cursor.moveToNext()) {
                    double curLat = cursor.getDouble(0);
                    double curLon = cursor.getDouble(1);

                    // Суммируем расстояние между последовательными точками
                    totalDistance += calculateDistance(prevLat, prevLon, curLat, curLon);

                    prevLat = curLat;
                    prevLon = curLon;
                }
            }
            cursor.close();
        }


        // Возвращаем результат в километрах
        return totalDistance / 1000.0;
    }

    // 3. Проверка условий Target
    private String evaluateTargetStatus(String uniqueId, double curLat, double curLon, long curTime, String tableName) {
        if (uniqueId == null || uniqueId.isEmpty()) return "scanned";

        SQLiteDatabase db = this.getReadableDatabase();
        // Определяем колонку: MAC-адрес (с двоеточиями) или CellID
        String column = uniqueId.contains(":") ? "bssid" : "cell_id";

        // Ищем последнюю известную точку устройства
        Cursor cursor = db.query("\"" + tableName + "\"",
                new String[]{"latitude", "longitude", "timestamp"},
                "CAST(" + column + " AS TEXT) = ?",
                new String[]{uniqueId},
                null, null, "timestamp DESC", "1");

        double speedKmH = 0;
        double dist24h = 0;

        if (cursor != null && cursor.moveToFirst()) {
            double prevLat = cursor.getDouble(0);
            double prevLon = cursor.getDouble(1);
            long prevTime = cursor.getLong(2);
            cursor.close();

            // 1. Считаем дистанцию текущего "прыжка"
            double currentJumpMeters = calculateDistance(prevLat, prevLon, curLat, curLon);

            // 2. Считаем скорость
            double timeHours = (double) (curTime - prevTime) / 3600000.0;
            if (timeHours > 0) {
                speedKmH = (currentJumpMeters / 1000.0) / timeHours;
            }

            // 3. Считаем накопленный путь за 24ч и ПРИБАВЛЯЕМ текущий прыжок
            // Это важно, так как в базе новой точки еще нет
            dist24h = getDistanceLast24h(uniqueId, tableName) + (currentJumpMeters / 1000.0);

            Log.d("MATH_CHECK", String.format("ID: %s | Скорость: %.2f км/ч | Итоговый путь: %.2f км",
                    uniqueId, speedKmH, dist24h));
        } else {
            // Если устройства еще нет в базе, это первый скан
            Log.d("MATH_CHECK", "ID: " + uniqueId + " - первая встреча, статус scanned");
            return "scanned";
        }

        // Условия из задания
        if (speedKmH > 20 && dist24h > 10.0) {
            Log.d("MATH_CHECK", "!!! TARGET DETECTED (High Speed) !!!");
            return "Target";
        }

        if (speedKmH <= 20 && dist24h > 1.0) {
            Log.d("MATH_CHECK", "!!! TARGET DETECTED (Movement) !!!");
            return "Target";
        }

        return "scanned";
    }
    private void sendSystemNotification(String title, String message) {
        String channelId = "target_alerts";
        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        // 1. Создаем канал для Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Target Alerts",
                    NotificationManager.IMPORTANCE_HIGH // Высокая важность дает звук и всплывающий баннер
            );
            channel.setDescription("Уведомления об обнаружении целей");
            channel.enableVibration(true);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        // 2. Куда перейдет юзер при клике на пуш (открываем твою NotificationsActivity)
        Intent intent = new Intent(mContext, NotificationsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        // 3. Собираем само уведомление
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, channelId)
                .setSmallIcon(R.drawable.ic_cloud) // Замени на свою иконку!
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        // 4. Отправляем
        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }

        // 5. Дополнительная вибрация (если нужно, чтобы вибрировало сильнее)
        Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(500);
            }
        }
    }


    // Класс для хранения местоположения устройства
    public static class DeviceLocation {
        public String deviceName;
        public double latitude;
        public double longitude;
        public String timestamp;
        public String mac;

        public DeviceLocation(String name, double lat, double lon, String time, String mac) {
            this.deviceName = name;
            this.latitude = lat;
            this.longitude = lon;
            this.timestamp = time;
            this.mac = mac;
        }
    }
}