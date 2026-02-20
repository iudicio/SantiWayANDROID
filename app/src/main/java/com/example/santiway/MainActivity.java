package com.example.santiway;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.santiway.bluetooth_scanner.BluetoothForegroundService;
import com.example.santiway.cell_scanner.CellForegroundService;
import com.example.santiway.upload_data.ApiConfig;
import com.example.santiway.upload_data.DeviceUploadManager;
import com.example.santiway.upload_data.DeviceUploadService;
import com.example.santiway.upload_data.MainDatabaseHelper;
//import com.example.santiway.upload_data.UniqueDeviceUploadWorker;
import com.example.santiway.upload_data.UniqueDevicesHelper;
import com.example.santiway.websocket.ApkAssembler;
import com.example.santiway.websocket.WebSocketNotificationClient;
import com.example.santiway.websocket.WebSocketService;
import com.example.santiway.wifi_scanner.WifiForegroundService;
import com.example.santiway.gsm_protocol.LocationManager;
import com.google.android.material.navigation.NavigationView;
import com.example.santiway.FolderDeletionBottomSheet.FolderDeletionListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, CreateFolderDialogFragment.CreateFolderListener, FolderDeletionListener {
    private TextView timeLabelTextView;
    private static final String TAG = "MainActivity";
    private BroadcastReceiver folderSwitchedReceiver;
    private DrawerLayout drawerLayout;
    private Toolbar toolbar;

    private ImageButton playPauseButton;
    private TextView wifiStatusTextView;
    private TextView bluetoothStatusTextView;
    private TextView cellularStatusTextView;
    private TextView coordinatesTextView;
    private TextView toolbarFolderTitleTextView;

    private MainDatabaseHelper databaseHelper;
    private LocationManager locationManager;
    private boolean isScanning = false;
    private String currentScanFolder = "unified_data";
    private DeviceUploadManager uploadManager;

    private Handler timerHandler = new Handler();
    private long startTime = 0L;
    private long timeInMilliseconds = 0L;
    private TextView lastUploadDateTextView;
    private WebSocketService webSocketService;
    private boolean isWebSocketBound = false;
    private ApkAssembler apkAssembler;
    private BroadcastReceiver webSocketReceiver;

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            timeInMilliseconds = System.currentTimeMillis() - startTime;
            int seconds = (int) (timeInMilliseconds / 1000);
            int minutes = seconds / 60;
            int hours = minutes / 60;
            seconds = seconds % 60;
            minutes = minutes % 60;

            String timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            timeLabelTextView.setText("Время работы : " + timeString);
            timerHandler.postDelayed(this, 1000);
        }
    };

    // Флаги для проверки функционалов
    private boolean isLocationEnabled = false;
    private boolean isWifiEnabled = false;
    private boolean isBluetoothEnabled = false;
    private boolean isNetworkAvailable = false;
    private boolean isGpsProviderEnabled = false;
    private boolean isNetworkProviderEnabled = false;

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    permissions -> {
                        boolean allGranted = true;
                        for (Boolean granted : permissions.values()) {
                            if (!granted) {
                                allGranted = false;
                                break;
                            }
                        }

                        if (allGranted) {
                            // Разрешения получены
                            initializeLocationManager();
                            checkAllFunctionalityAndWarn();

                            // Запрашиваем дополнительные разрешения, если нужно
                            requestOptionalPermissions();
                        } else {
                            // Не все разрешения даны
                            Toast.makeText(MainActivity.this,
                                    "Некоторые разрешения не предоставлены",
                                    Toast.LENGTH_SHORT).show();
                            showMissingPermissionsWarning();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main_new);

        drawerLayout = findViewById(R.id.drawer_layout);
        toolbar = findViewById(R.id.toolbar);
        NavigationView navigationView = findViewById(R.id.nav_view);

        playPauseButton = findViewById(R.id.play_pause_button);
        toolbarFolderTitleTextView = findViewById(R.id.toolbar_folder_title);
        wifiStatusTextView = findViewById(R.id.wifi_status);
        bluetoothStatusTextView = findViewById(R.id.bluetooth_status);
        cellularStatusTextView = findViewById(R.id.cellular_status);
        coordinatesTextView = findViewById(R.id.coordinates_text);
        timeLabelTextView = findViewById(R.id.time_label);
        lastUploadDateTextView = findViewById(R.id.last_upload_date);

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_apps);
        navigationView.setNavigationItemSelectedListener(this);

        updateToolbarTitle(currentScanFolder);
        toolbarFolderTitleTextView.setOnClickListener(v -> showFolderSelectionDialog());

        registerFolderSwitchedReceiver();

        databaseHelper = new MainDatabaseHelper(this);
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        try {
            UniqueDevicesHelper uniqueHelper = new UniqueDevicesHelper(this);
            // Передаем открытое соединение для создания таблицы
            uniqueHelper.addOrUpdateDevice(db, new ContentValues()); // Это создаст таблицу если её нет
        } catch (Exception e) {
            Log.e("MainActivity", "Error initializing unique devices: " + e.getMessage());
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }

        checkAndRequestPermissionsStepByStep();

        playPauseButton.setOnClickListener(v -> {
            if (checkEssentialPermissions()) {
                toggleScanState();
                requestOptionalPermissions();
            } else {
                showInitialPermissionsExplanation();
            }
        });

        findViewById(R.id.footer_device).setOnClickListener(v -> openDeviceListActivity());
        findViewById(R.id.footer_create).setOnClickListener(v -> showCreateFolderDialog());
        findViewById(R.id.footer_delete).setOnClickListener(v -> showFolderManagementDialog());

        ApiConfig.initialize(this);
        uploadManager = new DeviceUploadManager(this);
        startUploadService();
        updateLastUploadDateDisplay();
        registerUploadUpdateReceiver();
        //apkAssembler = new ApkAssembler(this);
        //registerWebSocketReceivers();
        //startWebSocketService();
        cleanupOldDataOnStart();


        LinearLayout notificationsButton = findViewById(R.id.footer_notifications);
        if (notificationsButton != null) {
            notificationsButton.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });
        } else {
            Log.e("MainActivity", "Notifications button (footer_notifications) not found.");
        }
    }



    private void updateToolbarTitle(String folderName) {
        if (toolbarFolderTitleTextView != null) {
            toolbarFolderTitleTextView.setText(folderName);
        }
    }


    @Override
    public void onFolderCreated(String folderName) {
        if (folderName.equals("unified_data")) {
            Toast.makeText(this, "Имя папки недоступно", Toast.LENGTH_SHORT).show();
            return;
        }

        databaseHelper.createTableIfNotExists(folderName);
        currentScanFolder = folderName;
        updateToolbarTitle(currentScanFolder);
        Toast.makeText(this, "Создана папка: " + folderName, Toast.LENGTH_SHORT).show();
    }

    private void registerFolderSwitchedReceiver() {
        folderSwitchedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.example.santiway.FOLDER_SWITCHED".equals(intent.getAction())) {
                    String newTableName = intent.getStringExtra("newTableName");
                    if (newTableName != null && !newTableName.isEmpty()) {
                        currentScanFolder = newTableName;
                        updateToolbarTitle(currentScanFolder);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                "Папка сканирования переключена на: " + newTableName, Toast.LENGTH_LONG).show());
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter("com.example.santiway.FOLDER_SWITCHED");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(folderSwitchedReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(folderSwitchedReceiver, filter);
        }
    }

    private void initializeLocationManager() {
        if (locationManager == null) {
            locationManager = new LocationManager(this);
            locationManager.setOnLocationUpdateListener(new LocationManager.OnLocationUpdateListener() {
                @Override
                public void onLocationUpdate(Location location) {
                    updateCoordinatesDisplay(location);
                }

                @Override
                public void onPermissionDenied() {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Location permission denied", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onLocationError(String error) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Location error: " + error, Toast.LENGTH_SHORT).show());
                }
            });
            locationManager.startLocationUpdates();
        }
    }

    private void updateCoordinatesDisplay(Location location) {
        if (location != null) {
            String coords = String.format("Lat: %.6f, Lon: %.6f", location.getLatitude(), location.getLongitude());
            coordinatesTextView.setText(coords);
        } else {
            coordinatesTextView.setText("Координаты недоступны");
        }
    }

    private void toggleScanState() {
        if (isScanning) {
            stopScanning();
        } else {
            startScanning();
        }
    }

    private void startScanning() {
        // Проверяем функционалы перед началом сканирования
        checkAllFunctionalityAndWarn();

        isScanning = true;
        updateScanStatusUI(true);

        double latitude = 0.0;
        double longitude = 0.0;
        double altitude = 0.0;
        float accuracy = 0.0f;

        if (locationManager != null && locationManager.hasValidLocation()) {
            Location currentLocation = locationManager.getCurrentLocation();
            if (currentLocation != null) {
                latitude = currentLocation.getLatitude();
                longitude = currentLocation.getLongitude();
                altitude = currentLocation.hasAltitude() ? currentLocation.getAltitude() : 0.0;
                accuracy = currentLocation.getAccuracy();
                Log.d("Scanning", "Using scanner coordinates: " + latitude + ", " + longitude);
            }
        }

        startScannerService(WifiForegroundService.class, latitude, longitude, altitude, accuracy);
        startScannerService(CellForegroundService.class, latitude, longitude, altitude, accuracy);
        startScannerService(BluetoothForegroundService.class, latitude, longitude, altitude, accuracy);

        timerHandler.removeCallbacks(timerRunnable);
        startTime = System.currentTimeMillis();
        timerHandler.postDelayed(timerRunnable, 0);

        Toast.makeText(this, "Сканирование запущено: " + currentScanFolder, Toast.LENGTH_SHORT).show();
    }

    private void stopScanning() {
        isScanning = false;
        updateScanStatusUI(false);

        stopScannerService(WifiForegroundService.class);
        stopScannerService(CellForegroundService.class);
        stopScannerService(BluetoothForegroundService.class);

        timerHandler.removeCallbacks(timerRunnable);

        Toast.makeText(this, "Сканирование остановлено", Toast.LENGTH_SHORT).show();
    }

    private void updateScanStatusUI(boolean scanning) {
        int textRes = scanning ? R.string.scanning_status : R.string.stopped_status;
        playPauseButton.setImageResource(scanning ? R.drawable.ic_pause : R.drawable.ic_starts);

        wifiStatusTextView.setText(textRes);
        bluetoothStatusTextView.setText(textRes);
        cellularStatusTextView.setText(textRes);
    }

    private void startScannerService(Class<?> serviceClass, double latitude, double longitude, double altitude, float accuracy) {
        Intent intent = new Intent(this, serviceClass);
        intent.setAction("START_SCAN");
        intent.putExtra("tableName", currentScanFolder);
        intent.putExtra("latitude", latitude);
        intent.putExtra("longitude", longitude);
        intent.putExtra("altitude", altitude);
        intent.putExtra("accuracy", accuracy);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopScannerService(Class<?> serviceClass) {
        Intent intent = new Intent(this, serviceClass);
        stopService(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            locationManager.cleanup();
        }
        stopScanning();
        if (folderSwitchedReceiver != null) {
            unregisterReceiver(folderSwitchedReceiver);
        }
        timerHandler.removeCallbacks(timerRunnable);
        if (isWebSocketBound) {
            unbindService(webSocketConnection);
        }
        if (webSocketReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(webSocketReceiver);
        }
    }

    // ВАЖНО: МЕТОДЫ ПРОВЕРКИ ФУНКЦИОНАЛОВ И ПРЕДУПРЕЖДЕНИЙ

    private void checkAllFunctionalityAndWarn() {
        checkNetworkAvailability();
        checkLocationProviders();
        checkWifiState();
        checkBluetoothState();

        if (!isNetworkAvailable || !isLocationEnabled || !isWifiEnabled || !isBluetoothEnabled) {
            showMissingFunctionalityWarning();
        }
    }

    private void showMissingPermissionsWarning() {
        new AlertDialog.Builder(this)
                .setTitle("Недостаточно разрешений")
                .setMessage("Для корректной работы приложения необходимы все запрошенные разрешения. " +
                        "Без них сканирование и определение местоположения будут невозможны.")
                .setPositiveButton("Повторно запросить", (dialog, which) -> requestNecessaryPermissions())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showMissingFunctionalityWarning() {
        StringBuilder warningMessage = new StringBuilder();
        warningMessage.append("Включите следующие функции для корректной работы приложения:\n\n");

        if (!isNetworkAvailable) {
            warningMessage.append("• Мобильный интернет или Wi-Fi (для отправки данных)\n");
        }
        if (!isLocationEnabled) {
            warningMessage.append("• Геолокация (GPS и сетевое определение местоположения)\n");
        }
        if (!isWifiEnabled) {
            warningMessage.append("• Wi-Fi (для сканирования Wi-Fi сетей)\n");
        }
        if (!isBluetoothEnabled) {
            warningMessage.append("• Bluetooth (для сканирования Bluetooth устройств)\n");
        }

        warningMessage.append("\nБез этих функций данные могут собираться неполно или некорректно.");

        new AlertDialog.Builder(this)
                .setTitle("Внимание: ограниченная функциональность")
                .setMessage(warningMessage.toString())
                .setPositiveButton("Перейти к настройкам", (dialog, which) -> openSettings())
                .setNegativeButton("Продолжить", null)
                .show();
    }

    private void checkNetworkAvailability() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            isNetworkAvailable = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        } else {
            isNetworkAvailable = false;
        }
    }

    private void checkLocationProviders() {
        android.location.LocationManager locationManager = (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null) {
            isGpsProviderEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
            isNetworkProviderEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
            isLocationEnabled = isGpsProviderEnabled || isNetworkProviderEnabled;
        } else {
            isLocationEnabled = false;
        }
    }

    private void checkWifiState() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            isWifiEnabled = wifiManager.isWifiEnabled();
        } else {
            isWifiEnabled = false;
        }
    }

    private void checkBluetoothState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Для Android 12+
            android.bluetooth.BluetoothAdapter bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            isBluetoothEnabled = bluetoothAdapter != null && bluetoothAdapter.isEnabled();
        } else {
            // Для старых версий
            try {
                android.bluetooth.BluetoothAdapter bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                isBluetoothEnabled = bluetoothAdapter != null && bluetoothAdapter.isEnabled();
            } catch (Exception e) {
                isBluetoothEnabled = false;
                Log.e("MainActivity", "Error checking Bluetooth state: " + e.getMessage());
            }
        }
    }

    private void openSettings() {
        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, "Не удается открыть настройки", Toast.LENGTH_SHORT).show();
        }
    }

    private void showInitialPermissionsExplanation() {
        new AlertDialog.Builder(this)
                .setTitle("Необходимые разрешения")
                .setMessage("Для сканирования Wi-Fi и Bluetooth устройств приложению нужны:\n\n" +
                        "• Доступ к местоположению (для обнаружения устройств)\n" +
                        "• Разрешение на сканирование Bluetooth (Android 12+)\n\n" +
                        "Эти разрешения необходимы для основной работы приложения.")
                .setPositiveButton("Запросить", (dialog, which) -> requestEssentialPermissions())
                .setNegativeButton("Позже", (dialog, which) -> {
                    // Показываем предупреждение о ограниченной функциональности
                    showMissingPermissionsWarning();
                })
                .setCancelable(false)
                .show();
    }

    private void requestNecessaryPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        // Основное разрешение
        permissionsToRequest.add(android.Manifest.permission.ACCESS_FINE_LOCATION);

        // Bluetooth (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_SCAN);
            permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_CONNECT);
        }

        // Уведомления (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS);
        }

        // READ_PHONE_STATE запрашивайте отдельно, только если действительно нужно
        // permissionsToRequest.add(android.Manifest.permission.READ_PHONE_STATE);

        if (!permissionsToRequest.isEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
        }
    }

    private void checkAndRequestPermissionsStepByStep() {
        // Шаг 1: Проверяем и запрашиваем только самое необходимое
        if (!checkEssentialPermissions()) {
            requestEssentialPermissions();
        } else {
            // Шаг 2: Если основные есть, запрашиваем опциональные
            requestOptionalPermissions();
        }
    }

    private boolean checkEssentialPermissions() {
        // Только локация и Bluetooth
        boolean hasLocation = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        boolean hasBluetooth = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean hasBluetoothScan = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
            boolean hasBluetoothConnect = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            hasBluetooth = hasBluetoothScan && hasBluetoothConnect;
        }

        return hasLocation && hasBluetooth;
    }

    private void requestEssentialPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }

        // ИСПОЛЬЗУЙТЕ СУЩЕСТВУЮЩИЙ requestPermissionLauncher
        requestPermissionLauncher.launch(permissions.toArray(new String[0]));
    }

    private void requestOptionalPermissions() {
        // Уведомления запрашиваем только когда они понадобятся
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

                // Запрашиваем при первом запуске или когда пользователь начинает сканирование
                SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
                if (!prefs.getBoolean("notifications_asked", false)) {
                    new AlertDialog.Builder(this)
                            .setTitle("Уведомления")
                            .setMessage("Разрешить уведомления для получения информации о сканировании?")
                            .setPositiveButton("Разрешить", (d, w) -> {
                                requestPermissionLauncher.launch(
                                        new String[]{Manifest.permission.POST_NOTIFICATIONS}
                                );
                                prefs.edit().putBoolean("notifications_asked", true).apply();
                            })
                            .setNegativeButton("Позже", null)
                            .show();
                }
            }
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_clear_status) {
            viewAppConfig();
        }

        if (id == R.id.nav_make_safe) {
            if (currentScanFolder != null && !currentScanFolder.isEmpty()) {
                int affectedRows = databaseHelper.updateAllDeviceStatusForTable(currentScanFolder, "SAFE");
                Toast.makeText(this, affectedRows + " устройств в '" + currentScanFolder + "' помечены как SAFE.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Текущая папка для сканирования не определена.", Toast.LENGTH_SHORT).show();
            }
        }
        if (id == R.id.nav_clear_triggers) {
            if (currentScanFolder != null && !currentScanFolder.isEmpty()) {
                int affectedRows = databaseHelper.updateAllDeviceStatusForTable(currentScanFolder, "CLEAR");
                Toast.makeText(this, affectedRows + " устройств в '" + currentScanFolder + "' помечены как CLEAR.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Текущая папка для сканирования не определена.", Toast.LENGTH_SHORT).show();
            }
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            drawerLayout.openDrawer(GravityCompat.START);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openDeviceListActivity() {
        startActivity(new Intent(this, DeviceListActivity.class));
    }

    private void viewAppConfig() {
        startActivity(new Intent(this, AppConfigViewActivity.class));
    }

    private List<String> getAllFolders() {
        List<String> tables = databaseHelper.getAllTables();
        if (!tables.contains("unified_data")) {
            tables.add("unified_data");
        }
        return tables;
    }

    private void showFolderSelectionDialog() {
        List<String> folders = getAllFolders();

        FolderSelectionBottomSheet bottomSheet = new FolderSelectionBottomSheet(folders, new FolderSelectionBottomSheet.FolderSelectionListener() {
            @Override
            public void onFolderSelected(String selectedFolder) {
                stopScanning();
                currentScanFolder = selectedFolder;
                updateToolbarTitle(currentScanFolder);
                startScanning();
                Toast.makeText(MainActivity.this, "Выбрана папка: " + currentScanFolder, Toast.LENGTH_SHORT).show();
            }
        });

        bottomSheet.show(getSupportFragmentManager(), "FolderSelectionTag");
    }

    private void showFolderManagementDialog() {
        List<String> folders = databaseHelper.getAllTables();
        List<String> deletableFolders = new ArrayList<>();

        for (String folder : folders) {
            if (!folder.isEmpty() && !folder.equals("unified_data")) {
                deletableFolders.add(folder);
            }
        }

        if (deletableFolders.isEmpty()) {
            Toast.makeText(this, "Нет папок для удаления (кроме 'unified_data')", Toast.LENGTH_SHORT).show();
            return;
        }

        FolderDeletionBottomSheet bottomSheet = new FolderDeletionBottomSheet(
                deletableFolders,
                this
        );

        bottomSheet.show(getSupportFragmentManager(), "FolderDeletionTag");
    }

    @Override
    public void onDeleteRequested(String folderName) {
        boolean success = databaseHelper.deleteTable(folderName);
        if (success) {
            Toast.makeText(this, "Папка удалена: " + folderName, Toast.LENGTH_SHORT).show();

            if (currentScanFolder.equals(folderName)) {
                stopScanning();
                currentScanFolder = "unified_data";
                updateToolbarTitle(currentScanFolder);
                startScanning();
            }
        } else {
            Toast.makeText(this, "Ошибка при удалении папки", Toast.LENGTH_SHORT).show();
        }
    }

    private void showCreateFolderDialog() {
        CreateFolderDialogFragment dialogFragment = new CreateFolderDialogFragment();
        dialogFragment.show(getSupportFragmentManager(), "create_folder_dialog");
    }

    private void startUploadService() {
        Intent serviceIntent = new Intent(this, DeviceUploadService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    // НОВЫЙ МЕТОД: обновление отображения даты последней отправки
    private void updateLastUploadDateDisplay() {
        SharedPreferences prefs = getSharedPreferences("DeviceUploadPrefs", MODE_PRIVATE);
        long lastUploadTime = prefs.getLong("last_upload_time", 0);

        if (lastUploadDateTextView != null) {
            if (lastUploadTime > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd.MM.yyyy", Locale.getDefault());
                String formattedDate = sdf.format(new Date(lastUploadTime));
                lastUploadDateTextView.setText(formattedDate);
            } else {
                lastUploadDateTextView.setText("--:--:-- --.--.----");
            }
        }
    }

    // НОВЫЙ МЕТОД: регистрация BroadcastReceiver для обновлений
    private void registerUploadUpdateReceiver() {
        BroadcastReceiver uploadUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.example.santiway.UPLOAD_COMPLETED".equals(intent.getAction())) {
                    int count = intent.getIntExtra("device_count", 0);
                    long timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis());

                    // Обновляем UI
                    runOnUiThread(() -> {
                        updateLastUploadDateDisplay();
                        Toast.makeText(MainActivity.this,
                                "✅ Отправлено " + count + " устройств",
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }
        };

        IntentFilter filter = new IntentFilter("com.example.santiway.UPLOAD_COMPLETED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(uploadUpdateReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(uploadUpdateReceiver, filter);
        }
    }

    //Очистка данных старее 7 дней
    private void cleanupOldDataOnStart() {
        new Thread(() -> {
            try {
                MainDatabaseHelper helper = new MainDatabaseHelper(this);
                long maxAge = 7 * 24 * 60 * 60 * 1000L; // 7 дней в миллисекундах
                helper.deleteOldRecordsFromAllTables(maxAge);

                Log.d(TAG, "✅ Old data cleaned up on app start");
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning old data: " + e.getMessage());
            }
        }).start();
    }

    private void startWebSocketService() {
        Intent intent = new Intent(this, WebSocketService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Для Android 8+ используем startForegroundService
            startForegroundService(intent);
        } else {
            // Для старых версий просто startService
            startService(intent);
        }
        bindService(intent, webSocketConnection, Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection webSocketConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            WebSocketService.WebSocketBinder binder = (WebSocketService.WebSocketBinder) service;
            webSocketService = binder.getService();
            isWebSocketBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isWebSocketBound = false;
        }
    };

    private void registerWebSocketReceivers() {
        webSocketReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (WebSocketNotificationClient.ACTION_NOTIFICATION_RECEIVED.equals(action)) {
                    String title = intent.getStringExtra("title");
                    String text = intent.getStringExtra("text");
                    String type = intent.getStringExtra("notif_type");

                    // Показываем уведомление
                    showWebSocketNotification(title, text, type);

                } else if (WebSocketNotificationClient.ACTION_CONNECTION_STATUS.equals(action)) {
                    boolean connected = intent.getBooleanExtra(
                            WebSocketNotificationClient.EXTRA_CONNECTION_STATUS, false);
                    updateWebSocketStatus(connected);

                } else if (WebSocketNotificationClient.ACTION_APK_CHUNK_RECEIVED.equals(action)) {
                    String buildId = intent.getStringExtra(WebSocketNotificationClient.EXTRA_BUILD_ID);
                    int chunkIndex = intent.getIntExtra(WebSocketNotificationClient.EXTRA_CHUNK_INDEX, 0);
                    int chunkCount = intent.getIntExtra(WebSocketNotificationClient.EXTRA_CHUNK_COUNT, 0);
                    String filename = intent.getStringExtra(WebSocketNotificationClient.EXTRA_FILENAME);
                    byte[] data = intent.getByteArrayExtra(WebSocketNotificationClient.EXTRA_CHUNK_DATA);

                    if (data != null) {
                        apkAssembler.addChunk(buildId, chunkIndex, chunkCount, filename, data);
                    }

                } else if (WebSocketNotificationClient.ACTION_APK_COMPLETE.equals(action)) {
                    String buildId = intent.getStringExtra(WebSocketNotificationClient.EXTRA_BUILD_ID);
                    String apkPath = intent.getStringExtra(WebSocketNotificationClient.EXTRA_APK_PATH);

                    Toast.makeText(MainActivity.this,
                            "APK получен: " + apkPath, Toast.LENGTH_LONG).show();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(WebSocketNotificationClient.ACTION_NOTIFICATION_RECEIVED);
        filter.addAction(WebSocketNotificationClient.ACTION_CONNECTION_STATUS);
        filter.addAction(WebSocketNotificationClient.ACTION_APK_CHUNK_RECEIVED);
        filter.addAction(WebSocketNotificationClient.ACTION_APK_COMPLETE);

        LocalBroadcastManager.getInstance(this).registerReceiver(webSocketReceiver, filter);
    }

    private void showWebSocketNotification(String title, String text, String type) {
        // Создаем Android уведомление
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "websocket_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void updateWebSocketStatus(boolean connected) {
        // Обновляем UI статус подключения
        // Например, добавить иконку в тулбар
        runOnUiThread(() -> {
            if (connected) {
                Toast.makeText(this, "WebSocket подключен", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "WebSocket отключен", Toast.LENGTH_SHORT).show();
            }
        });
    }

}

