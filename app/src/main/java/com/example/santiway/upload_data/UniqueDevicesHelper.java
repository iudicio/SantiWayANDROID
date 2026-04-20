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
    private final String uniqueTableName;
    private final Context context;
    private boolean tableChecked = false;

    public UniqueDevicesHelper(Context context, String uniqueTableName) {

        this.context = context;
        this.uniqueTableName = uniqueTableName;
    }

    /**
     * Создает таблицу уникальных устройств, используя существующее соединение с БД
     */
    private void createTableIfNeeded(SQLiteDatabase db) {
        if (tableChecked) return;

        try {
            String safeTableName = "\"" + uniqueTableName + "\"";
            String safeIndexBase = uniqueTableName.replaceAll("[^A-Za-z0-9_]", "_");

            String createTableQuery = "CREATE TABLE IF NOT EXISTS " + safeTableName + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "type TEXT NOT NULL," +
                    "name TEXT," +
                    "bssid TEXT," +
                    "cell_id INTEGER," +
                    "unique_identifier TEXT UNIQUE," +
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
                    "status TEXT DEFAULT 'GREY'," +
                    "is_uploaded INTEGER DEFAULT 0," +
                    "folder_name TEXT DEFAULT ''," +
                    "total_scans INTEGER DEFAULT 1," +
                    "avg_signal_strength REAL DEFAULT 0," +
                    "last_location_change LONG DEFAULT 0" +
                    ");";

            db.execSQL(createTableQuery);

            db.execSQL("CREATE INDEX IF NOT EXISTS \"" + safeIndexBase + "_uid\" ON " + safeTableName + "(unique_identifier)");
            db.execSQL("CREATE INDEX IF NOT EXISTS \"" + safeIndexBase + "_last_seen\" ON " + safeTableName + "(last_seen)");
            db.execSQL("CREATE INDEX IF NOT EXISTS \"" + safeIndexBase + "_type\" ON " + safeTableName + "(type)");

            tableChecked = true;
            Log.d(TAG, "Таблица уникальных устройств создана или уже существует: " + uniqueTableName);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка создания таблицы unique: " + e.getMessage(), e);
        }
    }

    /**
     * Обновляет или добавляет устройство в таблицу уникальных устройств
     */
    public void addOrUpdateDevice(SQLiteDatabase db, ContentValues deviceData) {
        String type = deviceData.getAsString("type");
        String uniqueIdentifier = null;
        String bssid = null;
        Long cellId = null;

        try {
            // Убеждаемся, что таблица существует
            createTableIfNeeded(db);

            // 1. Формируем ЕДИНЫЙ device key / unique_identifier
            if ("Wi-Fi".equalsIgnoreCase(type) || "Bluetooth".equalsIgnoreCase(type)) {
                bssid = deviceData.getAsString("bssid");

                if (bssid != null && !bssid.trim().isEmpty()) {
                    bssid = bssid.trim().toUpperCase(Locale.US);
                    uniqueIdentifier = bssid;              // ВАЖНО: без "MAC:"
                    deviceData.put("bssid", bssid);
                }

            } else if ("Cell".equalsIgnoreCase(type)) {
                cellId = deviceData.getAsLong("cell_id");
                Integer mcc = deviceData.getAsInteger("mcc");
                Integer mnc = deviceData.getAsInteger("mnc");
                Integer lac = deviceData.getAsInteger("lac");
                Long tac = deviceData.getAsLong("tac");
                String networkType = deviceData.getAsString("network_type");

                if (cellId != null && cellId > 0 && cellId != 2147483647) {
                    if ("LTE".equalsIgnoreCase(networkType) || "5G".equalsIgnoreCase(networkType)) {
                        uniqueIdentifier = String.format(
                                Locale.US,
                                "%d_%d_%d_%d",
                                mcc != null ? mcc : 0,
                                mnc != null ? mnc : 0,
                                tac != null ? tac : 0,
                                cellId
                        );
                    } else {
                        uniqueIdentifier = String.format(
                                Locale.US,
                                "%d_%d_%d_%d",
                                mcc != null ? mcc : 0,
                                mnc != null ? mnc : 0,
                                lac != null ? lac : 0,
                                cellId
                        );
                    }

                    uniqueIdentifier = uniqueIdentifier.toUpperCase(Locale.US);
                }
            }

            if (uniqueIdentifier == null || uniqueIdentifier.trim().isEmpty()) {
                Log.d(TAG, "Пропуск устройства без корректного unique_identifier: " + type);
                return;
            }

            Cursor cursor = null;

            try {
                // 2. Ищем устройство по новому unique_identifier
                cursor = db.query(
                        "\"" + uniqueTableName + "\"",
                        new String[]{
                                "id",
                                "total_scans",
                                "avg_signal_strength",
                                "name",
                                "latitude",
                                "longitude",
                                "bssid",
                                "cell_id"
                        },
                        "UPPER(unique_identifier) = ?",
                        new String[]{uniqueIdentifier},
                        null,
                        null,
                        null
                );

                long currentTime = System.currentTimeMillis();
                ContentValues values = new ContentValues();

                // 3. Копируем исходные поля
                copyFields(deviceData, values);

                // 4. Явно проставляем ключевые поля
                values.put("unique_identifier", uniqueIdentifier);

                if (bssid != null && !bssid.isEmpty()) {
                    values.put("bssid", bssid);
                }

                if (cellId != null) {
                    values.put("cell_id", cellId);
                }

                // Если статуса нет — пусть остается GREY как технический дефолт
                String status = values.getAsString("status");
                if (status == null || status.trim().isEmpty()) {
                    values.put("status", "GREY");
                }

                if (cursor != null && cursor.moveToFirst()) {
                    // 5. Устройство уже есть -> обновляем агрегированные поля
                    int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                    int totalScans = cursor.getInt(cursor.getColumnIndexOrThrow("total_scans"));

                    Integer oldAvgSignal = null;
                    int avgSignalIndex = cursor.getColumnIndex("avg_signal_strength");
                    if (avgSignalIndex >= 0 && !cursor.isNull(avgSignalIndex)) {
                        oldAvgSignal = cursor.getInt(avgSignalIndex);
                    }

                    Integer newSignal = values.getAsInteger("signal_strength");
                    if (newSignal != null) {
                        int updatedAvg;
                        if (oldAvgSignal != null) {
                            updatedAvg = (oldAvgSignal * totalScans + newSignal) / (totalScans + 1);
                        } else {
                            updatedAvg = newSignal;
                        }
                        values.put("avg_signal_strength", updatedAvg);
                    }

                    values.put("total_scans", totalScans + 1);
                    values.put("last_seen", currentTime);

                    // Если имя пустое, сохраняем старое
                    String currentName = values.getAsString("name");
                    if (currentName == null || currentName.trim().isEmpty()) {
                        int nameIndex = cursor.getColumnIndex("name");
                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                            values.put("name", cursor.getString(nameIndex));
                        }
                    }

                    db.update(
                            "\"" + uniqueTableName + "\"",
                            values,
                            "id = ?",
                            new String[]{String.valueOf(id)}
                    );

                } else {
                    // 6. Новое устройство -> вставляем
                    values.put("first_seen", currentTime);
                    values.put("last_seen", currentTime);

                    if (values.getAsInteger("total_scans") == null) {
                        values.put("total_scans", 1);
                    }

                    Integer signal = values.getAsInteger("signal_strength");
                    if (signal != null && values.getAsInteger("avg_signal_strength") == null) {
                        values.put("avg_signal_strength", signal);
                    }

                    db.insert(
                            "\"" + uniqueTableName + "\"",
                            null,
                            values
                    );
                }

            } finally {
                if (cursor != null) cursor.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "Ошибка addOrUpdateDevice: " + e.getMessage(), e);
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
            createTableIfNeeded(db);

            String sql = "SELECT type, name, bssid, cell_id, unique_identifier, " +
                    "latitude, longitude, last_seen, status, total_scans, network_type " +
                    "FROM \"" + uniqueTableName + "\" " +
                    "ORDER BY last_seen DESC";

            cursor = db.rawQuery(sql, null);

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    String mac = cursor.getString(cursor.getColumnIndexOrThrow("bssid"));
                    long cellId = cursor.getLong(cursor.getColumnIndexOrThrow("cell_id"));
                    String uniqueIdentifier = cursor.getString(cursor.getColumnIndexOrThrow("unique_identifier"));
                    long lastSeen = cursor.getLong(cursor.getColumnIndexOrThrow("last_seen"));
                    String status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
                    int totalScans = cursor.getInt(cursor.getColumnIndexOrThrow("total_scans"));
                    String networkType = cursor.getString(cursor.getColumnIndexOrThrow("network_type"));

                    String displayName;
                    String finalId;

                    if ("Cell".equalsIgnoreCase(type)) {
                        displayName = (name != null && !name.trim().isEmpty()) ? name : "Cell Tower";

                        finalId = (uniqueIdentifier != null && !uniqueIdentifier.trim().isEmpty())
                                ? uniqueIdentifier
                                : String.valueOf(cellId);
                    } else {
                        displayName = (name != null && !name.trim().isEmpty()) ? name : "Unknown";

                        finalId = (mac != null && !mac.trim().isEmpty())
                                ? mac
                                : "";
                    }

                    String finalDisplayName = displayName + " [" + totalScans + "]";
                    String finalStatus = (status != null && !status.trim().isEmpty()) ? status : "GREY";

                    deviceList.add(new DeviceListActivity.Device(
                            finalDisplayName,
                            type,
                            "",
                            "",
                            finalId,
                            finalStatus,
                            lastSeen
                    ));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка получения списка устройств: " + e.getMessage(), e);
        } finally {
            if (cursor != null) cursor.close();
            if (db != null && db.isOpen()) db.close();
        }

        return deviceList;
    }

    public List<DeviceListActivity.Device> getAllDevicesWithSearch(String query) {
        List<DeviceListActivity.Device> deviceList = new ArrayList<>();
        MainDatabaseHelper dbHelper = new MainDatabaseHelper(context);
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = dbHelper.getReadableDatabase();
            createTableIfNeeded(db);

            String normalizedQuery = query == null ? "" : query.trim().toUpperCase(Locale.US);
            String likeQuery = "%" + normalizedQuery + "%";

            String sql = "SELECT type, name, bssid, cell_id, unique_identifier, " +
                    "latitude, longitude, last_seen, status, total_scans, network_type " +
                    "FROM \"" + uniqueTableName + "\" " +
                    "WHERE UPPER(COALESCE(name, '')) LIKE ? " +
                    "   OR UPPER(COALESCE(bssid, '')) LIKE ? " +
                    "   OR UPPER(COALESCE(unique_identifier, '')) LIKE ? " +
                    "   OR CAST(COALESCE(cell_id, '') AS TEXT) LIKE ? " +
                    "   OR UPPER(COALESCE(network_type, '')) LIKE ? " +
                    "ORDER BY last_seen DESC";

            cursor = db.rawQuery(sql, new String[]{
                    likeQuery, likeQuery, likeQuery, likeQuery, likeQuery
            });

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    String mac = cursor.getString(cursor.getColumnIndexOrThrow("bssid"));
                    long cellId = cursor.getLong(cursor.getColumnIndexOrThrow("cell_id"));
                    String uniqueIdentifier = cursor.getString(cursor.getColumnIndexOrThrow("unique_identifier"));
                    long lastSeen = cursor.getLong(cursor.getColumnIndexOrThrow("last_seen"));
                    String status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
                    int totalScans = cursor.getInt(cursor.getColumnIndexOrThrow("total_scans"));
                    String networkType = cursor.getString(cursor.getColumnIndexOrThrow("network_type"));

                    String displayName;
                    String finalId;

                    if ("Cell".equalsIgnoreCase(type)) {
                        displayName = (name != null && !name.trim().isEmpty()) ? name : "Cell Tower";

                        finalId = (uniqueIdentifier != null && !uniqueIdentifier.trim().isEmpty())
                                ? uniqueIdentifier
                                : String.valueOf(cellId);
                    } else {
                        displayName = (name != null && !name.trim().isEmpty()) ? name : "Unknown";

                        finalId = (mac != null && !mac.trim().isEmpty())
                                ? mac
                                : "";
                    }

                    String finalDisplayName = displayName + " [" + totalScans + "]";
                    String finalStatus = (status != null && !status.trim().isEmpty()) ? status : "GREY";

                    deviceList.add(new DeviceListActivity.Device(
                            finalDisplayName,
                            type,
                            "",
                            "",
                            finalId,
                            finalStatus,
                            lastSeen
                    ));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка поиска устройств: " + e.getMessage(), e);
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

            String uniqueIdentifier = identifier == null ? "" : identifier.trim().toUpperCase(Locale.US);

            cursor = db.query(uniqueTableName,
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

            db.delete(uniqueTableName, null, null);
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