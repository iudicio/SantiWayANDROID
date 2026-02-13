package com.example.santiway.wifi_scanner;

import android.app.Notification;
import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.santiway.upload_data.MainDatabaseHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.List;
import java.lang.IllegalArgumentException;


public class WifiForegroundService extends Service {
    private static final String TAG = "WifiForegroundService";
    private static final String CHANNEL_ID = "wifi_scanner_channel";
    private static final int NOTIFICATION_ID = 1001;

    private MainDatabaseHelper databaseHelper;
    private WifiManager wifiManager;
    private BroadcastReceiver wifiScanReceiver;
    private Handler handler;
    private Runnable scanRunnable;
    private boolean isScanning = false;
    private long scanInterval = 15000;
    private String currentTableName = "unified_data";
    private double currentLatitude = 0.0;
    private double currentLongitude = 0.0;
    private double currentAltitude = 0.0;
    private float currentAccuracy = 0.0f;
    private float minSignalStrength = -100.0f; // Значение по умолчанию

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        databaseHelper = new MainDatabaseHelper(this);

        createNotificationChannel();
        setupWifiScanReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");

        // Сразу создаем уведомление и запускаем foreground
        try {
            Notification notification = createNotification();
            startForeground(NOTIFICATION_ID, notification);
            Log.d(TAG, "Foreground service started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при создании уведомления: " + e.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null) {
            // Получаем координаты из intent
            if (intent.hasExtra("latitude")) {
                currentLatitude = intent.getDoubleExtra("latitude", 0.0);
                currentLongitude = intent.getDoubleExtra("longitude", 0.0);
                currentAccuracy = intent.getFloatExtra("accuracy", 0.0f);
                if (intent.hasExtra("altitude")) {
                    currentAltitude = intent.getDoubleExtra("altitude", 0.0);
                }
                Log.d(TAG, "Location received: " + currentLatitude + ", " + currentLongitude);
                if (intent.hasExtra("minSignalStrength")) {
                    minSignalStrength = intent.getFloatExtra("minSignalStrength", -100.0f);
                    Log.d(TAG, "Min signal strength set to: " + minSignalStrength);
                }
            }

            // Получить имя таблицы из intent
            if (intent.hasExtra("tableName")) {
                currentTableName = intent.getStringExtra("tableName");
                Log.d(TAG, "Table name set to: " + currentTableName);
            }

            String action = intent.getAction();
            if ("START_SCAN".equals(action)) {
                Log.d(TAG, "Received START_SCAN action");
                startForegroundScanning();
            } else if ("STOP_SCAN".equals(action)) {
                Log.d(TAG, "Received STOP_SCAN action");
                stopForegroundScanning();
            }
        }

        return START_STICKY;
    }

    private void startForegroundScanning() {
        if (isScanning) {
            Log.d(TAG, "Scanning already in progress");
            return;
        }

        Log.d(TAG, "Starting foreground scanning for table: " + currentTableName);

        // Check permissions
        if (!checkPermissions()) {
            Log.w(TAG, "Permissions not granted, stopping service");
            stopSelf();
            return;
        }

        // Check if WiFi is enabled
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            Log.w(TAG, "WiFi is not enabled");
            Toast.makeText(this, "Включите WiFi для сканирования", Toast.LENGTH_SHORT).show();
            stopSelf();
            return;
        }

        isScanning = true;
        startPeriodicScanning();

        Log.d(TAG, "Foreground Wi-Fi scanning started successfully");

