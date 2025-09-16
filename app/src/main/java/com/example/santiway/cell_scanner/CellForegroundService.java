package com.example.santiway.cell_scanner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import java.util.List;

public class CellForegroundService extends Service {
    private static final String TAG = "CellForegroundService";
    private static final String CHANNEL_ID = "cell_scanner_channel";
    private static final int NOTIFICATION_ID = 1002;

    private CellDatabaseHelper databaseHelper;
    private CellScanner cellScanner;
    private Handler handler;
    private Runnable scanRunnable;
    private boolean isScanning = false;
    private final long scanInterval = 10000;
    private String currentTableName = "default_cell_table";
    private double currentLatitude = 0.0;
    private double currentLongitude = 0.0;
    private double currentAltitude = 0.0;
    private float currentAccuracy = 0.0f;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");

        cellScanner = new CellScanner(this);
        handler = new Handler(Looper.getMainLooper());
        databaseHelper = new CellDatabaseHelper(this);

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");

        if (intent != null) {
            if (intent.hasExtra("latitude")) {
                currentLatitude = intent.getDoubleExtra("latitude", 0.0);
                currentLongitude = intent.getDoubleExtra("longitude", 0.0);
                currentAccuracy = intent.getFloatExtra("accuracy", 0.0f);
                if (intent.hasExtra("altitude")) {
                    currentAltitude = intent.getDoubleExtra("altitude", 0.0);
                }
                Log.d(TAG, "Location received: " + currentLatitude + ", " + currentLongitude);
            }

            if (intent.hasExtra("tableName")) {
                currentTableName = intent.getStringExtra("tableName");
                Log.d(TAG, "Table name set to: " + currentTableName);
            }

            String action = intent.getAction();
            if ("START_CELL_SCAN".equals(action)) {
                Log.d(TAG, "Received START_CELL_SCAN action");
                startForegroundScanning();
            } else if ("STOP_CELL_SCAN".equals(action)) {
                Log.d(TAG, "Received STOP_CELL_SCAN action");
                stopForegroundScanning();
            }
        }

        return START_STICKY;
    }

    private void startForegroundScanning() {
        if (isScanning) {
            Log.d(TAG, "Cell scanning already in progress");
            return;
        }

        Log.d(TAG, "Starting foreground cell scanning for table: " + currentTableName);

        if (!checkPermissions()) {
            Log.w(TAG, "Permissions not granted, stopping service");
            stopSelf();
            return;
        }

        if (!cellScanner.isScanningAvailable()) {
            Log.w(TAG, "Cell scanning is not available");
            stopSelf();
            return;
        }

        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);

        isScanning = true;
        startPeriodicScanning();

        Log.d(TAG, "Foreground cell scanning started successfully");
    }

    private boolean checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "ACCESS_FINE_LOCATION permission not granted");
            return false;
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted");
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted");
                return false;
            }
        }

        return true;
    }

    private void stopForegroundScanning() {
        Log.d(TAG, "Stopping foreground cell scanning");

        isScanning = false;

        if (handler != null && scanRunnable != null) {
            handler.removeCallbacks(scanRunnable);
        }

        stopForeground(true);
        stopSelf();

        Log.d(TAG, "Foreground cell scanning stopped");
    }

    private void startPeriodicScanning() {
        scanRunnable = new Runnable() {
            @Override
            public void run() {
                if (isScanning) {
                    startCellScan();
                    if (handler != null) {
                        handler.postDelayed(this, scanInterval);
                    }
                }
            }
        };

        if (handler != null) {
            handler.post(scanRunnable);
        }
    }

    private void startCellScan() {
        if (cellScanner.isScanningAvailable()) {
            try {
                List<CellTower> cellTowers = cellScanner.getAllCellTowers();
                Log.d(TAG, "Cell scan completed: " + cellTowers.size() + " towers found for table: " + currentTableName);

                if (!cellTowers.isEmpty()) {
                    processCellTowers(cellTowers);
                } else {
                    Log.d(TAG, "No cell towers found in scan");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during cell scan: " + e.getMessage());
            }
        } else {
            Log.w(TAG, "Cannot start scan - cell scanning not available");
        }
    }

    private void processCellTowers(List<CellTower> cellTowers) {
        int savedCount = 0;

        for (CellTower tower : cellTowers) {
            tower.setLatitude(currentLatitude);
            tower.setLongitude(currentLongitude);
            tower.setAltitude(currentAltitude);
            tower.setLocationAccuracy(currentAccuracy);
            tower.setTimestamp(System.currentTimeMillis());

            if (saveToDatabase(tower)) {
                savedCount++;
            }
        }

        Log.d(TAG, "Saved " + savedCount + " cell towers to table: " + currentTableName);
        updateNotification(savedCount, cellTowers.size());
    }

    private boolean saveToDatabase(CellTower tower) {
        try {
            long resultId = databaseHelper.addCellTower(currentTableName, tower);

            if (resultId != -1) {
                Log.d(TAG, "✓ Saved: " + tower.getDescription());
                return true;
            } else {
                Log.w(TAG, "✗ Failed to save: " + tower.getDescription());
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving to database: " + e.getMessage());
            return false;
        }
    }


    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Сканер базовых станций")
                .setContentText("Сканирование базовых станций...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification(int savedTowers, int totalTowers) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Сканер базовых станций")
                    .setContentText("Найдено: " + totalTowers + " станций, сохранено: " + savedTowers)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build();

            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Cell Scanner Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Уведомления о сканировании базовых станций");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created");
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service onDestroy");

        if (handler != null && scanRunnable != null) {
            handler.removeCallbacks(scanRunnable);
        }

        if (databaseHelper != null) {
            databaseHelper.close();
        }

        Log.d(TAG, "Service cleanup completed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
