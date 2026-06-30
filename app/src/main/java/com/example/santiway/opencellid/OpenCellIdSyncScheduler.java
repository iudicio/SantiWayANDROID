package com.example.santiway.opencellid;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.santiway.cell_scanner.CellTower;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

public final class OpenCellIdSyncScheduler {
    private static final String TAG = "OpenCellIdSyncScheduler";

    private static final String PREFS = "opencellid_prefs";
    private static final String KEY_LAST_SYNC = "last_sync_time";
    private static final String KEY_LAST_RESULT = "last_sync_result";
    private static final String KEY_IMPORTED_COUNT = "imported_count";
    private static final String KEY_SYNC_RUNNING = "sync_running";
    private static final String KEY_SOURCE_URL = "opencellid_source_url";

    private static final long DAILY_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    public static final String DB_FILE_NAME = "opencellid_known_towers.csv";

    private OpenCellIdSyncScheduler() {
    }

    public static void scheduleDaily(Context context) {
        if (context == null) return;

        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("daily_scheduled", true)
                .apply();
    }

    public static void enqueueIfDue(Context context) {
        if (context == null) return;

        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        if (prefs.getBoolean(KEY_SYNC_RUNNING, false)) {
            return;
        }

        long lastSync = prefs.getLong(KEY_LAST_SYNC, 0L);
        long now = System.currentTimeMillis();

        File localDb = getLocalDbFile(app);
        boolean databaseMissing = !localDb.exists() || localDb.length() == 0;

        if (!databaseMissing && now - lastSync < DAILY_INTERVAL_MS) {
            return;
        }

        enqueueNow(app);
    }

    public static void enqueueNow(Context context) {
        if (context == null) return;

        Context app = context.getApplicationContext();

        new Thread(() -> syncNow(app), "OpenCellIdSync").start();
    }

    private static void syncNow(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        prefs.edit()
                .putBoolean(KEY_SYNC_RUNNING, true)
                .putString(KEY_LAST_RESULT, "Sync started")
                .apply();

        int imported = 0;

        try {
            File output = getLocalDbFile(context);

            String url = prefs.getString(KEY_SOURCE_URL, "");
            if (url != null && !url.trim().isEmpty()) {
                imported = downloadAndNormalizeCsv(url.trim(), output);
            } else {
                imported = importBundledAssetIfExists(context, output);
            }

            prefs.edit()
                    .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                    .putInt(KEY_IMPORTED_COUNT, imported)
                    .putString(KEY_LAST_RESULT, "Imported towers: " + imported)
                    .putBoolean(KEY_SYNC_RUNNING, false)
                    .apply();

            Log.d(TAG, "OpenCellID sync completed. Imported: " + imported);
        } catch (Exception e) {
            Log.e(TAG, "OpenCellID sync failed", e);

            prefs.edit()
                    .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                    .putString(KEY_LAST_RESULT, "Sync failed: " + e.getMessage())
                    .putBoolean(KEY_SYNC_RUNNING, false)
                    .apply();
        }
    }

