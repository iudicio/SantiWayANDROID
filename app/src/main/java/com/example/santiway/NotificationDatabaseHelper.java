package com.example.santiway;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NotificationDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "NotificationDBHelper";
    private static final String DATABASE_NAME = "notifications_history.db";
    private static final int DATABASE_VERSION = 2; // Увеличиваем версию БД

    // Константы имен колонок
    private static final String TABLE_NAME = "notifications";
    private static final String COL_ID = "id";
    private static final String COL_DEVICE_ID = "device_id";
    private static final String COL_TITLE = "title";
    private static final String COL_TEXT = "text";
    private static final String COL_TIMESTAMP = "timestamp";
    private static final String COL_TYPE = "type";
    private static final String COL_LAT = "latitude";
    private static final String COL_LON = "longitude";
    private static final String COL_DEVICE_IDENTIFIER = "device_identifier";

    public NotificationDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableQuery = "CREATE TABLE " + TABLE_NAME + " (" +
                COL_ID + " TEXT PRIMARY KEY, " +
                COL_DEVICE_ID + " TEXT, " +
                COL_TITLE + " TEXT, " +
                COL_TEXT + " TEXT, " +
                COL_TIMESTAMP + " LONG, " +
                COL_TYPE + " TEXT, " +
                COL_LAT + " DOUBLE, " +
                COL_LON + " DOUBLE, " +
                COL_DEVICE_IDENTIFIER + " TEXT)";
        db.execSQL(createTableQuery);
        Log.d(TAG, "Database created with version " + DATABASE_VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

        if (oldVersion < 2) {
            try {
                // Добавляем колонку device_identifier
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_DEVICE_IDENTIFIER + " TEXT DEFAULT ''");
                Log.d(TAG, "Added column: " + COL_DEVICE_IDENTIFIER);
            } catch (Exception e) {
                Log.e(TAG, "Error adding column: " + e.getMessage());
            }
        }
    }

    public void addNotification(NotificationData data, String deviceId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(COL_ID, data.getId());
        v.put(COL_DEVICE_ID, deviceId);
        v.put(COL_TITLE, data.getTitle());
        v.put(COL_TEXT, data.getText());
        v.put(COL_TIMESTAMP, data.getTimestamp().getTime());
        v.put(COL_TYPE, data.getType().name());
        v.put(COL_LAT, data.getLatitude());
        v.put(COL_LON, data.getLongitude());
        v.put(COL_DEVICE_IDENTIFIER, data.getDeviceId() != null ? data.getDeviceId() : "");

        long result = db.insert(TABLE_NAME, null, v);
        Log.d(TAG, "Notification added, result: " + result);
    }

    public List<NotificationData> getAllNotifications() {
        List<NotificationData> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor c = db.query(TABLE_NAME, null, null, null, null, null, COL_TIMESTAMP + " DESC");

        if (c.moveToFirst()) {
            do {
                try {
                    // Безопасное получение индексов колонок
                    int idIdx = c.getColumnIndex(COL_ID);
                    int titleIdx = c.getColumnIndex(COL_TITLE);
                    int textIdx = c.getColumnIndex(COL_TEXT);
                    int timestampIdx = c.getColumnIndex(COL_TIMESTAMP);
                    int typeIdx = c.getColumnIndex(COL_TYPE);
                    int latIdx = c.getColumnIndex(COL_LAT);
                    int lonIdx = c.getColumnIndex(COL_LON);
                    int deviceIdIdx = c.getColumnIndex(COL_DEVICE_IDENTIFIER);

                    if (idIdx == -1 || titleIdx == -1 || textIdx == -1 ||
                            timestampIdx == -1 || typeIdx == -1 || latIdx == -1 || lonIdx == -1) {
                        Log.e(TAG, "Required column not found in database");
                        continue;
                    }

                    String deviceId = "";
                    if (deviceIdIdx != -1) {
                        deviceId = c.getString(deviceIdIdx);
                        if (deviceId == null) deviceId = "";
                    }

                    NotificationData notification = new NotificationData(
                            c.getString(idIdx),
                            c.getString(titleIdx),
                            c.getString(textIdx),
                            new Date(c.getLong(timestampIdx)),
                            NotificationData.NotificationType.valueOf(c.getString(typeIdx)),
                            null, null,
                            c.getDouble(latIdx),
                            c.getDouble(lonIdx),
                            deviceId
                    );
                    list.add(notification);
                } catch (Exception e) {
                    Log.e(TAG, "Error reading notification: " + e.getMessage());
                }
            } while (c.moveToNext());
        }
        c.close();
        db.close();

        Log.d(TAG, "Loaded " + list.size() + " notifications");
        return list;
    }

    public boolean isUniqueAlert(String deviceId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(TABLE_NAME,
                new String[]{COL_TIMESTAMP},
                COL_DEVICE_ID + " = ?",
                new String[]{deviceId},
                null, null,
                COL_TIMESTAMP + " DESC",
                "1");

        if (c != null && c.moveToFirst()) {
            long lastTimestamp = c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP));
            c.close();

            long currentTime = System.currentTimeMillis();
            long oneHourInMillis = 3600000;

            return (currentTime - lastTimestamp) > oneHourInMillis;
        }

        if (c != null) c.close();
        return true;
    }

    public void deleteNotification(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int deleted = db.delete(TABLE_NAME, COL_ID + "=?", new String[]{id});
        Log.d(TAG, "Notification deleted: " + id + ", rows affected: " + deleted);
        db.close();
    }
}