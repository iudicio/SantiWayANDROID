package com.example.santiway.bluetooth_scanner;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class BluetoothDatabaseHelper extends SQLiteOpenHelper {

    // === Константы базы данных ===
    public static final String DATABASE_NAME = "BluetoothScanner.db";
    public static final int DATABASE_VERSION = 1;

    // Таблицы
    public static final String DEFAULT_TABLE = "default_table";

    // Колонки
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_DEVICE_NAME = "deviceName";
    public static final String COLUMN_MAC = "macAddress";
    public static final String COLUMN_SIGNAL = "signalStrength";
    public static final String COLUMN_VENDOR = "vendor";
    public static final String COLUMN_LAT = "latitude";
    public static final String COLUMN_LON = "longitude";
    public static final String COLUMN_ALT = "altitude";
    public static final String COLUMN_ACCURACY = "locationAccuracy";
    public static final String COLUMN_TIMESTAMP = "timestamp";

    public BluetoothDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createDefaultTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            updateTablesWithCoordinates(db);
        }
    }

    // Создание таблицы по умолчанию
    private void createDefaultTable(SQLiteDatabase db) {
        String query = "CREATE TABLE IF NOT EXISTS \"" + DEFAULT_TABLE + "\" (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COLUMN_DEVICE_NAME + " TEXT," +
                COLUMN_MAC + " TEXT UNIQUE," +
                COLUMN_SIGNAL + " INTEGER," +
                COLUMN_VENDOR + " TEXT," +
                COLUMN_LAT + " REAL," +
                COLUMN_LON + " REAL," +
                COLUMN_ALT + " REAL," +
                COLUMN_ACCURACY + " REAL," +
                COLUMN_TIMESTAMP + " LONG)";
        db.execSQL(query);
    }

    private void updateTablesWithCoordinates(SQLiteDatabase db) {
        List<String> tables = getAllTablesFromDB(db);
        for (String table : tables) {
            try {
                Cursor cursor = db.rawQuery("PRAGMA table_info(\"" + table + "\")", null);
                boolean hasLat = false, hasLon = false, hasAlt = false, hasAcc = false;

                   while (cursor.moveToNext()) {
                        String columnName = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                        if (COLUMN_LAT.equals(columnName)) hasLat = true;
                        if (COLUMN_LON.equals(columnName)) hasLon = true;
                        if (COLUMN_ALT.equals(columnName)) hasAlt = true;
                        if (COLUMN_ACCURACY.equals(columnName)) hasAcc = true;
                    }
                    cursor.close();


                if (!hasLat) db.execSQL("ALTER TABLE \"" + table + "\" ADD COLUMN " + COLUMN_LAT + " REAL");
                if (!hasLon) db.execSQL("ALTER TABLE \"" + table + "\" ADD COLUMN " + COLUMN_LON + " REAL");
                if (!hasAlt) db.execSQL("ALTER TABLE \"" + table + "\" ADD COLUMN " + COLUMN_ALT + " REAL");
                if (!hasAcc) db.execSQL("ALTER TABLE \"" + table + "\" ADD COLUMN " + COLUMN_ACCURACY + " REAL");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private List<String> getAllTablesFromDB(SQLiteDatabase db) {
        List<String> tables = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
                    null
            );
            if (cursor.moveToFirst()) {
                do {
                    tables.add(cursor.getString(0));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
        }
        return tables;
    }

    // Создание таблицы с переменными
    public void createTableIfNotExists(String tableName) {
        SQLiteDatabase db = this.getWritableDatabase();
        String safeName = "\"" + tableName + "\"";
        String query = "CREATE TABLE IF NOT EXISTS " + safeName + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COLUMN_DEVICE_NAME + " TEXT," +
                COLUMN_MAC + " TEXT," +
                COLUMN_SIGNAL + " INTEGER," +
                COLUMN_VENDOR + " TEXT," +
                COLUMN_LAT + " REAL," +
                COLUMN_LON + " REAL," +
                COLUMN_ALT + " REAL," +
                COLUMN_ACCURACY + " REAL," +
                COLUMN_TIMESTAMP + " LONG)";
        db.execSQL(query);
        db.close();
    }

    // Вставка или обновление устройства
    public long insertBluetoothDevice(BluetoothDevice device, String tableName) {
        createTableIfNotExists(tableName);
        SQLiteDatabase db = this.getWritableDatabase();
        long result = -1;

        try {
            db.beginTransaction();
            ContentValues values = new ContentValues();
            values.put(COLUMN_DEVICE_NAME, device.getDeviceName());
            values.put(COLUMN_MAC, device.getMacAddress());
            values.put(COLUMN_SIGNAL, device.getSignalStrength());
            values.put(COLUMN_VENDOR, device.getVendor());
            values.put(COLUMN_LAT, device.getLatitude());
            values.put(COLUMN_LON, device.getLongitude());
            values.put(COLUMN_ALT, device.getAltitude());
            values.put(COLUMN_ACCURACY, device.getLocationAccuracy());
            values.put(COLUMN_TIMESTAMP, device.getTimestamp());

            Cursor cursor = db.query("\"" + tableName + "\"",
                    new String[]{COLUMN_ID, COLUMN_TIMESTAMP},
                    COLUMN_MAC + " = ?",
                    new String[]{device.getMacAddress()},
                    null, null, null);

            if (cursor.moveToFirst()) {
                long existingId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
                long existingTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));
                if (device.getTimestamp() > existingTimestamp) {
                    result = db.update("\"" + tableName + "\"", values, COLUMN_ID + " = ?", new String[]{String.valueOf(existingId)});
                    Log.d("DatabaseHelper", "Обновлено устройство: " + device.getMacAddress());
                } else {
                    result = 1;
                    Log.d("DatabaseHelper", "Пропущено старое устройство: " + device.getMacAddress());
                }
                cursor.close();
            } else {
                result = db.insert("\"" + tableName + "\"", null, values);
                Log.d("DatabaseHelper", "Вставлено новое устройство: " + device.getMacAddress());
            }

            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e("DatabaseHelper", "Ошибка транзакции: " + e.getMessage());
            e.printStackTrace();
            result = -1;
        } finally {
            try { db.endTransaction(); } catch (Exception ignored) {}
            db.close();
        }

        return result;
    }

    public List<BluetoothDevice> getAllDevices(String tableName) {
        List<BluetoothDevice> devices = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.query("\"" + tableName + "\"", null, null, null, null, null, COLUMN_TIMESTAMP + " DESC");
            if (cursor.moveToFirst()) {
                do {
                    BluetoothDevice device = new BluetoothDevice();
                    device.setDeviceName(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DEVICE_NAME)));
                    device.setMacAddress(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MAC)));
                    device.setSignalStrength(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SIGNAL)));
                    device.setVendor(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VENDOR)));
                    device.setLatitude(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LAT)));
                    device.setLongitude(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LON)));
                    device.setAltitude(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_ALT)));
                    device.setLocationAccuracy(cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_ACCURACY)));
                    device.setTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
                    devices.add(device);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }

        return devices;
    }

    public void clearTable(String tableName) {
        try (SQLiteDatabase db = this.getWritableDatabase()) {
            db.delete("\"" + tableName + "\"", null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getDevicesCount(String tableName) {
        SQLiteDatabase db = this.getReadableDatabase();
        int count = 0;
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM \"" + tableName + "\"", null);
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return count;
    }

    public List<String> getAllTables() {
        List<String> tables = new ArrayList<>();

        try (SQLiteDatabase db = this.getReadableDatabase(); Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
                null
        )) {
            if (cursor.moveToFirst()) {
                do {
                    tables.add(cursor.getString(0));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return tables;
    }

    public boolean deleteTable(String tableName) {
        if (DEFAULT_TABLE.equals(tableName)) return false;
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
}