        // Обновляем уведомление
        updateNotification(0, 0);
    }

    private boolean checkPermissions() {
        // Check location permission
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "ACCESS_FINE_LOCATION permission not granted");
            return false;
        }

        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted");
                return false;
            }
        }

        return true;
    }

    private void stopForegroundScanning() {
        Log.d(TAG, "Stopping foreground scanning");

        isScanning = false;

        // Remove callbacks
        if (handler != null && scanRunnable != null) {
            handler.removeCallbacks(scanRunnable);
        }

        // Stop foreground service
        stopSelf();

        Log.d(TAG, "Foreground Wi-Fi scanning stopped");
    }

    private void startPeriodicScanning() {
        scanRunnable = new Runnable() {
            @Override
            public void run() {
                if (isScanning) {
                    startWifiScan();
                    // Schedule next scan
                    if (handler != null) {
                        handler.postDelayed(this, scanInterval);
                    }
                }
            }
        };

        // Start first scan immediately
        if (handler != null) {
            handler.post(scanRunnable);
        }
    }

    private void startWifiScan() {
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            updateCurrentLocation();
            boolean scanStarted = wifiManager.startScan();
            Log.d(TAG, "WiFi scan started: " + scanStarted + " for table: " + currentTableName);
        } else {
            Log.w(TAG, "Cannot start scan - WiFi not available or disabled");
        }
    }

    private void updateCurrentLocation() {
        // Если координаты нулевые, пытаемся получить их сейчас
        if (currentLatitude == 0.0 && currentLongitude == 0.0) {
            try {
                // Используем FusedLocationProvider для быстрого получения координат
                FusedLocationProviderClient fusedLocationClient =
                        LocationServices.getFusedLocationProviderClient(this);

                if (ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                    fusedLocationClient.getLastLocation()
                            .addOnSuccessListener(location -> {
                                if (location != null) {
                                    currentLatitude = location.getLatitude();
                                    currentLongitude = location.getLongitude();
                                    currentAltitude = location.getAltitude();
                                    currentAccuracy = location.getAccuracy();
                                    Log.d(TAG, "Got fresh location: " +
                                            currentLatitude + ", " + currentLongitude);
                                } else {
                                    Log.w(TAG, "Last location is null, using zero coordinates");
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to get location: " + e.getMessage());
                            });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting location: " + e.getMessage());
            }
        }
    }

    private void setupWifiScanReceiver() {
        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                    Log.d(TAG, "Scan results available");

                    if (wifiManager != null && checkPermissions()) {
                        try {
                            List<ScanResult> scanResults = wifiManager.getScanResults();
                            Log.d(TAG, "Found " + scanResults.size() + " scan results");

                            if (!scanResults.isEmpty()) {
                                processScanResults(scanResults);
                            } else {
                                Log.d(TAG, "No networks found in scan results");
                            }
                        } catch (SecurityException e) {
                            Log.e(TAG, "Security exception when getting scan results: " + e.getMessage());
                        } catch (Exception e) {
                            Log.e(TAG, "Error getting scan results: " + e.getMessage());
                        }
                    }
                }
            }
        };

        // Register receiver
        IntentFilter intentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        try {
            registerReceiver(wifiScanReceiver, intentFilter);
            Log.d(TAG, "WiFi scan receiver registered");
        } catch (Exception e) {
            Log.e(TAG, "Error registering WiFi scan receiver: " + e.getMessage());
        }
    }

    private void processScanResults(List<ScanResult> scanResults) {
        int savedCount = 0;
        int filteredCount = 0;

        for (ScanResult result : scanResults) {
            // Фильтруем по силе сигнала
            if (result.level < minSignalStrength) {
                filteredCount++;
                continue; // Пропускаем устройства со слабым сигналом
            }

            // Filter out hidden networks and invalid results
            if (result.SSID != null && !result.SSID.isEmpty() && result.BSSID != null) {
                if (saveToDatabase(result)) {
                    savedCount++;
                }
            }
        }

        Log.d(TAG, "Saved " + savedCount + " networks to table: " + currentTableName);
        updateNotification(savedCount, scanResults.size());
    }

    private boolean saveToDatabase(ScanResult result) {
        try {

            //Проверка на нулевые координаты
            if (currentLatitude == 0.0 && currentLongitude == 0.0) {
                Log.w(TAG, "WARNING: Saving device with zero coordinates!");
            }
            WifiDevice device = new WifiDevice();
            device.setSsid(result.SSID != null ? result.SSID : "Unknown");
            device.setBssid(result.BSSID != null ? result.BSSID : "Unknown");
            device.setSignalStrength(result.level);
            device.setFrequency(result.frequency);
            device.setCapabilities(result.capabilities != null ? result.capabilities : "Unknown");
            device.setVendor(getVendorFromBSSID(result.BSSID));
            device.setLatitude(currentLatitude);
            device.setLongitude(currentLongitude);
            device.setAltitude(currentAltitude);
            device.setLocationAccuracy(currentAccuracy);
            device.setTimestamp(System.currentTimeMillis());

            long resultId = databaseHelper.addWifiDevice(device, currentTableName);

            if (resultId != -1) {
                Log.d(TAG, "✓ Saved: " + device.getSsid() + " (" + device.getBssid() + ")");
                return true;
            } else {
                Log.w(TAG, "✗ Failed to save: " + device.getSsid());
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving to database: " + e.getMessage());
            return false;
        }
    }

    private String getVendorFromBSSID(String bssid) {
        if (bssid == null || bssid.length() < 8) {
            return "Unknown";
        }

        try {
            String oui = bssid.substring(0, 8).toUpperCase();
            switch (oui) {
                case "00:1A:2B": return "Cisco";
                case "00:1B:63": return "Netgear";
                case "00:1D:7E": return "Samsung";
                case "00:23:69": return "Apple";
                case "00:26:5A": return "LG";
                case "00:50:7F": return "Intel";
                case "00:1F:3B": return "Sony";
                case "00:1E:8C": return "TP-Link";
                case "00:18:4D": return "Ralink";
                case "00:13:46": return "D-Link";
                case "00:0C:43": return "Huawei";
                case "00:12:17": return "Lenovo";
                case "00:16:6F": return "ZTE";
                default: return "Unknown (" + oui + ")";
            }
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Wi-Fi Scanner")
                .setContentText("Сканирование сетей...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification(int savedNetworks, int totalNetworks) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Wi-Fi Scanner")
                    .setContentText("Сохранено: " + savedNetworks + " сетей, порог: " + (int)minSignalStrength + " dBm")
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
                    "Wi-Fi Scanner Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Уведомления о сканировании Wi-Fi сетей");
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

        // Unregister receiver
        if (wifiScanReceiver != null) {
            try {
                unregisterReceiver(wifiScanReceiver);
                Log.d(TAG, "WiFi scan receiver unregistered");
            } catch (IllegalArgumentException e) {
                // Ресивер не был зарегистрирован - это нормально
                Log.d(TAG, "WiFi scan receiver already unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering receiver: " + e.getMessage());
            }
        }

        // Remove callbacks
        if (handler != null && scanRunnable != null) {
            handler.removeCallbacks(scanRunnable);
        }

        // Close database
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