package com.example.santiway.opencellid;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.santiway.AlarmModeConfig;
import com.example.santiway.NotificationData;
import com.example.santiway.NotificationDatabaseHelper;
import com.example.santiway.NotificationDetailActivity;
import com.example.santiway.R;
import com.example.santiway.cell_scanner.CellTower;

import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public final class OpenCellIdUnknownTowerNotifier {
    private static final String TAG = "OpenCellIdUnknownTower";
    private static final String PREFS = "opencellid_unknown_towers";

    private static final String CHANNEL_ID = "opencellid_unknown_towers";
    private static final String KEY_PREFIX_LAST_NOTIFY = "last_notify_";

    private static final long DUPLICATE_WINDOW_MS = 60L * 60L * 1000L;

    private OpenCellIdUnknownTowerNotifier() {
    }

    public static void checkAndNotify(Context context, CellTower tower) {
        if (context == null || tower == null) return;

        Context app = context.getApplicationContext();

        if (!AlarmModeConfig.isCellTowerAlarmEnabled(app)) {
            return;
        }

        if (AlarmModeConfig.isQuietModeEnabled(app)
                && !AlarmModeConfig.isQuietModeIncludeCellTowers(app)) {
            return;
        }

        String key = OpenCellIdSyncScheduler.buildTowerKey(tower);
        if (key == null || key.trim().isEmpty()) {
            return;
        }

        if (OpenCellIdSyncScheduler.isKnownTower(app, tower)) {
            return;
        }

        if (!canNotify(app, key)) {
            return;
        }

        saveLastNotify(app, key);
        saveToNotificationHistory(app, tower, key);
        showSystemNotification(app, tower, key);
    }

    private static boolean canNotify(Context context, String key) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long last = prefs.getLong(KEY_PREFIX_LAST_NOTIFY + key, 0L);
        return System.currentTimeMillis() - last > DUPLICATE_WINDOW_MS;
    }

    private static void saveLastNotify(Context context, String key) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_PREFIX_LAST_NOTIFY + key, System.currentTimeMillis())
                .apply();
    }

    private static void saveToNotificationHistory(Context context, CellTower tower, String key) {
        try {
            String id = "opencellid_unknown:" + UUID.randomUUID();

            NotificationData data = new NotificationData(
                    id,
                    "Неизвестная сотовая вышка",
                    buildNotificationText(tower, key),
                    new Date(),
                    NotificationData.NotificationType.CELL_UNKNOWN,
                    new ArrayList<>(),
                    new ArrayList<>(),
                    validCoordinate(tower.getLatitude()) ? tower.getLatitude() : null,
                    validCoordinate(tower.getLongitude()) ? tower.getLongitude() : null,
                    key
            );

            NotificationDatabaseHelper helper = new NotificationDatabaseHelper(context);
            helper.addNotification(data, key);
            helper.close();
        } catch (Exception e) {
            Log.e(TAG, "Could not save unknown tower notification", e);
        }
    }

    private static void showSystemNotification(Context context, CellTower tower, String key) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null) return;

        createChannel(manager);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(context, NotificationDetailActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                key.hashCode(),
                intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Неизвестная сотовая вышка")
                .setContentText(shortText(tower, key))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(buildNotificationText(tower, key)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setColor(Color.parseColor("#FF9800"))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        manager.notify(key.hashCode(), builder.build());
    }

    private static void createChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "OpenCellID unknown towers",
                NotificationManager.IMPORTANCE_HIGH
        );

        channel.setDescription("Уведомления о сотовых вышках, которых нет в локальной базе OpenCellID");
        channel.enableVibration(true);

        manager.createNotificationChannel(channel);
    }

    private static String shortText(CellTower tower, String key) {
        return "Cell: " + tower.getCellId() + ", " + key;
    }

    private static String buildNotificationText(CellTower tower, String key) {
        return String.format(
                Locale.getDefault(),
                "Найдена сотовая вышка, отсутствующая в локальной базе OpenCellID.\n\n" +
                        "Ключ: %s\n" +
                        "Тип сети: %s\n" +
                        "MCC: %d\n" +
                        "MNC: %d\n" +
                        "LAC/TAC: %d\n" +
                        "Cell ID: %d\n" +
                        "Signal: %d dBm\n" +
                        "Operator: %s\n" +
                        "Coordinates: %.6f, %.6f",
                key,
                tower.getNetworkType(),
                tower.getMcc(),
                tower.getMnc(),
                usesTac(tower) ? tower.getTac() : tower.getLac(),
                tower.getCellId(),
                tower.getSignalStrength(),
                tower.getOperatorName(),
                tower.getLatitude(),
                tower.getLongitude()
        );
    }

    private static boolean usesTac(CellTower tower) {
        String type = tower.getNetworkType();
        if (type == null) return false;

        type = type.trim().toUpperCase(Locale.US);

        return "LTE".equals(type) || "5G".equals(type) || "NR".equals(type);
    }

    private static boolean validCoordinate(double value) {
        return !Double.isNaN(value)
                && !Double.isInfinite(value)
                && value != 0.0;
    }
}