    private static int downloadAndNormalizeCsv(String urlString, File output) throws Exception {
        HttpURLConnection connection = null;
        InputStream input = null;

        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "SantiWay-Android");

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code);
            }

            input = connection.getInputStream();

            if (urlString.toLowerCase(Locale.US).endsWith(".gz")) {
                input = new GZIPInputStream(input);
            }

            return normalizeCsvToLocalDb(input, output);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignored) {
                }
            }

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static int importBundledAssetIfExists(Context context, File output) throws Exception {
        try {
            InputStream input = context.getAssets().open(DB_FILE_NAME);
            return normalizeCsvToLocalDb(input, output);
        } catch (Exception e) {
            if (!output.exists()) {
                output.getParentFile().mkdirs();
                output.createNewFile();
            }
            return 0;
        }
    }

    private static int normalizeCsvToLocalDb(InputStream input, File output) throws Exception {
        if (input == null) return 0;

        File temp = new File(output.getParentFile(), output.getName() + ".tmp");
        if (temp.getParentFile() != null) {
            temp.getParentFile().mkdirs();
        }

        int count = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input));
             FileOutputStream fos = new FileOutputStream(temp, false)) {

            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty()) continue;

                if (firstLine && looksLikeHeader(line)) {
                    firstLine = false;
                    continue;
                }

                firstLine = false;

                String normalized = normalizeLine(line);
                if (normalized == null) continue;

                fos.write(normalized.getBytes());
                fos.write('\n');
                count++;
            }
        }

        if (output.exists() && !output.delete()) {
            throw new IllegalStateException("Cannot replace old OpenCellID database");
        }

        if (!temp.renameTo(output)) {
            throw new IllegalStateException("Cannot save OpenCellID database");
        }

        return count;
    }

    private static boolean looksLikeHeader(String line) {
        String lower = line.toLowerCase(Locale.US);
        return lower.contains("radio")
                || lower.contains("mcc")
                || lower.contains("net")
                || lower.contains("cell")
                || lower.contains("area");
    }

    private static String normalizeLine(String line) {
        String[] parts = line.split(",");

        if (parts.length < 5) return null;

        String radio = safe(parts[0]).toUpperCase(Locale.US);
        Integer mcc = parseInt(parts[1]);
        Integer mnc = parseInt(parts[2]);
        Long area = parseLong(parts[3]);
        Long cell = parseLong(parts[4]);

        if (mcc == null || mnc == null || area == null || cell == null) {
            return null;
        }

        if (mcc <= 0 || mnc < 0 || area < 0 || cell <= 0) {
            return null;
        }

        return radio + "," + mcc + "," + mnc + "," + area + "," + cell;
    }

    public static boolean isKnownTower(Context context, CellTower tower) {
        if (context == null || tower == null) return false;

        File db = getLocalDbFile(context);
        if (!db.exists() || db.length() == 0) return false;

        String expected = buildTowerKey(tower);
        if (expected == null) return false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(db)))) {

            String line;

            while ((line = reader.readLine()) != null) {
                if (expected.equalsIgnoreCase(line.trim())) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking OpenCellID database", e);
        }

        return false;
    }

    public static String buildTowerKey(CellTower tower) {
        if (tower == null) return null;

        int mcc = tower.getMcc();
        int mnc = tower.getMnc();
        long area = usesTac(tower) ? tower.getTac() : tower.getLac();
        long cell = tower.getCellId();

        if (mcc <= 0 || mnc < 0 || cell <= 0) return null;

        String radio = tower.getNetworkType();
        if (radio == null || radio.trim().isEmpty()) {
            radio = "CELL";
        }

        radio = radio.trim().toUpperCase(Locale.US);

        if ("5G".equals(radio)) {
            radio = "NR";
        }

        return radio + "," + mcc + "," + mnc + "," + area + "," + cell;
    }

    private static boolean usesTac(CellTower tower) {
        String type = tower.getNetworkType();
        if (type == null) return false;

        type = type.trim().toUpperCase(Locale.US);

        return "LTE".equals(type) || "5G".equals(type) || "NR".equals(type);
    }

    public static File getLocalDbFile(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), DB_FILE_NAME);
    }

    public static int getImportedCount(Context context) {
        if (context == null) return 0;

        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_IMPORTED_COUNT, 0);
    }

    public static long getLastSyncTime(Context context) {
        if (context == null) return 0L;

        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_SYNC, 0L);
    }

    public static String getLastResult(Context context) {
        if (context == null) return "";

        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST_RESULT, "");
    }

    public static void setSourceUrl(Context context, String url) {
        if (context == null) return;

        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SOURCE_URL, url == null ? "" : url.trim())
                .apply();
    }

    public static String getSourceUrl(Context context) {
        if (context == null) return "";

        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SOURCE_URL, "");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.parseInt(safe(value));
        } catch (Exception e) {
            return null;
        }
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(safe(value));
        } catch (Exception e) {
            return null;
        }
    }
}