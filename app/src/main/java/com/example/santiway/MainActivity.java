package com.example.santiway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.santiway.bluetooth_scanner.BluetoothForegroundService;
import com.example.santiway.cell_scanner.CellForegroundService;
import com.example.santiway.upload_data.ApiConfig;
import com.example.santiway.upload_data.ApiDevice;
import com.example.santiway.upload_data.DeviceUploadManager;
import com.example.santiway.upload_data.DeviceUploadService;
import com.example.santiway.upload_data.DeviceUploadWorker;
import com.example.santiway.upload_data.MainDatabaseHelper;
import com.example.santiway.wifi_scanner.WifiForegroundService;
import com.example.santiway.gsm_protocol.LocationManager;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private BroadcastReceiver folderSwitchedReceiver;
    private DrawerLayout drawerLayout;
    private Toolbar toolbar;
    private android.widget.Button scanButton;
    private android.widget.TextView wifiStatusTextView;
    private android.widget.TextView bluetoothStatusTextView;
    private android.widget.TextView cellularStatusTextView;
    private android.widget.TextView coordinatesTextView;
    private MainDatabaseHelper databaseHelper;
    private LocationManager locationManager;
    private boolean isScanning = false;
    private String currentScanFolder = "unified_data";
    private DeviceUploadManager uploadManager;

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
                            initializeLocationManager();
                            checkAllFunctionalityAndWarn();
                        } else {
                            Toast.makeText(MainActivity.this, "Permissions denied", Toast.LENGTH_SHORT).show();
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
        scanButton = findViewById(R.id.scan_button);
        wifiStatusTextView = findViewById(R.id.wifi_status);
        bluetoothStatusTextView = findViewById(R.id.bluetooth_status);
        cellularStatusTextView = findViewById(R.id.cellular_status);
        coordinatesTextView = findViewById(R.id.coordinates_text);

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_cloud);
        navigationView.setNavigationItemSelectedListener(this);

        registerFolderSwitchedReceiver();

        databaseHelper = new MainDatabaseHelper(this);

        checkAndRequestPermissions();

        scanButton.setOnClickListener(v -> {
            if (checkAllPermissions()) {
                toggleScanState();
            } else {
                requestNecessaryPermissions();
            }
        });

        // Инициализация конфигурации API
        ApiConfig.initialize(this);
        uploadManager = new DeviceUploadManager(this);
        startUploadService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (locationManager != null && checkAllPermissions()) {
            locationManager.startLocationUpdates();
        }
        // Проверяем функционалы при каждом возобновлении активности
        checkAllFunctionalityAndWarn();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Проверяем функционалы при запуске приложения
        checkAllFunctionalityAndWarn();
    }

    private void checkAllFunctionalityAndWarn() {
        List<String> missingFunctionality = new ArrayList<>();
        StringBuilder warningMessage = new StringBuilder();

        // Проверка геолокации
        checkLocationEnabled();
        if (!isLocationEnabled) {
            missingFunctionality.add("Геолокация");
            warningMessage.append("• Геолокация отключена\n");
        } else {
            if (!isGpsProviderEnabled) {
                missingFunctionality.add("GPS-провайдер");
                warningMessage.append("• GPS не доступен\n");
            }
            if (!isNetworkProviderEnabled) {
                missingFunctionality.add("Сетевой провайдер");
                warningMessage.append("• Сетевой провайдер не доступен\n");
            }
        }

        // Проверка Wi-Fi
        checkWifiEnabled();
        if (!isWifiEnabled) {
            missingFunctionality.add("Wi-Fi");
            warningMessage.append("• Wi-Fi отключен\n");
        }

        // Проверка Bluetooth
        checkBluetoothEnabled();
        if (!isBluetoothEnabled) {
            missingFunctionality.add("Bluetooth");
            warningMessage.append("• Bluetooth отключен\n");
        }

        // Проверка общего подключения к сети (Wi-Fi или мобильный интернет)
        checkNetworkAvailable();
        if (!isNetworkAvailable) {
            warningMessage.append("• Отсутствует подключение к интернету\n");
        }

        // Проверка разрешений
        if (!checkAllPermissions()) {
            warningMessage.append("• Отсутствуют необходимые разрешения\n");
        }

        // Если есть проблемы, показываем предупреждение
        if (!missingFunctionality.isEmpty() || warningMessage.length() > 0) {
            showFunctionalityWarning(missingFunctionality, warningMessage.toString());
        }
    }

    private void checkLocationEnabled() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                android.location.LocationManager lm = (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
                isLocationEnabled = lm.isLocationEnabled();

                // Проверяем доступность провайдеров
                isGpsProviderEnabled = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
                isNetworkProviderEnabled = lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
            } else {
                // Для старых версий Android
                int locationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);
                isLocationEnabled = (locationMode != Settings.Secure.LOCATION_MODE_OFF);

                android.location.LocationManager lm = (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
                isGpsProviderEnabled = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
                isNetworkProviderEnabled = lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error checking location: " + e.getMessage());
            isLocationEnabled = false;
            isGpsProviderEnabled = false;
            isNetworkProviderEnabled = false;
        }
    }

    private void checkWifiEnabled() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            isWifiEnabled = wifiManager != null && wifiManager.isWifiEnabled();
        } catch (Exception e) {
            Log.e("MainActivity", "Error checking Wi-Fi: " + e.getMessage());
            isWifiEnabled = false;
        }
    }

    private void checkBluetoothEnabled() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.bluetooth.BluetoothManager bluetoothManager =
                        (android.bluetooth.BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                isBluetoothEnabled = bluetoothManager != null && bluetoothManager.getAdapter() != null
                        && bluetoothManager.getAdapter().isEnabled();
            } else {
                android.bluetooth.BluetoothAdapter bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                isBluetoothEnabled = bluetoothAdapter != null && bluetoothAdapter.isEnabled();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error checking Bluetooth: " + e.getMessage());
            isBluetoothEnabled = false;
        }
    }

    private void checkNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                isNetworkAvailable = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            } else {
                isNetworkAvailable = false;
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error checking network: " + e.getMessage());
            isNetworkAvailable = false;
        }
    }

    private void showFunctionalityWarning(List<String> missingItems, String details) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Внимание! Функционал ограничен");

        StringBuilder message = new StringBuilder();
        message.append("Для стабильной работы приложения необходимо:\n\n");

        if (!missingItems.isEmpty()) {
            message.append("Включить:\n");
            for (String item : missingItems) {
                message.append("• ").append(item).append("\n");
            }
            message.append("\n");
        }

        message.append(details);
        message.append("\n\nБез этих функций приложение может работать нестабильно или некорректно!");

        builder.setMessage(message.toString());
        builder.setPositiveButton("Понятно", null);

        // Добавляем кнопки для быстрого перехода к настройкам
        builder.setNeutralButton("Настройки", (dialog, which) -> {
            openSettings();
        });

        builder.setCancelable(false);
        builder.show();
    }

    private void showMissingPermissionsWarning() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Отсутствуют разрешения");
        builder.setMessage("Для работы приложения необходимы все запрошенные разрешения. " +
                "Без них сканирование и геолокация будут работать некорректно!");
        builder.setPositiveButton("Запросить снова", (dialog, which) -> {
            requestNecessaryPermissions();
        });
        builder.setNegativeButton("Позже", null);
        builder.setCancelable(false);
        builder.show();
    }

    private void openSettings() {
        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Не удается открыть настройки", Toast.LENGTH_SHORT).show();
        }
    }

    private void registerFolderSwitchedReceiver() {
        folderSwitchedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.example.santiway.FOLDER_SWITCHED".equals(intent.getAction())) {
                    String newTableName = intent.getStringExtra("newTableName");
                    if (newTableName != null && !newTableName.isEmpty()) {
                        currentScanFolder = newTableName;
                        runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                "Папка сканирования переключена на: " + newTableName, Toast.LENGTH_LONG).show());
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter("com.example.santiway.FOLDER_SWITCHED");

        // Добавить проверку версии Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Для Android 13+ нужно указать флаг экспорта
            registerReceiver(folderSwitchedReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            // Для старых версий Android
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

        // Получаем текущие координаты сканирующего устройства
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

        // Запускаем периодическую отправку данных
        schedulePeriodicUpload();

        Toast.makeText(this, "Сканирование запущено: " + currentScanFolder, Toast.LENGTH_SHORT).show();
    }

    private void stopScanning() {
        isScanning = false;
        updateScanStatusUI(false);

        stopScannerService(WifiForegroundService.class);
        stopScannerService(CellForegroundService.class);
        stopScannerService(BluetoothForegroundService.class);

        // Отправляем оставшиеся данные перед остановкой
        uploadRemainingData();

        Toast.makeText(this, "Сканирование остановлено", Toast.LENGTH_SHORT).show();
    }

    private void schedulePeriodicUpload() {
        // Запускаем WorkManager для периодической отправки
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest uploadWork = new PeriodicWorkRequest.Builder(
                DeviceUploadWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "PeriodicUpload",
                ExistingPeriodicWorkPolicy.KEEP,
                uploadWork);
    }

    private void uploadRemainingData() {
        new Thread(() -> {
            DeviceUploadManager uploadManager = new DeviceUploadManager(this);
            List<ApiDevice> remainingDevices = uploadManager.getPendingDevicesBatch();
            if (!remainingDevices.isEmpty()) {
                boolean success = uploadManager.uploadBatch(remainingDevices);
                runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(this, "Отправлено " + remainingDevices.size() + " устройств", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            uploadManager.cleanup();
        }).start();
    }

    private void updateScanStatusUI(boolean scanning) {
        int textRes = scanning ? R.string.scanning_status : R.string.stopped_status;
        scanButton.setText(scanning ? R.string.button_stop : R.string.button_start);
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
        unregisterReceiver(folderSwitchedReceiver);
    }

    private boolean checkAllPermissions() {
        boolean hasLocationPermission = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasPhoneStatePermission = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;

        boolean hasNotificationPermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }

        boolean hasBluetoothScanPermission = true;
        boolean hasBluetoothConnectPermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasBluetoothScanPermission = ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
            hasBluetoothConnectPermission = ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }

        return hasLocationPermission && hasPhoneStatePermission && hasNotificationPermission
                && hasBluetoothScanPermission && hasBluetoothConnectPermission;
    }

    private void checkAndRequestPermissions() {
        if (!checkAllPermissions()) {
            requestNecessaryPermissions();
        } else {
            initializeLocationManager();
            checkAllFunctionalityAndWarn();
        }
    }

    private void requestNecessaryPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        permissionsToRequest.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        permissionsToRequest.add(android.Manifest.permission.READ_PHONE_STATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_SCAN);
            permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_CONNECT);
        }

        requestPermissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_open_folder) {
            showFolderSelectionDialog();
        } else if (id == R.id.nav_create_folder) {
            showCreateFolderDialog();
        } else if (id == R.id.nav_delete_folder) {
            showFolderManagementDialog();
        } else if (id == R.id.nav_clear_status) {
            viewAppConfig();
        } else if (id == R.id.nav_view_database) {
            openDeviceListActivity();
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
        final CharSequence[] items = folders.toArray(new CharSequence[0]);

        new AlertDialog.Builder(this)
                .setTitle("Выберите папку для сканирования")
                .setItems(items, (dialog, which) -> {
                    String selectedFolder = items[which].toString();
                    stopScanning();
                    currentScanFolder = selectedFolder;
                    startScanning();
                    Toast.makeText(this, "Выбрана папка: " + currentScanFolder, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showFolderManagementDialog() {
        List<String> folders = databaseHelper.getAllTables();
        List<String> deletableFolders = new ArrayList<>();
        for (String folder : folders) {
            if (!folder.isEmpty()) {
                deletableFolders.add(folder);
            }
        }

        if (deletableFolders.isEmpty()) {
            Toast.makeText(this, "Нет папок для удаления", Toast.LENGTH_SHORT).show();
            return;
        }

        final CharSequence[] items = deletableFolders.toArray(new CharSequence[0]);

        new AlertDialog.Builder(this)
                .setTitle("Удаление папок")
                .setItems(items, (dialog, which) -> showDeleteConfirmationDialog(items[which].toString()))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showDeleteConfirmationDialog(String folderName) {
        new AlertDialog.Builder(this)
                .setTitle("Удаление папки")
                .setMessage("Вы уверены, что хотите удалить папку '" + folderName + "'? Все данные будут потеряны!")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    boolean success = databaseHelper.deleteTable(folderName);
                    if (success) {
                        Toast.makeText(this, "Папка удалена: " + folderName, Toast.LENGTH_SHORT).show();
                        if (currentScanFolder.equals(folderName)) {
                            stopScanning();
                            currentScanFolder = "unified_data";
                            startScanning();
                        }
                    } else {
                        Toast.makeText(this, "Ошибка при удалении папки", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showCreateFolderDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Создать новую папку");
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Введите название папки");
        builder.setView(input);

        builder.setPositiveButton("Создать", (dialog, which) -> {
            String folderName = input.getText().toString().trim();
            if (folderName.isEmpty()) {
                Toast.makeText(this, "Имя папки не может быть пустым", Toast.LENGTH_SHORT).show();
                return;
            }
            if (folderName.equals("unified_data")) {
                Toast.makeText(this, "Имя папки недоступно", Toast.LENGTH_SHORT).show();
                return;
            }
            databaseHelper.createTableIfNotExists(folderName);
            currentScanFolder = folderName;
            Toast.makeText(this, "Создана папка: " + folderName, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void startUploadService() {
        Intent serviceIntent = new Intent(this, DeviceUploadService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
}