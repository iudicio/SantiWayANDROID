package com.example.santiway;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ActivityCompat;
import android.util.Log;
import java.util.List;

public class WifiForegroundService extends Service {
    private DatabaseHelper databaseHelper;
    public static final int NOTIFICATION_ID = 1001;
    public static final String CHANNEL_ID = "wifi_channel_id";
    private static final String TAG = "WifiForegroundService"; //TAG для отслеживания логов
    private Handler handler;
    private Runnable scanRunnable;
    private boolean isScanning = false;
    private long scanInterval = 15000; //ms
    private WifiManager wifiManager;
    private BroadcastReceiver wifiScanReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        databaseHelper = new DatabaseHelper(this);
        createNotificationChannel();
        setupWifiScanReceiver();

        cleanOldRecords();
    }

    private void cleanOldRecords() {
        // Удаляем записи старше 3 дней
        long sevenDaysAgo = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000);
        int deletedCount = databaseHelper.deleteOldRecords(sevenDaysAgo);
        Log.i(TAG, "Cleaned up " + deletedCount + " old records");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("START_SCAN".equals(action)) {
                startForegroundScanning();
            } else if ("STOP_SCAN".equals(action)) {
                stopForegroundScanning();
            } else if ("UPDATE_INTERVAL".equals(action)) {
                scanInterval = intent.getLongExtra("interval", 15000);
                restartScanning();
            }
        }
        return START_STICKY;
    }

    private void startForegroundScanning() {
        Log.d(TAG, "startForegroundScanning called");
        if (isScanning) return;

        // Проверка разрешений перед запуском
        if (!checkPermissions()) {
            Log.w(TAG, "Permissions not granted, stopping service");
            stopSelf();
            return;
        }

        try {
            Log.d(TAG, "Starting foreground service");
            Notification notification = createNotification();
            startForeground(NOTIFICATION_ID, notification);

            isScanning = true;
            Log.d(TAG, "Starting periodic scanning with interval: " + scanInterval + "ms");
            startPeriodicScanning();
        } catch (SecurityException e) {
            // Обработка отсутствия разрешений
            Log.e(TAG, "SecurityException: " + e.getMessage());
            stopSelf();
        }
    }

    private boolean checkPermissions() {
        // Проверка location permission
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "ACCESS_FINE_LOCATION permission not granted");
            return false;
        }

        // Для Android 13+ проверяем разрешение на уведомления
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted");
                return false;
            }
        }

        return true;
    }

    private void stopForegroundScanning() {
        isScanning = false;
        handler.removeCallbacks(scanRunnable);
        stopForeground(true);
        stopSelf();
    }

    private void restartScanning() {
        if (isScanning) {
            if (handler != null && scanRunnable != null) {
                handler.removeCallbacks(scanRunnable);
            }
            startPeriodicScanning();
        }
    }

    private void startPeriodicScanning() {
        scanRunnable = new Runnable() {
            @Override
            public void run() {
                if (isScanning) {
                    startWifiScan();
                    handler.postDelayed(this, scanInterval);
                }
            }
        };
        handler.post(scanRunnable);
    }

    private void startWifiScan() {
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            wifiManager.startScan();
        }
    }

    private void setupWifiScanReceiver() {
        Log.d(TAG, "Setting up WiFi scan receiver");
        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Broadcast received: " + intent.getAction());
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                    if (wifiManager != null) {
                        // Проверка разрешений
                        if (ActivityCompat.checkSelfPermission(context,
                                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            Log.w(TAG, "Location permission not granted, cannot get scan results");
                            return;
                        }

                        try {
                            List<ScanResult> scanResults = wifiManager.getScanResults();
                            Log.d(TAG, "Scan results available: " + scanResults.size() + " networks found");

                            if (scanResults.isEmpty()) {
                                Log.w(TAG, "No Wi-Fi networks found in scan results");
                            } else {
                                processScanResults(scanResults);
                            }
                        } catch (SecurityException e) {
                            Log.e(TAG, "SecurityException when getting scan results: " + e.getMessage());
                        } catch (Exception e) {
                            Log.e(TAG, "Error getting scan results: " + e.getMessage());
                        }
                    }
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        try {
            registerReceiver(wifiScanReceiver, intentFilter);
            Log.d(TAG, "WiFi scan receiver registered successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register WiFi scan receiver: " + e.getMessage());
        }
    }

    private void processScanResults(List<ScanResult> scanResults) {
        logScanResults(scanResults);
        for (ScanResult result : scanResults) {
            saveToDatabase(result);
        }

        mockSendToServer(scanResults);
    }

    private void logScanResults(List<ScanResult> scanResults) {
        Log.i(TAG,"=== Wi-Fi Scan Results ===");
        Log.i(TAG,"Found " + scanResults.size() + " devices");
        Log.i(TAG,"Timestamp: " + System.currentTimeMillis());

        for (int i = 0; i < scanResults.size(); i++) {
            ScanResult result = scanResults.get(i);
            Log.i(TAG,"\nDevice #" + (i + 1) + ":");
            Log.i(TAG,"  SSID: " + result.SSID);
            Log.i(TAG,"  BSSID: " + result.BSSID);
            Log.i(TAG,"  Signal Strength: " + result.level + " dBm");
            Log.i(TAG,"  Frequency: " + result.frequency + " MHz");
            Log.i(TAG,"  Capabilities: " + result.capabilities);
            Log.i(TAG,"  Channel Width: " + getChannelWidth(result));
            Log.i(TAG,"  Vendor: " + getVendorFromBSSID(result.BSSID));
        }
        Log.i(TAG,"=== End of Scan ===");
    }

    private String getChannelWidth(ScanResult result) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            switch (result.channelWidth) {
                case ScanResult.CHANNEL_WIDTH_20MHZ: return "20 MHz";
                case ScanResult.CHANNEL_WIDTH_40MHZ: return "40 MHz";
                case ScanResult.CHANNEL_WIDTH_80MHZ: return "80 MHz";
                case ScanResult.CHANNEL_WIDTH_160MHZ: return "160 MHz";
                case ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ: return "80+ MHz";
                default: return "Unknown";
            }
        }
        return "N/A";
    }

    private String getVendorFromBSSID(String bssid) {
        // Простая проверка по первым 6 символами (OUI)
        if (bssid != null && bssid.length() >= 8) {
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
                default: return "Unknown (" + oui + ")";
            }
        }
        return "Unknown";
    }

    private void saveToDatabase(ScanResult result) {
        WifiDevice device = new WifiDevice();
        device.setSsid(result.SSID);
        device.setBssid(result.BSSID);
        device.setSignalStrength(result.level);
        device.setFrequency(result.frequency);
        device.setCapabilities(result.capabilities);
        device.setVendor(getVendorFromBSSID(result.BSSID));
        device.setTimestamp(System.currentTimeMillis());

        long resultId = databaseHelper.addOrUpdateWifiDevice(device);

        if (resultId != -1) {
            Log.d(TAG, "Saved device to database: " + device.getBssid());
        } else {
            Log.e(TAG, "Failed to save device to database: " + device.getBssid());
        }
    }

    private void mockSendToServer(List<ScanResult> results) {
        // Заглушка отправки данных - теперь логируем в БД
        Log.i(TAG, "Mock server send: " + results.size() + " devices");
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Wi-Fi Scanner")
                .setContentText("Scanning nearby devices...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Wi-Fi Scanner",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background Wi-Fi scanning service");

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wifiScanReceiver != null) {
            unregisterReceiver(wifiScanReceiver);
        }
        handler.removeCallbacks(scanRunnable);

        if (databaseHelper != null) {
            databaseHelper.close();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
