package com.example.santiway.wifi_scanner;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "WifiScanner.db";
    private static final int DATABASE_VERSION = 1;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Таблица по умолчанию создается при первом обращении
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Миграция данных при обновлении базы
    }

    public void createTableIfNotExists(String tableName) {
        SQLiteDatabase db = this.getWritableDatabase();
        String createTableQuery = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ssid TEXT," +
                "bssid TEXT UNIQUE," +
                "signal_strength INTEGER," +
                "frequency INTEGER," +
                "capabilities TEXT," +
                "vendor TEXT," +
                "timestamp LONG)";
        db.execSQL(createTableQuery);
        db.close();
    }

    public boolean deleteTable(String tableName) {
        if (tableName.equals("default_table")) {
            return false;
        }

        SQLiteDatabase db = this.getWritableDatabase();
        try {
            db.execSQL("DROP TABLE IF EXISTS " + tableName);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            db.close();
        }
    }

    public long addWifiDevice(String tableName, WifiDevice device) {
        createTableIfNotExists(tableName);

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("ssid", device.getSsid());
        values.put("bssid", device.getBssid());
        values.put("signal_strength", device.getSignalStrength());
        values.put("frequency", device.getFrequency());
        values.put("capabilities", device.getCapabilities());
        values.put("vendor", device.getVendor());
        values.put("timestamp", device.getTimestamp());

        long result = db.insert(tableName, null, values);
        db.close();
        return result;
    }

    public long addOrUpdateDevice(String tableName, WifiDevice device) {
        createTableIfNotExists(tableName);

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("ssid", device.getSsid());
        values.put("bssid", device.getBssid());
        values.put("signal_strength", device.getSignalStrength());
        values.put("frequency", device.getFrequency());
        values.put("capabilities", device.getCapabilities());
        values.put("vendor", device.getVendor());
        values.put("timestamp", device.getTimestamp());

        long result = -1;
        try {
            int rowsAffected = db.update(tableName, values, "bssid = ?",
                    new String[]{device.getBssid()});

            if (rowsAffected == 0) {
                result = db.insert(tableName, null, values);
            } else {
                result = rowsAffected;
            }
        } catch (Exception e) {
            e.printStackTrace();
            result = db.insert(tableName, null, values);
        } finally {
            db.close();
        }

        return result;
    }

    public List<WifiDevice> getAllDevices(String tableName) {
        List<WifiDevice> devices = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.query(tableName, null, null, null, null, null, "timestamp DESC");

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    WifiDevice device = new WifiDevice();
                    device.setSsid(cursor.getString(cursor.getColumnIndexOrThrow("ssid")));
                    device.setBssid(cursor.getString(cursor.getColumnIndexOrThrow("bssid")));
                    device.setSignalStrength(cursor.getInt(cursor.getColumnIndexOrThrow("signal_strength")));
                    device.setFrequency(cursor.getInt(cursor.getColumnIndexOrThrow("frequency")));
                    device.setCapabilities(cursor.getString(cursor.getColumnIndexOrThrow("capabilities")));
                    device.setVendor(cursor.getString(cursor.getColumnIndexOrThrow("vendor")));
                    device.setTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")));

                    devices.add(device);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
        return devices;
    }

    public void clearTable(String tableName) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(tableName, null, null);
        db.close();
    }

    public int getDevicesCount(String tableName) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + tableName, null);
        int count = 0;

        if (cursor != null && cursor.moveToFirst()) {
            count = cursor.getInt(0);
            cursor.close();
        }
        db.close();
        return count;
    }

    public List<String> getAllTables() {
        List<String> tables = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'", null);

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String tableName = cursor.getString(0);
                    tables.add(tableName);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
        return tables;
    }
}