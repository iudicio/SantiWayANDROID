package com.example.santiway.upload_data;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
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
import com.example.santiway.AlarmModeConfig;
import com.example.santiway.LocaleHelper;
import com.example.santiway.cell_scanner.CellTower;
import com.example.santiway.opencellid.OpenCellIdUnknownTowerNotifier;
import com.example.santiway.wifi_scanner.WifiDevice;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.text.SimpleDateFormat;

import com.example.santiway.bluetooth_scanner.BluetoothDevice;
import com.example.santiway.DeviceListActivity;

public class MainDatabaseHelper extends SQLiteOpenHelper {
    private static long lastGlobalSoundTime = 0;
    private static final long SOUND_INTERVAL = 3600000;
    private static final int TARGET_NOTIFICATION_ID = 1001;
    private static final String TARGET_NOTIFICATION_CHANNEL_ID = "target_alerts_channel";
    private static final long DUPLICATE_WINDOW_MS = 30000L;
    private static final long RETENTION_CLEANUP_INTERVAL_MS = 60L * 60L * 1000L;
    private static final long DEVICES_CHANGED_BROADCAST_INTERVAL_MS = 2000L;
    private static volatile long lastRetentionCleanupAt = 0L;
    private static final Map<String, Long> LAST_DEVICES_CHANGED_BROADCAST = new HashMap<>();
    private static final Map<String, Boolean> INDEX_READY = new HashMap<>();
    private static final Set<String> RAW_TABLE_READY = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> UNIQUE_TABLE_READY = Collections.synchronizedSet(new HashSet<>());
    public static final String ACTION_DEVICES_CHANGED = "com.example.santiway.ACTION_DEVICES_CHANGED";
    public static final String EXTRA_TABLE_NAME = "table_name";

    private static final String TAG = "MainDatabaseHelper";
    private static final String DATABASE_NAME = "UnifiedScanner.db";
    private static final int DATABASE_VERSION = 8; // УВЕЛИЧЕНО
    private final Context mContext;

    public MainDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Создание единой таблицы для всех данных
        String createUnifiedTable = "CREATE TABLE \"Основная\" (" +
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
                "timestamp LONG," +
                "status TEXT DEFAULT 'ignore'," +
                "is_uploaded INTEGER DEFAULT 0," +
                "folder_name TEXT DEFAULT ''," +
                "UNIQUE(bssid, timestamp)," +      // Уникальность для WiFi/Bluetooth по MAC + время
                "UNIQUE(cell_id, timestamp)" +      // Уникальность для сотовых вышек по cell_id + время
                ");";

        String createTargetTable = "CREATE TABLE IF NOT EXISTS target_devices (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "device_key TEXT NOT NULL UNIQUE" + //Уникальность по mac/cell_id
                ");";

        String createSafeTable = "CREATE TABLE IF NOT EXISTS safe_devices (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "device_key TEXT NOT NULL UNIQUE" + //Уникальность по mac/cell_id
                ");";

