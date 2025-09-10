package com.example.santiway;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.util.Log;
import android.content.DialogInterface;
import com.example.santiway.wifi_scanner.DatabaseHelper;
import com.example.santiway.wifi_scanner.WifiForegroundService;
import com.example.santiway.gsm_protocol.LocationManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private Button startScanButton;
    private Button stopScanButton;
    private Button viewDatabaseButton;
    private Button viewAppConfigButton;
    private Button selectFolderButton;
    private TextView currentFolderTextView;
    private DatabaseHelper databaseHelper;
    private LocationManager locationManager;
    private String currentScanFolder = "default_table";

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
                            startWifiScanning();
                        } else {
                            Toast.makeText(MainActivity.this, "Permissions denied", Toast.LENGTH_SHORT).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        startScanButton = findViewById(R.id.startScanButton);
        stopScanButton = findViewById(R.id.stopScanButton);
        viewDatabaseButton = findViewById(R.id.viewDatabaseButton);
        viewAppConfigButton = findViewById(R.id.viewAppConfigButton);
        selectFolderButton = findViewById(R.id.selectFolderButton);
        currentFolderTextView = findViewById(R.id.currentFolderTextView);

        databaseHelper = new DatabaseHelper(this);
        updateCurrentFolderDisplay();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        startScanButton.setOnClickListener(v -> checkPermissionsAndStartScanning());
        stopScanButton.setOnClickListener(v -> stopWifiScanning());
        viewDatabaseButton.setOnClickListener(v -> openDatabaseView(currentScanFolder));
        viewAppConfigButton.setOnClickListener(v -> viewAppConfig());
        selectFolderButton.setOnClickListener(v -> showFolderSelectionDialog());

        // Проверяем разрешения при запуске
        checkAndRequestLocationPermissions();
    }

    private void checkAndRequestLocationPermissions() {
        if (!checkAllPermissions()) {
            requestNecessaryPermissions();
        } else {
            // Если разрешения уже есть, инициализируем LocationManager
            initializeLocationManager();
        }
    }

    private void initializeLocationManager() {
        locationManager = new LocationManager(this);
        locationManager.setOnLocationUpdateListener(new LocationManager.OnLocationUpdateListener() {
            @Override
            public void onLocationUpdate(Location location) {
                Log.d("MainActivity", "Location updated: " +
                        location.getLatitude() + ", " + location.getLongitude());
            }

            @Override
            public void onPermissionDenied() {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                            "Location permission denied", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onLocationError(String error) {
                Log.e("MainActivity", "Location error: " + error);
            }
        });

        // Запускаем обновления местоположения
        locationManager.startLocationUpdates();

        // Получаем последнюю известную локацию
        locationManager.getLastKnownLocation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // При возобновлении активности продолжаем обновления местоположения
        if (locationManager != null && checkAllPermissions()) {
            locationManager.startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // При сворачивании приложения НЕ останавливаем LocationManager
        // Он продолжит работать в фоновом режиме
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // При полном уничтожении активности очищаем ресурсы
        if (locationManager != null) {
            locationManager.cleanup();
            locationManager = null;
        }

        // Также останавливаем сервис сканирования
        stopWifiScanning();
    }

    private void updateCurrentFolderDisplay() {
        currentFolderTextView.setText("Текущая папка: " + currentScanFolder);
    }

    private void showFolderSelectionDialog() {
        List<String> tables = databaseHelper.getAllTables();
        final List<String> dialogItems = new ArrayList<>();
        dialogItems.add("+ Создать новую папку");
        if (!tables.contains("default_table")) {
            tables.add("default_table");
        }
        dialogItems.addAll(tables);
        dialogItems.add("---");
        dialogItems.add("Управление папками");

        final CharSequence[] items = dialogItems.toArray(new CharSequence[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Выберите папку для сканирования");
        builder.setItems(items, (dialog, which) -> {
            String selectedItem = items[which].toString();
            if (selectedItem.equals("+ Создать новую папку")) {
                showCreateFolderDialog();
            } else if (selectedItem.equals("Управление папками")) {
                showFolderManagementDialog();
            } else if (!selectedItem.equals("---")) {
                currentScanFolder = selectedItem;
                updateCurrentFolderDisplay();
                Toast.makeText(this, "Выбрана папка: " + currentScanFolder, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showFolderManagementDialog() {
        List<String> tables = databaseHelper.getAllTables();
        final List<String> deletableTables = new ArrayList<>();

        for (String table : tables) {
            if (!table.equals("default_table")) {
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
                            currentScanFolder = "default_table";
                            updateCurrentFolderDisplay();
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
                if (folderName.equals("default_table")) {
                    Toast.makeText(this, "Имя папки недоступно", Toast.LENGTH_SHORT).show();
                    return;
                }
                databaseHelper.createTableIfNotExists(folderName);
                currentScanFolder = folderName;
                updateCurrentFolderDisplay();
                Toast.makeText(this, "Создана папка: " + folderName, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Имя папки не может быть пустым", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }


    private void openDatabaseView(String tableName) {
        Intent intent = new Intent(this, DatabaseViewActivity.class);
        intent.putExtra("TABLE_NAME", tableName);
        startActivity(intent);
    }

    private void checkPermissionsAndStartScanning() {
        if (checkAllPermissions()) {
            startWifiScanning();
        } else {
            requestNecessaryPermissions();
        }
    }

    private boolean checkAllPermissions() {
        boolean hasLocationPermission = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasNotificationPermission = true;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return hasLocationPermission && hasNotificationPermission;
    }

    private void requestNecessaryPermissions() {
        String[] permissionsToRequest;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest = new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            permissionsToRequest = new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
        requestPermissionLauncher.launch(permissionsToRequest);
    }

    private void startWifiScanning() {
        // Получаем текущее местоположение и передаем в сервис
        Location currentLocation = null;
        if (locationManager != null) {
            currentLocation = locationManager.getCurrentLocation();
        }
        Intent serviceIntent = new Intent(this, WifiForegroundService.class);
        serviceIntent.setAction("START_SCAN");
        serviceIntent.putExtra("tableName", currentScanFolder);

        // Передаем координаты в сервис
        if (currentLocation != null) {
            serviceIntent.putExtra("latitude", currentLocation.getLatitude());
            serviceIntent.putExtra("longitude", currentLocation.getLongitude());
            serviceIntent.putExtra("accuracy", currentLocation.getAccuracy());
            if (currentLocation.hasAltitude()) {
                serviceIntent.putExtra("altitude", currentLocation.getAltitude());
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "Сканирование запущено: " + currentScanFolder, Toast.LENGTH_SHORT).show();
    }

    private void stopWifiScanning() {
        Intent serviceIntent = new Intent(this, WifiForegroundService.class);
        serviceIntent.setAction("STOP_SCAN");
        startService(serviceIntent);
        Toast.makeText(this, "Сканирование остановлено", Toast.LENGTH_SHORT).show();
    }

    private void viewAppConfig(){
        Intent intent = new Intent(this, AppConfigViewActivity.class);
        startActivity(intent);
    }
}