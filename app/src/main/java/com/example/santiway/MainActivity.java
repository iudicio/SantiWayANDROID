package com.example.santiway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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
import com.example.santiway.CreateFolderDialogFragment;
import com.example.santiway.FolderDeletionBottomSheet.FolderDeletionListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener,CreateFolderDialogFragment.CreateFolderListener
    ,FolderDeletionBottomSheet.FolderDeletionListener{
    private TextView timeLabelTextView;
    private BroadcastReceiver folderSwitchedReceiver;
    private DrawerLayout drawerLayout;
    private Toolbar toolbar;

    private ImageButton playPauseButton;

    private android.widget.TextView wifiStatusTextView;
    private android.widget.TextView bluetoothStatusTextView;
    private android.widget.TextView cellularStatusTextView;
    private android.widget.TextView coordinatesTextView;

    private TextView toolbarFolderTitleTextView;
    private TextView scanTimerText;

    private MainDatabaseHelper databaseHelper;
    private LocationManager locationManager;
    private boolean isScanning = false;
    private String currentScanFolder = "unified_data";
    private DeviceUploadManager uploadManager;
    private Handler timerHandler = new Handler();
    private long startTime = 0L;
    private long timeInMilliseconds = 0L;
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

        playPauseButton = findViewById(R.id.play_pause_button);
        toolbarFolderTitleTextView = findViewById(R.id.toolbar_folder_title);

        wifiStatusTextView = findViewById(R.id.wifi_status);
        bluetoothStatusTextView = findViewById(R.id.bluetooth_status);
        cellularStatusTextView = findViewById(R.id.cellular_status);
        coordinatesTextView = findViewById(R.id.coordinates_text);
        timeLabelTextView = findViewById(R.id.time_label);

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_apps);
        navigationView.setNavigationItemSelectedListener(this);

        updateToolbarTitle(currentScanFolder);
        toolbarFolderTitleTextView.setOnClickListener(v -> showFolderSelectionDialog());

        registerFolderSwitchedReceiver();

        databaseHelper = new MainDatabaseHelper(this);

        checkAndRequestPermissions();

        // ИСПРАВЛЕНО: Использование новой кнопки playPauseButton
        playPauseButton.setOnClickListener(v -> {
            if (checkAllPermissions()) {
                toggleScanState();
            } else {
                requestNecessaryPermissions();
            }
        });



        // ДОБАВЛЕНО: Обработчики нажатий для кнопок нижней панели (Footer)
        findViewById(R.id.footer_device).setOnClickListener(v -> openDeviceListActivity()); // Список устройств
        findViewById(R.id.footer_create).setOnClickListener(v -> showCreateFolderDialog()); // Создать папку
        findViewById(R.id.footer_delete).setOnClickListener(v -> showFolderManagementDialog()); // Удалить папку



        // Инициализация конфигурации API
        ApiConfig.initialize(this);
        uploadManager = new DeviceUploadManager(this);
        startUploadService();

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

    // ДОБАВЛЕН НОВЫЙ МЕТОД: Обновление заголовка Toolbar
    private void updateToolbarTitle(String folderName) {
        if (toolbarFolderTitleTextView != null) {
            toolbarFolderTitleTextView.setText(folderName);
        }
    }
    @Override
    public void onFolderCreated(String folderName) {
        // Вся логика, которая была внутри AlertDialog
        if (folderName.equals("unified_data")) {
            // Эта проверка должна была быть в диалоге, но на всякий случай оставим
            Toast.makeText(this, "Имя папки недоступно", Toast.LENGTH_SHORT).show();
            return;
        }

        // databaseHelper уже инициализирован в onCreate
        databaseHelper.createTableIfNotExists(folderName);
        currentScanFolder = folderName;
        updateToolbarTitle(currentScanFolder); // <-- Обновление заголовка
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
                        // ДОБАВЛЕНО: Обновление заголовка при переключении извне
                        updateToolbarTitle(currentScanFolder);
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

        // Отправляем оставшиеся данные перед остановкой
        uploadRemainingData();
        timerHandler.removeCallbacks(timerRunnable);

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

        // ИСПРАВЛЕНО: Меняем иконку ImageButton: ic_start/ic_stop
        // Использую ic_cloud в качестве заглушки для "Stop"
        playPauseButton.setImageResource(scanning ? R.drawable.ic_pause : R.drawable.ic_starts);
        // Если у вас есть ic_start и ic_stop, замените на:
        // playPauseButton.setImageResource(scanning ? R.drawable.ic_stop : R.drawable.ic_start);


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
        timerHandler.removeCallbacks(timerRunnable);
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

        if (id == R.id.nav_clear_status) { // Настройки
            viewAppConfig();
        }

        if (id == R.id.nav_make_safe) {
            // НОВОЕ: Массовое обновление статуса для текущей папки
            // currentScanFolder хранит имя таблицы, которую мы сейчас сканируем

            // Проверяем, что папка не пуста
            if (currentScanFolder != null && !currentScanFolder.isEmpty()) {
                // Вызываем массовое обновление в базе данных
                int affectedRows = databaseHelper.updateAllDeviceStatusForTable(currentScanFolder, "SAFE");

                Toast.makeText(this, affectedRows + " устройств в '" + currentScanFolder + "' помечены как SAFE.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Текущая папка для сканирования не определена.", Toast.LENGTH_SHORT).show();
            }
        }
        if (id == R.id.nav_clear_triggers) {
            // Массовое обновление статуса на "CLEAR" для текущей папки
            if (currentScanFolder != null && !currentScanFolder.isEmpty()) {
                int affectedRows = databaseHelper.updateAllDeviceStatusForTable(currentScanFolder, "CLEAR");

                Toast.makeText(this, affectedRows + " устройств в '" + currentScanFolder + "' помечены как CLEAR.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Текущая папка для сканирования не определена.", Toast.LENGTH_SHORT).show();
            }
        }

        // Логика закрытия drawer должна быть в конце
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


    // ИЗМЕНЕННЫЙ МЕТОД
    private void showFolderManagementDialog() {
        List<String> folders = databaseHelper.getAllTables();
        List<String> deletableFolders = new ArrayList<>();

        // Папку "unified_data" нельзя удалять
        for (String folder : folders) {
            if (!folder.isEmpty() && !folder.equals("unified_data")) {
                deletableFolders.add(folder);
            }
        }

        if (deletableFolders.isEmpty()) {
            Toast.makeText(this, "Нет папок для удаления (кроме 'unified_data')", Toast.LENGTH_SHORT).show();
            return;
        }

        // Запуск нового стилизованного BottomSheet
        FolderDeletionBottomSheet bottomSheet = new FolderDeletionBottomSheet(
                deletableFolders,
                this // MainActivity реализует FolderDeletionListener
        );

        bottomSheet.show(getSupportFragmentManager(), "FolderDeletionTag");
    }

    // УДАЛИТЬ старый метод, который вы предоставили:
    // private void showDeleteConfirmationDialog(String folderName) { ... }

    // НОВЫЙ МЕТОД: Обработка запроса на удаление из BottomSheet
    @Override
    public void onDeleteRequested(String folderName) {
        boolean success = databaseHelper.deleteTable(folderName);
        if (success) {
            Toast.makeText(this, "Папка удалена: " + folderName, Toast.LENGTH_SHORT).show();

            // Если удалена текущая папка сканирования, переключаемся на unified_data
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

    // Старый метод showCreateFolderDialog() удаляется и заменяется на:
    private void showCreateFolderDialog() {
        // Мы используем CreateFolderDialogFragment, который мы исправляли
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
}