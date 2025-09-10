package com.example.santiway.wifi_scanner;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "WifiScanner.db";
    private static final int DATABASE_VERSION = 2; // Увеличиваем версию базы

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Создаем таблицу по умолчанию при создании базы
        createDefaultTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // При обновлении базы можно добавить миграцию данных
        if (oldVersion < 2) {
            // Миграция для добавления полей координат
            updateTablesWithCoordinates(db);
        }
    }

    private void createDefaultTable(SQLiteDatabase db) {
        String createTableQuery = "CREATE TABLE IF NOT EXISTS \"default_table\" (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ssid TEXT," +
                "bssid TEXT," + // Убираем UNIQUE constraint
                "signal_strength INTEGER," +
                "frequency INTEGER," +
                "capabilities TEXT," +
                "vendor TEXT," +
                "latitude REAL," + // Добавляем запятую
                "longitude REAL," + // Добавляем запятую
                "altitude REAL," + // Добавляем запятую
                "location_accuracy REAL," + // Исправляем опечатку и добавляем запятую
                "timestamp LONG)";
        db.execSQL(createTableQuery);
    }

    private void updateTablesWithCoordinates(SQLiteDatabase db) {
        // Получаем все таблицы
        List<String> tables = getAllTablesFromDB(db);

        for (String table : tables) {
            try {
                // Проверяем, есть ли уже поля координат
                Cursor cursor = db.rawQuery("PRAGMA table_info(\"" + table + "\")", null);
                boolean hasLatitude = false;
                boolean hasLongitude = false;
                boolean hasAltitude = false;
                boolean hasAccuracy = false;

                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String columnName = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                        if ("latitude".equals(columnName)) hasLatitude = true;
                        if ("longitude".equals(columnName)) hasLongitude = true;
                        if ("altitude".equals(columnName)) hasAltitude = true;
                        if ("location_accuracy".equals(columnName)) hasAccuracy = true;
                    }
                    cursor.close();
                }

                // Добавляем отсутствующие поля
                if (!hasLatitude) {
                    db.execSQL("ALTER TABLE \"" + table + "\" ADD COLUMN latitude REAL");
                }
                if (!hasLongitude) {
                    db.execSQL("ALTER TABLE \"" + table + "\" ADD COLUMN longitude REAL");
                }
                if (!hasAltitude) {
                    db.execSQL("ALTER TABLE \"" + table + "\" ADD COLUMN altitude REAL");
                }
                if (!hasAccuracy) {
                    db.execSQL("ALTER TABLE \"" + table + "\" ADD COLUMN location_accuracy REAL");
                }

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
            if (cursor != null && cursor.moveToFirst()) {
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

    // Создание таблицы (оборачиваем имя таблицы в двойные кавычки)
    public void createTableIfNotExists(String tableName) {
        SQLiteDatabase db = this.getWritableDatabase();
        String safeName = "\"" + tableName + "\"";
        String createTableQuery = "CREATE TABLE IF NOT EXISTS " + safeName + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ssid TEXT," +
                "bssid TEXT," + // Убираем UNIQUE constraint
                "signal_strength INTEGER," +
                "frequency INTEGER," +
                "capabilities TEXT," +
                "vendor TEXT," +
                "latitude REAL," + // Добавляем запятую
                "longitude REAL," + // Добавляем запятую
                "altitude REAL," + // Добавляем запятую
                "location_accuracy REAL," + // Исправляем опечатку и добавляем запятую
                "timestamp LONG)";
        db.execSQL(createTableQuery);
        db.close();
    }

    // Удаление таблицы, default_table нельзя удалить
    public boolean deleteTable(String tableName) {
        if (tableName.equals("default_table")) return false;

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

    // Добавление Wi-Fi устройства (используем addOrUpdateDevice вместо insert)
    public long addWifiDevice(String tableName, WifiDevice device) {
        return addOrUpdateDevice(tableName, device);
    }

    // Добавление или обновление устройства по BSSID с транзакцией
    public long addOrUpdateDevice(String tableName, WifiDevice device) {
        createTableIfNotExists(tableName);
        SQLiteDatabase db = this.getWritableDatabase();
        long result = -1;

        try {
            db.beginTransaction();

            ContentValues values = new ContentValues();
            values.put("ssid", device.getSsid());
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

            // Сначала проверяем, существует ли уже устройство с таким BSSID
            Cursor cursor = db.query("\"" + tableName + "\"",
                    new String[]{"id", "timestamp"},
                    "bssid = ?",
                    new String[]{device.getBssid()},
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                // Устройство уже существует - обновляем если новая запись новее
                long existingId = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                long existingTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));

                if (device.getTimestamp() > existingTimestamp) {
                    // Новая запись новее - обновляем
                    result = db.update("\"" + tableName + "\"", values,
                            "id = ?", new String[]{String.valueOf(existingId)});
                    Log.d("DatabaseHelper", "Updated device: " + device.getBssid());
                } else {
                    // Существующая запись новее - пропускаем
                    result = 1;
                    Log.d("DatabaseHelper", "Skipped older device: " + device.getBssid());
                }
                cursor.close();
            } else {
                // Устройство не существует - вставляем новую запись
                result = db.insert("\"" + tableName + "\"", null, values);
                Log.d("DatabaseHelper", "Inserted new device: " + device.getBssid());
            }

            db.setTransactionSuccessful();

        } catch (Exception e) {
            Log.e("DatabaseHelper", "Error in transaction: " + e.getMessage());
            e.printStackTrace();
            result = -1;
        } finally {
            try {
                db.endTransaction();
            } catch (Exception e) {
                Log.e("DatabaseHelper", "Error ending transaction: " + e.getMessage());
            }
            db.close();
        }
        return result;
    }

    // Получение всех устройств из таблицы
    public List<WifiDevice> getAllDevices(String tableName) {
        List<WifiDevice> devices = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.query("\"" + tableName + "\"", null, null, null, null, null, "timestamp DESC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    WifiDevice device = new WifiDevice();
                    device.setSsid(cursor.getString(cursor.getColumnIndexOrThrow("ssid")));
                    device.setBssid(cursor.getString(cursor.getColumnIndexOrThrow("bssid")));
                    device.setSignalStrength(cursor.getInt(cursor.getColumnIndexOrThrow("signal_strength")));
                    device.setFrequency(cursor.getInt(cursor.getColumnIndexOrThrow("frequency")));
                    device.setCapabilities(cursor.getString(cursor.getColumnIndexOrThrow("capabilities")));
                    device.setVendor(cursor.getString(cursor.getColumnIndexOrThrow("vendor")));
                    device.setLatitude(cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")));
                    device.setLongitude(cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")));
                    device.setAltitude(cursor.getDouble(cursor.getColumnIndexOrThrow("altitude")));
                    device.setLocationAccuracy(cursor.getFloat(cursor.getColumnIndexOrThrow("location_accuracy")));
                    device.setTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")));
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

    // Очистка таблицы
    public void clearTable(String tableName) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            db.delete("\"" + tableName + "\"", null, null);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            db.close();
        }
    }

    // Подсчет записей
    public int getDevicesCount(String tableName) {
        SQLiteDatabase db = this.getReadableDatabase();
        int count = 0;
        Cursor cursor = null;

        try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM \"" + tableName + "\"", null);
            if (cursor != null && cursor.moveToFirst()) {
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

    // Получение всех таблиц (кроме системных)
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
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }

        return tables;
    }
}