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

    // Database Info
    private static final String DATABASE_NAME = "wifi_scanner.db";
    private static final int DATABASE_VERSION = 1;

    // Table Names
    private static final String TABLE_WIFI_DEVICES = "wifi_devices";

    // Wifi Devices Table Columns
    private static final String KEY_ID = "id";
    private static final String KEY_SSID = "ssid";
    private static final String KEY_BSSID = "bssid";
    private static final String KEY_SIGNAL_STRENGTH = "signal_strength";
    private static final String KEY_FREQUENCY = "frequency";
    private static final String KEY_CAPABILITIES = "capabilities";
    private static final String KEY_VENDOR = "vendor";
    private static final String KEY_TIMESTAMP = "timestamp";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_WIFI_DEVICES_TABLE = "CREATE TABLE " + TABLE_WIFI_DEVICES +
                "(" +
                KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                KEY_SSID + " TEXT," +
                KEY_BSSID + " TEXT UNIQUE," + // BSSID should be unique
                KEY_SIGNAL_STRENGTH + " INTEGER," +
                KEY_FREQUENCY + " INTEGER," +
                KEY_CAPABILITIES + " TEXT," +
                KEY_VENDOR + " TEXT," +
                KEY_TIMESTAMP + " INTEGER" +
                ")";

        db.execSQL(CREATE_WIFI_DEVICES_TABLE);
        Log.d(TAG, "Database created successfully");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion != newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_WIFI_DEVICES);
            onCreate(db);
        }
    }

    // Insert or update a wifi device
    public long addOrUpdateWifiDevice(WifiDevice device) {
        SQLiteDatabase db = getWritableDatabase();
        long result = -1;

        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put(KEY_SSID, device.getSsid());
            values.put(KEY_BSSID, device.getBssid());
            values.put(KEY_SIGNAL_STRENGTH, device.getSignalStrength());
            values.put(KEY_FREQUENCY, device.getFrequency());
            values.put(KEY_CAPABILITIES, device.getCapabilities());
            values.put(KEY_VENDOR, device.getVendor());
            values.put(KEY_TIMESTAMP, device.getTimestamp());

            // First try to update if device exists
            int rowsAffected = db.update(TABLE_WIFI_DEVICES, values,
                    KEY_BSSID + " = ?", new String[]{device.getBssid()});

            // If no rows were updated, insert new device
            if (rowsAffected == 0) {
                result = db.insertOrThrow(TABLE_WIFI_DEVICES, null, values);
                Log.d(TAG, "Inserted new device: " + device.getBssid());
            } else {
                result = rowsAffected;
                Log.d(TAG, "Updated existing device: " + device.getBssid());
            }

            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error while adding/updating device: " + e.getMessage());
        } finally {
            db.endTransaction();
        }

        return result;
    }

    // Get all wifi devices
    public List<WifiDevice> getAllWifiDevices() {
        List<WifiDevice> devices = new ArrayList<>();

        String SELECT_QUERY = "SELECT * FROM " + TABLE_WIFI_DEVICES +
                " ORDER BY " + KEY_TIMESTAMP + " DESC";

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(SELECT_QUERY, null);

        try {
            if (cursor.moveToFirst()) {
                do {
                    WifiDevice device = new WifiDevice();
                    device.setId(cursor.getInt(cursor.getColumnIndexOrThrow(KEY_ID)));
                    device.setSsid(cursor.getString(cursor.getColumnIndexOrThrow(KEY_SSID)));
                    device.setBssid(cursor.getString(cursor.getColumnIndexOrThrow(KEY_BSSID)));
                    device.setSignalStrength(cursor.getInt(cursor.getColumnIndexOrThrow(KEY_SIGNAL_STRENGTH)));
                    device.setFrequency(cursor.getInt(cursor.getColumnIndexOrThrow(KEY_FREQUENCY)));
                    device.setCapabilities(cursor.getString(cursor.getColumnIndexOrThrow(KEY_CAPABILITIES)));
                    device.setVendor(cursor.getString(cursor.getColumnIndexOrThrow(KEY_VENDOR)));
                    device.setTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TIMESTAMP)));

                    devices.add(device);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while getting devices: " + e.getMessage());
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }

        return devices;
    }

    public List<WifiDevice> getDevicesBySignalStrength(int minStrength, int maxStrength) {
        List<WifiDevice> devices = new ArrayList<>();

        String SELECT_QUERY = "SELECT * FROM " + TABLE_WIFI_DEVICES +
                " WHERE " + KEY_SIGNAL_STRENGTH + " BETWEEN ? AND ?" +
                " ORDER BY " + KEY_SIGNAL_STRENGTH + " DESC";

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(SELECT_QUERY,
                new String[]{String.valueOf(minStrength), String.valueOf(maxStrength)});

        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    WifiDevice device = new WifiDevice();

                    // Безопасное получение значений из cursor
                    int idIndex = cursor.getColumnIndex(KEY_ID);
                    int ssidIndex = cursor.getColumnIndex(KEY_SSID);
                    int bssidIndex = cursor.getColumnIndex(KEY_BSSID);
                    int signalIndex = cursor.getColumnIndex(KEY_SIGNAL_STRENGTH);
                    int freqIndex = cursor.getColumnIndex(KEY_FREQUENCY);
                    int capsIndex = cursor.getColumnIndex(KEY_CAPABILITIES);
                    int vendorIndex = cursor.getColumnIndex(KEY_VENDOR);
                    int timeIndex = cursor.getColumnIndex(KEY_TIMESTAMP);

                    if (idIndex != -1) device.setId(cursor.getInt(idIndex));
                    if (ssidIndex != -1) device.setSsid(cursor.getString(ssidIndex));
                    if (bssidIndex != -1) device.setBssid(cursor.getString(bssidIndex));
                    if (signalIndex != -1) device.setSignalStrength(cursor.getInt(signalIndex));
                    if (freqIndex != -1) device.setFrequency(cursor.getInt(freqIndex));
                    if (capsIndex != -1) device.setCapabilities(cursor.getString(capsIndex));
                    if (vendorIndex != -1) device.setVendor(cursor.getString(vendorIndex));
                    if (timeIndex != -1) device.setTimestamp(cursor.getLong(timeIndex));

                    devices.add(device);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while getting devices: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return devices;
    }

    // Delete old records (older than 7 days)
    public int deleteOldRecords(long cutoffTime) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE_WIFI_DEVICES,
                KEY_TIMESTAMP + " < ?",
                new String[]{String.valueOf(cutoffTime)});
    }

    // Get total count of devices
    public int getDevicesCount() {
        String countQuery = "SELECT COUNT(*) FROM " + TABLE_WIFI_DEVICES;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(countQuery, null);

        int count = 0;
        if (cursor != null) {
            cursor.moveToFirst();
            count = cursor.getInt(0);
            cursor.close();
        }

        return count;
    }

    public void clearAllData() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_WIFI_DEVICES, null, null);
    }
}
