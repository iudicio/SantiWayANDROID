package com.example.santiway.bluetooth_scanner;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.santiway.MainDatabaseHelper;
import com.example.santiway.host_database.AppSettingsRepository;
import com.example.santiway.host_database.ScannerSettings;


import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;



public class BluetoothForegroundService extends Service {
    private static final String TAG = "BluetoothForegroundService";
    private static final String CHANNEL_ID = "bluetooth_scanner_channel";
    private static final int NOTIFICATION_ID = 1002;

    private AppSettingsRepository appSettingsRepository;
    private ScannerSettings scannerSettings;

    // Сканеры и адаптеры Bluetooth
    private MainDatabaseHelper databaseHelper;
    private BluetoothAdapter btAdapter;
    private BluetoothLeScanner bleScanner;
    private BroadcastReceiver classicReceiver;
    private ScanCallback bleScanCallback;

    // Множество для фильтрации дубликатов по MAC адресу
    private HashSet<String> seenAddresses;

    private Handler handler;
    private Runnable scanRunnable;
    private boolean isScanning = false;

    private long scanInterval = 15000; // интервал между циклами сканирования
    private long scanDuration = 8000;  // длительность одного активного сканирования
    private float minRssi = -100;        // минимальный RSSI для сохранения устройства

    // Параметры геопозиции — получаем извне через Intent
    private double currentLatitude = 0.0;
    private double currentLongitude = 0.0;
    private double currentAltitude = 0.0;
    private float currentAccuracy = 0.0f;
    private static final Map<String, String> OUI_MAP = new HashMap<>();
    static {
        OUI_MAP.put("30:7B:07", "Apple");
        OUI_MAP.put("72:52:3A", "Samsung");
        OUI_MAP.put("C0:85:40", "JBL");
        OUI_MAP.put("0C:B9:83", "HONOR");
        OUI_MAP.put("C0:0E:D6", "Xiaomi");
        OUI_MAP.put("CA:4F:CE", "Sony");
        OUI_MAP.put("98:5F:41", "Dell");
        OUI_MAP.put("66:AB:65", "HP");
    }
    private String currentTableName = "default_table"; // имя таблицы для сохранения

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");

        handler = new Handler(Looper.getMainLooper());
        databaseHelper = new MainDatabaseHelper(this);
        appSettingsRepository = new AppSettingsRepository(this);
        updateScannerSettings();

        // Инициализация Bluetooth
        BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (btManager != null) {
            btAdapter = btManager.getAdapter();
        } else {
            btAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        if (btAdapter == null) {
            Log.e(TAG, "Bluetooth не поддерживается на данном устройстве");
            stopSelf();
            return;
        }

        if (!btAdapter.isEnabled()) {
            Log.w(TAG, "Bluetooth адаптер существует, но отключен");
        } else {
            // Подключение BLE сканера для
            bleScanner = btAdapter.getBluetoothLeScanner();
        }

        // Инициализация множества для фильтрации дубликатов
        seenAddresses = new HashSet<>();

        // Создание канала уведомлений
        createNotificationChannel();

        // Подготовка обработчиков BLE и классических устройств
        prepareBleScanCallback();
        prepareClassicReceiver();
    }

    private void updateScannerSettings(){
        scannerSettings = appSettingsRepository.getScannerSettings("bluetooth");
        if (scannerSettings != null){
            scanInterval = (long) (scannerSettings.getScanInterval() * 1000);
            minRssi = scannerSettings.getSignalStrength();
            Log.d(TAG, "Настройки обновлены: интервал=" + scanInterval + "мс, minRssi=" + minRssi);
        }
    }


