package com.example.santiway.host_database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class AppSettingsRepository {
    private AppSettingsDbHelper dbHelper;

    public AppSettingsRepository(Context context) {
        dbHelper = new AppSettingsDbHelper(context);
    }

    private String getAppConfigValue(String columnName) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
                AppSettingsDbHelper.TABLE_APP_CONFIG,
        new String[]{columnName},
        AppSettingsDbHelper.COLUMN_ID + " = ?",
        new String[]{"1"},
        null, null, null
        );

        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            } else {
                return null;
            }
        } finally {
            cursor.close();
        }
    }

    private void setAppConfigValue(String columnName, Object value) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        if (value instanceof String) {
            values.put(columnName, (String) value);
        } else if (value instanceof Boolean) {
            values.put(columnName, (Boolean) value ? 1 : 0);
        } else {
            throw new IllegalArgumentException("Unsupported type");
        }

        db.update(
            AppSettingsDbHelper.TABLE_APP_CONFIG,
            values,
            AppSettingsDbHelper.COLUMN_ID + " = ?",
            new String[]{"1"}
        );
    }

    public String getApiKey() {
        return getAppConfigValue(AppSettingsDbHelper.COLUMN_API_KEY);
    }

    public String getGeoProtocol() {
        return getAppConfigValue(AppSettingsDbHelper.COLUMN_GEO_PROTOCOL);
    }

    public boolean isScanning() {
        String value = getAppConfigValue(AppSettingsDbHelper.COLUMN_IS_SCANNING);
        return value != null && Integer.parseInt(value) == 1;
    }

    public ScannerSettings getScannerSettings(String scannerName) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
                AppSettingsDbHelper.TABLE_SCANNERS_CONFIG,
        null,
        AppSettingsDbHelper.COLUMN_SCANNER_NAME + " = ?",
        new String[]{scannerName},
        null, null, null
        );

        try {
            if (cursor.moveToFirst()) {
                return new ScannerSettings(
                        cursor.getString(cursor.getColumnIndexOrThrow(AppSettingsDbHelper.COLUMN_SCANNER_NAME)),
                cursor.getInt(cursor.getColumnIndexOrThrow(AppSettingsDbHelper.COLUMN_SCANNER_ENABLED)) == 1,
                cursor.getFloat(cursor.getColumnIndexOrThrow(AppSettingsDbHelper.COLUMN_SCAN_INTERVAL)),
                cursor.getFloat(cursor.getColumnIndexOrThrow(AppSettingsDbHelper.COLUMN_SIGNAL_STRENGTH))
                );
            } else {
                return null;
            }
        } finally {
            cursor.close();
        }
    }

    public List<String> getAllScanners() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
                AppSettingsDbHelper.TABLE_SCANNERS_CONFIG,
        new String[]{AppSettingsDbHelper.COLUMN_SCANNER_NAME},
        null, null, null, null, null
        );

        List<String> scanners = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                scanners.add(cursor.getString(0));
            }
        } finally {
            cursor.close();
        }
        return scanners;
    }

    public void setApiKey(String newApiKey) {
        setAppConfigValue(AppSettingsDbHelper.COLUMN_API_KEY, newApiKey);
    }

    public void setGeoProtocol(String newGeoProtocol) {
        setAppConfigValue(AppSettingsDbHelper.COLUMN_GEO_PROTOCOL, newGeoProtocol);
    }

    public void setScanning(boolean enabled) {
        setAppConfigValue(AppSettingsDbHelper.COLUMN_IS_SCANNING, enabled);
    }

    public boolean updateScannerSettings(ScannerSettings settings) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(AppSettingsDbHelper.COLUMN_SCANNER_ENABLED, settings.isEnabled() ? 1 : 0);
        values.put(AppSettingsDbHelper.COLUMN_SCAN_INTERVAL, settings.getScanInterval());
        values.put(AppSettingsDbHelper.COLUMN_SIGNAL_STRENGTH, settings.getSignalStrength());

        int rowsAffected = db.update(
                AppSettingsDbHelper.TABLE_SCANNERS_CONFIG,
        values,
        AppSettingsDbHelper.COLUMN_SCANNER_NAME + " = ?",
        new String[]{settings.getName()}
        );

        return rowsAffected > 0;
    }
}