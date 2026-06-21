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
    private static final int DB_VERSION = 4;
    public static final int COORDINATES_UNSET = 0;
    public static final int COORDINATES_MANUAL = 1;
    public static final int COORDINATES_AUTO = 2;
    private static final long FRESH_COORDINATE_MS = 120000;

    public Esp32DatabaseHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createConnections(db);
        createObservations(db);
        createDevices(db);
        createMeshLinks(db);
        createPhoneSamples(db);
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
                "auto_connect INTEGER NOT NULL DEFAULT 1,coordinates_mode INTEGER NOT NULL DEFAULT 0," +
                "coordinates_updated_at INTEGER NOT NULL DEFAULT 0)");
    }

    private void createMeshLinks(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS mesh_links (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,source_address TEXT NOT NULL,neighbor_address TEXT NOT NULL," +
                "rssi INTEGER NOT NULL,hops_to_phone INTEGER NOT NULL DEFAULT 0,last_seen INTEGER NOT NULL)");
    }

    private void createPhoneSamples(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS phone_samples (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,phone_id TEXT NOT NULL,source_address TEXT NOT NULL," +
                "rssi INTEGER NOT NULL,hops_to_phone INTEGER NOT NULL DEFAULT 0,received_at INTEGER NOT NULL)");
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
        if (oldVersion < 3) {
            createMeshLinks(db);
            addColumnIfMissing(db, "esp_devices", "coordinates_mode",
                    "INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE esp_devices SET coordinates_mode=1 WHERE latitude<>0 OR longitude<>0");
        }
        if (oldVersion < 4) {
            createPhoneSamples(db);
            addColumnIfMissing(db, "esp_devices", "coordinates_updated_at",
                    "INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE esp_devices SET coordinates_updated_at=connected_at WHERE coordinates_updated_at=0");
        }
    }

    private void addColumnIfMissing(SQLiteDatabase db, String table, String column, String definition) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                if (column.equalsIgnoreCase(cursor.getString(cursor.getColumnIndexOrThrow("name")))) return;
            }
        }
        db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
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
        values.put("coordinates_mode", lat == 0 && lon == 0 ? COORDINATES_UNSET : COORDINATES_AUTO);
        values.put("coordinates_updated_at", lat == 0 && lon == 0 ? 0 : System.currentTimeMillis());
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
        values.put("coordinates_mode", COORDINATES_MANUAL);
        values.put("coordinates_updated_at", System.currentTimeMillis());
        getWritableDatabase().update("esp_devices", values, "mac_address=?", new String[]{mac});
    }

    public void updateDefaultCoordinatesIfUnset(String mac, double lat, double lon, double altitude) {
        ContentValues values = new ContentValues();
        values.put("latitude", lat);
        values.put("longitude", lon);
        values.put("altitude", altitude);
        values.put("coordinates_mode", COORDINATES_AUTO);
        values.put("coordinates_updated_at", System.currentTimeMillis());
        getWritableDatabase().update("esp_devices", values,
                "mac_address=? AND coordinates_mode<>?", new String[]{mac, String.valueOf(COORDINATES_MANUAL)});
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

    public int getCoordinatesMode(String mac) {
        try (Cursor cursor = getReadableDatabase().query("esp_devices",
                new String[]{"coordinates_mode"}, "mac_address=?",
                new String[]{mac}, null, null, null)) {
            if (cursor.moveToFirst()) return cursor.getInt(0);
        }
        return COORDINATES_UNSET;
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

    public void saveMeshLink(String source, String neighbor, int rssi, int hopsToPhone) {
        if (source == null || neighbor == null || source.equalsIgnoreCase(neighbor)) return;
        SQLiteDatabase db = getWritableDatabase();
        String src = source.toUpperCase();
        String dst = neighbor.toUpperCase();
        ContentValues values = new ContentValues();
        values.put("source_address", src);
        values.put("neighbor_address", dst);
        values.put("rssi", rssi);
        values.put("hops_to_phone", Math.max(0, hopsToPhone));
        values.put("last_seen", System.currentTimeMillis());
        int updated = db.update("mesh_links", values, "source_address=? AND neighbor_address=?",
                new String[]{src, dst});
        if (updated == 0) db.insert("mesh_links", null, values);
    }

    public void autoPositionUnknownDevices() {
        SQLiteDatabase db = getWritableDatabase();
        long cutoff = System.currentTimeMillis() - FRESH_COORDINATE_MS;
        try (Cursor devices = db.query("esp_devices", new String[]{"mac_address"},
                "coordinates_mode<>? AND (coordinates_mode=? OR latitude=0 OR longitude=0 OR coordinates_updated_at<?)",
                new String[]{String.valueOf(COORDINATES_MANUAL), String.valueOf(COORDINATES_UNSET),
                        String.valueOf(cutoff)},
                null, null, null)) {
            while (devices.moveToNext()) {
                String mac = devices.getString(0);
                double[] position = estimateFromAnchors(db, mac, cutoff);
                if (position == null) continue;
                ContentValues values = new ContentValues();
                values.put("latitude", position[0]);
                values.put("longitude", position[1]);
                values.put("altitude", position[2]);
                values.put("coordinates_mode", COORDINATES_AUTO);
                values.put("coordinates_updated_at", System.currentTimeMillis());
                db.update("esp_devices", values, "mac_address=? AND coordinates_mode<>?",
                        new String[]{mac, String.valueOf(COORDINATES_MANUAL)});
            }
        }
    }

    private double[] estimateFromAnchors(SQLiteDatabase db, String mac, long cutoff) {
        String sql = "SELECT e.latitude,e.longitude,e.altitude,MAX(l.rssi) " +
                "FROM mesh_links l JOIN esp_devices e ON e.mac_address=l.neighbor_address " +
                "WHERE l.source_address=? AND e.mac_address<>? AND e.latitude<>0 AND e.longitude<>0 " +
                "AND (e.coordinates_mode=? OR e.coordinates_updated_at>=?) AND l.last_seen>=? " +
                "GROUP BY e.mac_address ORDER BY MAX(l.rssi) DESC LIMIT 3";
        double[][] anchors = new double[3][4];
        int count = 0;
        String normalizedMac = mac.toUpperCase();
        try (Cursor cursor = db.rawQuery(sql, new String[]{normalizedMac, normalizedMac,
                String.valueOf(COORDINATES_MANUAL), String.valueOf(cutoff), String.valueOf(cutoff)})) {
            while (cursor.moveToNext() && count < 3) {
                anchors[count][0] = cursor.getDouble(0);
                anchors[count][1] = cursor.getDouble(1);
                anchors[count][2] = cursor.getDouble(2);
                anchors[count][3] = rssiToMeters(cursor.getInt(3));
                count++;
            }
        }
        if (count < 3) return null;
        return trilaterate(anchors);
    }

    private double rssiToMeters(int rssi) {
        double txPower = -59.0;
        double pathLoss = 2.4;
        return Math.max(0.5, Math.min(80.0, Math.pow(10.0, (txPower - rssi) / (10.0 * pathLoss))));
    }

    private double[] trilaterate(double[][] anchors) {
        double lat0 = anchors[0][0];
        double lon0 = anchors[0][1];
        double cosLat = Math.cos(Math.toRadians(lat0));
        double[][] p = new double[3][3];
        for (int i = 0; i < 3; i++) {
            p[i][0] = (anchors[i][1] - lon0) * 111320.0 * cosLat;
            p[i][1] = (anchors[i][0] - lat0) * 110540.0;
            p[i][2] = anchors[i][2];
        }
        double[] result = solve2d(p, anchors[0][3], anchors[1][3], anchors[2][3]);
        if (result == null) {
            double weightSum = 0, x = 0, y = 0, z = 0;
            for (int i = 0; i < 3; i++) {
                double weight = 1.0 / Math.max(1.0, anchors[i][3]);
                weightSum += weight;
                x += p[i][0] * weight;
                y += p[i][1] * weight;
                z += p[i][2] * weight;
            }
            result = new double[]{x / weightSum, y / weightSum, z / weightSum};
        }
        return new double[]{lat0 + result[1] / 110540.0,
                lon0 + result[0] / (111320.0 * cosLat), result[2]};
    }

    private double[] solve2d(double[][] p, double r1, double r2, double r3) {
        double a = 2 * (p[1][0] - p[0][0]);
        double b = 2 * (p[1][1] - p[0][1]);
        double c = r1 * r1 - r2 * r2 - p[0][0] * p[0][0] + p[1][0] * p[1][0]
                - p[0][1] * p[0][1] + p[1][1] * p[1][1];
        double d = 2 * (p[2][0] - p[0][0]);
        double e = 2 * (p[2][1] - p[0][1]);
        double f = r1 * r1 - r3 * r3 - p[0][0] * p[0][0] + p[2][0] * p[2][0]
                - p[0][1] * p[0][1] + p[2][1] * p[2][1];
        double det = a * e - b * d;
        if (Math.abs(det) < 0.0001) return null;
        return new double[]{(c * e - b * f) / det, (a * f - c * d) / det,
                (p[0][2] + p[1][2] + p[2][2]) / 3.0};
    }

    public PhonePosition savePhoneSampleAndEstimate(String phoneId, String source, int rssi, int hopsToPhone) {
        ContentValues values = new ContentValues();
        values.put("phone_id", phoneId == null ? "phone" : phoneId);
        values.put("source_address", source.toUpperCase());
        values.put("rssi", rssi);
        values.put("hops_to_phone", Math.max(0, hopsToPhone));
        values.put("received_at", System.currentTimeMillis());
        getWritableDatabase().insert("phone_samples", null, values);
        return estimatePhonePosition(phoneId);
    }

    public PhonePosition estimatePhonePosition(String phoneId) {
        SQLiteDatabase db = getReadableDatabase();
        long cutoff = System.currentTimeMillis() - FRESH_COORDINATE_MS;
        String sql = "SELECT e.latitude,e.longitude,e.altitude,MAX(p.rssi),MAX(p.received_at) " +
                "FROM phone_samples p JOIN esp_devices e ON e.mac_address=p.source_address " +
                "WHERE p.phone_id=? AND e.latitude<>0 AND e.longitude<>0 " +
                "AND (e.coordinates_mode=? OR e.coordinates_updated_at>=?) AND p.received_at>=? " +
                "GROUP BY e.mac_address ORDER BY MAX(p.rssi) DESC LIMIT 3";
        double[][] anchors = new double[3][4];
        long freshest = 0;
        int count = 0;
        try (Cursor cursor = db.rawQuery(sql, new String[]{phoneId == null ? "phone" : phoneId,
                String.valueOf(COORDINATES_MANUAL), String.valueOf(cutoff), String.valueOf(cutoff)})) {
            while (cursor.moveToNext() && count < 3) {
                anchors[count][0] = cursor.getDouble(0);
                anchors[count][1] = cursor.getDouble(1);
                anchors[count][2] = cursor.getDouble(2);
                anchors[count][3] = rssiToMeters(cursor.getInt(3));
                freshest = Math.max(freshest, cursor.getLong(4));
                count++;
            }
        }
        if (count < 3) return null;
        double[] position = trilaterate(anchors);
        return new PhonePosition(position[0], position[1], position[2], freshest, count);
    }

    public Cursor getMapDevices() {
        return getReadableDatabase().query("esp_devices",
                new String[]{"mac_address", "name", "latitude", "longitude", "altitude",
                        "coordinates_mode", "coordinates_updated_at", "is_connected"},
                "latitude<>0 AND longitude<>0", null, null, null, "coordinates_updated_at DESC");
    }

    public static final class PhonePosition {
        public final double latitude;
        public final double longitude;
        public final double altitude;
        public final long updatedAt;
        public final int anchorCount;

        PhonePosition(double latitude, double longitude, double altitude, long updatedAt, int anchorCount) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.updatedAt = updatedAt;
            this.anchorCount = anchorCount;
        }
    }
}
