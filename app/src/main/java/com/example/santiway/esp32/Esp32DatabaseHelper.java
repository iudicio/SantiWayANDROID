package com.example.santiway.esp32;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashSet;
import java.util.Set;

public class Esp32DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "esp32_devices.db";
    private static final int DB_VERSION = 2;

    public Esp32DatabaseHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createConnections(db);
        createObservations(db);
        createDevices(db);
    }

    private void createConnections(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS connections (id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "device_address TEXT NOT NULL,device_type TEXT NOT NULL,connected_at INTEGER NOT NULL," +
                "disconnected_at INTEGER,is_connected INTEGER NOT NULL DEFAULT 1," +
                "latitude REAL,longitude REAL,altitude REAL)");
    }

    private void createObservations(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS observations (id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "source_address TEXT NOT NULL,transport TEXT NOT NULL,device_mac TEXT NOT NULL," +
                "rssi INTEGER NOT NULL,details TEXT,received_at INTEGER NOT NULL)");
    }

    private void createDevices(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS esp_devices (" +
                "mac_address TEXT PRIMARY KEY,name TEXT NOT NULL,device_type TEXT NOT NULL DEFAULT 'ESP32'," +
                "connected_at INTEGER,disconnected_at INTEGER,is_connected INTEGER NOT NULL DEFAULT 0," +
                "latitude REAL NOT NULL DEFAULT 0,longitude REAL NOT NULL DEFAULT 0,altitude REAL NOT NULL DEFAULT 0," +
                "auto_connect INTEGER NOT NULL DEFAULT 1)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createDevices(db);
            db.execSQL("INSERT OR IGNORE INTO esp_devices(mac_address,name,device_type,connected_at," +
                    "disconnected_at,is_connected,latitude,longitude,altitude) " +
                    "SELECT device_address,'ESP32 ' || substr(device_address,-5),device_type," +
                    "MAX(connected_at),MAX(disconnected_at),0,MAX(latitude),MAX(longitude),MAX(altitude) " +
                    "FROM connections GROUP BY device_address");
        }
    }

    public void markAllDisconnected() {
        ContentValues values = new ContentValues();
        values.put("is_connected", 0);
        values.put("disconnected_at", System.currentTimeMillis());
        getWritableDatabase().update("esp_devices", values, "is_connected=1", null);
    }

    public void upsertDevice(String mac, String defaultName, double lat, double lon, double altitude) {
        SQLiteDatabase db = getWritableDatabase();
        try (Cursor cursor = db.query("esp_devices", new String[]{"mac_address"},
                "mac_address=?", new String[]{mac}, null, null, null)) {
            if (cursor.moveToFirst()) return;
        }
        ContentValues values = new ContentValues();
        values.put("mac_address", mac);
        values.put("name", defaultName);
        values.put("device_type", "ESP32");
        values.put("latitude", lat);
        values.put("longitude", lon);
        values.put("altitude", altitude);
        values.put("auto_connect", 1);
        db.insert("esp_devices", null, values);
    }

    public void setConnected(String mac, boolean connected) {
        ContentValues values = new ContentValues();
        values.put("is_connected", connected ? 1 : 0);
        if (connected) values.put("connected_at", System.currentTimeMillis());
        else values.put("disconnected_at", System.currentTimeMillis());
        getWritableDatabase().update("esp_devices", values, "mac_address=?", new String[]{mac});
    }

    public void updateSettings(String mac, String name, double lat, double lon, double altitude) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("latitude", lat);
        values.put("longitude", lon);
        values.put("altitude", altitude);
        getWritableDatabase().update("esp_devices", values, "mac_address=?", new String[]{mac});
    }

    public void updateDefaultCoordinatesIfUnset(String mac, double lat, double lon, double altitude) {
        ContentValues values = new ContentValues();
        values.put("latitude", lat);
        values.put("longitude", lon);
        values.put("altitude", altitude);
        getWritableDatabase().update("esp_devices", values,
                "mac_address=? AND latitude=0 AND longitude=0", new String[]{mac});
    }

    public void setAutoConnect(String mac, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put("auto_connect", enabled ? 1 : 0);
        getWritableDatabase().update("esp_devices", values, "mac_address=?", new String[]{mac});
    }

    public void deleteDevice(String mac) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("observations", "source_address=?", new String[]{mac});
            db.delete("connections", "device_address=?", new String[]{mac});
            db.delete("esp_devices", "mac_address=?", new String[]{mac});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public Cursor getDevices() {
        return getReadableDatabase().query("esp_devices", null, null, null,
                null, null, "name COLLATE NOCASE");
    }

    public Set<String> getAutoConnectMacs() {
        Set<String> result = new HashSet<>();
        try (Cursor cursor = getReadableDatabase().query("esp_devices",
                new String[]{"mac_address"}, "auto_connect=1", null, null, null, null)) {
            while (cursor.moveToNext()) result.add(cursor.getString(0).toUpperCase());
        }
        return result;
    }

    public double[] getCoordinates(String mac) {
        try (Cursor cursor = getReadableDatabase().query("esp_devices",
                new String[]{"latitude", "longitude", "altitude"}, "mac_address=?",
                new String[]{mac}, null, null, null)) {
            if (cursor.moveToFirst()) return new double[]{cursor.getDouble(0), cursor.getDouble(1), cursor.getDouble(2)};
        }
        return new double[]{0, 0, 0};
    }

    public void saveObservation(String source, String transport, String mac, int rssi, String details) {
        ContentValues values = new ContentValues();
        values.put("source_address", source);
        values.put("transport", transport);
        values.put("device_mac", mac);
        values.put("rssi", rssi);
        values.put("details", details);
        values.put("received_at", System.currentTimeMillis());
        getWritableDatabase().insert("observations", null, values);
    }
}
