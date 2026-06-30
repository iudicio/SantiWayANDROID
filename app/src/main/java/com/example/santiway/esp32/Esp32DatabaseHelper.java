package com.example.santiway.esp32;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.santiway.FolderNameHelper;
import com.example.santiway.bluetooth_scanner.BluetoothDevice;
import com.example.santiway.upload_data.MainDatabaseHelper;
import com.example.santiway.wifi_scanner.WifiDevice;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Esp32DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "esp32_devices.db";
    private static final int DB_VERSION = 5;
    public static final int COORDINATES_UNSET = 0;
    public static final int COORDINATES_MANUAL = 1;
    public static final int COORDINATES_AUTO = 2;
    private static final long FRESH_COORDINATE_MS = Esp32TriangulationEngine.FRESH_ANCHOR_MS;
    private static final long OBSERVATION_LOOKBACK_MS = Esp32TriangulationEngine.OBSERVATION_LOOKBACK_MS;
    private static final long TRIANGULATION_TIME_WINDOW_MS = Esp32TriangulationEngine.TRIANGULATION_TIME_WINDOW_MS;
    private static final double METERS_PER_LATITUDE = 110540.0;
    private static final double METERS_PER_LONGITUDE = 111320.0;

    private final Context context;

    public Esp32DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createConnections(db);
        createObservations(db);
        createDevices(db);
        createMeshLinks(db);
        createPhoneSamples(db);
        createTriangulatedDevices(db);
        createIndexes(db);
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

    private void createTriangulatedDevices(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS triangulated_devices (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,device_mac TEXT NOT NULL,transport TEXT NOT NULL," +
                "name TEXT,latitude REAL NOT NULL,longitude REAL NOT NULL,altitude REAL NOT NULL DEFAULT 0," +
                "accuracy REAL NOT NULL DEFAULT 0,anchor_count INTEGER NOT NULL,rssi_avg REAL NOT NULL," +
                "last_seen INTEGER NOT NULL,is_uploaded INTEGER NOT NULL DEFAULT 0)");
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
        if (oldVersion < 5) {
            createTriangulatedDevices(db);
        }
        createIndexes(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (db.isReadOnly()) return;
        createTriangulatedDevices(db);
        createIndexes(db);
    }

    private void createIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_esp32_mesh_source_time " +
                "ON mesh_links(source_address,last_seen,neighbor_address)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_esp32_phone_time " +
                "ON phone_samples(phone_id,received_at,source_address)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_esp32_observed_device_time " +
                "ON observations(transport,device_mac,received_at,source_address)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_esp32_triangulated_last_seen " +
                "ON triangulated_devices(last_seen)");
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
        String sql = "SELECT e.latitude,e.longitude,e.altitude,l.rssi,l.last_seen,l.neighbor_address " +
                "FROM mesh_links l JOIN esp_devices e ON e.mac_address=l.neighbor_address " +
                "WHERE l.source_address=? AND e.mac_address<>? AND e.latitude<>0 AND e.longitude<>0 " +
                "AND (e.coordinates_mode=? OR e.coordinates_updated_at>=?) AND l.last_seen>=? " +
                "ORDER BY l.last_seen DESC,l.rssi DESC";
        List<AnchorSample> samples = new ArrayList<>();
        String normalizedMac = mac.toUpperCase();
        try (Cursor cursor = db.rawQuery(sql, new String[]{normalizedMac, normalizedMac,
                String.valueOf(COORDINATES_MANUAL), String.valueOf(cutoff), String.valueOf(cutoff)})) {
            while (cursor.moveToNext()) {
                samples.add(new AnchorSample(
                        cursor.getString(5),
                        cursor.getDouble(0),
                        cursor.getDouble(1),
                        cursor.getDouble(2),
                        cursor.getInt(3),
                        cursor.getLong(4),
                        null
                ));
            }
        }
        Esp32TriangulationEngine.Position position = estimateWithEngine(samples);
        return position == null ? null : new double[]{position.latitude, position.longitude, position.altitude};
    }

    private double[] estimatePositionFromAllAnchors(List<double[]> anchors) {
        if (anchors.size() < 3) return null;
        double[][] anchorArray = anchors.toArray(new double[0][]);
        double[] position = multilaterate(anchorArray, anchorArray.length);
        return position == null ? weightedCentroid(anchorArray, anchorArray.length) : position;
    }

    private double[] multilaterate(double[][] anchors, int count) {
        double lat0 = anchors[0][0];
        double lon0 = anchors[0][1];
        double cosLat = Math.max(0.01, Math.abs(Math.cos(Math.toRadians(lat0))));
        double[][] p = new double[count][3];
        double[] distances = new double[count];
        for (int i = 0; i < count; i++) {
            p[i][0] = (anchors[i][1] - lon0) * METERS_PER_LONGITUDE * cosLat;
            p[i][1] = (anchors[i][0] - lat0) * METERS_PER_LATITUDE;
            p[i][2] = anchors[i][2];
            distances[i] = anchors[i][3];
        }

        double[] result = null;
        if (count >= 4 && hasAltitudeSpread(p, count)) {
            result = solveMultilateration(p, distances, count, 3);
        }
        if (!isReasonablePosition(result)) {
            result = solveMultilateration(p, distances, count, 2);
            if (result != null) {
                result = new double[]{result[0], result[1], weightedAltitude(anchors, count)};
            }
        }
        if (!isReasonablePosition(result)) return null;
        return new double[]{lat0 + result[1] / METERS_PER_LATITUDE,
                lon0 + result[0] / (METERS_PER_LONGITUDE * cosLat), result[2]};
    }

    private double[] solveMultilateration(double[][] points, double[] distances, int count, int dimensions) {
        if (count - 1 < dimensions) return null;
        double[][] normal = new double[dimensions][dimensions];
        double[] rhs = new double[dimensions];
        double r0 = distances[0];
        for (int i = 1; i < count; i++) {
            double[] row = new double[dimensions];
            double b = r0 * r0 - distances[i] * distances[i];
            for (int d = 0; d < dimensions; d++) {
                row[d] = 2.0 * (points[i][d] - points[0][d]);
                b += points[i][d] * points[i][d] - points[0][d] * points[0][d];
            }
            double weight = 1.0 / Math.max(1.0, distances[i]);
            for (int r = 0; r < dimensions; r++) {
                rhs[r] += weight * row[r] * b;
                for (int c = 0; c < dimensions; c++) {
                    normal[r][c] += weight * row[r] * row[c];
                }
            }
        }
        return solveLinearSystem(normal, rhs, dimensions);
    }

    private double[] solveLinearSystem(double[][] matrix, double[] vector, int size) {
        double[][] augmented = new double[size][size + 1];
        for (int r = 0; r < size; r++) {
            System.arraycopy(matrix[r], 0, augmented[r], 0, size);
            augmented[r][size] = vector[r];
        }
        for (int column = 0; column < size; column++) {
            int pivot = column;
            for (int r = column + 1; r < size; r++) {
                if (Math.abs(augmented[r][column]) > Math.abs(augmented[pivot][column])) pivot = r;
            }
            if (Math.abs(augmented[pivot][column]) < 0.000001) return null;
            if (pivot != column) {
                double[] tmp = augmented[column];
                augmented[column] = augmented[pivot];
                augmented[pivot] = tmp;
            }
            double divisor = augmented[column][column];
            for (int c = column; c <= size; c++) augmented[column][c] /= divisor;
            for (int r = 0; r < size; r++) {
                if (r == column) continue;
                double factor = augmented[r][column];
                for (int c = column; c <= size; c++) augmented[r][c] -= factor * augmented[column][c];
            }
        }
        double[] result = new double[size];
        for (int i = 0; i < size; i++) result[i] = augmented[i][size];
        return result;
    }

    private boolean hasAltitudeSpread(double[][] points, int count) {
        double min = points[0][2];
        double max = points[0][2];
        for (int i = 1; i < count; i++) {
            min = Math.min(min, points[i][2]);
            max = Math.max(max, points[i][2]);
        }
        return max - min >= 2.0;
    }

    private double weightedAltitude(double[][] anchors, int count) {
        double weightSum = 0;
        double altitude = 0;
        for (int i = 0; i < count; i++) {
            double weight = 1.0 / Math.max(1.0, anchors[i][3]);
            weightSum += weight;
            altitude += anchors[i][2] * weight;
        }
        return weightSum == 0 ? 0 : altitude / weightSum;
    }

    private boolean isReasonablePosition(double[] result) {
        if (result == null || result.length < 2) return false;
        for (double value : result) {
            if (Double.isNaN(value) || Double.isInfinite(value)) return false;
            if (Math.abs(value) > 20000.0) return false;
        }
        return true;
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
        String sql = "SELECT e.latitude,e.longitude,e.altitude,p.rssi,p.received_at,p.source_address " +
                "FROM phone_samples p JOIN esp_devices e ON e.mac_address=p.source_address " +
                "WHERE p.phone_id=? AND e.latitude<>0 AND e.longitude<>0 " +
                "AND (e.coordinates_mode=? OR e.coordinates_updated_at>=?) AND p.received_at>=? " +
                "ORDER BY p.received_at DESC,p.rssi DESC";
        List<AnchorSample> samples = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, new String[]{phoneId == null ? "phone" : phoneId,
                String.valueOf(COORDINATES_MANUAL), String.valueOf(cutoff), String.valueOf(cutoff)})) {
            while (cursor.moveToNext()) {
                samples.add(new AnchorSample(
                        cursor.getString(5),
                        cursor.getDouble(0),
                        cursor.getDouble(1),
                        cursor.getDouble(2),
                        cursor.getInt(3),
                        cursor.getLong(4),
                        null
                ));
            }
        }
        Esp32TriangulationEngine.Position position = estimateWithEngine(samples);
        if (position == null) return null;
        return new PhonePosition(position.latitude, position.longitude, position.altitude,
                position.lastSeen, position.anchorCount);
    }

    public Cursor getMapDevices() {
        return getReadableDatabase().query(
                "esp_devices",
                new String[]{
                        "mac_address",
                        "name",
                        "latitude",
                        "longitude",
                        "altitude",
                        "coordinates_mode",
                        "coordinates_updated_at",
                        "is_connected"
                },
                null,
                null,
                null,
                null,
                "is_connected DESC, coordinates_updated_at DESC, name COLLATE NOCASE"
        );
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

    public int triangulateObservedDevices() {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        long observationCutoff = now - OBSERVATION_LOOKBACK_MS;
        long anchorCutoff = now - FRESH_COORDINATE_MS;
        int saved = 0;
        try (Cursor devices = db.rawQuery("SELECT transport,device_mac,MAX(received_at) FROM observations " +
                "WHERE received_at>=? GROUP BY transport,device_mac", new String[]{String.valueOf(observationCutoff)})) {
            while (devices.moveToNext()) {
                String transport = devices.getString(0);
                String mac = devices.getString(1);
                TriangulatedPosition position = estimateObservedDevice(db, transport, mac,
                        observationCutoff, anchorCutoff);
                if (position == null) continue;
                saveTriangulatedDevice(db, transport, mac, position, position.lastSeen);
                mirrorTriangulatedToMainDb(transport, mac, position, position.lastSeen);
                saved++;
            }
        }
        return saved;
    }

    public boolean triangulateObservedDeviceNow(String transport, String mac) {
        if (transport == null || mac == null || mac.trim().isEmpty()) return false;
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        TriangulatedPosition position = estimateObservedDevice(db, transport, mac,
                now - OBSERVATION_LOOKBACK_MS, now - FRESH_COORDINATE_MS);
        if (position == null) return false;
        saveTriangulatedDevice(db, transport, mac, position, position.lastSeen);
        mirrorTriangulatedToMainDb(transport, mac, position, position.lastSeen);
        return true;
    }

    private TriangulatedPosition estimateObservedDevice(SQLiteDatabase db, String transport, String mac,
                                                        long observationCutoff, long anchorCutoff) {
        String sql = "SELECT e.latitude,e.longitude,e.altitude,o.rssi,o.received_at,o.source_address,o.details " +
                "FROM observations o JOIN esp_devices e ON e.mac_address=o.source_address " +
                "WHERE o.transport=? AND o.device_mac=? AND o.received_at>=? " +
                "AND e.latitude<>0 AND e.longitude<>0 " +
                "AND (e.coordinates_mode=? OR e.coordinates_updated_at>=?) " +
                "ORDER BY o.received_at DESC,o.rssi DESC";
        List<AnchorSample> samples = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, new String[]{transport, mac, String.valueOf(observationCutoff),
                String.valueOf(COORDINATES_MANUAL), String.valueOf(anchorCutoff)})) {
            while (cursor.moveToNext()) {
                samples.add(new AnchorSample(
                        cursor.getString(5),
                        cursor.getDouble(0),
                        cursor.getDouble(1),
                        cursor.getDouble(2),
                        cursor.getInt(3),
                        cursor.getLong(4),
                        cursor.isNull(6) ? "" : cursor.getString(6)
                ));
            }
        }
        Esp32TriangulationEngine.Position position = estimateWithEngine(samples);
        if (position == null) return null;
        return new TriangulatedPosition(position.latitude, position.longitude, position.altitude,
                position.accuracy, position.anchorCount, position.rssiAverage,
                position.name, position.lastSeen);
    }

    private Esp32TriangulationEngine.Position estimateWithEngine(List<AnchorSample> samples) {
        List<Esp32TriangulationEngine.Sample> engineSamples = new ArrayList<>();
        for (AnchorSample sample : samples) {
            engineSamples.add(new Esp32TriangulationEngine.Sample(
                    sample.sourceAddress,
                    sample.latitude,
                    sample.longitude,
                    sample.altitude,
                    sample.rssi,
                    sample.observedAt,
                    sample.details
            ));
        }
        return Esp32TriangulationEngine.estimate(engineSamples);
    }

    private List<AnchorSample> selectCoherentAnchorWindow(List<AnchorSample> samples) {
        for (AnchorSample candidate : samples) {
            long windowEnd = candidate.observedAt;
            long windowStart = windowEnd - TRIANGULATION_TIME_WINDOW_MS;
            Set<String> usedSources = new HashSet<>();
            List<AnchorSample> selected = new ArrayList<>();
            for (AnchorSample sample : samples) {
                if (sample.observedAt > windowEnd || sample.observedAt < windowStart) continue;
                if (usedSources.add(sample.sourceAddress.toUpperCase())) selected.add(sample);
            }
            if (selected.size() >= 3) return selected;
        }
        return new ArrayList<>();
    }

    private List<double[]> toAnchorCoordinates(List<AnchorSample> samples) {
        List<double[]> anchors = new ArrayList<>();
        for (AnchorSample sample : samples) {
            anchors.add(new double[]{sample.latitude, sample.longitude, sample.altitude, sample.distanceMeters});
        }
        return anchors;
    }

    private long newestSampleTime(List<AnchorSample> samples) {
        long newest = 0;
        for (AnchorSample sample : samples) newest = Math.max(newest, sample.observedAt);
        return newest;
    }

    private double averageRssi(List<AnchorSample> samples) {
        if (samples.isEmpty()) return 0;
        double sum = 0;
        for (AnchorSample sample : samples) sum += sample.rssi;
        return sum / samples.size();
    }

    private String firstDetails(List<AnchorSample> samples) {
        for (AnchorSample sample : samples) {
            if (sample.details != null && !sample.details.isEmpty()) return sample.details;
        }
        return "";
    }

    private double[] weightedCentroid(double[][] anchors, int count) {
        double weightSum = 0, lat = 0, lon = 0, alt = 0;
        for (int i = 0; i < count; i++) {
            double weight = 1.0 / Math.max(1.0, anchors[i][3]);
            weightSum += weight;
            lat += anchors[i][0] * weight;
            lon += anchors[i][1] * weight;
            alt += anchors[i][2] * weight;
        }
        return new double[]{lat / weightSum, lon / weightSum, alt / weightSum};
    }

    private void saveTriangulatedDevice(SQLiteDatabase db, String transport, String mac,
                                        TriangulatedPosition position, long lastSeen) {
        ContentValues values = new ContentValues();
        values.put("device_mac", mac.toUpperCase());
        values.put("transport", transport);
        values.put("name", position.name);
        values.put("latitude", position.latitude);
        values.put("longitude", position.longitude);
        values.put("altitude", position.altitude);
        values.put("accuracy", position.accuracy);
        values.put("anchor_count", position.anchorCount);
        values.put("rssi_avg", position.rssiAverage);
        values.put("last_seen", lastSeen);
        db.insert("triangulated_devices", null, values);
    }

    private void mirrorTriangulatedToMainDb(String transport, String mac, TriangulatedPosition position, long lastSeen) {
        String folder = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getString("current_folder", FolderNameHelper.MAIN_FOLDER_INTERNAL);
        try (MainDatabaseHelper main = new MainDatabaseHelper(context)) {
            if ("Wi-Fi".equals(transport)) {
                WifiDevice device = new WifiDevice();
                device.setBssid(mac);
                device.setSsid(position.name);
                device.setSignalStrength((int) Math.round(position.rssiAverage));
                device.setVendor("ESP32 triangulation");
                device.setCapabilities("TRIANGULATED");
                device.setLatitude(position.latitude);
                device.setLongitude(position.longitude);
                device.setAltitude(position.altitude);
                device.setLocationAccuracy((float) position.accuracy);
                device.setTimestamp(lastSeen);
                main.addTriangulatedWifiDevice(device, folder);
            } else if ("Bluetooth".equals(transport)) {
                BluetoothDevice device = new BluetoothDevice();
                device.setMacAddress(mac);
                device.setDeviceName(position.name);
                device.setSignalStrength((int) Math.round(position.rssiAverage));
                device.setVendor("ESP32 triangulation");
                device.setLatitude(position.latitude);
                device.setLongitude(position.longitude);
                device.setAltitude(position.altitude);
                device.setLocationAccuracy((float) position.accuracy);
                device.setTimestamp(lastSeen);
                main.addTriangulatedBluetoothDevice(device, folder);
            }
        }
    }

    public Cursor getTriangulatedDevices() {
        return getReadableDatabase().query("triangulated_devices", null, null, null,
                null, null, "last_seen DESC");
    }

    public Cursor getTriangulatedDevicesSince(long cutoff) {
        return getReadableDatabase().query("triangulated_devices", null, "last_seen>=?",
                new String[]{String.valueOf(cutoff)}, null, null, "last_seen DESC");
    }

    public void deleteOldRuntimeData(long maxAgeMillis) {
        long cutoff = System.currentTimeMillis() - Math.max(86400000L, maxAgeMillis);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("observations", "received_at<?", new String[]{String.valueOf(cutoff)});
            db.delete("mesh_links", "last_seen<?", new String[]{String.valueOf(cutoff)});
            db.delete("phone_samples", "received_at<?", new String[]{String.valueOf(cutoff)});
            db.delete("triangulated_devices", "last_seen<?", new String[]{String.valueOf(cutoff)});
            db.delete("connections", "(connected_at<? AND (disconnected_at IS NULL OR disconnected_at<?))",
                    new String[]{String.valueOf(cutoff), String.valueOf(cutoff)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static final class AnchorSample {
        final String sourceAddress;
        final double latitude;
        final double longitude;
        final double altitude;
        final double distanceMeters;
        final int rssi;
        final long observedAt;
        final String details;

        AnchorSample(String sourceAddress, double latitude, double longitude, double altitude,
                     int rssi, long observedAt, String details) {
            this.sourceAddress = sourceAddress == null ? "" : sourceAddress;
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.distanceMeters = Esp32TriangulationEngine.rssiToMeters(rssi);
            this.rssi = rssi;
            this.observedAt = observedAt;
            this.details = details;
        }
    }

    private static double rssiToMetersStatic(int rssi) {
        double txPower = -59.0;
        double pathLoss = 2.4;
        return Math.max(0.5, Math.min(80.0, Math.pow(10.0, (txPower - rssi) / (10.0 * pathLoss))));
    }

    private static final class TriangulatedPosition {
        final double latitude;
        final double longitude;
        final double altitude;
        final double accuracy;
        final int anchorCount;
        final double rssiAverage;
        final String name;
        final long lastSeen;

        TriangulatedPosition(double latitude, double longitude, double altitude, double accuracy,
                             int anchorCount, double rssiAverage, String name, long lastSeen) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.accuracy = accuracy;
            this.anchorCount = anchorCount;
            this.rssiAverage = rssiAverage;
            this.name = name == null ? "" : name;
            this.lastSeen = lastSeen;
        }
    }
}
