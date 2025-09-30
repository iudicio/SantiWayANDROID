package com.example.santiway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
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

import com.example.santiway.bluetooth_scanner.BluetoothForegroundService;
import com.example.santiway.cell_scanner.CellForegroundService;
import com.example.santiway.wifi_scanner.WifiForegroundService;
import com.example.santiway.gsm_protocol.LocationManager;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;

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
                        } else {
                            Toast.makeText(MainActivity.this, "Permissions denied", Toast.LENGTH_SHORT).show();
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
        databaseHelper.deleteOldRecordsFromAllTables(2 * 24 * 60 * 60 * 1000);

        checkAndRequestPermissions();

        scanButton.setOnClickListener(v -> {
            if (checkAllPermissions()) {
                toggleScanState();
            } else {
                requestNecessaryPermissions();
            }
        });
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
        registerReceiver(folderSwitchedReceiver, filter);
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
        isScanning = true;
        updateScanStatusUI(true);

        Location currentLocation = (locationManager != null) ? locationManager.getCurrentLocation() : null;

        double latitude = (currentLocation != null) ? currentLocation.getLatitude() : 0.0;
        double longitude = (currentLocation != null) ? currentLocation.getLongitude() : 0.0;
        double altitude = (currentLocation != null) ? currentLocation.getAltitude() : 0.0;
        float accuracy = (currentLocation != null) ? currentLocation.getAccuracy() : 0.0f;

        startScannerService(WifiForegroundService.class, latitude, longitude, altitude, accuracy);
        startScannerService(CellForegroundService.class, latitude, longitude, altitude, accuracy);
        startScannerService(BluetoothForegroundService.class, latitude, longitude, altitude, accuracy);

        Toast.makeText(this, "Сканирование запущено: " + currentScanFolder, Toast.LENGTH_SHORT).show();
    }

    private void stopScanning() {
        isScanning = false;
        updateScanStatusUI(false);

        stopScannerService(WifiForegroundService.class);
        stopScannerService(CellForegroundService.class);
        stopScannerService(BluetoothForegroundService.class);

        Toast.makeText(this, "Сканирование остановлено", Toast.LENGTH_SHORT).show();
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
}