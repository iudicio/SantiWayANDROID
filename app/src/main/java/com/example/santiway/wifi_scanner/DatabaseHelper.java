package com.example.santiway.wifi_scanner;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "wifi_scanner.db";
    private static final int DATABASE_VERSION = 2;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "Database created (empty), tables will be created dynamically");
        createDefaultTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Database upgraded from " + oldVersion + " to " + newVersion);
    }

    private void createDefaultTable(SQLiteDatabase db) {
        try {
            String sql = "CREATE TABLE IF NOT EXISTS \"default_table\" (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "ssid TEXT," +
                    "bssid TEXT," +
                    "signalStrength INTEGER," +
                    "frequency INTEGER," +
                    "capabilities TEXT," +
                    "vendor TEXT," +
                    "timestamp LONG" +
                    ")";
            db.execSQL(sql);
            Log.d(TAG, "Default table created");
        } catch (Exception e) {
            Log.e(TAG, "Error creating default table", e);
        }
    }

    public void createTableIfNotExists(String tableName) {
        String safeTableName = sanitizeTableName(tableName);
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            String sql = "CREATE TABLE IF NOT EXISTS \"" + safeTableName + "\" (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "ssid TEXT," +
                    "bssid TEXT," +
                    "signalStrength INTEGER," +
                    "frequency INTEGER," +
                    "capabilities TEXT," +
                    "vendor TEXT," +
                    "timestamp LONG" +
                    ")";
            db.execSQL(sql);
            Log.d(TAG, "Table created: " + safeTableName);
        } catch (Exception e) {
            Log.e(TAG, "Error creating table: " + safeTableName, e);
        }
    }

    private String sanitizeTableName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return "default_table";
        }

        String sanitized = tableName.replaceAll("[^a-zA-Z0-9_]", "_");

        if (!sanitized.isEmpty() && Character.isDigit(sanitized.charAt(0))) {
            sanitized = "t_" + sanitized;
        }

        if (sanitized.isEmpty()) {
            sanitized = "table_" + System.currentTimeMillis();
        }

        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }

        return sanitized;
    }

    public long addOrUpdateDevice(String tableName, WifiDevice device) {
        String safeTableName = sanitizeTableName(tableName);
        createTableIfNotExists(safeTableName);
        SQLiteDatabase db = this.getWritableDatabase();
        long result = -1;

        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("ssid", device.getSsid() != null ? device.getSsid() : "");
            values.put("bssid", device.getBssid() != null ? device.getBssid() : "");
            values.put("signalStrength", device.getSignalStrength());
            values.put("frequency", device.getFrequency());
            values.put("capabilities", device.getCapabilities() != null ? device.getCapabilities() : "");
            values.put("vendor", device.getVendor() != null ? device.getVendor() : "");
            values.put("timestamp", device.getTimestamp());

            int rows = db.update("\"" + safeTableName + "\"", values, "bssid = ?", new String[]{device.getBssid()});
            if (rows == 0) {
                result = db.insert("\"" + safeTableName + "\"", null, values);
                Log.d(TAG, "Inserted new device into " + safeTableName + ": " + device.getBssid());
            } else {
                result = rows;
                Log.d(TAG, "Updated device in " + safeTableName + ": " + device.getBssid());
            }

            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error addOrUpdateDevice in table: " + safeTableName, e);
        } finally {
            db.endTransaction();
        }

        return result;
    }

    public List<WifiDevice> getAllDevices(String tableName) {
        List<WifiDevice> list = new ArrayList<>();
        String safeTableName = sanitizeTableName(tableName);
        SQLiteDatabase db = this.getReadableDatabase();

        if (!doesTableExist(db, safeTableName)) {
            Log.d(TAG, "Table does not exist: " + safeTableName);
            return list;
        }

        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT * FROM \"" + safeTableName + "\" ORDER BY timestamp DESC", null);
            if (cursor.moveToFirst()) {
                do {
                    WifiDevice device = new WifiDevice();
                    device.setId(cursor.getInt(cursor.getColumnIndexOrThrow("id")));
                    device.setSsid(cursor.getString(cursor.getColumnIndexOrThrow("ssid")));
                    device.setBssid(cursor.getString(cursor.getColumnIndexOrThrow("bssid")));
                    device.setSignalStrength(cursor.getInt(cursor.getColumnIndexOrThrow("signalStrength")));
                    device.setFrequency(cursor.getInt(cursor.getColumnIndexOrThrow("frequency")));
                    device.setCapabilities(cursor.getString(cursor.getColumnIndexOrThrow("capabilities")));
                    device.setVendor(cursor.getString(cursor.getColumnIndexOrThrow("vendor")));
                    device.setTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")));
                    list.add(device);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getAllDevices from table: " + safeTableName, e);
        } finally {
            if (cursor != null) cursor.close();
        }

        return list;
    }

    private boolean doesTableExist(SQLiteDatabase db, String tableName) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                    new String[]{tableName});
            return cursor.getCount() > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error checking table existence: " + tableName, e);
            return false;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    public void clearTable(String tableName) {
        String safeTableName = sanitizeTableName(tableName);
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            int deletedRows = db.delete("\"" + safeTableName + "\"", null, null);
            Log.d(TAG, "Cleared table " + safeTableName + ", deleted rows: " + deletedRows);
        } catch (Exception e) {
            Log.e(TAG, "Error clearing table: " + safeTableName, e);
        }
    }

    public int getDevicesCount(String tableName) {
        String safeTableName = sanitizeTableName(tableName);
        SQLiteDatabase db = this.getReadableDatabase();

        if (!doesTableExist(db, safeTableName)) {
            return 0;
        }

        int count = 0;
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM \"" + safeTableName + "\"", null);
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getDevicesCount from table: " + safeTableName, e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return count;
    }

    public List<String> getAllTables() {
        List<String> tables = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%'",
                    null
            );
            if (cursor.moveToNext()) {
                do {
                    String tableName = cursor.getString(0);
                    // Убираем кавычки если есть
                    if (tableName.startsWith("\"") && tableName.endsWith("\"")) {
                        tableName = tableName.substring(1, tableName.length() - 1);
                    }
                    tables.add(tableName);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getAllTables: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }

        return tables;
    }
}
