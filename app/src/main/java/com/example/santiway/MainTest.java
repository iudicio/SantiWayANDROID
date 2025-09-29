package com.example.santiway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
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





public class MainTest extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private BroadcastReceiver folderSwitchedReceiver;
    private DrawerLayout drawerLayout;
    private Toolbar toolbar;
    private Button scanButton;
    private TextView wifiStatusTextView;
    private TextView bluetoothStatusTextView;
    private TextView cellularStatusTextView;
    private TextView coordinatesTextView;
    private MainDatabaseHelper databaseHelper;
    private LocationManager locationManager;
    private boolean isScanning = false;
    private String currentScanFolder = "unified_data";

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    permissions -> {
                        boolean allGranted = true;
                        for (Boolean isGranted : permissions.values()) {
                            if (!isGranted) {
                                allGranted = false;
                                break;
                            }
                        }
                        if (allGranted) {
                            initializeLocationManager();
                        } else {
                            Toast.makeText(MainTest.this, "Permissions denied", Toast.LENGTH_SHORT).show();
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
        checkAndRequestLocationPermissions();
        databaseHelper.deleteOldRecordsFromAllTables(60 * 1000);

        scanButton.setOnClickListener(v -> {
            if (checkAllPermissions()) {
                toggleScanState();
            } else {
                requestNecessaryPermissions();
            }
        });
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
                    runOnUiThread(() -> Toast.makeText(MainTest.this, "Location permission denied", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onLocationError(String error) {
                    runOnUiThread(() -> Toast.makeText(MainTest.this, "Location error: " + error, Toast.LENGTH_SHORT).show());
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
        scanButton.setText(R.string.button_stop);
        wifiStatusTextView.setText(R.string.scanning_status);
        bluetoothStatusTextView.setText(R.string.scanning_status);
        cellularStatusTextView.setText(R.string.scanning_status);

        Location currentLocation = null;
        if (locationManager != null) {
            currentLocation = locationManager.getCurrentLocation();
        }

        double latitude = (currentLocation != null) ? currentLocation.getLatitude() : 0.0;
        double longitude = (currentLocation != null) ? currentLocation.getLongitude() : 0.0;
        double altitude = (currentLocation != null) ? currentLocation.getAltitude() : 0.0;
        float accuracy = (currentLocation != null) ? currentLocation.getAccuracy() : 0.0f;

        // Запуск Wi-Fi сканера
        Intent wifiServiceIntent = new Intent(this, WifiForegroundService.class);
        wifiServiceIntent.setAction("START_SCAN");
        wifiServiceIntent.putExtra("tableName", currentScanFolder);
        wifiServiceIntent.putExtra("latitude", latitude);
        wifiServiceIntent.putExtra("longitude", longitude);
        wifiServiceIntent.putExtra("altitude", altitude);
        wifiServiceIntent.putExtra("accuracy", accuracy);

        // Запуск Cell сканера
        Intent cellServiceIntent = new Intent(this, CellForegroundService.class);
        cellServiceIntent.setAction("START_SCAN");
        cellServiceIntent.putExtra("tableName", currentScanFolder);
        cellServiceIntent.putExtra("latitude", latitude);
        cellServiceIntent.putExtra("longitude", longitude);
        cellServiceIntent.putExtra("altitude", altitude);
        cellServiceIntent.putExtra("accuracy", accuracy);

        Intent bluetoothServiceIntent = new Intent(this, BluetoothForegroundService.class);
        bluetoothServiceIntent.setAction("START_SCAN");
        bluetoothServiceIntent.putExtra("tableName", currentScanFolder);
        bluetoothServiceIntent.putExtra("latitude", latitude);
        bluetoothServiceIntent.putExtra("longitude", longitude);
        bluetoothServiceIntent.putExtra("altitude", altitude);
        bluetoothServiceIntent.putExtra("accuracy", accuracy);


        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(wifiServiceIntent);
            startForegroundService(cellServiceIntent);
            startForegroundService(bluetoothServiceIntent);
        } else {
            startService(wifiServiceIntent);
            startService(cellServiceIntent);
            startService(bluetoothServiceIntent);
        }
        Toast.makeText(this, "Сканирование запущено: " + currentScanFolder, Toast.LENGTH_SHORT).show();
    }

    private void stopScanning() {
        isScanning = false;
        scanButton.setText(R.string.button_start);
        wifiStatusTextView.setText(R.string.stopped_status);
        bluetoothStatusTextView.setText(R.string.stopped_status);
        cellularStatusTextView.setText(R.string.stopped_status);

        // Остановка Wi-Fi сканера
        Intent wifiServiceIntent = new Intent(this, WifiForegroundService.class);
        stopService(wifiServiceIntent);

        // Остановка Cell сканера
        Intent cellServiceIntent = new Intent(this, CellForegroundService.class);
        stopService(cellServiceIntent);

        Intent blueServiceIntent = new Intent(this, BluetoothForegroundService.class);
        stopService(blueServiceIntent);

        Toast.makeText(this, "Сканирование остановлено", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (locationManager != null && checkAllPermissions()) {
            locationManager.startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
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
    private void registerFolderSwitchedReceiver() {
        folderSwitchedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.example.santiway.FOLDER_SWITCHED".equals(intent.getAction())) {
                    String newTableName = intent.getStringExtra("newTableName");
                    currentScanFolder = newTableName;
                    runOnUiThread(() -> {
                        Toast.makeText(MainTest.this, "Папка сканирования удалена. Переключено на: " + newTableName, Toast.LENGTH_LONG).show();
                    });
                }
            }
        };
        IntentFilter filter = new IntentFilter("com.example.santiway.FOLDER_SWITCHED");
    }
    private boolean checkAllPermissions() {
        boolean hasLocationPermission = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasPhoneStatePermission = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
        boolean hasNotificationPermission = true;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        boolean hasBluetoothScanPermission = true;
        boolean hasBluetoothConnectPermission = true;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            hasBluetoothScanPermission = ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
            hasBluetoothConnectPermission = ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }

        return hasLocationPermission && hasPhoneStatePermission && hasNotificationPermission
                && hasBluetoothScanPermission && hasBluetoothConnectPermission;
    }

    private void checkAndRequestLocationPermissions() {
        if (!checkAllPermissions()) {
            requestNecessaryPermissions();
        } else {
            initializeLocationManager();
        }
    }

    private void requestNecessaryPermissions() {
        String[] permissionsToRequest;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest = new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.READ_PHONE_STATE,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT
            };
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissionsToRequest = new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.READ_PHONE_STATE,
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT
            };
        } else {
            permissionsToRequest = new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.READ_PHONE_STATE
            };
        }
        requestPermissionLauncher.launch(permissionsToRequest);
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
        Intent intent = new Intent(this, DeviceListActivity.class);
        startActivity(intent);
    }

    private void viewAppConfig(){
        Intent intent = new Intent(this, AppConfigViewActivity.class);
        startActivity(intent);
    }

    private void showFolderSelectionDialog() {
        List<String> tables = databaseHelper.getAllTables();
        final List<String> dialogItems = new ArrayList<>();
        if (!tables.contains("unified_data")) {
            tables.add("unified_data");
        }
        dialogItems.addAll(tables);

        final CharSequence[] items = dialogItems.toArray(new CharSequence[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Выберите папку для сканирования");
        builder.setItems(items, (dialog, which) -> {
            String selectedItem = items[which].toString();
            stopScanning();
            currentScanFolder = selectedItem;
            startScanning();
            Toast.makeText(this, "Выбрана папка: " + currentScanFolder, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showFolderManagementDialog() {
        List<String> tables = databaseHelper.getAllTables();
        final List<String> deletableTables = new ArrayList<>();
        for (String table : tables) {
            if (!table.equals("")) {
                deletableTables.add(table);
            }
        }

        if (deletableTables.isEmpty()) {
            Toast.makeText(this, "Нет папок для удаления", Toast.LENGTH_SHORT).show();
            return;
        }

        final CharSequence[] items = deletableTables.toArray(new CharSequence[0]);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Удаление папок");
        builder.setItems(items, (dialog, which) -> {
            String tableToDelete = items[which].toString();
            showDeleteConfirmationDialog(tableToDelete);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showDeleteConfirmationDialog(String tableName) {
        new AlertDialog.Builder(this)
                .setTitle("Удаление папки")
                .setMessage("Вы уверены, что хотите удалить папку '" + tableName + "'? Все данные будут потеряны!")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    boolean success = databaseHelper.deleteTable(tableName);
                    if (success) {
                        Toast.makeText(this, "Папка удалена: " + tableName, Toast.LENGTH_SHORT).show();
                        if (currentScanFolder.equals(tableName)) {
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
            if (!folderName.isEmpty()) {
                if (folderName.equals("unified_data")) {
                    Toast.makeText(this, "Имя папки недоступно", Toast.LENGTH_SHORT).show();
                    return;
                }
                databaseHelper.createTableIfNotExists(folderName);
                currentScanFolder = folderName;
                Toast.makeText(this, "Создана папка: " + folderName, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Имя папки не может быть пустым", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }
}