    // Подготовка BLE ScanCallback
    private void prepareBleScanCallback() {
        bleScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                handleDeviceFound(result.getDevice(), result.getRssi(), true);
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                for (ScanResult r : results) {
                    handleDeviceFound(r.getDevice(), r.getRssi(), true);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e(TAG, "Ошибка BLE сканирования: " + errorCode);
            }
        };
    }

    // Подготовка классического BroadcastReceiver
    private void prepareClassicReceiver() {
        classicReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice sysDev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                    handleDeviceFound(sysDev, rssi, false);
                }
            }
        };
    }

    private void handleDeviceFound(BluetoothDevice device, int rssi, boolean isBle) {
        if (device == null || rssi < minRssi) return;

        String address = device.getAddress();
        String name;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                name = device.getName();
            } else {
                name = "Unknown";
            }
        } else {
            name = device.getName();
        }

        // Проверка на дубликат
        if (seenAddresses.contains(address)) return;
        seenAddresses.add(address);


        // Создание модели устройства
        com.example.santiway.bluetooth_scanner.BluetoothDevice myDev = new com.example.santiway.bluetooth_scanner.BluetoothDevice();
        myDev.setDeviceName(name != null ? name : "Unknown");
        myDev.setMacAddress(address);
        myDev.setSignalStrength(rssi);
        myDev.setVendor(getVendor(address));
        myDev.setTimestamp(System.currentTimeMillis());

        // Добавление геопозиции (пока из intent)
        myDev.setLatitude(currentLatitude);
        myDev.setLongitude(currentLongitude);
        myDev.setAltitude(currentAltitude);
        myDev.setLocationAccuracy(currentAccuracy);

        // Сохранение в базу
        long rowId = databaseHelper.addBluetoothDevice(myDev, currentTableName);


        Log.d(TAG, (isBle ? "BLE" : "Classic") + " устройство сохранено: " + name + " [" + address + "] RSSI=" + rssi + " rowId=" + rowId);
    }

    public static String getVendor(String macAddress) {
        if (macAddress == null || macAddress.length() < 8) return "Unknown";
        String prefix = macAddress.substring(0, 8).toUpperCase(); // первые 3 байта
        return OUI_MAP.getOrDefault(prefix, "Unknown");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.d(TAG, "Сервис запущен onStartCommand");

        if (intent != null) {
            // Получение координат из Intent
            if (intent.hasExtra("latitude")) {
                currentLatitude = intent.getDoubleExtra("latitude", 0.0);
                currentLongitude = intent.getDoubleExtra("longitude", 0.0);
                currentAccuracy = intent.getFloatExtra("accuracy", 0.0f);
                if (intent.hasExtra("altitude")) {
                    currentAltitude = intent.getDoubleExtra("altitude", 0.0);
                }
                Log.d(TAG, "Получена геопозиция: " + currentLatitude + ", " + currentLongitude);
            }

            // Получение имени таблицы
            if (intent.hasExtra("tableName")) {
                currentTableName = intent.getStringExtra("tableName");
                Log.d(TAG, "Имя таблицы установлено: " + currentTableName);
            }

            String action = intent.getAction();
            if ("START_SCAN".equals(action)) {
                startForegroundScanning();
            } else if ("STOP_SCAN".equals(action)) {
                stopForegroundScanning();
            }
        }

        return START_STICKY;
    }

    private void startForegroundScanning() {
        if (isScanning) return;

        if (!checkPermissions()) {
            Log.w(TAG, "Нет нужных разрешений, остановка сервиса");
            stopSelf();
            return;
        }

        if (btAdapter == null || !btAdapter.isEnabled()) {
            Log.w(TAG, "Bluetooth адаптер недоступен или отключен");
            stopSelf();
            return;
        }

        // Создание уведомления и запуск foreground
        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);

        isScanning = true;
        seenAddresses.clear();
        startPeriodicScanning();

        Log.d(TAG, "Фоновое Bluetooth сканирование успешно запущено");
    }

    private void stopForegroundScanning() {
        isScanning = false;

        if (handler != null && scanRunnable != null) {
            handler.removeCallbacks(scanRunnable);
        }

        stopForeground(true);
        stopSelf();

        Log.d(TAG, "Фоновое Bluetooth сканирование остановлен");
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Нет разрешений (BLUETOOTH_SCAN/CONNECT)");
                return false;
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Location permission not granted (needed for BT discovery on older Android)");
                return false;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted");
                return false;
            }
        }

        return true;
    }

    private void startPeriodicScanning() {
        scanRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isScanning) return;

                updateScannerSettings();
                startAllScanning();

                handler.postDelayed(() -> {
                    stopAllScanning();
                    if (isScanning) handler.postDelayed(scanRunnable, scanInterval);
                }, scanDuration);
            }
        };

        if (handler != null) {
            handler.post(scanRunnable);
        }
    }

    private void startAllScanning() {
        // BLE
        if (bleScanner != null) {
            try {
                android.bluetooth.le.ScanSettings settings = new android.bluetooth.le.ScanSettings.Builder()
                        .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                        bleScanner.startScan(null, settings, bleScanCallback);
                        Log.d(TAG, "BLE-сканирование запущено");
                    } else {
                        Log.w(TAG, "Нет разрешения BLUETOOTH_SCAN - BLE-сканирование невозможно");
                    }
                } else {
                    Log.d(TAG, "BLE-сканирование запущено (Android < S)");
                    bleScanner.startScan(bleScanCallback);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Ошибка запуска BLE-сканирования: " + e.getMessage());
            }
        }

        // Классическое сканирование
        if (btAdapter != null && btAdapter.isEnabled()) {
            try {
                IntentFilter filter = new IntentFilter();
                filter.addAction(BluetoothDevice.ACTION_FOUND);
                filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
                filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

                try {
                    registerReceiver(classicReceiver, filter);
                } catch (IllegalArgumentException ignored) {}

                if (btAdapter.isDiscovering()) btAdapter.cancelDiscovery();

                boolean started = btAdapter.startDiscovery();
                Log.d(TAG, "Классическое сканирование запущено: " + started);
            } catch (SecurityException e) {
                Log.e(TAG, "Ошибка запуска классического сканирования: " + e.getMessage());
            }
        }
    }

    private void stopAllScanning() {
        // BLE
        if (bleScanner != null) {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                        || ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bleScanner.stopScan(bleScanCallback);
                    Log.d(TAG, "BLE-сканирование остановлено");
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка остановки BLE-сканирования: " + e.getMessage());
            }
        }

        // Classic
        if (btAdapter != null) {
            try {
                if (btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
                try {
                    unregisterReceiver(classicReceiver);
                } catch (IllegalArgumentException ignored) {}
                Log.d(TAG, "Классическое сканирование остановлено");
            } catch (Exception e) {
                Log.e(TAG, "Ошибка остановки классического сканирования: " + e.getMessage());
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Bluetooth Scanner")
                .setContentText("Сканирование Bluetooth-устройств...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification(int savedDevices, int totalDevices) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Bluetooth Scanner")
                    .setContentText("Найдено: " + totalDevices + " устройств, сохранено: " + savedDevices)
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
                    "Bluetooth Scanner Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Уведомления о сканировании Bluetooth устройств");
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
        isScanning = false;

        try {
            stopAllScanning();
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при остановке сканирования в onDestroy: " + e.getMessage());
        }

        if (classicReceiver != null) {
            try {
                unregisterReceiver(classicReceiver);
                Log.d(TAG, "Ресивер сканирования Bluetooth отключен");
            } catch (Exception e) {
                Log.e(TAG, "Ошибка при отключении ресивера: " + e.getMessage());
            }
        }

        if (handler != null && scanRunnable != null) {
            handler.removeCallbacks(scanRunnable);
        }

        if (databaseHelper != null) {
            databaseHelper.close();
        }

        Log.d(TAG, "Очистка ресурсов сервиса завершена");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
