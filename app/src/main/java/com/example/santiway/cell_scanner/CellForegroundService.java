package com.example.santiway.cell_scanner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.santiway.FolderNameHelper;
import com.example.santiway.LocaleHelper;
import com.example.santiway.R;
import com.example.santiway.opencellid.OpenCellIdSyncScheduler;
import com.example.santiway.upload_data.MainDatabaseHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.List;

public class CellForegroundService extends Service {
    private static final String TAG = "CellForegroundService";
    private static final String CHANNEL_ID = "cell_scanner_channel";
    private static final int NOTIFICATION_ID = 1003;

    private MainDatabaseHelper databaseHelper;
    private CellScanner cellScanner;
    private Handler handler;
    private Runnable scanRunnable;
    private boolean isScanning = false;
    private boolean scanInProgress = false;
    private final long scanInterval = 10000;

    private String currentTableName = FolderNameHelper.MAIN_FOLDER_INTERNAL;
    private double currentLatitude = 0.0;
    private double currentLongitude = 0.0;
    private double currentAltitude = 0.0;
    private float currentAccuracy = 0.0f;

    private FusedLocationProviderClient fusedLocationClient;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");

        cellScanner = new CellScanner(this);
        handler = new Handler(Looper.getMainLooper());
        databaseHelper = new MainDatabaseHelper(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        OpenCellIdSyncScheduler.scheduleDaily(this);
        OpenCellIdSyncScheduler.enqueueIfDue(this);

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("START_SCAN".equals(action)) {
                if (intent.hasExtra("tableName")) {
                    currentTableName = intent.getStringExtra("tableName");
                }
                startForegroundService();
                startScanning();
            } else if ("STOP_SCAN".equals(action)) {
                stopSelf();
            }
        }
        return START_STICKY;
    }

    private void startForegroundService() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(LocaleHelper.getString(this, R.string.cell_scanner_notification_title))
                .setContentText(LocaleHelper.getString(this, R.string.cell_scanner_notification_scanning))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void startScanning() {
        if (isScanning) return;
        isScanning = true;

        scanRunnable = new Runnable() {
            @Override
            public void run() {
                if (scanInProgress) {
                    if (isScanning) {
                        handler.postDelayed(this, scanInterval);
                    }
                    return;
                }
                scanInProgress = true;
                // Получаем координаты перед сканированием
                if (ActivityCompat.checkSelfPermission(CellForegroundService.this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(CellForegroundService.this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Location permissions not granted. Cannot perform scan.");
                    scanInProgress = false;
                    return;
                }

                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener(location -> {
                            if (location != null) {
                                currentLatitude = location.getLatitude();
                                currentLongitude = location.getLongitude();
                                currentAltitude = location.getAltitude();
                                currentAccuracy = location.getAccuracy();
                            }

                            try {
                                scanAndSave();
                            } finally {
                                scanInProgress = false;
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to get location: " + e.getMessage());
                            scanAndSave(); // Даже без координат пробуем сохранить
                            scanInProgress = false;
                        });

                if (isScanning) {
                    handler.postDelayed(this, scanInterval);
                }
            }
        };

        handler.post(scanRunnable);
    }

    private void scanAndSave() {
        List<CellTower> towers = cellScanner.getAllCellTowers();
        if (towers != null) {
            SQLiteDatabase db = databaseHelper.getWritableDatabase();
            boolean ownsTransaction = !db.inTransaction();
            if (ownsTransaction) db.beginTransaction();
            try {
            for (CellTower tower : towers) {
                // Записываем координаты в объект перед сохранением
                tower.setLatitude(currentLatitude);
                tower.setLongitude(currentLongitude);
                tower.setAltitude(currentAltitude);
                tower.setLocationAccuracy(currentAccuracy);

                saveToDatabase(tower);
            }
            if (ownsTransaction) db.setTransactionSuccessful();
            } finally {
                if (ownsTransaction && db.inTransaction()) db.endTransaction();
            }
            Log.d(TAG, "Cell scan successful. Found " + towers.size() + " towers.");
        } else {
            Log.e(TAG, "Cell scan failed.");
        }
    }

    private boolean saveToDatabase(CellTower tower) {
        try {
            long resultId = databaseHelper.addCellTower(tower, currentTableName);
            if (resultId != -1) return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to save to database. Retrying with 'Основная'. Error: " + e.getMessage());
            currentTableName = FolderNameHelper.MAIN_FOLDER_INTERNAL;
            try {
                long retryResultId = databaseHelper.addCellTower(tower, currentTableName);
                if (retryResultId != -1) {
                    sendFolderSwitchedBroadcast(currentTableName);
                    return true;
                }
            } catch (Exception retryE) {
                Log.e(TAG, "Failed to save to 'Основная' as well. Error: " + retryE.getMessage());
            }
        }
        return false;
    }

    private void sendFolderSwitchedBroadcast(String newTableName) {
        Intent broadcastIntent = new Intent("com.example.santiway.FOLDER_SWITCHED");
        broadcastIntent.putExtra("newTableName", newTableName);
        sendBroadcast(broadcastIntent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    LocaleHelper.getString(this, R.string.cell_scanner_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(LocaleHelper.getString(this, R.string.cell_scanner_channel_description));
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
        isScanning = false;
        scanInProgress = false;

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
