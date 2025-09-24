package com.example.santiway;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.util.Log;
import java.util.Map;

import com.example.santiway.wifi_scanner.DatabaseHelper;
import com.example.santiway.wifi_scanner.WifiForegroundService;
import com.example.santiway.gsm_protocol.LocationManager;
import com.example.santiway.cell_scanner.CellScannerManager;
import com.example.santiway.bluetooth_scanner.BluetoothForegroundService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // UI компоненты
    private Button startScanButton, stopScanButton, viewDatabaseButton;
    private Button viewAppConfigButton, selectFolderButton;
    private Button startCellScanButton, stopCellScanButton, viewCellDatabaseButton;
    private TextView currentFolderTextView, cellScanStatusTextView;

    // Менеджеры и хелперы
    private DatabaseHelper databaseHelper;
    private LocationManager locationManager;
    private CellScannerManager cellScannerManager;

    // Текущие настройки
    private String currentScanFolder = "default_table";
    private String currentCellScanFolder = "default_cell_table";

    // Launcher для запроса разрешений
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    permissions -> handlePermissionResult(permissions));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        initializeViews();
        initializeManagers();
        setupButtonListeners();
        checkAndRequestPermissions();

        setupWindowInsets();
    }

    private void initializeViews() {
        startScanButton = findViewById(R.id.startScanButton);
        stopScanButton = findViewById(R.id.stopScanButton);
        viewDatabaseButton = findViewById(R.id.viewDatabaseButton);
        viewAppConfigButton = findViewById(R.id.viewAppConfigButton);
        selectFolderButton = findViewById(R.id.selectFolderButton);
        startCellScanButton = findViewById(R.id.startCellScanButton);
        stopCellScanButton = findViewById(R.id.stopCellScanButton);
        viewCellDatabaseButton = findViewById(R.id.viewCellDatabaseButton);
        currentFolderTextView = findViewById(R.id.currentFolderTextView);
        cellScanStatusTextView = findViewById(R.id.cellScanStatusTextView);
    }

    private void initializeManagers() {
        databaseHelper = new DatabaseHelper(this);
        cellScannerManager = new CellScannerManager(this);
        updateFolderDisplay();
        updateCellScanStatus();
    }

    private void setupButtonListeners() {
        // Основное сканирование (Wi-Fi + Bluetooth)
        startScanButton.setOnClickListener(v -> checkPermissionsAndStartScanning());
        stopScanButton.setOnClickListener(v -> stopAllScanning());

        // Работа с базой данных
        viewDatabaseButton.setOnClickListener(v -> openDatabaseView(currentScanFolder));
        selectFolderButton.setOnClickListener(v -> showFolderSelectionDialog());

        // Настройки приложения
        viewAppConfigButton.setOnClickListener(v -> viewAppConfig());

        // Сканирование базовых станций
        if (startCellScanButton != null) {
            startCellScanButton.setOnClickListener(v -> checkPermissionsAndStartCellScanning());
        }
        if (stopCellScanButton != null) {
            stopCellScanButton.setOnClickListener(v -> stopCellScanning());
        }
        if (viewCellDatabaseButton != null) {
            viewCellDatabaseButton.setOnClickListener(v -> showCellDatabaseInfo());
        }
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    // === УПРОЩЕННАЯ ОБРАБОТКА РАЗРЕШЕНИЙ ===
    private void checkAndRequestPermissions() {
        if (checkAllPermissions()) {
            initializeLocationManager();
        } else {
            requestNecessaryPermissions();
        }
    }

    private void handlePermissionResult(Map<String, Boolean> permissions) {
        boolean allGranted = permissions.values().stream().allMatch(granted -> granted);

        if (allGranted) {
            initializeLocationManager();
            startWifiScanning();
            startBluetoothScanning();
        } else {
            Toast.makeText(this, "Не все разрешения предоставлены", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean checkAllPermissions() {
        String[] requiredPermissions = getRequiredPermissions();
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private String[] getRequiredPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.READ_PHONE_STATE,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT
            };
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.READ_PHONE_STATE,
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT
            };
        } else {
            return new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.READ_PHONE_STATE
            };
        }
    }

    private void requestNecessaryPermissions() {
        requestPermissionLauncher.launch(getRequiredPermissions());
    }

    // === УПРАВЛЕНИЕ ЛОКАЦИЕЙ ===
    private void initializeLocationManager() {
        locationManager = new LocationManager(this);
        locationManager.setOnLocationUpdateListener(new LocationManager.OnLocationUpdateListener() {
            @Override public void onLocationUpdate(Location location) {
                Log.d("MainActivity", "Location updated: " + location.getLatitude() + ", " + location.getLongitude());
            }
            @Override public void onPermissionDenied() {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Location permission denied", Toast.LENGTH_SHORT).show());
            }
            @Override public void onLocationError(String error) {
                Log.e("MainActivity", "Location error: " + error);
            }
        });
        locationManager.startLocationUpdates();
        locationManager.getLastKnownLocation();
    }

    // === ОСНОВНОЕ СКАНИРОВАНИЕ ===
    private void checkPermissionsAndStartScanning() {
        if (checkAllPermissions()) {
            startWifiScanning();
            startBluetoothScanning();
        } else {
            requestNecessaryPermissions();
        }
    }

    private void startWifiScanning() {
        Location currentLocation = getCurrentLocation();
        Intent serviceIntent = new Intent(this, WifiForegroundService.class);
        serviceIntent.setAction("START_SCAN");
        serviceIntent.putExtra("tableName", currentScanFolder);
        addLocationToIntent(serviceIntent, currentLocation);

        startForegroundServiceCompat(serviceIntent);
        Toast.makeText(this, "Сканирование запущено: " + currentScanFolder, Toast.LENGTH_SHORT).show();
    }

    private void startBluetoothScanning() {
        if (!isBluetoothAvailable()) {
            Toast.makeText(this, "Bluetooth отключен или не поддерживается", Toast.LENGTH_SHORT).show();
            return;
        }

        Location currentLocation = getCurrentLocation();
        Intent btServiceIntent = new Intent(this, BluetoothForegroundService.class);
        btServiceIntent.setAction("START_SCAN");
        btServiceIntent.putExtra("tableName", currentScanFolder);
        addLocationToIntent(btServiceIntent, currentLocation);

        startForegroundServiceCompat(btServiceIntent);
    }

    private void stopAllScanning() {
        stopWifiScanning();
        stopBluetoothScanning();
    }

    private void stopWifiScanning() {
        Intent serviceIntent = new Intent(this, WifiForegroundService.class);
        serviceIntent.setAction("STOP_SCAN");
        startService(serviceIntent);
        Toast.makeText(this, "Сканирование остановлено", Toast.LENGTH_SHORT).show();
    }

    private void stopBluetoothScanning() {
        Intent btServiceIntent = new Intent(this, BluetoothForegroundService.class);
        btServiceIntent.setAction("STOP_SCAN");
        startService(btServiceIntent);
    }

    // === СКАНИРОВАНИЕ БАЗОВЫХ СТАНЦИЙ ===
    private void checkPermissionsAndStartCellScanning() {
        if (checkAllPermissions()) {
            startCellScanning();
        } else {
            requestNecessaryPermissions();
        }
    }

    private void startCellScanning() {
        if (cellScannerManager != null) {
            cellScannerManager.startScanning(currentCellScanFolder);
            Toast.makeText(this, "Сканирование базовых станций запущено", Toast.LENGTH_SHORT).show();
            updateCellScanStatus();
        }
    }

    private void stopCellScanning() {
        if (cellScannerManager != null) {
            cellScannerManager.stopScanning();
            Toast.makeText(this, "Сканирование базовых станций остановлено", Toast.LENGTH_SHORT).show();
            updateCellScanStatus();
        }
    }

    // === УПРАВЛЕНИЕ ПАПКАМИ ===
    private void updateFolderDisplay() {
        currentFolderTextView.setText("Текущая папка: " + currentScanFolder);
    }

    private void updateCellScanStatus() {
        if (cellScanStatusTextView != null && cellScannerManager != null) {
            cellScanStatusTextView.setText("Статус сканирования базовых станций:\n" +
                    cellScannerManager.getScanningStatus());
        }
    }

    private void showFolderSelectionDialog() {
        List<String> tables = databaseHelper.getAllTables();
        List<String> dialogItems = new ArrayList<>();

        dialogItems.add("+ Создать новую папку");
        if (!tables.contains("default_table")) tables.add("default_table");
        dialogItems.addAll(tables);
        dialogItems.add("---");
        dialogItems.add("Управление папками");

        new AlertDialog.Builder(this)
                .setTitle("Выберите папку для сканирования")
                .setItems(dialogItems.toArray(new CharSequence[0]), (dialog, which) ->
                        handleFolderSelection(dialogItems.get(which)))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void handleFolderSelection(String selectedItem) {
        if (selectedItem.equals("+ Создать новую папку")) {
            showCreateFolderDialog();
        } else if (selectedItem.equals("Управление папками")) {
            showFolderManagementDialog();
        } else if (!selectedItem.equals("---")) {
            currentScanFolder = selectedItem;
            updateFolderDisplay();
            Toast.makeText(this, "Выбрана папка: " + currentScanFolder, Toast.LENGTH_SHORT).show();
        }
    }

    private void showCreateFolderDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Введите название папки");

        new AlertDialog.Builder(this)
                .setTitle("Создать новую папку")
                .setView(input)
                .setPositiveButton("Создать", (dialog, which) -> createNewFolder(input.getText().toString().trim()))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void createNewFolder(String folderName) {
        if (folderName.isEmpty()) {
            Toast.makeText(this, "Имя папки не может быть пустым", Toast.LENGTH_SHORT).show();
            return;
        }
        if (folderName.equals("default_table")) {
            Toast.makeText(this, "Имя папки недоступно", Toast.LENGTH_SHORT).show();
            return;
        }

        databaseHelper.createTableIfNotExists(folderName);
        currentScanFolder = folderName;
        updateFolderDisplay();
        Toast.makeText(this, "Создана папка: " + folderName, Toast.LENGTH_SHORT).show();
    }

    // === ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ===
    private Location getCurrentLocation() {
        return locationManager != null ? locationManager.getCurrentLocation() : null;
    }

    private boolean isBluetoothAvailable() {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        return btAdapter != null && btAdapter.isEnabled();
    }

    private void addLocationToIntent(Intent intent, Location location) {
        if (location != null) {
            intent.putExtra("latitude", location.getLatitude());
            intent.putExtra("longitude", location.getLongitude());
            intent.putExtra("accuracy", location.getAccuracy());
            if (location.hasAltitude()) {
                intent.putExtra("altitude", location.getAltitude());
            }
        }
    }

    private void startForegroundServiceCompat(Intent intent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    // === ПРОСМОТР ДАННЫХ ===
    private void openDatabaseView(String tableName) {
        Intent intent = new Intent(this, DatabaseViewActivity.class);
        intent.putExtra("TABLE_NAME", tableName);
        startActivity(intent);
    }

    private void viewAppConfig() {
        startActivity(new Intent(this, AppConfigViewActivity.class));
    }

    private void showCellDatabaseInfo() {
        if (cellScannerManager == null) {
            Toast.makeText(this, "CellScannerManager не инициализирован", Toast.LENGTH_SHORT).show();
            return;
        }

        String info = "=== ИНФОРМАЦИЯ О БАЗОВЫХ СТАНЦИЯХ ===\n\n" +
                "Статус сканирования:\n" + cellScannerManager.getScanningStatus() + "\n\n" +
                "Информация о покрытии:\n" + cellScannerManager.getCoverageInfo() + "\n\n" +
                "Информация о сети:\n" + cellScannerManager.getNetworkInfo() + "\n\n" +
                "Статистика по типам сетей:\n" + cellScannerManager.getTowerStatistics() + "\n\n" +
                "Записей в базе данных: " + cellScannerManager.getTowersCount(currentCellScanFolder) + "\n\n" +
                "=== ТЕКУЩИЕ БАЗОВЫЕ СТАНЦИИ ===\n" + cellScannerManager.getDetailedTowerInfo();

        new AlertDialog.Builder(this)
                .setTitle("Информация о базовых станциях")
                .setMessage(info)
                .setPositiveButton("OK", null)
                .setNeutralButton("Обновить", (dialog, which) -> updateCellScanStatus())
                .show();
    }

    // Управление папками (остальные методы остаются без изменений)
    private void showFolderManagementDialog() {
        // Реализация остается прежней
    }

    // === LIFECYCLE МЕТОДЫ ===
    @Override
    protected void onResume() {
        super.onResume();
        if (locationManager != null && checkAllPermissions()) {
            locationManager.startLocationUpdates();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            locationManager.cleanup();
        }
        stopAllScanning();
        stopCellScanning();
        if (cellScannerManager != null) {
            cellScannerManager.cleanup();
        }
    }
}