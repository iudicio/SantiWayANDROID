package com.example.santiway;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import com.example.santiway.cell_scanner.CellTower;
import com.example.santiway.wifi_scanner.WifiDevice;
import java.util.ArrayList;
import java.util.List;
import com.example.santiway.bluetooth_scanner.BluetoothDevice;

public class MainDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "MainDatabaseHelper";
    private static final String DATABASE_NAME = "UnifiedScanner.db";
    private static final int DATABASE_VERSION = 5; // УВЕЛИЧЕНО

    public MainDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
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
                "status TEXT DEFAULT 'ignore')";
        db.execSQL(createUnifiedTable);
    }
    public void deleteOldRecordsFromAllTables(long maxAgeMillis) {
        List<String> tables = getAllTables();
        for (String table : tables) {
            long cutoffTime = System.currentTimeMillis() - maxAgeMillis;
            SQLiteDatabase db = this.getWritableDatabase();
            try {
                db.delete("\"" + table + "\"", "timestamp < ?", new String[]{String.valueOf(cutoffTime)});
            } catch (Exception e) {
                Log.e(TAG, "Error deleting old records from table " + table + ": " + e.getMessage());
            } finally {
                db.close();
            }
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
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
            db.beginTransaction();
            cursor = db.query("\"" + tableName + "\"",
                    new String[]{"id", "timestamp"},
                    selection,
                    selectionArgs,
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                long existingId = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                long existingTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));

                if (newTimestamp > existingTimestamp) {
                    result = db.update("\"" + tableName + "\"", values, "id = ?", new String[]{String.valueOf(existingId)});
                    Log.d(TAG, "Updated device in table: " + tableName);
                } else {
                    result = 1;
                    Log.d(TAG, "Skipped older device in table: " + tableName);
                }
            } else {
                result = db.insert("\"" + tableName + "\"", null, values);
                Log.d(TAG, "Inserted new device in table: " + tableName);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error in transaction: " + e.getMessage());
            result = -1;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            try {
                db.endTransaction();
            } catch (Exception e) {
                Log.e(TAG, "Error ending transaction: " + e.getMessage());
            }
            db.close();
        }
        return result;
    }

    public List<DeviceListActivity.Device> getAllDataFromTable(String tableName) {
        List<DeviceListActivity.Device> deviceList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery("SELECT type, name, latitude, longitude, timestamp FROM \"" + tableName + "\"", null);
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
        String safeName = "\"" + tableName + "\"";
        String createTableQuery = "CREATE TABLE IF NOT EXISTS " + safeName + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "type TEXT NOT NULL," + // Важно: добавляем колонки type и name
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
                "status TEXT DEFAULT 'ignore')";
        db.execSQL(createTableQuery);
        db.close();
    }
}