        db.execSQL(createUnifiedTable);
        db.execSQL(createTargetTable);
        db.execSQL(createSafeTable);
        createCoreIndexes(db);
    }

    private void createCoreIndexes(SQLiteDatabase db) {
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_target_devices_key ON target_devices(device_key)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_safe_devices_key ON safe_devices(device_key)");
        } catch (Exception e) {
            Log.e(TAG, "Error creating status indexes: " + e.getMessage());
        }
        createIndexesForExistingScannerTables(db);
    }

    private void createIndexesForExistingScannerTables(SQLiteDatabase db) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' " +
                            "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
                    null
            );
            while (cursor != null && cursor.moveToNext()) {
                String table = cursor.getString(0);
                if (table == null
                        || "target_devices".equals(table)
                        || "safe_devices".equals(table)
                        || "unique_devices".equals(table)) {
                    continue;
                }
                if (table.endsWith("_unique")) {
                    createUniqueTableIndexes(db, table);
                } else {
                    createRawTableIndexes(db, table);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating scanner indexes: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void createRawTableIndexes(SQLiteDatabase db, String tableName) {
        if (!markIndexSetupNeeded("raw:" + tableName)) {
            return;
        }
        String safeName = "\"" + tableName + "\"";
        createIndex(db, safeIndexName(tableName, "timestamp"), safeName + "(timestamp)");
        createIndex(db, safeIndexName(tableName, "bssid_timestamp"), safeName + "(bssid,timestamp)");
        createIndex(db, safeIndexName(tableName, "type_timestamp"), safeName + "(type,timestamp)");
        createIndex(db, safeIndexName(tableName, "cell_timestamp"),
                safeName + "(cell_id,mcc,mnc,timestamp)");
        createIndex(db, safeIndexName(tableName, "uploaded_timestamp"),
                safeName + "(is_uploaded,timestamp)");
    }

    private void createUniqueTableIndexes(SQLiteDatabase db, String tableName) {
        if (!markIndexSetupNeeded("unique:" + tableName)) {
            return;
        }
        String safeName = "\"" + tableName + "\"";
        createIndex(db, safeIndexName(tableName, "uid"), safeName + "(unique_identifier)");
        createIndex(db, safeIndexName(tableName, "last_seen"), safeName + "(last_seen)");
        createIndex(db, safeIndexName(tableName, "status_last_seen"), safeName + "(status,last_seen)");
    }

    private boolean markIndexSetupNeeded(String key) {
        synchronized (INDEX_READY) {
            if (INDEX_READY.containsKey(key)) {
                return false;
            }
            INDEX_READY.put(key, true);
            return true;
        }
    }

    private void createIndex(SQLiteDatabase db, String indexName, String target) {
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS \"" + indexName + "\" ON " + target);
        } catch (Exception e) {
            Log.e(TAG, "Error creating index " + indexName + ": " + e.getMessage());
        }
    }

    private String safeIndexName(String tableName, String suffix) {
        String base = tableName == null ? "table" : tableName.replaceAll("[^A-Za-z0-9_]", "_");
        if (base.isEmpty()) {
            base = "table";
        }
        String hash = Integer.toHexString((tableName == null ? "" : tableName).hashCode());
        return "idx_" + base + "_" + hash + "_" + suffix;
    }


    public void deleteOldRecordsFromAllTables(long maxAgeMillis) {
        SQLiteDatabase db = this.getWritableDatabase();
        deleteOldRecordsFromAllTables(db, maxAgeMillis);
    }

    private void deleteOldRecordsFromAllTables(SQLiteDatabase db, long maxAgeMillis) {
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master " +
                            "WHERE type='table' " +
                            "AND name NOT LIKE 'sqlite_%' " +
                            "AND name NOT LIKE 'android_%'",
                    null
            );

            long cutoffTime = System.currentTimeMillis() - maxAgeMillis;

            while (cursor != null && cursor.moveToNext()) {
                String table = cursor.getString(0);

                if ("target_devices".equals(table)
                        || "safe_devices".equals(table)
                        || "unique_devices".equals(table)) {
                    continue;
                }

                try {
                    if (table.endsWith("_unique")) {
                        if (hasColumn(db, table, "last_seen")) {
                            int deleted = db.delete(
                                    "\"" + table + "\"",
                                    "last_seen < ?",
                                    new String[]{String.valueOf(cutoffTime)}
                            );
                            Log.d(TAG, "Deleted old unique records from " + table + ": " + deleted);
                        }
                    } else {
                        if (hasColumn(db, table, "timestamp")) {
                            int deleted = db.delete(
                                    "\"" + table + "\"",
                                    "timestamp < ?",
                                    new String[]{String.valueOf(cutoffTime)}
                            );
                            Log.d(TAG, "Deleted old records from " + table + ": " + deleted);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error deleting old records from table " + table + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting old records: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void scheduleRetentionCleanupIfDue() {
        long now = System.currentTimeMillis();
        synchronized (MainDatabaseHelper.class) {
            if (now - lastRetentionCleanupAt < RETENTION_CLEANUP_INTERVAL_MS) {
                return;
            }
            lastRetentionCleanupAt = now;
        }

        Context appContext = mContext.getApplicationContext();
        int retentionDays = appContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                .getInt("data_retention_days", 7);
        if (retentionDays <= 0) {
            retentionDays = 7;
        }
        long maxAge = retentionDays * 24L * 60L * 60L * 1000L;

        new Thread(() -> {
            try (MainDatabaseHelper helper = new MainDatabaseHelper(appContext)) {
                helper.deleteOldRecordsFromAllTables(maxAge);
                try (NotificationDatabaseHelper notificationHelper = new NotificationDatabaseHelper(appContext)) {
                    notificationHelper.deleteOldNotifications(maxAge);
                }
            } catch (Exception e) {
                Log.e(TAG, "Retention cleanup failed: " + e.getMessage());
            }
        }).start();
    }

    private boolean hasColumn(SQLiteDatabase db, String tableName, String columnName) {
        Cursor cursor = null;

        try {
            cursor = db.rawQuery("PRAGMA table_info(\"" + tableName + "\")", null);
            int nameIndex = cursor.getColumnIndex("name");

            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && columnName.equals(cursor.getString(nameIndex))) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "hasColumn error for " + tableName + ": " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }

        return false;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        createCoreIndexes(db);
//        if (oldVersion < 8) {
//            try {
//                Cursor cursor = db.rawQuery(
//                        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
//                        null
//                );
//
//                if (cursor != null) {
//                    try {
//                        while (cursor.moveToNext()) {
//                            String tableName = cursor.getString(0);
//                            try {
//                                // Проверяем существование колонки
//                                Cursor pragma = db.rawQuery("PRAGMA table_info(\"" + tableName + "\")", null);
//                                boolean hasColumn = false;
//                                int nameIndex = pragma.getColumnIndex("name");
//                                while (pragma.moveToNext()) {
//                                    if (nameIndex >= 0 && "folder_name".equals(pragma.getString(nameIndex))) {
//                                        hasColumn = true;
//                                        break;
//                                    }
//                                }
//                                pragma.close();
//
//                                if (!hasColumn) {
//                                    db.execSQL("ALTER TABLE \"" + tableName + "\" ADD COLUMN folder_name TEXT DEFAULT ''");
//                                    Log.d(TAG, "Added folder_name to table: " + tableName);
//                                }
//                            } catch (Exception e) {
//                                Log.e(TAG, "Error updating table " + tableName + ": " + e.getMessage());
//                            }
//                        }
//                    } finally {
//                        cursor.close();
//                    }
//                }
//            } catch (Exception e) {
//                Log.e(TAG, "Error during database upgrade: " + e.getMessage());
//            }
//        }
    }
    public long addBluetoothDevice(BluetoothDevice device, String tableName) {
        ContentValues values = new ContentValues();
        values.put("type", "Bluetooth"); // Тип устройства
        values.put("name", device.getDeviceName());
        String macAddress = device.getMacAddress();
        if (macAddress != null) {
            macAddress = macAddress.toUpperCase(Locale.US);
        }
        values.put("bssid", macAddress);
        values.put("signal_strength", device.getSignalStrength());
        values.put("vendor", device.getVendor());

        // Общие поля геолокации и времени
        double lat = device.getLatitude();
        double lon = device.getLongitude();
        boolean hasValidLocation = !(lat == 0.0 && lon == 0.0);
        if (hasValidLocation) {
            values.put("latitude", lat);
            values.put("longitude", lon);
            values.put("altitude", device.getAltitude());
            values.put("location_accuracy", device.getLocationAccuracy());
        } else {
            // не кладём latitude/longitude -> в БД будут NULL (если колонки допускают NULL)
        }
        values.put("timestamp", device.getTimestamp());
        values.put("status", "GREY"); // Статус
        values.put("folder_name", tableName);

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
        String bssid = device.getBssid();
        if (bssid != null) {
            bssid = bssid.toUpperCase(Locale.US);
        }
        values.put("bssid", bssid);
        values.put("signal_strength", device.getSignalStrength());
        values.put("frequency", device.getFrequency());
        values.put("capabilities", device.getCapabilities());
        values.put("vendor", device.getVendor());
        // Общие поля геолокации и времени
        double lat = device.getLatitude();
        double lon = device.getLongitude();
        boolean hasValidLocation = !(lat == 0.0 && lon == 0.0);
        if (hasValidLocation) {
            values.put("latitude", lat);
            values.put("longitude", lon);
            values.put("altitude", device.getAltitude());
            values.put("location_accuracy", device.getLocationAccuracy());
        } else {
            // не кладём latitude/longitude -> в БД будут NULL (если колонки допускают NULL)
        }
        values.put("timestamp", device.getTimestamp());
        values.put("status", "GREY");
        values.put("folder_name", tableName);

        String selection = "bssid = ?";
        String[] selectionArgs = {device.getBssid()};
        return addOrUpdateUnifiedDevice(tableName, values, selection, selectionArgs, device.getTimestamp());
    }

    public long addTriangulatedWifiDevice(WifiDevice device, String tableName) {
        long inserted = addWifiDevice(device, tableName);
        if (inserted != -1) return inserted;

        ContentValues values = new ContentValues();
        values.put("type", "Wi-Fi");
        values.put("name", device.getSsid());
        String bssid = device.getBssid() == null ? null : device.getBssid().toUpperCase(Locale.US);
        values.put("bssid", bssid);
        values.put("signal_strength", device.getSignalStrength());
        values.put("frequency", device.getFrequency());
        values.put("capabilities", device.getCapabilities());
        values.put("vendor", device.getVendor());
        values.put("latitude", device.getLatitude());
        values.put("longitude", device.getLongitude());
        values.put("altitude", device.getAltitude());
        values.put("location_accuracy", device.getLocationAccuracy());
        values.put("timestamp", device.getTimestamp());
        values.put("folder_name", tableName);
        return updateRecentTriangulatedDevice(tableName, values, bssid, device.getTimestamp());
    }

    public long addTriangulatedBluetoothDevice(BluetoothDevice device, String tableName) {
        long inserted = addBluetoothDevice(device, tableName);
        if (inserted != -1) return inserted;

        ContentValues values = new ContentValues();
        values.put("type", "Bluetooth");
        values.put("name", device.getDeviceName());
        String mac = device.getMacAddress() == null ? null : device.getMacAddress().toUpperCase(Locale.US);
        values.put("bssid", mac);
        values.put("signal_strength", device.getSignalStrength());
        values.put("vendor", device.getVendor());
        values.put("latitude", device.getLatitude());
        values.put("longitude", device.getLongitude());
        values.put("altitude", device.getAltitude());
        values.put("location_accuracy", device.getLocationAccuracy());
        values.put("timestamp", device.getTimestamp());
        values.put("folder_name", tableName);
        return updateRecentTriangulatedDevice(tableName, values, mac, device.getTimestamp());
    }

    private long updateRecentTriangulatedDevice(String tableName, ContentValues values,
                                                String mac, long timestamp) {
        if (mac == null || mac.trim().isEmpty()) return -1;
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = null;
        try {
            scheduleRetentionCleanupIfDue();
            createFolderRawTableIfNotExists(db, tableName);
            createFolderUniqueTableIfNotExists(db, getUniqueTableName(tableName));

            long duplicateStart = timestamp - DUPLICATE_WINDOW_MS;
            long duplicateEnd = timestamp + DUPLICATE_WINDOW_MS;
            cursor = db.rawQuery(
                    "SELECT id,status FROM \"" + tableName + "\" " +
                            "WHERE UPPER(COALESCE(bssid,''))=? AND timestamp BETWEEN ? AND ? " +
                            "ORDER BY ABS(timestamp - ?) ASC LIMIT 1",
                    new String[]{
                            mac.toUpperCase(Locale.US),
                            String.valueOf(duplicateStart),
                            String.valueOf(duplicateEnd),
                            String.valueOf(timestamp)
                    }
            );
            if (cursor == null || !cursor.moveToFirst()) return -1;

            long id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
            String status = cursor.isNull(cursor.getColumnIndexOrThrow("status"))
                    ? "GREY"
                    : cursor.getString(cursor.getColumnIndexOrThrow("status"));
            values.put("status", status == null || status.trim().isEmpty() ? "GREY" : status);
            values.put("is_uploaded", 0);

            db.beginTransaction();
            int updated = db.update("\"" + tableName + "\"", values, "id=?",
                    new String[]{String.valueOf(id)});
            if (updated > 0) {
                addToFolderUniqueDevices(db, tableName, values);
                syncStatusTables(db, mac, values.getAsString("status"));
                notifyDevicesChangedThrottled(tableName);
            }
            db.setTransactionSuccessful();
            return updated > 0 ? id : -1;
        } catch (Exception e) {
            Log.e(TAG, "Error updating triangulated duplicate: " + e.getMessage(), e);
            return -1;
        } finally {
            if (cursor != null) cursor.close();
            if (db.inTransaction()) db.endTransaction();
        }
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
        // Общие поля геолокации и времени
        double lat = tower.getLatitude();
        double lon = tower.getLongitude();
        boolean hasValidLocation = !(lat == 0.0 && lon == 0.0);
        if (hasValidLocation) {
            values.put("latitude", lat);
            values.put("longitude", lon);
            values.put("altitude", tower.getAltitude());
            values.put("location_accuracy", tower.getLocationAccuracy());
        } else {
            // не кладём latitude/longitude -> в БД будут NULL (если колонки допускают NULL)
        }
        values.put("timestamp", tower.getTimestamp());
        values.put("status", "GREY");
        values.put("folder_name", tableName);

        String selection = "cell_id = ? AND network_type = ?";
        String[] selectionArgs = {String.valueOf(tower.getCellId()), tower.getNetworkType()};
        long result = addOrUpdateUnifiedDevice(tableName, values, selection, selectionArgs, tower.getTimestamp());
        if (result != -1) {
            OpenCellIdUnknownTowerNotifier.checkAndNotify(mContext, tower);
        }
        return result;
    }

    private long addOrUpdateUnifiedDevice(String tableName, ContentValues values, String selection, String[] selectionArgs, long newTimestamp) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        long result = -1;
        boolean ownsTransaction = false;

        try {
            scheduleRetentionCleanupIfDue();
            db = this.getWritableDatabase();
            ownsTransaction = !db.inTransaction();
            createFolderRawTableIfNotExists(db, tableName);
            createFolderUniqueTableIfNotExists(db, getUniqueTableName(tableName));

            // Определяем уникальный ID устройства
            String uniqueId = null;
            String bssid = values.getAsString("bssid");

            if (bssid != null && !bssid.isEmpty()) {
                uniqueId = bssid.toUpperCase(Locale.US);
            } else {
                // Для сотовых вышек используем составной ключ
                Long cellId = values.getAsLong("cell_id");
                Integer mcc = values.getAsInteger("mcc");
                Integer mnc = values.getAsInteger("mnc");
                Long tac = values.getAsLong("tac");
                Integer lac = values.getAsInteger("lac");
                String networkType = values.getAsString("network_type");

                if (cellId != null && cellId > 0 && cellId != 2147483647) {
                    if ("LTE".equals(networkType) || "5G".equals(networkType)) {
                        // Для LTE/5G: MCC_MNC_TAC_CI
                        uniqueId = String.format(Locale.US, "%d_%d_%d_%d",
                                mcc != null ? mcc : 0,
                                mnc != null ? mnc : 0,
                                tac != null ? tac : 0,
                                cellId);
                    } else {
                        // Для GSM/UMTS: MCC_MNC_LAC_CI
                        uniqueId = String.format(Locale.US, "%d_%d_%d_%d",
                                mcc != null ? mcc : 0,
                                mnc != null ? mnc : 0,
                                lac != null ? lac : 0,
                                cellId);
                    }
                }
            }

            if (uniqueId == null || uniqueId.isEmpty()) {
                Log.d(TAG, "Skipping device without unique identifier");
                return -1;
            }

            // 2. ЖЕСТКАЯ ДЕДУПЛИКАЦИЯ: проверяем точное совпадение за последние 2 секунды
            String checkQuery;
            String[] checkArgs;
            long duplicateStart = newTimestamp - DUPLICATE_WINDOW_MS;
            long duplicateEnd = newTimestamp + DUPLICATE_WINDOW_MS;

            if (bssid != null) {
                checkQuery = "SELECT COUNT(*) FROM \"" + tableName + "\" " +
                        "WHERE UPPER(COALESCE(bssid, '')) = ? AND timestamp BETWEEN ? AND ?";
                checkArgs = new String[]{
                        uniqueId,
                        String.valueOf(duplicateStart),
                        String.valueOf(duplicateEnd)
                };
            } else {
                // Для сотовых вышек ищем по составному ключу
                checkQuery = "SELECT COUNT(*) FROM \"" + tableName + "\" " +
                        "WHERE type = 'Cell' AND " +
                        "cell_id = ? AND mcc = ? AND mnc = ? AND " +
                        "timestamp BETWEEN ? AND ?";

                Long cellId = values.getAsLong("cell_id");
                Integer mcc = values.getAsInteger("mcc");
                Integer mnc = values.getAsInteger("mnc");

                checkArgs = new String[]{
                        String.valueOf(cellId),
                        String.valueOf(mcc),
                        String.valueOf(mnc),
                        String.valueOf(duplicateStart),
                        String.valueOf(duplicateEnd)
                };
            }

            cursor = db.rawQuery(checkQuery, checkArgs);
            if (cursor != null && cursor.moveToFirst()) {
                int count = cursor.getInt(0);
                if (count > 0) {
                    String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            .format(new Date(newTimestamp));
                    Log.d(TAG, "⚠️ DUPLICATE BLOCKED: " + uniqueId + " at " + timeStr);
                    return -1;
                }
            }

            if (cursor != null) {
                cursor.close();
                cursor = null;
            }

            // 4. Получаем координаты
            Double curLat = values.getAsDouble("latitude");
            Double curLon = values.getAsDouble("longitude");
            Double curAlt = values.getAsDouble("altitude");
            Float curAcc = values.getAsFloat("location_accuracy");

            // 5. Проверяем Target только для GREY-устройств
            String lastStatus = "GREY";
            String dbStatus = getLatestDeviceStatus(db, uniqueId, tableName, values);
            if (dbStatus != null && !dbStatus.isEmpty()) lastStatus = dbStatus;

            boolean trackingAlarmEnabled = AlarmModeConfig.isTrackingDeviceAlarmEnabled(mContext);
            boolean markedTargetAlarmEnabled = AlarmModeConfig.isMarkedTargetAlarmEnabled(mContext);
            boolean quietModeEnabled = AlarmModeConfig.isQuietModeEnabled(mContext);
            boolean quietIncludesCells = AlarmModeConfig.isQuietModeIncludeCellTowers(mContext);
            boolean isCellRecord = "Cell".equalsIgnoreCase(values.getAsString("type"));
            boolean notifyTarget = false;

            if ("SAFE".equalsIgnoreCase(lastStatus)) {
                values.put("status", "SAFE");
            } else if (quietModeEnabled && (!isCellRecord || quietIncludesCells)) {
                values.put("status", "TARGET");
                notifyTarget = true;
            } else if ("TARGET".equalsIgnoreCase(lastStatus)) {
                values.put("status", "TARGET");
                notifyTarget = markedTargetAlarmEnabled;
            } else if (trackingAlarmEnabled && curLat != null && curLon != null && "GREY".equals(lastStatus)) {
                try {
                    String calculatedStatus = evaluateTargetStatus(uniqueId, curLat, curLon, newTimestamp, tableName);
                    values.put("status", calculatedStatus);
                    Log.d(TAG, "Status for " + uniqueId + ": " + calculatedStatus);
                    notifyTarget = "TARGET".equals(calculatedStatus);
                } catch (Exception e) {
                    Log.e(TAG, "Error calculating status: " + e.getMessage());
                    values.put("status", lastStatus);
                }
            } else {
                values.put("status", lastStatus);
            }

            if (notifyTarget && curLat != null && curLon != null) {
                createTargetNotification(values, uniqueId, curLat, curLon);
            }

            // 7. Добавляем запись в основную таблицу
            if (ownsTransaction) db.beginTransaction();
            result = db.insert("\"" + tableName + "\"", null, values);

            if (result != -1) {
                addToFolderUniqueDevices(db, tableName, values);
                syncStatusTables(db, uniqueId, values.getAsString("status"));
                notifyDevicesChangedThrottled(tableName);
            }

            if (ownsTransaction) db.setTransactionSuccessful();

        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
            if (db != null && db.isOpen()) {
                try {
                    if (ownsTransaction && db.inTransaction()) {
                        db.endTransaction();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error ending transaction: " + e.getMessage());
                }
            }
        }
        return result;
    }

    /**
     * Создает уведомление для Target устройств
     */
    private void createTargetNotification(ContentValues values, String uniqueId, double lat, double lon) {
        NotificationDatabaseHelper notifDb = null;
        try {
            String type = values.getAsString("type");
            String name = values.getAsString("name");
            if (name == null || name.isEmpty()) {
                name = LocaleHelper.getString(mContext, R.string.unknown_value);
            }

            // 1. Сохраняем уведомление в БД
            notifDb = new NotificationDatabaseHelper(mContext);
            if (!notifDb.isUniqueAlert(uniqueId)) {
                return;
            }

            String title = LocaleHelper.getString(mContext, R.string.target_notification_title, type);
            String message = LocaleHelper.getString(mContext, R.string.target_device_moving_message,
                    name, uniqueId, lat, lon);

            NotificationData alert = new NotificationData(
                    UUID.randomUUID().toString(),
                    title,
                    message,
                    new Date(),
                    NotificationData.NotificationType.ALARM,
                    null, null, lat, lon,
                    uniqueId
            );
            notifDb.addNotification(alert, uniqueId);

            // 2. Создаем канал уведомлений (только один раз, при первом вызове)
            NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
                NotificationChannel channel = notificationManager.getNotificationChannel(TARGET_NOTIFICATION_CHANNEL_ID);
                if (channel == null) {
                    NotificationChannel newChannel = new NotificationChannel(
                            TARGET_NOTIFICATION_CHANNEL_ID,
                            LocaleHelper.getString(mContext, R.string.target_channel_name),
                            NotificationManager.IMPORTANCE_HIGH
                    );
                    newChannel.setDescription(LocaleHelper.getString(mContext, R.string.target_channel_description));
                    newChannel.enableVibration(true);
                    newChannel.setVibrationPattern(new long[]{0, 500, 200, 500});
                    newChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null);
                    newChannel.enableLights(true);
                    newChannel.setLightColor(Color.RED);
                    notificationManager.createNotificationChannel(newChannel);
                    Log.d(TAG, "✅ Created HIGH importance channel");
                }
            }

            // 3. Создаем Intent для открытия NotificationsActivity
            Intent intent = new Intent(mContext, NotificationsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    mContext,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // 4. Счетчик уведомлений
            int alertCount = notifDb.countNotificationsByType(NotificationData.NotificationType.ALARM);
            String contentText = LocaleHelper.getString(mContext, R.string.targets_detected_count, alertCount);

            // 5. Получаем флаг "первое ли уведомление" из SharedPreferences
            SharedPreferences prefs = mContext.getSharedPreferences("notif_prefs", Context.MODE_PRIVATE);
            boolean isFirstAlert = prefs.getBoolean("is_first_alert", true);

            // 6. Строим базовое уведомление
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, TARGET_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notifications)
                    .setContentTitle(LocaleHelper.getString(mContext, R.string.targets_detected_title))
                    .setContentText(contentText)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);

            // 7. Логика: первый раз - со звуком и push, потом - тихо
            if (isFirstAlert) {
                builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                        .setVibrate(new long[]{0, 500, 200, 500});

                // Сохраняем, что первый alert уже был
                prefs.edit().putBoolean("is_first_alert", false).apply();
                Log.d(TAG, "🔊 FIRST ALERT: with sound and vibration");
            } else {
                builder.setPriority(NotificationCompat.PRIORITY_LOW)
                        .setOnlyAlertOnce(true);
                Log.d(TAG, "🔇 UPDATE ONLY: silent notification");
            }

            // 8. Показываем или обновляем уведомление
            if (notificationManager != null) {
                notificationManager.notify(TARGET_NOTIFICATION_ID, builder.build());
                Log.d(TAG, "✅ Notification sent/updated. Total alerts: " + alertCount);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating notification: " + e.getMessage(), e);
        } finally {
            if (notifDb != null) {
                notifDb.close();
            }
        }
    }
    public void clearTableData(String folderName) {
        SQLiteDatabase db = this.getWritableDatabase();

        try {
            db.beginTransaction();

            db.delete("\"" + folderName + "\"", null, null);
            db.delete("\"" + folderName + "_unique\"", null, null);

            // После очистки текущей папки служебные таблицы тоже должны очиститься
            db.delete("target_devices", null, null);
            db.delete("safe_devices", null, null);

            db.setTransactionSuccessful();
            notifyDevicesChanged(folderName);
        } catch (Exception e) {
            Log.e(TAG, "Error clearing folder tables: " + e.getMessage());
        } finally {
            if (db.inTransaction()) db.endTransaction();
        }
    }
    public void renameTable(String oldName, String newName) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            db.execSQL("ALTER TABLE \"" + oldName + "\" RENAME TO \"" + newName + "\"");
            db.execSQL("ALTER TABLE \"" + oldName + "_unique\" RENAME TO \"" + newName + "_unique\"");
            RAW_TABLE_READY.remove(oldName);
            UNIQUE_TABLE_READY.remove(oldName + "_unique");
            RAW_TABLE_READY.add(newName);
            UNIQUE_TABLE_READY.add(newName + "_unique");
        } catch (Exception e) {
            Log.e("DB_RENAME", "Error: " + e.getMessage());
        }
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
        }
        return deviceList;
    }

    public List<DeviceListActivity.Device> getAllDataFromTableWithPagination(String tableName, int offset, int limit) {
        List<DeviceListActivity.Device> deviceList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            // УБИРАЕМ фильтр по типу или добавляем Cell
            String sql = "SELECT type, name, bssid, cell_id, latitude, longitude, timestamp, status " +
                    "FROM \"" + tableName + "\" " +
                    "ORDER BY timestamp DESC " +  // Убрали WHERE type IN (...)
                    "LIMIT ? OFFSET ?";

            cursor = db.rawQuery(sql, new String[]{String.valueOf(limit), String.valueOf(offset)});

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    String mac = cursor.getString(cursor.getColumnIndexOrThrow("bssid"));
                    int cellId = cursor.getInt(cursor.getColumnIndexOrThrow("cell_id"));
                    double lat = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
                    double lon = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
                    long ts = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));

                    int statusIdx = cursor.getColumnIndex("status");
                    String currentStatus = "GREY";
                    if (statusIdx != -1) {
                        String dbStatus = cursor.getString(statusIdx);
                        if (dbStatus != null && !dbStatus.isEmpty()) {
                            currentStatus = dbStatus;
                        }
                    }

                    String loc = String.format("Lat: %.4f, Lon: %.4f", lat, lon);
                    String time = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date(ts));

                    // Для Cell устройств используем cell_id как идентификатор
                    String deviceId = ("Cell".equals(type)) ? String.valueOf(cellId) : mac;

                    deviceList.add(new DeviceListActivity.Device(name, type, loc, time, deviceId, currentStatus));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Pagination error: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return deviceList;
    }

    public List<DeviceListActivity.Device> getAllDataFromTableWithPaginationAndSearch(
            String tableName,
            String searchQuery,
            int offset,
            int limit
    ) {
        List<DeviceListActivity.Device> deviceList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            String likeQuery = "%" + searchQuery + "%";

            String sql = "SELECT type, name, bssid, cell_id, latitude, longitude, timestamp, status " +
                    "FROM \"" + tableName + "\" " +
                    "WHERE UPPER(COALESCE(name, '')) LIKE UPPER(?) " +
                    "   OR UPPER(COALESCE(bssid, '')) LIKE UPPER(?) " +
                    "ORDER BY timestamp DESC " +
                    "LIMIT ? OFFSET ?";

            cursor = db.rawQuery(sql, new String[]{
                    likeQuery,
                    likeQuery,
                    String.valueOf(limit),
                    String.valueOf(offset)
            });

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    String mac = cursor.getString(cursor.getColumnIndexOrThrow("bssid"));
                    int cellId = cursor.getInt(cursor.getColumnIndexOrThrow("cell_id"));
                    double lat = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
                    double lon = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
                    long ts = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));

                    int statusIdx = cursor.getColumnIndex("status");
                    String currentStatus = "GREY";
                    if (statusIdx != -1) {
                        String dbStatus = cursor.getString(statusIdx);
                        if (dbStatus != null && !dbStatus.isEmpty()) {
                            currentStatus = dbStatus;
                        }
                    }

                    String loc = String.format(Locale.getDefault(), "Lat: %.4f, Lon: %.4f", lat, lon);
                    String time = new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            .format(new java.util.Date(ts));

                    String deviceId = ("Cell".equals(type)) ? String.valueOf(cellId) : mac;

                    deviceList.add(new DeviceListActivity.Device(
                            name,
                            type,
                            loc,
                            time,
                            deviceId,
                            currentStatus
                    ));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Search pagination error: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }

        return deviceList;
    }

    public int updateDeviceStatus(String tableName, String deviceKey, String newStatus) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsAffected = 0;

        try {
            deviceKey = normalizeDeviceKey(deviceKey);
            if (deviceKey == null || deviceKey.isEmpty()) {
                return 0;
            }

            db.beginTransaction();

            ContentValues values = new ContentValues();
            values.put("status", newStatus);

            // 1. Обновляем raw-таблицу
            if (deviceKey.contains(":")) {
                // Wi-Fi / Bluetooth -> MAC
                rowsAffected = db.update(
                        "\"" + tableName + "\"",
                        values,
                        "UPPER(COALESCE(bssid, '')) = ?",
                        new String[]{deviceKey}
                );
            } else if (deviceKey.contains("_")) {
                // Cell -> обновляем по данным из unique-таблицы
                Cursor cellCursor = null;
                try {
                    cellCursor = db.rawQuery(
                            "SELECT cell_id, mcc, mnc, lac, tac, network_type " +
                                    "FROM \"" + tableName + "_unique\" " +
                                    "WHERE UPPER(unique_identifier) = ? LIMIT 1",
                            new String[]{deviceKey}
                    );

                    if (cellCursor.moveToFirst()) {
                        String cellId = String.valueOf(cellCursor.getInt(0));
                        String mcc = String.valueOf(cellCursor.getInt(1));
                        String mnc = String.valueOf(cellCursor.getInt(2));
                        String lac = String.valueOf(cellCursor.getInt(3));
                        String tac = String.valueOf(cellCursor.getInt(4));
                        String networkType = cellCursor.getString(5);

                        String whereClause;
                        String[] whereArgs;

                        if ("LTE".equalsIgnoreCase(networkType) || "5G".equalsIgnoreCase(networkType)) {
                            whereClause = "type = 'Cell' AND CAST(cell_id AS TEXT) = ? AND " +
                                    "CAST(mcc AS TEXT) = ? AND CAST(mnc AS TEXT) = ? AND CAST(tac AS TEXT) = ?";
                            whereArgs = new String[]{cellId, mcc, mnc, tac};
                        } else {
                            whereClause = "type = 'Cell' AND CAST(cell_id AS TEXT) = ? AND " +
                                    "CAST(mcc AS TEXT) = ? AND CAST(mnc AS TEXT) = ? AND CAST(lac AS TEXT) = ?";
                            whereArgs = new String[]{cellId, mcc, mnc, lac};
                        }

                        rowsAffected = db.update(
                                "\"" + tableName + "\"",
                                values,
                                whereClause,
                                whereArgs
                        );
                    }
                } finally {
                    if (cellCursor != null) cellCursor.close();
                }
            } else {
                // fallback: старый вариант для Cell по одному cell_id
                rowsAffected = db.update(
                        "\"" + tableName + "\"",
                        values,
                        "CAST(cell_id AS TEXT) = ?",
                        new String[]{deviceKey}
                );
            }

            // 2. Обновляем unique-таблицу
            db.update(
                    "\"" + tableName + "_unique\"",
                    values,
                    "UPPER(COALESCE(unique_identifier, '')) = ? " +
                            "OR UPPER(COALESCE(bssid, '')) = ? " +
                            "OR CAST(cell_id AS TEXT) = ?",
                    new String[]{deviceKey, deviceKey, deviceKey}
            );

            // 3. Полностью пересобираем служебные таблицы
            rebuildStatusTables(tableName);

            db.setTransactionSuccessful();

            if ("TARGET".equalsIgnoreCase(newStatus)
                    && AlarmModeConfig.isMarkedTargetAlarmEnabled(mContext)) {
                notifyTargetDeviceNow(tableName, deviceKey);
            }

            notifyDevicesChanged(tableName);


        } catch (Exception e) {
            Log.e(TAG, "Error updating device status: " + e.getMessage());
        } finally {
            if (db.inTransaction()) db.endTransaction();
        }

        return rowsAffected;
    }

    private String normalizeDeviceKey(String deviceKey) {
        return deviceKey == null ? null : deviceKey.trim().toUpperCase(Locale.US);
    }

    private void addDeviceToTarget(SQLiteDatabase db, String deviceKey) {
        deviceKey = normalizeDeviceKey(deviceKey);
        if (deviceKey == null || deviceKey.isEmpty()) return;

        ContentValues values = new ContentValues();
        values.put("device_key", deviceKey);

        db.insertWithOnConflict(
                "target_devices",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );
    }

    private void addDeviceToSafe(SQLiteDatabase db, String deviceKey) {
        deviceKey = normalizeDeviceKey(deviceKey);
        if (deviceKey == null || deviceKey.isEmpty()) return;

        ContentValues values = new ContentValues();
        values.put("device_key", deviceKey);

        db.insertWithOnConflict(
                "safe_devices",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );
    }

    private void removeDeviceFromTarget(SQLiteDatabase db, String deviceKey) {
        deviceKey = normalizeDeviceKey(deviceKey);
        if (deviceKey == null || deviceKey.isEmpty()) return;

        db.delete("target_devices", "device_key = ?", new String[]{deviceKey});
    }

    private void removeDeviceFromSafe(SQLiteDatabase db, String deviceKey) {
        deviceKey = normalizeDeviceKey(deviceKey);
        if (deviceKey == null || deviceKey.isEmpty()) return;

        db.delete("safe_devices", "device_key = ?", new String[]{deviceKey});
    }

    private void syncStatusTables(SQLiteDatabase db, String deviceKey, String status) {
        deviceKey = normalizeDeviceKey(deviceKey);
        if (deviceKey == null || deviceKey.isEmpty()) return;

        String normalizedStatus = status == null ? "GREY" : status.trim().toUpperCase(Locale.US);

        switch (normalizedStatus) {
            case "TARGET":
                addDeviceToTarget(db, deviceKey);
                removeDeviceFromSafe(db, deviceKey);
                break;

            case "SAFE":
                addDeviceToSafe(db, deviceKey);
                removeDeviceFromTarget(db, deviceKey);
                break;

            default:
                removeDeviceFromTarget(db, deviceKey);
                removeDeviceFromSafe(db, deviceKey);
                break;
        }
    }

    public void rebuildStatusTables(String folderName) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = null;
        boolean ownsTransaction = false;

        try {
            ownsTransaction = !db.inTransaction();
            if (ownsTransaction) db.beginTransaction();

            // Полностью очищаем служебные таблицы
            db.delete("target_devices", null, null);
            db.delete("safe_devices", null, null);

            // Берем все устройства из unique-таблицы
            cursor = db.rawQuery(
                    "SELECT unique_identifier, bssid, cell_id, status " +
                            "FROM \"" + folderName + "_unique\"",
                    null
            );

            while (cursor != null && cursor.moveToNext()) {
                String uniqueIdentifier = cursor.getString(0);
                String bssid = cursor.getString(1);
                int cellId = cursor.getInt(2);
                String status = cursor.getString(3);

                String deviceKey = null;

                if (uniqueIdentifier != null && !uniqueIdentifier.trim().isEmpty()) {
                    deviceKey = uniqueIdentifier;
                } else if (bssid != null && !bssid.trim().isEmpty()) {
                    deviceKey = bssid;
                } else if (cellId > 0) {
                    deviceKey = String.valueOf(cellId);
                }

                syncStatusTables(db, deviceKey, status);
            }

            if (ownsTransaction) db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error rebuilding status tables: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
            if (ownsTransaction && db.inTransaction()) db.endTransaction();
        }
    }

    // Добавлен метод из dev для массового обновления статуса
    public int updateAllDeviceStatusForTable(String folderName, String newStatus) {
        return updateAllDeviceStatusForTable(folderName, newStatus, true, false);
    }

    public int updateAllDeviceStatusForTable(String folderName, String newStatus,
                                             boolean includeCellTowers,
                                             boolean includeSafeDevices) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("status", newStatus);

        int rowsAffected = 0;

        try {
            db.beginTransaction();

            if ("TARGET".equalsIgnoreCase(newStatus)) {
                String targetWhere = buildBulkTargetWhere(includeCellTowers, includeSafeDevices);
                rowsAffected = db.update("\"" + folderName + "\"", values, targetWhere, null);
                db.update("\"" + folderName + "_unique\"", values, targetWhere, null);
            } else {
                rowsAffected = db.update("\"" + folderName + "\"", values, null, null);
                db.update("\"" + folderName + "_unique\"", values, null, null);
            }

            // Полная пересборка target/safe по актуальному status из _unique
            rebuildStatusTables(folderName);
            if ("TARGET".equalsIgnoreCase(newStatus)
                    && AlarmModeConfig.isMarkedTargetAlarmEnabled(mContext)) {
                Cursor c = null;
                try {
                    String targetWhere = buildBulkTargetWhere(includeCellTowers, includeSafeDevices);
                    String whereClause = targetWhere == null ? "" : " WHERE " + targetWhere;
                    c = db.rawQuery(
                            "SELECT unique_identifier, bssid, cell_id FROM \"" + folderName + "_unique\"" + whereClause,
                            null
                    );

                    while (c != null && c.moveToNext()) {
                        String uniqueIdentifier = c.getString(0);
                        String bssid = c.getString(1);
                        int cellId = c.getInt(2);

                        String deviceKey = null;

                        if (uniqueIdentifier != null && !uniqueIdentifier.trim().isEmpty()) {
                            deviceKey = uniqueIdentifier;
                        } else if (bssid != null && !bssid.trim().isEmpty()) {
                            deviceKey = bssid;
                        } else if (cellId > 0) {
                            deviceKey = String.valueOf(cellId);
                        }

                        if (deviceKey != null && !deviceKey.trim().isEmpty()) {
                            notifyTargetDeviceNow(folderName, deviceKey);
                        }
                    }
                } finally {
                    if (c != null) c.close();
                }
            }

            db.setTransactionSuccessful();
            notifyDevicesChanged(folderName);
        } catch (Exception e) {
            Log.e(TAG, "Error updating folder statuses: " + e.getMessage());
        } finally {
            if (db.inTransaction()) db.endTransaction();
        }

        return rowsAffected;
    }

    private String buildBulkTargetWhere(boolean includeCellTowers, boolean includeSafeDevices) {
        List<String> clauses = new ArrayList<>();
        if (!includeSafeDevices) {
            clauses.add("UPPER(COALESCE(status, 'GREY')) != 'SAFE'");
        }
        if (!includeCellTowers) {
            clauses.add("UPPER(COALESCE(type, '')) != 'CELL'");
        }
        if (clauses.isEmpty()) {
            return null;
        }
        StringBuilder where = new StringBuilder();
        for (int i = 0; i < clauses.size(); i++) {
            if (i > 0) {
                where.append(" AND ");
            }
            where.append(clauses.get(i));
        }
        return where.toString();
    }

    public boolean deleteTable(String folderName) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            db.execSQL("DROP TABLE IF EXISTS \"" + folderName + "\"");
            db.execSQL("DROP TABLE IF EXISTS \"" + folderName + "_unique\"");
            RAW_TABLE_READY.remove(folderName);
            UNIQUE_TABLE_READY.remove(folderName + "_unique");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting folder tables: " + e.getMessage());
            return false;
        }
    }

    public List<String> getAllTables() {
        List<String> tables = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master " +
                            "WHERE type='table' " +
                            "AND name NOT LIKE 'sqlite_%' " +
                            "AND name NOT LIKE 'android_%' " +
                            "AND name != 'unique_devices' " +
                            "AND name != 'target_devices' " +
                            "AND name != 'safe_devices' " +
                            "AND name NOT LIKE '%_unique'",
                    null
            );
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    tables.add(cursor.getString(0));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return tables;
    }

    public GreySearchData searchGreyDevicesAcrossFolders(
            String sourceFolder,
            Long firstPeriodStart,
            Long firstPeriodEnd,
            Long secondPeriodStart,
            Long secondPeriodEnd,
            List<String> searchFolders
    ) {
        GreySearchData data = new GreySearchData();
        if (sourceFolder == null || sourceFolder.trim().isEmpty()) {
            return data;
        }

        Set<String> initialMacs = getGreyMacsFromFolder(sourceFolder, firstPeriodStart, firstPeriodEnd);
        if (initialMacs.isEmpty()) {
            return data;
        }

        List<String> folders = new ArrayList<>();
        if (searchFolders == null || searchFolders.isEmpty()) {
            folders.addAll(getAllTables());
        } else {
            folders.addAll(searchFolders);
        }

        Map<String, GreySearchDevice> devices = new LinkedHashMap<>();
        for (String folder : folders) {
            if (folder == null || folder.trim().isEmpty()) continue;
            loadGreySearchPointsForFolder(folder, initialMacs, secondPeriodStart, secondPeriodEnd, devices, data.points);
        }

        data.devices.addAll(devices.values());
        Collections.sort(data.devices, (left, right) -> {
            int byCount = Integer.compare(right.detectionCount, left.detectionCount);
            if (byCount != 0) return byCount;
            return Long.compare(right.lastSeen, left.lastSeen);
        });
        Collections.sort(data.points, (left, right) -> Long.compare(right.timestamp, left.timestamp));
        return data;
    }

    private Set<String> getGreyMacsFromFolder(String folderName, Long start, Long end) {
        Set<String> macs = new LinkedHashSet<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT DISTINCT UPPER(bssid) AS mac FROM \"" + folderName + "\" " +
                            "WHERE bssid IS NOT NULL AND TRIM(bssid) != '' " +
                            "AND UPPER(COALESCE(status, 'GREY')) = 'GREY' " +
                            "AND UPPER(COALESCE(type, '')) != 'CELL'"
            );
            List<String> args = new ArrayList<>();
            appendTimestampWhere(sql, args, start, end);
            cursor = db.rawQuery(sql.toString(), args.toArray(new String[0]));
            while (cursor != null && cursor.moveToNext()) {
                String mac = normalizeDeviceKey(cursor.getString(0));
                if (mac != null && !mac.isEmpty()) {
                    macs.add(mac);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading grey MACs from " + folderName + ": " + e.getMessage(), e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return macs;
    }

    private void loadGreySearchPointsForFolder(
            String folderName,
            Set<String> targetMacs,
            Long start,
            Long end,
            Map<String, GreySearchDevice> devices,
            List<GreySearchPoint> points
    ) {
        if (targetMacs == null || targetMacs.isEmpty()) return;
        List<String> macList = new ArrayList<>(targetMacs);
        for (int offset = 0; offset < macList.size(); offset += 400) {
            int endIndex = Math.min(offset + 400, macList.size());
            loadGreySearchPointsChunk(folderName, macList.subList(offset, endIndex), start, end, devices, points);
        }
    }

    private void loadGreySearchPointsChunk(
            String folderName,
            List<String> macChunk,
            Long start,
            Long end,
            Map<String, GreySearchDevice> devices,
            List<GreySearchPoint> points
    ) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            StringBuilder placeholders = new StringBuilder();
            List<String> args = new ArrayList<>();
            for (int i = 0; i < macChunk.size(); i++) {
                if (i > 0) placeholders.append(',');
                placeholders.append('?');
                args.add(macChunk.get(i));
            }

            StringBuilder sql = new StringBuilder(
                    "SELECT type,name,UPPER(bssid) AS mac,latitude,longitude,timestamp,status " +
                            "FROM \"" + folderName + "\" " +
                            "WHERE UPPER(COALESCE(bssid, '')) IN (" + placeholders + ")"
            );
            appendTimestampWhere(sql, args, start, end);
            sql.append(" ORDER BY timestamp DESC");

            cursor = db.rawQuery(sql.toString(), args.toArray(new String[0]));
            while (cursor != null && cursor.moveToNext()) {
                String mac = normalizeDeviceKey(cursor.getString(cursor.getColumnIndexOrThrow("mac")));
                if (mac == null || mac.isEmpty()) continue;

                String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
                double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));
                String status = cursor.getString(cursor.getColumnIndexOrThrow("status"));

                GreySearchDevice device = devices.get(mac);
                if (device == null) {
                    device = new GreySearchDevice(mac, name, type);
                    devices.put(mac, device);
                }
                device.detectionCount++;
                device.lastSeen = Math.max(device.lastSeen, timestamp);
                if ((device.name == null || device.name.trim().isEmpty()) && name != null && !name.trim().isEmpty()) {
                    device.name = name;
                }
                if ((device.type == null || device.type.trim().isEmpty()) && type != null && !type.trim().isEmpty()) {
                    device.type = type;
                }

                if (latitude != 0.0 && longitude != 0.0) {
                    points.add(new GreySearchPoint(mac, name, type, folderName, latitude, longitude, timestamp, status));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading grey search points from " + folderName + ": " + e.getMessage(), e);
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void appendTimestampWhere(StringBuilder sql, List<String> args, Long start, Long end) {
        if (start != null) {
            sql.append(" AND timestamp >= ?");
            args.add(String.valueOf(start));
        }
        if (end != null) {
            sql.append(" AND timestamp <= ?");
            args.add(String.valueOf(end));
        }
    }

    public static class GreySearchData {
        public final List<GreySearchDevice> devices = new ArrayList<>();
        public final List<GreySearchPoint> points = new ArrayList<>();
    }

    public static class GreySearchDevice {
        public final String mac;
        public String name;
        public String type;
        public int detectionCount;
        public long lastSeen;

        public GreySearchDevice(String mac, String name, String type) {
            this.mac = mac;
            this.name = name;
            this.type = type;
        }
    }

    public static class GreySearchPoint {
        public final String mac;
        public final String name;
        public final String type;
        public final String folderName;
        public final double latitude;
        public final double longitude;
        public final long timestamp;
        public final String status;

        public GreySearchPoint(String mac, String name, String type, String folderName,
                               double latitude, double longitude, long timestamp, String status) {
            this.mac = mac;
            this.name = name;
            this.type = type;
            this.folderName = folderName;
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
            this.status = status;
        }
    }

    public void createTableIfNotExists(String tableName) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            if ("target_devices".equals(tableName) || "safe_devices".equals(tableName)) {
                return;
            }
            createFolderRawTableIfNotExists(db, tableName);
            createFolderUniqueTableIfNotExists(db, getUniqueTableName(tableName));
        } catch (Exception e) {
            Log.e(TAG, "Error creating table " + tableName + ": " + e.getMessage());
        }
    }

    private String getUniqueTableName(String folderName) {
        return folderName + "_unique";
    }

    private void createFolderRawTableIfNotExists(SQLiteDatabase db, String tableName) {
        if (RAW_TABLE_READY.contains(tableName)) return;
        String safeName = "\"" + tableName + "\"";
        Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{tableName}
        );
        boolean tableExists = cursor != null && cursor.getCount() > 0;
        if (cursor != null) cursor.close();

        if (!tableExists) {
            String createTableQuery = "CREATE TABLE " + safeName + " (" +
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
                    "latitude REAL NOT NULL," +
                    "longitude REAL NOT NULL," +
                    "altitude REAL," +
                    "location_accuracy REAL," +
                    "timestamp INTEGER," +
                    "status TEXT DEFAULT 'GREY'," +
                    "is_uploaded INTEGER DEFAULT 0," +
                    "folder_name TEXT DEFAULT '')";
            db.execSQL(createTableQuery);
        } else {
            addMissingColumns(db, tableName);
        }
        createRawTableIndexes(db, tableName);
        RAW_TABLE_READY.add(tableName);
    }

    private void createFolderUniqueTableIfNotExists(SQLiteDatabase db, String uniqueTableName) {
        if (UNIQUE_TABLE_READY.contains(uniqueTableName)) return;
        String safeName = "\"" + uniqueTableName + "\"";

        String createTableQuery = "CREATE TABLE IF NOT EXISTS " + safeName + " (" +
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
        createUniqueTableIndexes(db, uniqueTableName);
        UNIQUE_TABLE_READY.add(uniqueTableName);
    }

    private void addMissingColumns(SQLiteDatabase db, String tableName) {
        try {
            // Проверяем наличие колонки folder_name
            Cursor cursor = db.rawQuery("PRAGMA table_info(\"" + tableName + "\")", null);
            boolean hasFolderColumn = false;

            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex("name");
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && "folder_name".equals(cursor.getString(nameIndex))) {
                        hasFolderColumn = true;
                        break;
                    }
                }
                cursor.close();
            }

            // Добавляем колонку если её нет
            if (!hasFolderColumn) {
                db.execSQL("ALTER TABLE \"" + tableName + "\" ADD COLUMN folder_name TEXT DEFAULT ''");
                Log.d(TAG, "Added folder_name column to table: " + tableName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adding missing columns to table " + tableName + ": " + e.getMessage());
        }
    }

    // Метод для получения всех записей устройства по MAC адресу (bssid) и cell_id
    public static class DeviceHistorySummary {
        public final List<DeviceLocation> points;
        public final long firstTimestamp;
        public final long lastTimestamp;
        public final int detectionCount;

        public DeviceHistorySummary(List<DeviceLocation> points, long firstTimestamp, long lastTimestamp, int detectionCount) {
            this.points = points;
            this.firstTimestamp = firstTimestamp;
            this.lastTimestamp = lastTimestamp;
            this.detectionCount = detectionCount;
        }

        public boolean isEmpty() {
            return detectionCount <= 0 || points == null || points.isEmpty();
        }
    }

    private static class DeviceHistoryQuery {
        final String whereClause;
        final String[] args;

        DeviceHistoryQuery(String whereClause, String[] args) {
            this.whereClause = whereClause;
            this.args = args;
        }
    }

    private DeviceHistoryQuery buildDeviceHistoryQuery(String deviceKey, String deviceType) {
        boolean isCell = deviceType != null && deviceType.equalsIgnoreCase("Cell");
        if (isCell) {
            if (deviceKey != null && deviceKey.contains("_")) {
                return new DeviceHistoryQuery(
                        "type = 'Cell' AND " +
                                "CAST(mcc AS TEXT) || '_' || CAST(mnc AS TEXT) || '_' || " +
                                "CASE WHEN network_type IN ('LTE', '5G') " +
                                "THEN CAST(tac AS TEXT) ELSE CAST(lac AS TEXT) END || '_' || " +
                                "CAST(cell_id AS TEXT) = ?",
                        new String[]{deviceKey}
                );
            }
            return new DeviceHistoryQuery(
                    "type = 'Cell' AND CAST(cell_id AS TEXT) = ?",
                    new String[]{deviceKey}
            );
        }

        return new DeviceHistoryQuery(
                "bssid = ? AND type IN ('Wi-Fi', 'Bluetooth')",
                new String[]{deviceKey}
        );
    }

    private String[] appendArg(String[] args, String extra) {
        String[] result = new String[args.length + 1];
        System.arraycopy(args, 0, result, 0, args.length);
        result[args.length] = extra;
        return result;
    }

    public DeviceHistorySummary getDeviceHistorySummaryByKey(String tableName,
                                                             String deviceKey,
                                                             String deviceType,
                                                             int pointLimit) {
        List<DeviceLocation> points = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        int count = 0;
        long firstTimestamp = 0L;
        long lastTimestamp = 0L;

        try {
            DeviceHistoryQuery spec = buildDeviceHistoryQuery(deviceKey, deviceType);
            cursor = db.rawQuery(
                    "SELECT COUNT(*), MIN(timestamp), MAX(timestamp) FROM \"" + tableName + "\" WHERE " + spec.whereClause,
                    spec.args
            );
            if (cursor != null && cursor.moveToFirst()) {
                count = cursor.getInt(0);
                if (count > 0) {
                    firstTimestamp = cursor.getLong(1);
                    lastTimestamp = cursor.getLong(2);
                }
            }
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }

            if (count <= 0) {
                return new DeviceHistorySummary(points, 0L, 0L, 0);
            }

            int safeLimit = Math.max(1, Math.min(pointLimit, 5000));
            String pointQuery = "SELECT name, latitude, longitude, timestamp FROM (" +
                    "SELECT name, latitude, longitude, timestamp FROM \"" + tableName + "\" " +
                    "WHERE " + spec.whereClause + " ORDER BY timestamp DESC LIMIT ?" +
                    ") ORDER BY timestamp ASC";
            cursor = db.rawQuery(pointQuery, appendArg(spec.args, String.valueOf(safeLimit)));

            while (cursor != null && cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
                double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
                String timestamp = cursor.getString(cursor.getColumnIndexOrThrow("timestamp"));
                points.add(new DeviceLocation(name, latitude, longitude, timestamp, deviceKey));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting device history summary: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }

        return new DeviceHistorySummary(points, firstTimestamp, lastTimestamp, count);
    }

    public List<DeviceLocation> getDeviceHistoryByKey(String tableName, String deviceKey, String deviceType) {
        List<DeviceLocation> history = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            boolean isCell = deviceType != null && deviceType.equalsIgnoreCase("Cell");

            String query;
            String[] args;

            if (isCell) {
                if (deviceKey.contains("_")) {
                    query = "SELECT name, latitude, longitude, timestamp, cell_id " +
                            "FROM \"" + tableName + "\" " +
                            "WHERE type = 'Cell' AND " +
                            "CAST(mcc AS TEXT) || '_' || CAST(mnc AS TEXT) || '_' || " +
                            "CASE WHEN network_type IN ('LTE', '5G') " +
                            "THEN CAST(tac AS TEXT) ELSE CAST(lac AS TEXT) END || '_' || " +
                            "CAST(cell_id AS TEXT) = ? " +
                            "ORDER BY timestamp ASC";
                    args = new String[]{deviceKey};
                } else {
                    query = "SELECT name, latitude, longitude, timestamp, cell_id " +
                            "FROM \"" + tableName + "\" " +
                            "WHERE type = 'Cell' AND CAST(cell_id AS TEXT) = ? " +
                            "ORDER BY timestamp ASC";
                    args = new String[]{deviceKey};
                }
            } else {
                query = "SELECT name, latitude, longitude, timestamp, bssid " +
                        "FROM \"" + tableName + "\" " +
                        "WHERE bssid = ? AND type IN ('Wi-Fi', 'Bluetooth') " +
                        "ORDER BY timestamp ASC";
                args = new String[]{deviceKey};
            }

            cursor = db.rawQuery(query, args);

            while (cursor != null && cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
                double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
                String timestamp = cursor.getString(cursor.getColumnIndexOrThrow("timestamp"));

                history.add(new DeviceLocation(name, latitude, longitude, timestamp, deviceKey));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting device history by key: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
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

                    double jumpMeters = calculateDistance(prevLat, prevLon, curLat, curLon);

                    // Пропускаем слишком близкие точки
                    if (jumpMeters < 10.0) {
                        prevLat = curLat;
                        prevLon = curLon;
                        continue;
                    }

                    totalDistance += jumpMeters;

                    prevLat = curLat;
                    prevLon = curLon;
                }
            }
            cursor.close();
        }


        // Возвращаем результат в километрах
        return totalDistance / 1000.0;
    }
    public String getDeviceExportJson(String tableName, String mac) {
        SQLiteDatabase db = this.getReadableDatabase();
        org.json.JSONObject root = new org.json.JSONObject();
        try {
            Cursor cursor = db.rawQuery("SELECT * FROM \"" + tableName + "\" WHERE bssid = ? ORDER BY timestamp ASC", new String[]{mac});

            org.json.JSONArray historyArray = new org.json.JSONArray();
            if (cursor != null && cursor.moveToFirst()) {
                root.put("name", cursor.getString(cursor.getColumnIndexOrThrow("name")));
                root.put("mac", mac);
                root.put("type", cursor.getString(cursor.getColumnIndexOrThrow("type")));
                root.put("status", cursor.getString(cursor.getColumnIndexOrThrow("status")));

                do {
                    org.json.JSONObject point = new org.json.JSONObject();
                    point.put("lat", cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")));
                    point.put("lon", cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")));
                    point.put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")));
                    historyArray.put(point);
                } while (cursor.moveToNext());

                root.put("points_history", historyArray);
                cursor.close();
            }
            return root.toString(4);
        } catch (Exception e) {
            return null;
        }
    }

    // 3. Проверка условий Target
    private String evaluateTargetStatus(String uniqueId, double curLat, double curLon, long curTime, String tableName) {
        if (uniqueId == null || uniqueId.isEmpty()) return "GREY";

        SQLiteDatabase db = this.getReadableDatabase();
        String column = uniqueId.contains(":") ? "bssid" : "cell_id";

        Cursor cursor = db.query("\"" + tableName + "\"",
                new String[]{"latitude", "longitude", "timestamp"},
                "CAST(" + column + " AS TEXT) = ?",
                new String[]{uniqueId},
                null, null, "timestamp DESC", "1");

        double speedKmH = 0;
        double dist24h = 0;

        try {
            if (cursor != null && cursor.moveToFirst()) {
                double prevLat = cursor.getDouble(0);
                double prevLon = cursor.getDouble(1);
                long prevTime = cursor.getLong(2);

                // 1. Считаем дистанцию текущего прыжка
                double currentJumpMeters = calculateDistance(prevLat, prevLon, curLat, curLon);

                // Если между точками меньше 10 метров - пропускаем
                if (currentJumpMeters < 10.0) {
                    Log.d("MATH_CHECK", "ID: " + uniqueId + " - прыжок < 10 м, пропускаем точку");
                    return "GREY";
                }

                // 2. Считаем скорость
                double timeHours = (double) (curTime - prevTime) / 3600000.0;
                if (timeHours > 0) {
                    speedKmH = (currentJumpMeters / 1000.0) / timeHours;
                }

                // 3. Фильтры перед дальнейшим расчетом
                if (currentJumpMeters > 1000.0 && speedKmH < 20.0) {
                    Log.d("MATH_CHECK", String.format(
                            Locale.US,
                            "ID: %s - расстояние > 1 км и скорость < 20 км/ч, дальше не считаем",
                            uniqueId
                    ));
                    return "TARGET";
                }

                if (currentJumpMeters > 10000.0 && speedKmH > 20.0) {
                    Log.d("MATH_CHECK", String.format(
                            Locale.US,
                            "ID: %s - расстояние > 10 км и скорость > 20 км/ч, дальше не считаем",
                            uniqueId
                    ));
                    return "TARGET";
                }

                // 4. Считаем накопленный путь за 24 часа
                dist24h = getDistanceLast24h(uniqueId, tableName) + (currentJumpMeters / 1000.0);

                Log.d("MATH_CHECK", String.format(
                        Locale.US,
                        "ID: %s | Прыжок: %.2f м | Скорость: %.2f км/ч | Итоговый путь: %.2f км",
                        uniqueId, currentJumpMeters, speedKmH, dist24h
                ));
            } else {
                Log.d("MATH_CHECK", "ID: " + uniqueId + " - первая встреча, статус GREY");
                return "GREY";
            }

            // 5. Основные условия target
            if (speedKmH > 20.0 && dist24h > 10.0) {
                Log.d("MATH_CHECK", "!!! TARGET DETECTED (High Speed) !!!");
                return "TARGET";
            }

            if (speedKmH <= 20.0 && dist24h > 1.0) {
                Log.d("MATH_CHECK", "!!! TARGET DETECTED (Movement) !!!");
                return "TARGET";
            }

            return "GREY";

        } catch (Exception e) {
            Log.e(TAG, "Error in evaluateTargetStatus: " + e.getMessage());
            return "GREY";
        } finally {
            if (cursor != null) cursor.close();
        }
    }
    private void sendSystemNotification(String title, String message) {
        String channelId = "target_alerts";
        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        // 1. Создаем канал для Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, LocaleHelper.getString(mContext, R.string.target_alerts_channel_name),
                    NotificationManager.IMPORTANCE_HIGH // Высокая важность дает звук и всплывающий баннер
            );
            channel.setDescription(LocaleHelper.getString(mContext, R.string.target_alerts_channel_description));
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
                .setSmallIcon(R.drawable.ic_cloud)
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

    /**
     * Добавляет устройство в таблицу уникальных устройств, используя существующее соединение с БД
     */
    public void addToUniqueDevices(SQLiteDatabase db, ContentValues deviceData) {
        try {
            String folderName = deviceData.getAsString("folder_name");
            if (folderName == null || folderName.trim().isEmpty()) {
                Log.w(TAG, "addToUniqueDevices skipped: folder_name is empty");
                return;
            }

            UniqueDevicesHelper helper =
                    new UniqueDevicesHelper(mContext, getUniqueTableName(folderName));
            helper.addOrUpdateDevice(db, deviceData);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка добавления в уникальные устройства: " + e.getMessage());
        }
    }

    public void addToFolderUniqueDevices(SQLiteDatabase db, String folderName, ContentValues deviceData) {
        try {
            String uniqueTableName = getUniqueTableName(folderName);
            UniqueDevicesHelper helper = new UniqueDevicesHelper(mContext, uniqueTableName);
            helper.addOrUpdateDevice(db, deviceData);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка добавления в уникальные устройства папки: " + e.getMessage());
        }
    }

    /**
     * Добавляет устройство в таблицу уникальных устройств (создает новое соединение)
     * Используйте этот метод только вне транзакций!
     */
    public void addToUniqueDevices(ContentValues deviceData) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            db.beginTransaction();

            addToUniqueDevices(db, deviceData);

            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Ошибка добавления в уникальные устройства: " + e.getMessage());
        } finally {
            if (db != null && db.isOpen()) {
                try {
                    db.endTransaction();
                } catch (Exception e) {
                    Log.e(TAG, "Error ending transaction: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Получает helper для работы с уникальными устройствами
     */
    public UniqueDevicesHelper getUniqueDevicesHelper(String folderName) {
        return new UniqueDevicesHelper(mContext, getUniqueTableName(folderName));
    }

    /**
     * Получает последнюю запись устройства из Основная
     */
    private String getLatestDeviceStatus(SQLiteDatabase db, String uniqueId, String tableName, ContentValues values) {
        Cursor cursor = null;
        try {
            String bssid = values.getAsString("bssid");
            String query;
            String[] args;

            if (bssid != null && !bssid.trim().isEmpty()) {
                query = "SELECT status FROM \"" + tableName + "\" " +
                        "WHERE UPPER(COALESCE(bssid, '')) = ? " +
                        "ORDER BY timestamp DESC LIMIT 1";
                args = new String[]{uniqueId};
            } else {
                Long cellId = values.getAsLong("cell_id");
                Integer mcc = values.getAsInteger("mcc");
                Integer mnc = values.getAsInteger("mnc");
                String networkType = values.getAsString("network_type");
                boolean lteLike = "LTE".equalsIgnoreCase(networkType) || "5G".equalsIgnoreCase(networkType);
                Long areaValue = lteLike ? values.getAsLong("tac") : null;
                if (!lteLike) {
                    Integer lac = values.getAsInteger("lac");
                    if (lac != null) areaValue = lac.longValue();
                }

                if (areaValue != null) {
                    query = "SELECT status FROM \"" + tableName + "\" " +
                            "WHERE type = 'Cell' AND cell_id = ? AND mcc = ? AND mnc = ? AND " +
                            (lteLike ? "tac" : "lac") + " = ? " +
                            "ORDER BY timestamp DESC LIMIT 1";
                    args = new String[]{
                            String.valueOf(cellId),
                            String.valueOf(mcc),
                            String.valueOf(mnc),
                            String.valueOf(areaValue)
                    };
                } else {
                    query = "SELECT status FROM \"" + tableName + "\" " +
                            "WHERE type = 'Cell' AND cell_id = ? AND mcc = ? AND mnc = ? " +
                            "ORDER BY timestamp DESC LIMIT 1";
                    args = new String[]{
                            String.valueOf(cellId),
                            String.valueOf(mcc),
                            String.valueOf(mnc)
                    };
                }
            }

            cursor = db.rawQuery(query, args);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting latest device status: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    public ContentValues getLatestDeviceData(String uniqueId, String tableName) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        ContentValues values = null;

        try {
            String column = uniqueId.contains(":") ? "bssid" : "cell_id";
            String query = "SELECT * FROM \"" + tableName + "\" WHERE " + column + " = ? " +
                    "ORDER BY timestamp DESC LIMIT 1";

            cursor = db.rawQuery(query, new String[]{uniqueId});

            if (cursor != null && cursor.moveToFirst()) {
                values = new ContentValues();
                String[] columns = cursor.getColumnNames();
                for (String col : columns) {
                    int index = cursor.getColumnIndex(col);
                    switch (cursor.getType(index)) {
                        case Cursor.FIELD_TYPE_STRING:
                            values.put(col, cursor.getString(index));
                            break;
                        case Cursor.FIELD_TYPE_INTEGER:
                            values.put(col, cursor.getLong(index));
                            break;
                        case Cursor.FIELD_TYPE_FLOAT:
                            values.put(col, cursor.getDouble(index));
                            break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting latest device data: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }

        return values;
    }

    public void notifyTargetDeviceNow(String tableName, String deviceKey) {
        if (!AlarmModeConfig.isMarkedTargetAlarmEnabled(mContext)) return;

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            deviceKey = normalizeDeviceKey(deviceKey);
            if (deviceKey == null || deviceKey.isEmpty()) return;

            if (deviceKey.contains(":")) {
                cursor = db.rawQuery(
                        "SELECT type, name, latitude, longitude, bssid " +
                                "FROM \"" + tableName + "\" " +
                                "WHERE UPPER(COALESCE(bssid, '')) = ? " +
                                "ORDER BY timestamp DESC LIMIT 1",
                        new String[]{deviceKey}
                );
            } else {
                cursor = db.rawQuery(
                        "SELECT type, name, latitude, longitude, cell_id " +
                                "FROM \"" + tableName + "\" " +
                                "WHERE CAST(cell_id AS TEXT) = ? " +
                                "ORDER BY timestamp DESC LIMIT 1",
                        new String[]{deviceKey}
                );
            }

            if (cursor != null && cursor.moveToFirst()) {
                ContentValues values = new ContentValues();
                values.put("type", cursor.getString(cursor.getColumnIndexOrThrow("type")));
                values.put("name", cursor.getString(cursor.getColumnIndexOrThrow("name")));

                double lat = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
                double lon = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));

                createTargetNotification(values, deviceKey, lat, lon);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error notifyTargetDeviceNow: " + e.getMessage(), e);
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    //единый метод получения статуса из target_devices / safe_devices
    public String getStatusFromServiceTables(String deviceKey) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            deviceKey = normalizeDeviceKey(deviceKey);
            if (deviceKey == null || deviceKey.isEmpty()) {
                return "GREY";
            }

            cursor = db.rawQuery(
                    "SELECT 1 FROM target_devices WHERE UPPER(device_key) = ? LIMIT 1",
                    new String[]{deviceKey}
            );
            if (cursor != null && cursor.moveToFirst()) {
                return "TARGET";
            }
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }

            cursor = db.rawQuery(
                    "SELECT 1 FROM safe_devices WHERE UPPER(device_key) = ? LIMIT 1",
                    new String[]{deviceKey}
            );
            if (cursor != null && cursor.moveToFirst()) {
                return "SAFE";
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting status from service tables: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }

        return "GREY";
    }

    // метод для получения deviceKey
    public Map<String, String> getStatusesFromServiceTables(Set<String> deviceKeys) {
        Map<String, String> statuses = new HashMap<>();
        if (deviceKeys == null || deviceKeys.isEmpty()) return statuses;

        List<String> normalizedKeys = new ArrayList<>();
        for (String key : deviceKeys) {
            String normalized = normalizeDeviceKey(key);
            if (normalized != null && !normalized.isEmpty() && !normalizedKeys.contains(normalized)) {
                normalizedKeys.add(normalized);
            }
        }
        if (normalizedKeys.isEmpty()) return statuses;

        SQLiteDatabase db = this.getReadableDatabase();
        fillStatusesFromTable(db, "target_devices", "TARGET", normalizedKeys, statuses);
        fillStatusesFromTable(db, "safe_devices", "SAFE", normalizedKeys, statuses);
        return statuses;
    }

    private void fillStatusesFromTable(SQLiteDatabase db,
                                       String tableName,
                                       String status,
                                       List<String> normalizedKeys,
                                       Map<String, String> statuses) {
        final int chunkSize = 400;
        for (int offset = 0; offset < normalizedKeys.size(); offset += chunkSize) {
            int end = Math.min(offset + chunkSize, normalizedKeys.size());
            StringBuilder placeholders = new StringBuilder();
            String[] args = new String[end - offset];
            for (int i = offset; i < end; i++) {
                if (i > offset) placeholders.append(',');
                placeholders.append('?');
                args[i - offset] = normalizedKeys.get(i);
            }

            Cursor cursor = null;
            try {
                cursor = db.rawQuery(
                        "SELECT UPPER(device_key) FROM " + tableName +
                                " WHERE UPPER(device_key) IN (" + placeholders + ")",
                        args
                );
                while (cursor != null && cursor.moveToNext()) {
                    String key = cursor.getString(0);
                    if ("SAFE".equals(status) && "TARGET".equals(statuses.get(key))) {
                        continue;
                    }
                    statuses.put(key, status);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error bulk loading statuses from " + tableName + ": " + e.getMessage());
            } finally {
                if (cursor != null) cursor.close();
            }
        }
    }

    public String buildDeviceKeyFromRow(
            String bssid,
            Long cellId,
            Integer mcc,
            Integer mnc,
            Integer lac,
            Long tac,
            String networkType
    ) {
        if (bssid != null && !bssid.trim().isEmpty()) {
            return normalizeDeviceKey(bssid);
        }

        if (cellId != null && cellId > 0 && cellId != 2147483647) {
            if ("LTE".equalsIgnoreCase(networkType) || "5G".equalsIgnoreCase(networkType)) {
                return normalizeDeviceKey(String.format(
                        Locale.US, "%d_%d_%d_%d",
                        mcc != null ? mcc : 0,
                        mnc != null ? mnc : 0,
                        tac != null ? tac : 0,
                        cellId
                ));
            } else {
                return normalizeDeviceKey(String.format(
                        Locale.US, "%d_%d_%d_%d",
                        mcc != null ? mcc : 0,
                        mnc != null ? mnc : 0,
                        lac != null ? lac : 0,
                        cellId
                ));
            }
        }

        return null;
    }

    // метод для получения истории устройства:
    public List<DeviceLocation> getFullDeviceHistory(String tableName, String mac) {
        List<DeviceLocation> history = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            String query = "SELECT name, latitude, longitude, timestamp, bssid FROM \"" + tableName + "\" " +
                    "WHERE bssid = ? ORDER BY timestamp ASC";
            cursor = db.rawQuery(query, new String[]{mac});

            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
                double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
                String timestamp = cursor.getString(cursor.getColumnIndexOrThrow("timestamp"));
                String bssid = cursor.getString(cursor.getColumnIndexOrThrow("bssid"));

                history.add(new DeviceLocation(name, latitude, longitude, timestamp, bssid));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting device history: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }

        return history;
    }

    // метод для получения полной информации об устройстве по его ID:
    public DeviceInfo getDeviceInfoForNotification(String uniqueId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        DeviceInfo deviceInfo = null;

        try {
            // Ищем во всех таблицах
            List<String> tables = getAllTables();

            for (String tableName : tables) {
                String query;
                String[] args;

                if (uniqueId.contains(":")) {
                    // Это MAC-адрес
                    query = "SELECT name, bssid, type, latitude, longitude, timestamp, status FROM \"" + tableName + "\" " +
                            "WHERE bssid = ? ORDER BY timestamp DESC LIMIT 1";
                    args = new String[]{uniqueId};
                } else {
                    // Это cell_id
                    query = "SELECT name, cell_id, type, latitude, longitude, timestamp, status FROM \"" + tableName + "\" " +
                            "WHERE CAST(cell_id AS TEXT) = ? ORDER BY timestamp DESC LIMIT 1";
                    args = new String[]{uniqueId};
                }

                cursor = db.rawQuery(query, args);

                if (cursor != null && cursor.moveToFirst()) {
                    String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                    double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
                    double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));
                    String status = cursor.getString(cursor.getColumnIndexOrThrow("status"));

                    deviceInfo = new DeviceInfo(name, uniqueId, type, latitude, longitude, timestamp, status, tableName);
                    break;
                }
                if (cursor != null) cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting device info for notification: " + e.getMessage());
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
            db.close();
        }

        return deviceInfo;
    }

    // Вспомогательный класс для передачи данных
    public static class DeviceInfo {
        public String name;
        public String mac;
        public String type;
        public double latitude;
        public double longitude;
        public long timestamp;
        public String status;
        public String tableName;

        public DeviceInfo(String name, String mac, String type, double latitude, double longitude,
                          long timestamp, String status, String tableName) {
            this.name = name;
            this.mac = mac;
            this.type = type;
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
            this.status = status;
            this.tableName = tableName;
        }
    }

    private void notifyDevicesChanged(String tableName) {
        try {
            Intent intent = new Intent(ACTION_DEVICES_CHANGED);
            intent.putExtra(EXTRA_TABLE_NAME, tableName);
            mContext.sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error sending devices changed broadcast: " + e.getMessage(), e);
        }
    }

    private void notifyDevicesChangedThrottled(String tableName) {
        long now = System.currentTimeMillis();
        synchronized (LAST_DEVICES_CHANGED_BROADCAST) {
            Long last = LAST_DEVICES_CHANGED_BROADCAST.get(tableName);
            if (last != null && now - last < DEVICES_CHANGED_BROADCAST_INTERVAL_MS) {
                return;
            }
            LAST_DEVICES_CHANGED_BROADCAST.put(tableName, now);
        }
        notifyDevicesChanged(tableName);
    }
}
