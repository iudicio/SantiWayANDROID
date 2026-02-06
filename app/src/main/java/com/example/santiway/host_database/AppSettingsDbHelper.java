package com.example.santiway.host_database;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class AppSettingsDbHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "app_settings.db";
    public static final int DATABASE_VERSION = 3;

    // Названия таблиц
    public static final String TABLE_APP_CONFIG = "app_config";
    public static final String TABLE_SCANNERS_CONFIG = "scanner_config";

    // Столбцы APP_SETTINGS
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_API_KEY = "api_key";
    public static final String COLUMN_IS_SCANNING = "is_scanning";
    public static final String COLUMN_GEO_PROTOCOL = "geo_protocol";

    // Столбцы для scanners
    public static final String COLUMN_SCANNER_ID = "scanner_id";
    public static final String COLUMN_SCANNER_NAME = "scanner_name";
    public static final String COLUMN_SCAN_INTERVAL = "scan_interval";
    public static final String COLUMN_SIGNAL_STRENGTH = "signal_strength";
    public static final String COLUMN_SCANNER_ENABLED = "scanner_enabled";
    public static final String COLUMN_DEVICE_NAME = "device_name";

    public AppSettingsDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createAppSettingsTable(db);
        createScannersConfigTable(db);
        insertDefaultSettings(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_APP_CONFIG);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SCANNERS_CONFIG);
        onCreate(db);
    }

    private void createAppSettingsTable(SQLiteDatabase db) {
        String createTableQuery = "CREATE TABLE " + TABLE_APP_CONFIG + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY DEFAULT 1 CHECK(" + COLUMN_ID + " = 1)," +
                COLUMN_API_KEY + " TEXT," +
                COLUMN_GEO_PROTOCOL + " TEXT NOT NULL DEFAULT 'GSM'," +
                COLUMN_IS_SCANNING + " INTEGER NOT NULL DEFAULT 0" +
                COLUMN_DEVICE_NAME + " TEXT NOT NULL DEFAULT 'Telephone', " +
                ")";
        db.execSQL(createTableQuery);
    }

    private void createScannersConfigTable(SQLiteDatabase db) {
        String createTableQuery = "CREATE TABLE " + TABLE_APP_CONFIG + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY DEFAULT 1 CHECK(" + COLUMN_ID + " = 1)," +
                COLUMN_API_KEY + " TEXT," +
                COLUMN_GEO_PROTOCOL + " TEXT NOT NULL DEFAULT 'GSM'," +
                COLUMN_IS_SCANNING + " INTEGER NOT NULL DEFAULT 0," +
                COLUMN_DEVICE_NAME + " TEXT NOT NULL DEFAULT 'Telephone'" +
                ")";
        db.execSQL(createTableQuery);
    }

    private void insertDefaultSettings(SQLiteDatabase db) {
        // Добавление настроек приложения
        ContentValues appValues = new ContentValues();
        appValues.put(COLUMN_API_KEY, "just_api_key");
        appValues.put(COLUMN_GEO_PROTOCOL, "GSM");
        appValues.put(COLUMN_IS_SCANNING, 0);
        appValues.put(COLUMN_DEVICE_NAME, "Telephone");
        db.insert(TABLE_APP_CONFIG, null, appValues);

        // Добавление сканеров
        String[] scanners = {"wifi", "bluetooth", "cell"};
        for (String name : scanners) {
            ContentValues scannerValues = new ContentValues();
            scannerValues.put(COLUMN_SCANNER_NAME, name);
            db.insert(TABLE_SCANNERS_CONFIG, null, scannerValues);
        }
    }

    // Вспомогательные методы для работы с базой данных
    public SQLiteDatabase getReadableDatabase() {
        return super.getReadableDatabase();
    }

    public SQLiteDatabase getWritableDatabase() {
        return super.getWritableDatabase();
    }
}