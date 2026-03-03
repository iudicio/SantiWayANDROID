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
import android.location.Location;
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

import com.example.santiway.gsm_protocol.LocationManager;
import com.example.santiway.upload_data.MainDatabaseHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private float minSignalStrength = -100.0f;
    private Set<String> processedInCurrentScan = new HashSet<>();

    // Используем LocationManager вместо хранения координат
    private LocationManager locationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        databaseHelper = new MainDatabaseHelper(this);

        // Получаем экземпляр LocationManager
        locationManager = LocationManager.getInstance(this);

        // Запускаем обновления геолокации
        if (checkPermissions()) {
            locationManager.startLocationUpdates();
        }

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
            // Получить имя таблицы из intent
            if (intent.hasExtra("tableName")) {
                currentTableName = intent.getStringExtra("tableName");
                Log.d(TAG, "Table name set to: " + currentTableName);
            }

            if (intent.hasExtra("minSignalStrength")) {
                minSignalStrength = intent.getFloatExtra("minSignalStrength", -100.0f);
                Log.d(TAG, "Min signal strength set to: " + minSignalStrength);
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
            // Принудительно обновляем координаты перед сканированием
            Location freshLocation = locationManager.getFreshLocation();
            if (freshLocation != null) {
                Log.d(TAG, "Fresh location obtained: " + freshLocation.getLatitude() +
                        ", " + freshLocation.getLongitude());
            } else {
                Log.w(TAG, "Could not get fresh location, will use last known or zeros");
            }

            boolean scanStarted = wifiManager.startScan();
            Log.d(TAG, "WiFi scan started: " + scanStarted + " for table: " + currentTableName);
        } else {
            Log.w(TAG, "Cannot start scan - WiFi not available or disabled");
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
        processedInCurrentScan.clear();

        // Получаем текущие координаты из LocationManager
        double latitude = locationManager.getLatitude();
        double longitude = locationManager.getLongitude();
        double altitude = locationManager.getAltitude();
        float accuracy = locationManager.getAccuracy();

        for (ScanResult result : scanResults) {
            // Фильтруем по силе сигнала
            if (result.level < minSignalStrength) {
                filteredCount++;
                continue; // Пропускаем устройства со слабым сигналом
            }

            if (result.SSID != null && !result.SSID.isEmpty() && result.BSSID != null) {
                // Проверяем дубликаты в текущем сканировании
                String key = result.BSSID + "_" + (System.currentTimeMillis() / 1000);
                if (processedInCurrentScan.contains(key)) {
                    Log.d(TAG, "⏱️ Duplicate WiFi in current scan: " + result.BSSID);
                    continue;
                }
                processedInCurrentScan.add(key);

                if (saveToDatabase(result, latitude, longitude, altitude, accuracy)) {
                    savedCount++;
                }
            }
        }

        Log.d(TAG, "Saved " + savedCount + " networks to table: " + currentTableName +
                " (filtered: " + filteredCount + ")");
        updateNotification(savedCount, scanResults.size());
    }

    private boolean saveToDatabase(ScanResult result, double latitude, double longitude,
                                   double altitude, float accuracy) {
        try {
            // ✅ НЕ сохраняем записи без координат
            if (latitude == 0 && longitude == 0) {
                Log.w(TAG, "Skipping save (zero coords): " + result.BSSID + " / " + result.SSID);
                return false;
            }
            WifiDevice device = new WifiDevice();
            device.setSsid(result.SSID != null ? result.SSID : "Неизвестен");
            device.setBssid(result.BSSID != null ? result.BSSID : "Неизвестен");
            device.setSignalStrength(result.level);
            device.setFrequency(result.frequency);
            device.setCapabilities(result.capabilities != null ? result.capabilities : "Неизвестен");
            device.setVendor(getVendorFromBSSID(result.BSSID));
            device.setLatitude(latitude);
            device.setLongitude(longitude);
            device.setAltitude(altitude);
            device.setLocationAccuracy(accuracy);
            device.setTimestamp(System.currentTimeMillis());

            long resultId = databaseHelper.addWifiDevice(device, currentTableName);

            if (resultId != -1) {
                Log.d(TAG, "✓ Saved: " + device.getSsid() + " (" + device.getBssid() +
                        ") at [" + latitude + ", " + longitude + "]");
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