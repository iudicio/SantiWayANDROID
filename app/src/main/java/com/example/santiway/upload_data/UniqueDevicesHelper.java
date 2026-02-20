package com.example.santiway.upload_data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.santiway.DeviceListActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UniqueDevicesHelper {
    private static final String TAG = "UniqueDevicesHelper";
    private static final String UNIQUE_DEVICES_TABLE = "unique_devices";
    private final Context context;
    private boolean tableChecked = false;

    public UniqueDevicesHelper(Context context) {
        this.context = context;
    }

    /**
     * Создает таблицу уникальных устройств, используя существующее соединение с БД
     */
    private void createTableIfNeeded(SQLiteDatabase db) {
        if (tableChecked) return;

        try {
            String createTableQuery = "CREATE TABLE IF NOT EXISTS " + UNIQUE_DEVICES_TABLE + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "type TEXT NOT NULL," +
                    "name TEXT," +
                    "bssid TEXT," + // MAC-адрес для Wi-Fi/Bluetooth
                    "cell_id INTEGER," + // ID вышки для сотовых сетей
                    "unique_identifier TEXT UNIQUE," + // Уникальный идентификатор (MAC или cell_id с префиксом)
                    "signal_strength INTEGER," +
                    "frequency INTEGER," +
                    "capabilities TEXT," +
                    "vendor TEXT," +
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
                    "first_seen LONG," +
                    "last_seen LONG," +
                    "status TEXT DEFAULT 'scanned'," +
                    "is_uploaded INTEGER DEFAULT 0," +
                    "folder_name TEXT DEFAULT ''," +
                    "total_scans INTEGER DEFAULT 1," +
                    "avg_signal_strength REAL DEFAULT 0," +
                    "last_location_change LONG DEFAULT 0" +
                    ");";

            db.execSQL(createTableQuery);

            // Создаем индексы для быстрого поиска
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_unique_identifier ON " + UNIQUE_DEVICES_TABLE + "(unique_identifier)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_unique_last_seen ON " + UNIQUE_DEVICES_TABLE + "(last_seen)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_unique_type ON " + UNIQUE_DEVICES_TABLE + "(type)");

            tableChecked = true;
            Log.d(TAG, "Таблица уникальных устройств создана или уже существует");
        } catch (Exception e) {
            Log.e(TAG, "Ошибка создания таблицы: " + e.getMessage());
        }
    }

    /**
     * Обновляет или добавляет устройство в таблицу уникальных устройств
     */
    public void addOrUpdateDevice(SQLiteDatabase db, ContentValues deviceData) {
        String type = deviceData.getAsString("type");
        String uniqueIdentifier = null;
        String bssid = null;
        Integer cellId = null;

        // Определяем уникальный идентификатор в зависимости от типа
        if ("Wi-Fi".equals(type) || "Bluetooth".equals(type)) {
            bssid = deviceData.getAsString("bssid");
            if (bssid != null && !bssid.isEmpty()) {
                // Приводим MAC-адрес к верхнему регистру
                bssid = bssid.toUpperCase(Locale.US);
                uniqueIdentifier = "MAC:" + bssid;
                deviceData.put("bssid", bssid); // Сохраняем в верхнем регистре
            }
        } else if ("Cell".equals(type)) {
            cellId = deviceData.getAsInteger("cell_id");
            if (cellId != null && cellId > 0) {
                uniqueIdentifier = "CELL:" + cellId;
            }
        }

        if (uniqueIdentifier == null) {
            Log.d(TAG, "Пропуск устройства без уникального идентификатора: " + type);
            return; // Пропускаем устройства без уникального идентификатора
        }

        // Убеждаемся, что таблица существует
        createTableIfNeeded(db);

        Cursor cursor = null;

        try {
            // Проверяем, существует ли устройство по уникальному идентификатору
            cursor = db.query(UNIQUE_DEVICES_TABLE,
                    new String[]{"id", "total_scans", "avg_signal_strength", "name", "latitude", "longitude", "bssid", "cell_id"},
                    "unique_identifier = ?",
                    new String[]{uniqueIdentifier},
                    null, null, null);

            long currentTime = System.currentTimeMillis();
            ContentValues values = new ContentValues();

            // Копируем все поля из исходных данных
            copyFields(deviceData, values);

            // Добавляем уникальный идентификатор
            values.put("unique_identifier", uniqueIdentifier);

            // Для сотовых вышек сохраняем cell_id отдельно
            if (cellId != null) {
                values.put("cell_id", cellId);
            }

            // Для MAC-адресов сохраняем в верхнем регистре
            if (bssid != null) {
                values.put("bssid", bssid);
            }

            if (cursor != null && cursor.moveToFirst()) {
                // Устройство существует - обновляем
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                int totalScans = cursor.getInt(cursor.getColumnIndexOrThrow("total_scans"));
                float avgSignal = cursor.getFloat(cursor.getColumnIndexOrThrow("avg_signal_strength"));
                String oldName = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                double oldLat = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
                double oldLon = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));

                // Обновляем счетчик и средний сигнал
                Integer newSignalObj = deviceData.getAsInteger("signal_strength");
                int newSignal = newSignalObj != null ? newSignalObj : 0;
                float newAvgSignal = (avgSignal * totalScans + newSignal) / (totalScans + 1);

                values.put("total_scans", totalScans + 1);
                values.put("avg_signal_strength", newAvgSignal);
                values.put("last_seen", currentTime);

                // Проверяем изменение названия
                String newName = deviceData.getAsString("name");
                if (newName != null && !newName.isEmpty() && !newName.equals(oldName)) {
                    values.put("name", newName);
                }

                // Проверяем изменение координат
                Double newLatObj = deviceData.getAsDouble("latitude");
                Double newLonObj = deviceData.getAsDouble("longitude");

                if (newLatObj != null && newLonObj != null) {
                    double newLat = newLatObj;
                    double newLon = newLonObj;
                    if (Math.abs(oldLat - newLat) > 0.0001 || Math.abs(oldLon - newLon) > 0.0001) {
                        values.put("last_location_change", currentTime);
                    }
                }

                db.update(UNIQUE_DEVICES_TABLE, values, "id = ?", new String[]{String.valueOf(id)});
                Log.d(TAG, "Обновлено устройство: " + uniqueIdentifier + ", сканирований: " + (totalScans + 1));
            } else {
                // Новое устройство
                values.put("first_seen", currentTime);
                values.put("last_seen", currentTime);
                values.put("total_scans", 1);

                Integer signalObj = deviceData.getAsInteger("signal_strength");
                values.put("avg_signal_strength", signalObj != null ? signalObj : 0);
                values.put("last_location_change", currentTime);

                db.insert(UNIQUE_DEVICES_TABLE, null, values);
                Log.d(TAG, "Добавлено новое устройство: " + uniqueIdentifier);
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка обновления устройства: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Копирует поля из одного ContentValues в другой
     */
    private void copyFields(ContentValues source, ContentValues destination) {
        String[] fields = {
                "type", "name", "bssid", "cell_id", "signal_strength", "frequency",
                "capabilities", "vendor", "lac", "mcc", "mnc",
                "psc", "pci", "tac", "earfcn", "arfcn", "signal_quality",
                "network_type", "is_registered", "is_neighbor", "latitude",
                "longitude", "altitude", "location_accuracy", "status",
                "is_uploaded", "folder_name"
        };

        for (String field : fields) {
            if (source.containsKey(field)) {
                Object value = source.get(field);
                if (value != null) {
                    if (value instanceof String) {
                        // Для MAC-адресов приводим к верхнему регистру
                        if ("bssid".equals(field)) {
                            destination.put(field, ((String) value).toUpperCase(Locale.US));
                        } else {
                            destination.put(field, (String) value);
                        }
                    } else if (value instanceof Integer) {
                        destination.put(field, (Integer) value);
                    } else if (value instanceof Long) {
                        destination.put(field, (Long) value);
                    } else if (value instanceof Double) {
                        destination.put(field, (Double) value);
                    } else if (value instanceof Float) {
                        destination.put(field, (Float) value);
                    } else if (value instanceof Boolean) {
                        destination.put(field, (Boolean) value);
                    }
                }
            }
        }
    }

    /**
     * Получает список всех уникальных устройств для отображения
     */
    public List<DeviceListActivity.Device> getAllDevices() {
        List<DeviceListActivity.Device> deviceList = new ArrayList<>();
        MainDatabaseHelper dbHelper = new MainDatabaseHelper(context);
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = dbHelper.getReadableDatabase();

            // Убеждаемся, что таблица существует
            createTableIfNeeded(db);

            String query = "SELECT type, name, bssid, cell_id, latitude, longitude, last_seen, status, total_scans, network_type " +
                    "FROM " + UNIQUE_DEVICES_TABLE + " ORDER BY last_seen DESC";

            cursor = db.rawQuery(query, null);

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    String mac = cursor.getString(cursor.getColumnIndexOrThrow("bssid"));
                    int cellId = cursor.getInt(cursor.getColumnIndexOrThrow("cell_id"));
                    double lat = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
                    double lon = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
                    long lastSeen = cursor.getLong(cursor.getColumnIndexOrThrow("last_seen"));
                    String status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
                    int totalScans = cursor.getInt(cursor.getColumnIndexOrThrow("total_scans"));
                    String networkType = cursor.getString(cursor.getColumnIndexOrThrow("network_type"));

                    String locationStr = String.format(Locale.getDefault(), "Lat: %.4f, Lon: %.4f", lat, lon);
                    String timeStr = new java.text.SimpleDateFormat("HH:mm:ss dd.MM.yyyy",
                            Locale.getDefault()).format(new java.util.Date(lastSeen));

                    // Формируем отображаемое имя
                    String displayName;
                    String displayId;

                    if ("Cell".equals(type)) {
                        displayName = (name != null && !name.isEmpty()) ? name : "Cell Tower";
                        displayId = "CID: " + cellId + (networkType != null ? " (" + networkType + ")" : "");
                    } else {
                        displayName = (name != null && !name.isEmpty()) ? name : "Unknown";
                        displayId = mac != null ? mac : "";
                    }

                    // Добавляем информацию о количестве сканирований
                    String finalDisplayName = displayName + " [" + totalScans + "] " + displayId;

                    deviceList.add(new DeviceListActivity.Device(
                            finalDisplayName, type, locationStr, timeStr,
                            mac != null ? mac : String.valueOf(cellId), status));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка получения списка устройств: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
            if (db != null && db.isOpen()) db.close();
        }
        return deviceList;
    }

    /**
     * Получает статистику по конкретному устройству
     */
    public DeviceStats getDeviceStats(String identifier) {
        MainDatabaseHelper dbHelper = new MainDatabaseHelper(context);
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = dbHelper.getReadableDatabase();

            // Убеждаемся, что таблица существует
            createTableIfNeeded(db);

            String uniqueIdentifier;
            if (identifier.contains(":")) {
                // Это MAC-адрес
                uniqueIdentifier = "MAC:" + identifier.toUpperCase(Locale.US);
            } else {
                // Это cell_id
                uniqueIdentifier = "CELL:" + identifier;
            }

            cursor = db.query(UNIQUE_DEVICES_TABLE,
                    new String[]{"name", "type", "first_seen", "last_seen", "total_scans",
                            "avg_signal_strength", "last_location_change", "bssid", "cell_id", "network_type"},
                    "unique_identifier = ?",
                    new String[]{uniqueIdentifier},
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                return new DeviceStats(
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("type")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("first_seen")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("last_seen")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("total_scans")),
                        cursor.getFloat(cursor.getColumnIndexOrThrow("avg_signal_strength")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("last_location_change")),
                        cursor.getString(cursor.getColumnIndexOrThrow("bssid")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("cell_id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("network_type"))
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка получения статистики: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
            if (db != null && db.isOpen()) db.close();
        }
        return null;
    }

    /**
     * Удаляет все записи из таблицы уникальных устройств
     */
    public void clearAllDevices() {
        MainDatabaseHelper dbHelper = new MainDatabaseHelper(context);
        SQLiteDatabase db = null;
        try {
            db = dbHelper.getWritableDatabase();

            // Убеждаемся, что таблица существует
            createTableIfNeeded(db);

            db.delete(UNIQUE_DEVICES_TABLE, null, null);
            Log.d(TAG, "Все уникальные устройства удалены");
        } catch (Exception e) {
            Log.e(TAG, "Ошибка очистки таблицы: " + e.getMessage());
        } finally {
            if (db != null && db.isOpen()) db.close();
        }
    }

    /**
     * Класс для хранения статистики устройства
     */
    public static class DeviceStats {
        public String name;
        public String type;
        public long firstSeen;
        public long lastSeen;
        public int totalScans;
        public float avgSignalStrength;
        public long lastLocationChange;
        public String bssid;
        public int cellId;
        public String networkType;

        public DeviceStats(String name, String type, long firstSeen, long lastSeen,
                           int totalScans, float avgSignalStrength, long lastLocationChange,
                           String bssid, int cellId, String networkType) {
            this.name = name;
            this.type = type;
            this.firstSeen = firstSeen;
            this.lastSeen = lastSeen;
            this.totalScans = totalScans;
            this.avgSignalStrength = avgSignalStrength;
            this.lastLocationChange = lastLocationChange;
            this.bssid = bssid;
            this.cellId = cellId;
            this.networkType = networkType;
        }
    }
}