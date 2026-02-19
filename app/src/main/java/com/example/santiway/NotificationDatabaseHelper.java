package com.example.santiway;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NotificationDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "notifications_history.db";
    private static final int DATABASE_VERSION = 1;

    // Константы имен колонок - используем их везде, чтобы не ошибиться
    private static final String TABLE_NAME = "notifications";
    private static final String COL_ID = "id";
    private static final String COL_DEVICE_ID = "device_id";
    private static final String COL_TITLE = "title";
    private static final String COL_TEXT = "text";
    private static final String COL_TIMESTAMP = "timestamp";
    private static final String COL_TYPE = "type";
    private static final String COL_LAT = "latitude";  // Проверь, что тут "latitude"
    private static final String COL_LON = "longitude"; // Проверь, что тут "longitude"

    public NotificationDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Создаем таблицу с именами latitude и longitude
        db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
                COL_ID + " TEXT PRIMARY KEY, " +
                COL_DEVICE_ID + " TEXT, " +
                COL_TITLE + " TEXT, " +
                COL_TEXT + " TEXT, " +
                COL_TIMESTAMP + " LONG, " +
                COL_TYPE + " TEXT, " +
                COL_LAT + " DOUBLE, " +
                COL_LON + " DOUBLE)");
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
        db.insert(TABLE_NAME, null, v);
    }

    public List<NotificationData> getAllNotifications() {
        List<NotificationData> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // Используем константы COL_LAT и COL_LON, чтобы поиск в Cursor совпал с CREATE TABLE
        Cursor c = db.query(TABLE_NAME, null, null, null, null, null, COL_TIMESTAMP + " DESC");

        if (c.moveToFirst()) {
            do {
                list.add(new NotificationData(
                        c.getString(c.getColumnIndexOrThrow(COL_ID)),
                        c.getString(c.getColumnIndexOrThrow(COL_TITLE)),
                        c.getString(c.getColumnIndexOrThrow(COL_TEXT)),
                        new Date(c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP))),
                        NotificationData.NotificationType.valueOf(c.getString(c.getColumnIndexOrThrow(COL_TYPE))),
                        null, null,
                        c.getDouble(c.getColumnIndexOrThrow(COL_LAT)), // Исправлено: теперь ищет "latitude"
                        c.getDouble(c.getColumnIndexOrThrow(COL_LON))  // Исправлено: теперь ищет "longitude"
                ));
            } while (c.moveToNext());
        }
        c.close();
        return list;
    }

    public boolean isUniqueAlert(String deviceId) {
        SQLiteDatabase db = this.getReadableDatabase();

        // Ищем самое последнее уведомление для данного устройства
        // Сортируем по timestamp в обратном порядке и берем 1 запись
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

            // Если с момента последнего уведомления прошло БОЛЬШЕ часа,
            // значит устройство "новое" (или вернулось), возвращаем true (нужно уведомление)
            return (currentTime - lastTimestamp) > oneHourInMillis;
        }

        if (c != null) c.close();

        // Если уведомлений для этого устройства вообще не было в базе - возвращаем true
        return true;
    }

    public void deleteNotification(String id) {
        getWritableDatabase().delete(TABLE_NAME, COL_ID + "=?", new String[]{id});
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int old, int n) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }
}