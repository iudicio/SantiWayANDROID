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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.text.SimpleDateFormat;

import com.example.santiway.bluetooth_scanner.BluetoothDevice;
import com.example.santiway.DeviceListActivity;

public class MainDatabaseHelper extends SQLiteOpenHelper {
    private static long lastGlobalSoundTime = 0;
    private static final long SOUND_INTERVAL = 3600000;

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
        String createUnifiedTable = "CREATE TABLE \"unified_data\" (" +
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
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 8) {
            try {
                Cursor cursor = db.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
                        null
                );

                if (cursor != null) {
                    try {
                        while (cursor.moveToNext()) {
                            String tableName = cursor.getString(0);
                            try {
                                // Проверяем существование колонки
                                Cursor pragma = db.rawQuery("PRAGMA table_info(\"" + tableName + "\")", null);
                                boolean hasColumn = false;
                                int nameIndex = pragma.getColumnIndex("name");
                                while (pragma.moveToNext()) {
                                    if (nameIndex >= 0 && "folder_name".equals(pragma.getString(nameIndex))) {
                                        hasColumn = true;
                                        break;
                                    }
                                }
                                pragma.close();

                                if (!hasColumn) {
                                    db.execSQL("ALTER TABLE \"" + tableName + "\" ADD COLUMN folder_name TEXT DEFAULT ''");
                                    Log.d(TAG, "Added folder_name to table: " + tableName);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error updating table " + tableName + ": " + e.getMessage());
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
        values.put("folder_name", "");

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
        values.put("folder_name", "");

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
        values.put("folder_name", "");

        String selection = "cell_id = ? AND network_type = ?";
        String[] selectionArgs = {String.valueOf(tower.getCellId()), tower.getNetworkType()};
        return addOrUpdateUnifiedDevice(tableName, values, selection, selectionArgs, tower.getTimestamp());
    }

    private long addOrUpdateUnifiedDevice(String tableName, ContentValues values, String selection, String[] selectionArgs, long newTimestamp) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        long result = -1;

        try {
            db = this.getWritableDatabase();

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

            if (bssid != null) {
                checkQuery = "SELECT COUNT(*) FROM \"" + tableName + "\" " +
                        "WHERE bssid = ? AND ABS(timestamp - ?) <= 2000";
                checkArgs = new String[]{uniqueId, String.valueOf(newTimestamp)};
            } else {
                // Для сотовых вышек ищем по составному ключу
                checkQuery = "SELECT COUNT(*) FROM \"" + tableName + "\" " +
                        "WHERE type = 'Cell' AND " +
                        "cell_id = ? AND mcc = ? AND mnc = ? AND " +
                        "ABS(timestamp - ?) <= 2000";

                Long cellId = values.getAsLong("cell_id");
                Integer mcc = values.getAsInteger("mcc");
                Integer mnc = values.getAsInteger("mnc");

                checkArgs = new String[]{
                        String.valueOf(cellId),
                        String.valueOf(mcc),
                        String.valueOf(mnc),
                        String.valueOf(newTimestamp)
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
            ContentValues latestDevice = getLatestDeviceData(uniqueId, tableName);

            if (latestDevice != null) {
                String dbStatus = latestDevice.getAsString("status");
                if (dbStatus != null && !dbStatus.isEmpty()) {
                    lastStatus = dbStatus;
                }
            }

            if (curLat != null && curLon != null && "GREY".equals(lastStatus)) {
                try {
                    String calculatedStatus = evaluateTargetStatus(uniqueId, curLat, curLon, newTimestamp, tableName);
                    values.put("status", calculatedStatus);
                    Log.d(TAG, "Status for " + uniqueId + ": " + calculatedStatus);

                    if ("Target".equals(calculatedStatus)) {
                        createTargetNotification(values, uniqueId, curLat, curLon);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error calculating status: " + e.getMessage());
                }
            } else {
                values.put("status", lastStatus);
            }

            // 7. Добавляем запись в основную таблицу
            db.beginTransaction();
            result = db.insert("\"" + tableName + "\"", null, values);

            if (result != -1) {
                addToUniqueDevices(db, values);
            }

            db.setTransactionSuccessful();

        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
            if (db != null && db.isOpen()) {
                try {
                    if (db.inTransaction()) {
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
        try {
            String type = values.getAsString("type");
            String name = values.getAsString("name");

            if (name == null || name.isEmpty()) {
                name = "Unknown";
            }

            NotificationDatabaseHelper notifDb = new NotificationDatabaseHelper(mContext);

            String title = "Target: " + type;
            String message = String.format(Locale.getDefault(),
                    "Устройство %s (%s) в движении!\nКоординаты: %.6f, %.6f",
                    name, uniqueId, lat, lon);

            NotificationData alert = new NotificationData(
                    UUID.randomUUID().toString(),
                    title,
                    message,
                    new Date(),
                    NotificationData.NotificationType.ALARM,
                    null, null, lat, lon
            );

            notifDb.addNotification(alert, uniqueId);

            // Глобальное уведомление (не чаще раза в час)
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastGlobalSoundTime > SOUND_INTERVAL) {
                lastGlobalSoundTime = currentTime;
                sendSystemNotification(
                        "Внимание: Обнаружены цели!",
                        "Новые движущиеся устройства. Проверьте список."
                );
            }

            Log.d(TAG, "🔔 Target notification created for: " + uniqueId);

        } catch (Exception e) {
            Log.e(TAG, "Error creating notification: " + e.getMessage());
        }
    }
    public void clearTableData(String tableName) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            db.delete("\"" + tableName + "\"", null, null);
            Log.d("DB_CHECK", "Все данные из таблицы " + tableName + " удалены.");
        } catch (Exception e) {
            Log.e("DB_CHECK", "Ошибка при очистке: " + e.getMessage());
        } finally {
            db.close();
        }
    }
    public void renameTable(String oldName, String newName) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            // Оборачиваем имена в двойные кавычки
            String query = "ALTER TABLE \"" + oldName + "\" RENAME TO \"" + newName + "\"";
            db.execSQL(query);
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
        }
        return tables;
    }
    public void createTableIfNotExists(String tableName) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            String safeName = "\"" + tableName + "\"";

            // Проверяем существование таблицы
            Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                    new String[]{tableName});
            boolean tableExists = cursor != null && cursor.getCount() > 0;
            if (cursor != null) cursor.close();

            if (!tableExists) {
                // Создаем новую таблицу
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
                        "latitude REAL," +
                        "longitude REAL," +
                        "altitude REAL," +
                        "location_accuracy REAL," +
                        "timestamp INTEGER," +
                        "status TEXT DEFAULT 'ignore'," +
                        "is_uploaded INTEGER DEFAULT 0," +
                        "folder_name TEXT DEFAULT '')"; // Добавляем колонку
                db.execSQL(createTableQuery);
            } else {
                // Проверяем и добавляем недостающие колонки
                addMissingColumns(db, tableName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating table " + tableName + ": " + e.getMessage());
        } finally {
        }
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
                    return "GREY";
                }

                if (currentJumpMeters > 10000.0 && speedKmH > 20.0) {
                    Log.d("MATH_CHECK", String.format(
                            Locale.US,
                            "ID: %s - расстояние > 10 км и скорость > 20 км/ч, дальше не считаем",
                            uniqueId
                    ));
                    return "GREY";
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
            db.close();
        }
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
            UniqueDevicesHelper helper = new UniqueDevicesHelper(mContext);
            // Добавляем или обновляем устройство, используя переданное соединение
            helper.addOrUpdateDevice(db, deviceData);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка добавления в уникальные устройства: " + e.getMessage());
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
    public UniqueDevicesHelper getUniqueDevicesHelper() {
        return new UniqueDevicesHelper(mContext);
    }

    /**
     * Получает последнюю запись устройства из unified_data
     */
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
}