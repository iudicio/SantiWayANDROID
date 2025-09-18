package com.example.santiway.cell_scanner;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;

public class CellDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "WifiScanner.db";
    private static final int DATABASE_VERSION = 2;

    public CellDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createDefaultTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO: Добавить миграцию данных
        if (oldVersion < 2) {
        }
    }

    private void createDefaultTable(SQLiteDatabase db) {
        String createTableQuery = "CREATE TABLE IF NOT EXISTS \"default_cell_table\" (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "cell_id INTEGER," +
                "lac INTEGER," +
                "mcc INTEGER," +
                "mnc INTEGER," +
                "psc INTEGER," +
                "pci INTEGER," +
                "tac INTEGER," +
                "earfcn INTEGER," +
                "arfcn INTEGER," +
                "signal_strength INTEGER," +
                "signal_quality INTEGER," +
                "network_type TEXT," +
                "operator_name TEXT," +
                "is_registered INTEGER," +
                "is_neighbor INTEGER," +
                "latitude REAL," +
                "longitude REAL," +
                "altitude REAL," +
                "location_accuracy REAL," +
                "timestamp LONG)";
        db.execSQL(createTableQuery);
    }

    public void createCellTableIfNotExists(String tableName) {
        SQLiteDatabase db = this.getWritableDatabase();
        String safeName = "\"" + tableName + "\"";
        String createTableQuery = "CREATE TABLE IF NOT EXISTS " + safeName + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "cell_id INTEGER," +
                "lac INTEGER," +
                "mcc INTEGER," +
                "mnc INTEGER," +
                "psc INTEGER," +
                "pci INTEGER," +
                "tac INTEGER," +
                "earfcn INTEGER," +
                "arfcn INTEGER," +
                "signal_strength INTEGER," +
                "signal_quality INTEGER," +
                "network_type TEXT," +
                "operator_name TEXT," +
                "is_registered INTEGER," +
                "is_neighbor INTEGER," +
                "latitude REAL," +
                "longitude REAL," +
                "altitude REAL," +
                "location_accuracy REAL," +
                "timestamp LONG)";
        db.execSQL(createTableQuery);
        db.close();
    }

    public boolean deleteCellTable(String tableName) {
        if (tableName.equals("default_cell_table")) return false;

        try (SQLiteDatabase db = this.getWritableDatabase()) {
            String safeName = "\"" + tableName + "\"";
            db.execSQL("DROP TABLE IF EXISTS " + safeName);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public long addCellTower(String tableName, CellTower tower) {
        return addOrUpdateCellTower(tableName, tower);
    }

    public long addOrUpdateCellTower(String tableName, CellTower tower) {
        createCellTableIfNotExists(tableName);
        SQLiteDatabase db = this.getWritableDatabase();
        long result = -1;

        try {
            db.beginTransaction();

            ContentValues values = new ContentValues();
            values.put("cell_id", tower.getCellId());
            values.put("lac", tower.getLac());
            values.put("mcc", tower.getMcc());
            values.put("mnc", tower.getMnc());
            values.put("psc", tower.getPsc());
            values.put("pci", tower.getPci());
            values.put("tac", tower.getTac());
            values.put("earfcn", tower.getEarfcn());
            values.put("arfcn", tower.getArfcn());
            values.put("signal_strength", tower.getSignalStrength());
            values.put("signal_quality", tower.getSignalQuality());
            values.put("network_type", tower.getNetworkType());
            values.put("operator_name", tower.getOperatorName());
            values.put("is_registered", tower.isRegistered() ? 1 : 0);
            values.put("is_neighbor", tower.isNeighbor() ? 1 : 0);
            values.put("latitude", tower.getLatitude());
            values.put("longitude", tower.getLongitude());
            values.put("altitude", tower.getAltitude());
            values.put("location_accuracy", tower.getLocationAccuracy());
            values.put("timestamp", tower.getTimestamp());

            String uniqueKey = tower.getMcc() + "-" + tower.getMnc() + "-" +
                    tower.getLac() + "-" + tower.getCellId();

            try (Cursor cursor = db.query("\"" + tableName + "\"",
                    new String[]{"id", "timestamp"},
                    "mcc = ? AND mnc = ? AND lac = ? AND cell_id = ?",
                    new String[]{String.valueOf(tower.getMcc()),
                            String.valueOf(tower.getMnc()),
                            String.valueOf(tower.getLac()),
                            String.valueOf(tower.getCellId())},
                    null, null, null)) {
                if (cursor.moveToFirst()) {
                    long existingId = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                    long existingTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));

                    if (tower.getTimestamp() > existingTimestamp) {
                        result = db.update("\"" + tableName + "\"", values,
                                "id = ?", new String[]{String.valueOf(existingId)});
                        Log.d("CellDatabaseHelper", "Updated cell tower: " + uniqueKey);
                    } else {
                        result = 1;
                        Log.d("CellDatabaseHelper", "Skipped older cell tower: " + uniqueKey);
                    }
                } else {
                    result = db.insert("\"" + tableName + "\"", null, values);
                    Log.d("CellDatabaseHelper", "Inserted new cell tower: " + uniqueKey);
                }
            }

            db.setTransactionSuccessful();

        } catch (Exception e) {
            Log.e("CellDatabaseHelper", "Error in transaction: " + e.getMessage());
            e.printStackTrace();
            result = -1;
        } finally {
            try {
                db.endTransaction();
            } catch (Exception e) {
                Log.e("CellDatabaseHelper", "Error ending transaction: " + e.getMessage());
            }
            db.close();
        }
        return result;
    }

    public List<CellTower> getAllCellTowers(String tableName) {
        List<CellTower> towers = new ArrayList<>();

        try (SQLiteDatabase db = this.getReadableDatabase(); Cursor cursor = db.query("\"" + tableName + "\"", null, null, null, null, null, "timestamp DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    CellTower tower = new CellTower();
                    tower.setCellId(cursor.getInt(cursor.getColumnIndexOrThrow("cell_id")));
                    tower.setLac(cursor.getInt(cursor.getColumnIndexOrThrow("lac")));
                    tower.setMcc(cursor.getInt(cursor.getColumnIndexOrThrow("mcc")));
                    tower.setMnc(cursor.getInt(cursor.getColumnIndexOrThrow("mnc")));
                    tower.setPsc(cursor.getInt(cursor.getColumnIndexOrThrow("psc")));
                    tower.setPci(cursor.getInt(cursor.getColumnIndexOrThrow("pci")));
                    tower.setTac(cursor.getInt(cursor.getColumnIndexOrThrow("tac")));
                    tower.setEarfcn(cursor.getInt(cursor.getColumnIndexOrThrow("earfcn")));
                    tower.setArfcn(cursor.getInt(cursor.getColumnIndexOrThrow("arfcn")));
                    tower.setSignalStrength(cursor.getInt(cursor.getColumnIndexOrThrow("signal_strength")));
                    tower.setSignalQuality(cursor.getInt(cursor.getColumnIndexOrThrow("signal_quality")));
                    tower.setNetworkType(cursor.getString(cursor.getColumnIndexOrThrow("network_type")));
                    tower.setOperatorName(cursor.getString(cursor.getColumnIndexOrThrow("operator_name")));
                    tower.setRegistered(cursor.getInt(cursor.getColumnIndexOrThrow("is_registered")) == 1);
                    tower.setNeighbor(cursor.getInt(cursor.getColumnIndexOrThrow("is_neighbor")) == 1);
                    tower.setLatitude(cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")));
                    tower.setLongitude(cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")));
                    tower.setAltitude(cursor.getDouble(cursor.getColumnIndexOrThrow("altitude")));
                    tower.setLocationAccuracy(cursor.getFloat(cursor.getColumnIndexOrThrow("location_accuracy")));
                    tower.setTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")));
                    towers.add(tower);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return towers;
    }

    public List<CellTower> getCellTowersByNetworkType(String tableName, String networkType) {
        List<CellTower> towers = new ArrayList<>();

        try (SQLiteDatabase db = this.getReadableDatabase(); Cursor cursor = db.query("\"" + tableName + "\"", null,
                "network_type = ?", new String[]{networkType},
                null, null, "timestamp DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    CellTower tower = new CellTower();
                    tower.setCellId(cursor.getInt(cursor.getColumnIndexOrThrow("cell_id")));
                    tower.setLac(cursor.getInt(cursor.getColumnIndexOrThrow("lac")));
                    tower.setMcc(cursor.getInt(cursor.getColumnIndexOrThrow("mcc")));
                    tower.setMnc(cursor.getInt(cursor.getColumnIndexOrThrow("mnc")));
                    tower.setPsc(cursor.getInt(cursor.getColumnIndexOrThrow("psc")));
                    tower.setPci(cursor.getInt(cursor.getColumnIndexOrThrow("pci")));
                    tower.setTac(cursor.getInt(cursor.getColumnIndexOrThrow("tac")));
                    tower.setEarfcn(cursor.getInt(cursor.getColumnIndexOrThrow("earfcn")));
                    tower.setArfcn(cursor.getInt(cursor.getColumnIndexOrThrow("arfcn")));
                    tower.setSignalStrength(cursor.getInt(cursor.getColumnIndexOrThrow("signal_strength")));
                    tower.setSignalQuality(cursor.getInt(cursor.getColumnIndexOrThrow("signal_quality")));
                    tower.setNetworkType(cursor.getString(cursor.getColumnIndexOrThrow("network_type")));
                    tower.setOperatorName(cursor.getString(cursor.getColumnIndexOrThrow("operator_name")));
                    tower.setRegistered(cursor.getInt(cursor.getColumnIndexOrThrow("is_registered")) == 1);
                    tower.setNeighbor(cursor.getInt(cursor.getColumnIndexOrThrow("is_neighbor")) == 1);
                    tower.setLatitude(cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")));
                    tower.setLongitude(cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")));
                    tower.setAltitude(cursor.getDouble(cursor.getColumnIndexOrThrow("altitude")));
                    tower.setLocationAccuracy(cursor.getFloat(cursor.getColumnIndexOrThrow("location_accuracy")));
                    tower.setTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")));
                    towers.add(tower);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return towers;
    }

    public void clearCellTable(String tableName) {
        try (SQLiteDatabase db = this.getWritableDatabase()) {
            db.delete("\"" + tableName + "\"", null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getCellTowersCount(String tableName) {
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

    public List<String> getAllCellTables() {
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

    public String getNetworkTypeStatistics(String tableName) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        StringBuilder stats = new StringBuilder();

        try {
            cursor = db.rawQuery(
                    "SELECT network_type, COUNT(*) as count FROM \"" + tableName + "\" GROUP BY network_type",
                    null
            );
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String networkType = cursor.getString(cursor.getColumnIndexOrThrow("network_type"));
                    int count = cursor.getInt(cursor.getColumnIndexOrThrow("count"));
                    stats.append(networkType).append(": ").append(count).append("; ");
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }

        return stats.toString();
    }
}


