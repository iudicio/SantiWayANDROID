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
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
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
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ImageButton;
import android.widget.TextView;
import android.net.Uri;
import android.os.PowerManager;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.santiway.bluetooth_scanner.BluetoothForegroundService;
import com.example.santiway.cell_scanner.CellForegroundService;
import com.example.santiway.esp32.Esp32Activity;
import com.example.santiway.esp32.Esp32ConnectionService;
import com.example.santiway.esp32.Esp32DatabaseHelper;
import com.example.santiway.opencellid.OpenCellIdSyncScheduler;
import com.example.santiway.upload_data.ApiConfig;
import com.example.santiway.upload_data.DeviceUploadManager;
import com.example.santiway.upload_data.DeviceUploadService;
import com.example.santiway.upload_data.ServerUploadConfig;
import com.example.santiway.upload_data.MainDatabaseHelper;
import com.example.santiway.upload_data.UniqueDevicesHelper;
import com.example.santiway.upload_name_device.UserDeviceSyncManager;
import com.example.santiway.websocket.ApkAssembler;
import com.example.santiway.websocket.WebSocketNotificationClient;
import com.example.santiway.websocket.WebSocketService;
import com.example.santiway.wifi_scanner.WifiForegroundService;
import com.example.santiway.gsm_protocol.LocationManager;
import com.google.android.material.navigation.NavigationView;
import com.example.santiway.FolderDeletionBottomSheet.FolderDeletionListener;
import com.example.santiway.upload_folder_device.UserDeviceFolderSyncManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends BaseLocalizedActivity implements NavigationView.OnNavigationItemSelectedListener, CreateFolderDialogFragment.CreateFolderListener, FolderDeletionListener {
    private TextView timeLabelTextView;
    private static final String TAG = "MainActivity";
    private BroadcastReceiver folderSwitchedReceiver;
    private DrawerLayout drawerLayout;
    private Toolbar toolbar;

    private ImageButton playPauseButton;

    private ImageView scanArrowsIcon;
    private TextView wifiStatusTextView;
    private TextView bluetoothStatusTextView;
    private TextView cellularStatusTextView;
    private TextView coordinatesTextView;
    private TextView toolbarFolderTitleTextView;

    private MainDatabaseHelper databaseHelper;
    private LocationManager locationManager;
    private boolean isScanning = false;
    private static final String DEFAULT_FOLDER_INTERNAL = FolderNameHelper.MAIN_FOLDER_INTERNAL;
    private String currentScanFolder = DEFAULT_FOLDER_INTERNAL;
    private DeviceUploadManager uploadManager;

    private Handler timerHandler = new Handler();
    private long startTime = 0L;
    private long timeInMilliseconds = 0L;
    private TextView lastUploadDateTextView;
    private WebSocketService webSocketService;
    private boolean isWebSocketBound = false;
    private ApkAssembler apkAssembler;
    private BroadcastReceiver webSocketReceiver;
    private BroadcastReceiver uploadUpdateReceiver;
    private static final String PREFS_SCANNER_STATE = "scanner_state";
    private static final String KEY_IS_SCANNING = "is_scanning";
    private static final String KEY_SCAN_START_TIME = "scan_start_time";
    private static final String PREFS_APP = "app_prefs";
    private static final String KEY_CURRENT_FOLDER = "current_folder";
    private static final String KEY_OWNER_DEVICE_SYNCED_ONCE = "owner_device_synced_once";
    private GestureDetector mainNavigationGestureDetector;
    private ImageButton snapshotButton;
    private boolean isSnapshotRunning = false;
    private String folderBeforeSnapshot = null;
    private final Handler snapshotHandler = new Handler();
    private View snapshotTriggerContainer;
    private ImageButton snapshotTriggerButton;
    private ImageButton targetDetectionButton;
    private String activeSnapshotFolder = null;
    private double activeSnapshotLat = 0.0;
    private double activeSnapshotLon = 0.0;
    private boolean activeSnapshotHasOrigin = false;

    private static final String KEY_LAST_SNAPSHOT_TRIGGER_FOLDER = "last_snapshot_trigger_folder";
    private static final String KEY_LAST_SNAPSHOT_TRIGGER_LAT = "last_snapshot_trigger_lat";
    private static final String KEY_LAST_SNAPSHOT_TRIGGER_LON = "last_snapshot_trigger_lon";
    private static final String KEY_LAST_SNAPSHOT_TRIGGER_HAS_ORIGIN = "last_snapshot_trigger_has_origin";
    private static final String KEY_LAST_SNAPSHOT_TRIGGER_ARMED = "last_snapshot_trigger_armed";
    private static final String KEY_LAST_SNAPSHOT_TRIGGER_APPLIED = "last_snapshot_trigger_applied";
    private static final float SNAPSHOT_TRIGGER_DISTANCE_METERS = 500f;

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
            timeLabelTextView.setText(getString(R.string.working_time_format, timeString));
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
                            startEsp32ConnectionServiceIfPermitted();
                            // Разрешения получены
                            initializeLocationManager();
                            checkAllFunctionalityAndWarn();

                            // Запрашиваем дополнительные разрешения, если нужно
                            requestOptionalPermissions();
                        } else {
                            // Не все разрешения даны
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.toast_some_permissions_denied),
                                    Toast.LENGTH_SHORT).show();
                            showMissingPermissionsWarning();
                        }
                    });

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        applyNavigationBarColor();
        setContentView(R.layout.activity_main_new);

        drawerLayout = findViewById(R.id.drawer_layout);
        View root = drawerLayout;
        View bottomBar = findViewById(R.id.footer_actions);
        toolbar = findViewById(R.id.toolbar);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            ViewGroup.LayoutParams toolbarLp = toolbar.getLayoutParams();
            toolbarLp.height = bars.top + dpToPx(56);
            toolbar.setLayoutParams(toolbarLp);

            toolbar.setPadding(
                    toolbar.getPaddingLeft(),
                    bars.top,
                    toolbar.getPaddingRight(),
                    toolbar.getPaddingBottom()
            );

            if (bottomBar != null) {
                bottomBar.setPadding(
                        bottomBar.getPaddingLeft(),
                        bottomBar.getPaddingTop(),
                        bottomBar.getPaddingRight(),
                        bars.bottom
                );
            }

            return insets;
        });

        playPauseButton = findViewById(R.id.play_pause_button);
        scanArrowsIcon = findViewById(R.id.scan_arrows_icon);
        snapshotButton = findViewById(R.id.snapshot_button);
        targetDetectionButton = findViewById(R.id.target_detection_button);
        findViewById(R.id.esp32_button).setOnClickListener(v ->
                startActivity(new Intent(this, Esp32Activity.class)));
        startEsp32ConnectionServiceIfPermitted();
        toolbarFolderTitleTextView = findViewById(R.id.toolbar_folder_title);
        wifiStatusTextView = findViewById(R.id.wifi_status);
        bluetoothStatusTextView = findViewById(R.id.bluetooth_status);
        cellularStatusTextView = findViewById(R.id.cellular_status);
        coordinatesTextView = findViewById(R.id.coordinates_text);
        timeLabelTextView = findViewById(R.id.time_label);
        lastUploadDateTextView = null;
        toolbarFolderTitleTextView.setOnClickListener(v -> showFolderSelectionDialog());

        setSupportActionBar(toolbar);
        setupMainNavigationSwipe();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        databaseHelper = new MainDatabaseHelper(this);
        OpenCellIdSyncScheduler.scheduleDaily(this);
        OpenCellIdSyncScheduler.enqueueIfDue(this);

        SharedPreferences appPrefs = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        currentScanFolder = appPrefs.getString(KEY_CURRENT_FOLDER, DEFAULT_FOLDER_INTERNAL);
        databaseHelper.createTableIfNotExists(currentScanFolder);
        updateToolbarTitle(currentScanFolder);

        toolbarFolderTitleTextView.setOnClickListener(v -> showFolderSelectionDialog());

        registerFolderSwitchedReceiver();

        checkAndRequestPermissionsStepByStep();
        requestBatteryOptimizationDisable();

        playPauseButton.setOnClickListener(v -> {
            if (isSnapshotRunning) {
                Toast.makeText(this, getString(R.string.toast_snapshot_blocks_scan), Toast.LENGTH_SHORT).show();
                return;
            }
            if (checkEssentialPermissions()) {
                toggleScanState();
                requestOptionalPermissions();
            } else {
                showInitialPermissionsExplanation();
            }
        });

        View scanButtonContainer = findViewById(R.id.scan_button_container);
        scanButtonContainer.setOnClickListener(v -> {
            if (checkEssentialPermissions()) {
                toggleScanState();
                requestOptionalPermissions();
            } else {
                showInitialPermissionsExplanation();
            }
        });

        snapshotButton.setOnClickListener(v -> {
            if (checkEssentialPermissions()) {
                startSnapshotScan();
                requestOptionalPermissions();
            } else {
                showInitialPermissionsExplanation();
            }
        });
        if (snapshotTriggerButton != null) {
            snapshotTriggerButton.setOnClickListener(v -> armOrApplySnapshotTrigger());
        }
        updateSnapshotTriggerButton();

        targetDetectionButton.setOnClickListener(v -> {
            int currentMode = AlarmModeConfig.getMode(this);
            int nextMode = currentMode == AlarmModeConfig.MODE_OFF
                    ? AlarmModeConfig.MODE_ALL
                    : AlarmModeConfig.MODE_OFF;
            AlarmModeConfig.saveMode(this, nextMode);
            if (nextMode == AlarmModeConfig.MODE_OFF) {
                AlarmModeConfig.saveQuietModeEnabled(this, false);
            }

            Toast.makeText(this,
                    nextMode == AlarmModeConfig.MODE_OFF
                            ? getString(R.string.toast_alarm_mode_off)
                            : getString(R.string.toast_alarm_mode_all),
                    Toast.LENGTH_SHORT).show();

            updateTargetDetectionButton();
        });
        updateTargetDetectionButton();

        findViewById(R.id.footer_device).setOnClickListener(v -> openDeviceListActivity());
        findViewById(R.id.footer_create).setOnClickListener(v -> showCreateFolderDialog());
        findViewById(R.id.footer_delete).setOnClickListener(v -> showFolderManagementDialog());
        findViewById(R.id.footer_settings).setOnClickListener(v -> viewAppConfig());

        SharedPreferences prefs = getSharedPreferences(PREFS_SCANNER_STATE, MODE_PRIVATE);
        isScanning = prefs.getBoolean(KEY_IS_SCANNING, false);
        startTime = prefs.getLong(KEY_SCAN_START_TIME, 0L);

        updateScanStatusUI(isScanning);

        if (isScanning && startTime > 0L) {
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler.post(timerRunnable);
        }

        ApiConfig.initialize(this);
        uploadManager = new DeviceUploadManager(this);
        boolean ownerDeviceSyncedOnce = getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                .getBoolean(KEY_OWNER_DEVICE_SYNCED_ONCE, false);

        if (!ownerDeviceSyncedOnce) {
            new UserDeviceSyncManager(this).syncOwnerDevice();

            getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_OWNER_DEVICE_SYNCED_ONCE, true)
                    .apply();
        }
        startUploadService();
        updateLastUploadDateDisplay();
        registerUploadUpdateReceiver();
        cleanupOldDataOnStart();

        //startWebSocketService();
        //registerWebSocketReceivers();
        //apkAssembler = new ApkAssembler(this);

        boolean updateStarted = getSharedPreferences("update_prefs", MODE_PRIVATE)
                .getBoolean("apk_update_started", false);

        if (updateStarted) {
            getSharedPreferences("update_prefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("apk_update_started", false)
                    .apply();

            Toast.makeText(this, getString(R.string.toast_update_installed), Toast.LENGTH_LONG).show();
        }

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

    private String getDisplayFolderName(String folderName) {
        return FolderNameHelper.getDisplayName(this, folderName);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void startEsp32ConnectionServiceIfPermitted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
            ContextCompat.startForegroundService(this, new Intent(this, Esp32ConnectionService.class));
        }
    }

    private void applyNavigationBarColor() {
        getWindow().setStatusBarColor(Color.parseColor("#071427"));
        getWindow().setNavigationBarColor(Color.parseColor("#172A46"));
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    private void updateToolbarTitle(String folderName) {
        if (toolbarFolderTitleTextView != null) {
            toolbarFolderTitleTextView.setText(getDisplayFolderName(folderName));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateTargetDetectionButton();

        String savedFolder = getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                .getString(KEY_CURRENT_FOLDER, DEFAULT_FOLDER_INTERNAL);

        if (savedFolder != null && !savedFolder.equals(currentScanFolder)) {
            currentScanFolder = savedFolder;
            updateToolbarTitle(currentScanFolder);
        }
    }

    @Override
    public void onFolderCreated(String folderName) {
        if (folderName.equals(DEFAULT_FOLDER_INTERNAL)) {
            Toast.makeText(this, getString(R.string.error_folder_name_unavailable), Toast.LENGTH_SHORT).show();
            return;
        }

        boolean wasScanning = isScanning;

        if (wasScanning) {
            stopScanning(false);
        }

        databaseHelper.createTableIfNotExists(folderName);
        new UserDeviceFolderSyncManager(this).syncFolderCreated(folderName);
        currentScanFolder = folderName;
        updateToolbarTitle(currentScanFolder);

        getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                .edit()
                .putString(KEY_CURRENT_FOLDER, currentScanFolder)
                .apply();

        if (wasScanning) {
            startScanning();
        }

        Toast.makeText(this, getString(R.string.toast_folder_created, folderName), Toast.LENGTH_SHORT).show();
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
                        getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                                .edit()
                                .putString(KEY_CURRENT_FOLDER, currentScanFolder)
                                .apply();
                        runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                getString(R.string.toast_scan_folder_switched, getDisplayFolderName(newTableName)),
                                Toast.LENGTH_LONG).show());
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

    private String createSnapshotFolderName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.getDefault());
        return "snapshot-" + sdf.format(new Date());
    }

    private int snapshotDurationSeconds() {
        int seconds = getSharedPreferences("AppSettings", MODE_PRIVATE)
                .getInt("snapshot_duration_seconds", 60);
        return seconds > 0 ? seconds : 60;
    }

    private void startSnapshotScan() {
        if (isSnapshotRunning) {
            Toast.makeText(this, getString(R.string.toast_snapshot_already_running), Toast.LENGTH_SHORT).show();
            return;
        }

        boolean wasScanning = isScanning;
        folderBeforeSnapshot = currentScanFolder;

        if (wasScanning) {
            stopScanning(false);
        }

        String snapshotFolder = createSnapshotFolderName();

        databaseHelper.createTableIfNotExists(snapshotFolder);
        new UserDeviceFolderSyncManager(this).syncFolderCreated(snapshotFolder);

        currentScanFolder = snapshotFolder;
        updateToolbarTitle(currentScanFolder);

        getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                .edit()
                .putString(KEY_CURRENT_FOLDER, currentScanFolder)
                .apply();

        isSnapshotRunning = true;
        startScanning();

        if (snapshotButton != null) {
            snapshotButton.animate()
                    .rotationBy(360f)
                    .setDuration(700)
                    .withEndAction(this::animateSnapshotButton)
                    .start();
        }

        int snapshotDurationSeconds = snapshotDurationSeconds();
        Toast.makeText(
                this,
                getString(R.string.toast_snapshot_started, snapshotDurationSeconds),
                Toast.LENGTH_SHORT
        ).show();

        snapshotHandler.postDelayed(
                () -> finishSnapshotScan(wasScanning),
                snapshotDurationSeconds * 1000L
        );
    }

    private void animateSnapshotButton() {
        if (!isSnapshotRunning || snapshotButton == null) return;

        snapshotButton.animate()
                .rotationBy(360f)
                .setDuration(700)
                .withEndAction(this::animateSnapshotButton)
                .start();
    }

    private void finishSnapshotScan(boolean restorePreviousScanning) {
        if (!isSnapshotRunning) return;

        isSnapshotRunning = false;

        stopScanning(false);

        if (snapshotButton != null) {
            snapshotButton.animate().cancel();
            snapshotButton.setRotation(0f);
        }

        if (folderBeforeSnapshot != null && !folderBeforeSnapshot.trim().isEmpty()) {
            currentScanFolder = folderBeforeSnapshot;
            updateToolbarTitle(currentScanFolder);

            getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CURRENT_FOLDER, currentScanFolder)
                    .apply();
        }

        if (restorePreviousScanning) {
            startScanning();
        }

        Toast.makeText(this, getString(R.string.toast_snapshot_finished), Toast.LENGTH_SHORT).show();
    }

    private void saveSnapshotTriggerOrigin(String snapshotFolder) {
        Location origin = getCurrentSnapshotOrigin();
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_SNAPSHOT_TRIGGER_FOLDER, snapshotFolder)
                .putBoolean(KEY_LAST_SNAPSHOT_TRIGGER_ARMED, false)
                .putBoolean(KEY_LAST_SNAPSHOT_TRIGGER_APPLIED, false);

        activeSnapshotFolder = snapshotFolder;
        if (origin != null) {
            activeSnapshotLat = origin.getLatitude();
            activeSnapshotLon = origin.getLongitude();
            activeSnapshotHasOrigin = true;
            editor.putFloat(KEY_LAST_SNAPSHOT_TRIGGER_LAT, (float) activeSnapshotLat)
                    .putFloat(KEY_LAST_SNAPSHOT_TRIGGER_LON, (float) activeSnapshotLon)
                    .putBoolean(KEY_LAST_SNAPSHOT_TRIGGER_HAS_ORIGIN, true);
        } else {
            activeSnapshotLat = 0.0;
            activeSnapshotLon = 0.0;
            activeSnapshotHasOrigin = false;
            editor.putBoolean(KEY_LAST_SNAPSHOT_TRIGGER_HAS_ORIGIN, false);
        }
        editor.apply();
        updateSnapshotTriggerButton();
    }

    private Location getCurrentSnapshotOrigin() {
        if (locationManager != null && locationManager.hasValidLocation()) {
            Location currentLocation = locationManager.getCurrentLocation();
            if (currentLocation != null) return currentLocation;
        }

        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        if (prefs.getBoolean("static_location_enabled", false)) {
            float lat = prefs.getFloat("static_latitude", 0f);
            float lon = prefs.getFloat("static_longitude", 0f);
            if (lat != 0f || lon != 0f) {
                Location staticLocation = new Location("static");
                staticLocation.setLatitude(lat);
                staticLocation.setLongitude(lon);
                return staticLocation;
            }
        }
        return null;
    }

    private void markSnapshotTriggerReady() {
        getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_LAST_SNAPSHOT_TRIGGER_APPLIED, false)
                .apply();
    }

    private void updateSnapshotTriggerButton() {
        if (snapshotTriggerContainer == null || snapshotTriggerButton == null) return;
        SharedPreferences prefs = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        String folder = prefs.getString(KEY_LAST_SNAPSHOT_TRIGGER_FOLDER, "");
        boolean applied = prefs.getBoolean(KEY_LAST_SNAPSHOT_TRIGGER_APPLIED, false);
        boolean visible = !isSnapshotRunning && folder != null && !folder.trim().isEmpty() && !applied;
        snapshotTriggerContainer.setVisibility(visible ? View.VISIBLE : View.GONE);

        boolean armed = prefs.getBoolean(KEY_LAST_SNAPSHOT_TRIGGER_ARMED, false);
        int color = armed ? Color.parseColor("#F5C542") : Color.parseColor("#FF3B30");
        snapshotTriggerButton.setImageTintList(ColorStateList.valueOf(color));
        snapshotTriggerButton.setColorFilter(color);
        snapshotTriggerButton.setAlpha(visible ? 1.0f : 0.38f);
    }

    private void armOrApplySnapshotTrigger() {
        SharedPreferences prefs = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        String folder = prefs.getString(KEY_LAST_SNAPSHOT_TRIGGER_FOLDER, "");
        if (folder == null || folder.trim().isEmpty()) {
            Toast.makeText(this, R.string.toast_snapshot_trigger_no_snapshot, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!prefs.getBoolean(KEY_LAST_SNAPSHOT_TRIGGER_HAS_ORIGIN, false)) {
            Toast.makeText(this, R.string.toast_snapshot_trigger_no_origin, Toast.LENGTH_LONG).show();
            return;
        }

        Location current = getCurrentSnapshotOrigin();
        if (current == null) {
            prefs.edit().putBoolean(KEY_LAST_SNAPSHOT_TRIGGER_ARMED, true).apply();
            updateSnapshotTriggerButton();
            Toast.makeText(this, R.string.toast_snapshot_trigger_wait_location, Toast.LENGTH_LONG).show();
            return;
        }

        float distance = distanceFromSnapshot(current, prefs);
        if (distance >= SNAPSHOT_TRIGGER_DISTANCE_METERS) {
            applySnapshotTrigger(folder);
        } else {
            prefs.edit().putBoolean(KEY_LAST_SNAPSHOT_TRIGGER_ARMED, true).apply();
            updateSnapshotTriggerButton();
            Toast.makeText(this,
                    getString(R.string.toast_snapshot_trigger_armed, distance),
                    Toast.LENGTH_LONG).show();
        }
    }

    private float distanceFromSnapshot(Location current, SharedPreferences prefs) {
        float originLat = prefs.getFloat(KEY_LAST_SNAPSHOT_TRIGGER_LAT, 0f);
        float originLon = prefs.getFloat(KEY_LAST_SNAPSHOT_TRIGGER_LON, 0f);
        float[] result = new float[1];
        Location.distanceBetween(originLat, originLon,
                current.getLatitude(), current.getLongitude(), result);
        return result[0];
    }

    private void checkArmedSnapshotTrigger(Location current) {
        if (current == null) return;
        SharedPreferences prefs = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_LAST_SNAPSHOT_TRIGGER_ARMED, false)) return;
        if (!prefs.getBoolean(KEY_LAST_SNAPSHOT_TRIGGER_HAS_ORIGIN, false)) return;

        String folder = prefs.getString(KEY_LAST_SNAPSHOT_TRIGGER_FOLDER, "");
        if (folder == null || folder.trim().isEmpty()) return;

        if (distanceFromSnapshot(current, prefs) >= SNAPSHOT_TRIGGER_DISTANCE_METERS) {
            applySnapshotTrigger(folder);
        }
    }

    private void applySnapshotTrigger(String folder) {
        new Thread(() -> {
            int count = databaseHelper.updateAllDeviceStatusForTable(folder, "TARGET");
            getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_LAST_SNAPSHOT_TRIGGER_ARMED, false)
                    .putBoolean(KEY_LAST_SNAPSHOT_TRIGGER_APPLIED, true)
                    .apply();
            runOnUiThread(() -> {
                updateSnapshotTriggerButton();
                Toast.makeText(this,
                        getString(R.string.toast_folder_trigger_applied, getDisplayFolderName(folder), count),
                        Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void updateTargetDetectionButton() {
        int alarmMode = AlarmModeConfig.getMode(this);
        boolean enabled = alarmMode != AlarmModeConfig.MODE_OFF;
        int color = AlarmModeConfig.bellColorForMode(alarmMode);
        targetDetectionButton.setSelected(enabled);
        targetDetectionButton.setAlpha(enabled ? 1.0f : 0.38f);
        targetDetectionButton.setImageTintList(ColorStateList.valueOf(color));
        targetDetectionButton.setColorFilter(color);
    }

    private void initializeLocationManager() {
        if (locationManager == null) {
            locationManager = LocationManager.getInstance(this);
            locationManager.setOnLocationUpdateListener(new LocationManager.OnLocationUpdateListener() {
                @Override
                public void onLocationUpdate(Location location) {
                    updateCoordinatesDisplay(location);
                }

                @Override
                public void onPermissionDenied() {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, getString(R.string.error_location_permission_denied), Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onLocationError(String error) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, getString(R.string.error_location_error, error), Toast.LENGTH_SHORT).show());
                }
            });
            locationManager.startLocationUpdates();
        }
    }

    private void updateCoordinatesDisplay(Location location) {
        if (location != null) {
            String coords = String.format(
                    Locale.getDefault(),
                    "Lat: %.6f, Lon: %.6f",
                    location.getLatitude(),
                    location.getLongitude()
            );
            coordinatesTextView.setText(coords);
            checkArmedSnapshotTrigger(location);
        } else {
            coordinatesTextView.setText(getString(R.string.coordinates_unavailable));
        }
    }

    private void toggleScanState() {
        if (isScanning) {
            stopScanning();
        } else {
            startScanningFromUserAction();
        }
    }

    private void startScanningFromUserAction() {
        if (!AlarmModeConfig.isQuietModeEnabled(this)) {
            startScanning();
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.alarm_quiet_mode_dialog_title)
                .setMessage(R.string.alarm_quiet_mode_dialog_message)
                .setPositiveButton(R.string.alarm_quiet_mode_include_cells,
                        (d, which) -> {
                            AlarmModeConfig.saveQuietModeIncludeCellTowers(this, true);
                            startScanning();
                        })
                .setNegativeButton(R.string.alarm_quiet_mode_exclude_cells,
                        (d, which) -> {
                            AlarmModeConfig.saveQuietModeIncludeCellTowers(this, false);
                            startScanning();
                        })
                .setNeutralButton(R.string.dialog_cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> DialogStyleUtils.tintButtons(dialog));
        dialog.show();
    }

    private void startScanning() {
        checkAllFunctionalityAndWarn();

        isScanning = true;
        startTime = System.currentTimeMillis();

        getSharedPreferences(PREFS_SCANNER_STATE, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_IS_SCANNING, true)
                .putLong(KEY_SCAN_START_TIME, startTime)
                .apply();

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
        timerHandler.postDelayed(timerRunnable, 0);

        Toast.makeText(this, getString(R.string.toast_scan_started, getDisplayFolderName(currentScanFolder)), Toast.LENGTH_SHORT).show();
    }

    private void stopScanning() {
        stopScanning(true);
    }

    private void stopScanning(boolean showToast) {
        isScanning = false;
        startTime = 0L;

        getSharedPreferences(PREFS_SCANNER_STATE, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_IS_SCANNING, false)
                .putLong(KEY_SCAN_START_TIME, 0L)
                .apply();

        updateScanStatusUI(false);

        stopScannerService(WifiForegroundService.class);
        stopScannerService(CellForegroundService.class);
        stopScannerService(BluetoothForegroundService.class);

        timerHandler.removeCallbacks(timerRunnable);
        timeLabelTextView.setText(getString(R.string.working_time_format, "00:00:00"));

        if (showToast) {
            Toast.makeText(this, getString(R.string.toast_scan_stopped), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateScanStatusUI(boolean scanning) {
        int textRes = scanning ? R.string.scanning_status : R.string.stopped_status;

        playPauseButton.setImageResource(scanning
                ? R.drawable.ic_stop_scan
                : R.drawable.ic_play_scan);

        if (scanArrowsIcon != null) {
            if (scanning) {
                scanArrowsIcon.animate()
                        .rotationBy(360f)
                        .setDuration(900)
                        .withEndAction(() -> updateScanStatusUI(true))
                        .start();
            } else {
                scanArrowsIcon.animate().cancel();
                scanArrowsIcon.setRotation(0f);
            }
        }

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
            locationManager.clearOnLocationUpdateListener();
            if (!isScanning) {
                locationManager.cleanup();
            }
        }
        //stopScanning();
        if (folderSwitchedReceiver != null) {
            unregisterReceiver(folderSwitchedReceiver);
        }
        if (isScanning && startTime > 0L) {
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler.post(timerRunnable);
        }
        if (isWebSocketBound) {
            unbindService(webSocketConnection);
        }
        if (webSocketReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(webSocketReceiver);
        }
        unregisterUploadUpdateReceiver();
        snapshotHandler.removeCallbacksAndMessages(null);
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
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_permissions_missing_title))
                .setMessage(getString(R.string.dialog_permissions_missing_message))
                .setPositiveButton(getString(R.string.dialog_request_again), (d, which) -> requestNecessaryPermissions())
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .create();
        dialog.setOnShowListener(ignored -> DialogStyleUtils.tintButtons(dialog));
        dialog.show();
    }

    private void showMissingFunctionalityWarning() {
        StringBuilder warningMessage = new StringBuilder();
        warningMessage.append(getString(R.string.limited_functionality_intro));

        if (!isNetworkAvailable) {
            warningMessage.append(getString(R.string.limited_functionality_network));
        }
        if (!isLocationEnabled) {
            warningMessage.append(getString(R.string.limited_functionality_location));
        }
        if (!isWifiEnabled) {
            warningMessage.append(getString(R.string.limited_functionality_wifi));
        }
        if (!isBluetoothEnabled) {
            warningMessage.append(getString(R.string.limited_functionality_bluetooth));
        }

        warningMessage.append(getString(R.string.limited_functionality_outro));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_limited_functionality_title))
                .setMessage(warningMessage.toString())
                .setPositiveButton(getString(R.string.dialog_open_settings), (d, which) -> openSettings())
                .setNegativeButton(getString(R.string.dialog_continue), null)
                .create();
        dialog.setOnShowListener(ignored -> DialogStyleUtils.tintButtons(dialog));
        dialog.show();
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
            Toast.makeText(this, getString(R.string.error_cannot_open_settings), Toast.LENGTH_SHORT).show();
        }
    }

    private void showInitialPermissionsExplanation() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_required_permissions_title))
                .setMessage(getString(R.string.dialog_required_permissions_message))
                .setPositiveButton(getString(R.string.dialog_request), (d, which) -> requestEssentialPermissions())
                .setNegativeButton(getString(R.string.dialog_later), (d, which) -> {
                    showMissingPermissionsWarning();
                })
                .setCancelable(false)
                .create();
        dialog.setOnShowListener(ignored -> DialogStyleUtils.tintButtons(dialog));
        dialog.show();
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

        permissionsToRequest.add(android.Manifest.permission.READ_PHONE_STATE);

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
        permissions.add(Manifest.permission.READ_PHONE_STATE);

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
                    AlertDialog dialog = new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.dialog_notifications_title))
                            .setMessage(getString(R.string.dialog_notifications_permission_message))
                            .setPositiveButton(getString(R.string.dialog_allow), (d, w) -> {
                                requestPermissionLauncher.launch(
                                        new String[]{Manifest.permission.POST_NOTIFICATIONS}
                                );
                                prefs.edit().putBoolean("notifications_asked", true).apply();
                            })
                            .setNegativeButton(getString(R.string.dialog_later), null)
                            .create();
                    dialog.setOnShowListener(ignored -> DialogStyleUtils.tintButtons(dialog));
                    dialog.show();
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
                Toast.makeText(this,
                        getString(R.string.toast_devices_in_folder_marked, affectedRows, getDisplayFolderName(currentScanFolder), "SAFE"),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.error_current_scan_folder_unknown), Toast.LENGTH_SHORT).show();
            }
        }
        if (id == R.id.nav_clear_triggers) {
            if (currentScanFolder != null && !currentScanFolder.isEmpty()) {
                int affectedRows = databaseHelper.updateAllDeviceStatusForTable(currentScanFolder, "GREY");
                Toast.makeText(this,
                        getString(R.string.toast_devices_in_folder_marked, affectedRows, getDisplayFolderName(currentScanFolder), "GREY"),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.error_current_scan_folder_unknown), Toast.LENGTH_SHORT).show();
            }
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    private void openDeviceListActivity() {
        Intent intent = new Intent(this, DeviceListActivity.class);
        intent.putExtra("selected_folder", currentScanFolder);
        startActivity(intent);
    }

    private void viewAppConfig() {
        startActivity(new Intent(this, AppConfigViewActivity.class));
    }

    private List<String> getAllFolders() {
        List<String> tables = databaseHelper.getAllTables();
        if (!tables.contains(DEFAULT_FOLDER_INTERNAL)) {
            tables.add(DEFAULT_FOLDER_INTERNAL);
        }
        return tables;
    }

    private void showFolderSelectionDialog() {
        if (isSnapshotRunning) {
            Toast.makeText(this, getString(R.string.toast_snapshot_blocks_scan), Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> folders = getAllFolders();

        FolderSelectionBottomSheet bottomSheet = new FolderSelectionBottomSheet(folders, new FolderSelectionBottomSheet.FolderSelectionListener() {
            @Override
            public void onFolderSelected(String selectedFolder) {
                boolean wasScanning = isScanning;

                if (wasScanning) {
                    stopScanning(false);
                }

                currentScanFolder = selectedFolder;
                updateToolbarTitle(currentScanFolder);

                getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                        .edit()
                        .putString(KEY_CURRENT_FOLDER, currentScanFolder)
                        .apply();

                if (wasScanning) {
                    startScanning();
                }

                Toast.makeText(MainActivity.this, getString(R.string.toast_folder_selected, getDisplayFolderName(currentScanFolder)), Toast.LENGTH_SHORT).show();
            }
        });

        bottomSheet.show(getSupportFragmentManager(), "FolderSelectionTag");
    }

    private void showFolderManagementDialog() {
        List<String> folders = databaseHelper.getAllTables();
        List<String> deletableFolders = new ArrayList<>();

        for (String folder : folders) {
            if (!folder.isEmpty() && !folder.equals(DEFAULT_FOLDER_INTERNAL)) {
                deletableFolders.add(folder);
            }
        }

        if (deletableFolders.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_folders_to_delete, getString(R.string.main_folder_name)), Toast.LENGTH_SHORT).show();
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
            new UserDeviceFolderSyncManager(this).syncFolderDeleted(folderName);
            Toast.makeText(this, getString(R.string.toast_folder_deleted, folderName), Toast.LENGTH_SHORT).show();

            if (currentScanFolder.equals(folderName)) {
                stopScanning();
                currentScanFolder = DEFAULT_FOLDER_INTERNAL;
                updateToolbarTitle(currentScanFolder);

                getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                        .edit()
                        .putString(KEY_CURRENT_FOLDER, currentScanFolder)
                        .apply();

                startScanning();
            }
        } else {
            Toast.makeText(this, getString(R.string.error_folder_delete), Toast.LENGTH_SHORT).show();
        }
    }

    private void showCreateFolderDialog() {
        CreateFolderDialogFragment dialogFragment = new CreateFolderDialogFragment();
        dialogFragment.show(getSupportFragmentManager(), "create_folder_dialog");
    }

    private void startUploadService() {
        if (!ServerUploadConfig.isEnabled(this)) {
            Log.d(TAG, "Server upload disabled - upload service not started");
            return;
        }
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
                lastUploadDateTextView.setText(R.string.datetime_placeholder);
            }
        }
    }

    // НОВЫЙ МЕТОД: регистрация BroadcastReceiver для обновлений
    private void registerUploadUpdateReceiver() {
        if (uploadUpdateReceiver != null) return;

        uploadUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.example.santiway.UPLOAD_COMPLETED".equals(intent.getAction())) {
                    int count = intent.getIntExtra("device_count", 0);

                    runOnUiThread(() -> {
                        updateLastUploadDateDisplay();
                    });
                }
            }
        };

        IntentFilter filter = new IntentFilter("com.example.santiway.UPLOAD_COMPLETED");
        LocalBroadcastManager.getInstance(this).registerReceiver(uploadUpdateReceiver, filter);
    }

    private void unregisterUploadUpdateReceiver() {
        if (uploadUpdateReceiver == null) return;
        LocalBroadcastManager.getInstance(this).unregisterReceiver(uploadUpdateReceiver);
        uploadUpdateReceiver = null;
    }

    // Очистка данных старше выбранного срока хранения.
    private void cleanupOldDataOnStart() {
        new Thread(() -> {
            try {
                int retentionDays = getSharedPreferences("AppSettings", MODE_PRIVATE)
                        .getInt("data_retention_days", 7);
                if (retentionDays <= 0) retentionDays = 7;
                long retentionMaxAge = retentionDays * 24L * 60L * 60L * 1000L;

                try (MainDatabaseHelper helper = new MainDatabaseHelper(this)) {
                    helper.deleteOldRecordsFromAllTables(retentionMaxAge);
                }
                try (Esp32DatabaseHelper esp32Helper = new Esp32DatabaseHelper(this)) {
                    esp32Helper.deleteOldRuntimeData(retentionMaxAge);
                }
                try (NotificationDatabaseHelper notificationHelper = new NotificationDatabaseHelper(this)) {
                    notificationHelper.deleteOldNotifications(retentionMaxAge);
                }
                Log.d(TAG, "Old data cleaned up on app start. Retention days: " + retentionDays);
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
                            getString(R.string.toast_apk_received, apkPath), Toast.LENGTH_LONG).show();
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
                Toast.makeText(this, getString(R.string.toast_websocket_connected), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.toast_websocket_disconnected), Toast.LENGTH_SHORT).show();
            }
        });
    }

    //Методы для свайпа папок
    private void setupMainNavigationSwipe() {
        View swipeArea = findViewById(R.id.main_swipe_area);

        mainNavigationGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 120;
            private static final int SWIPE_VELOCITY_THRESHOLD = 120;

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY)
                        && Math.abs(diffX) > SWIPE_THRESHOLD
                        && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {

                    if (diffX > 0) {
                        viewAppConfig();
                    } else {
                        openDeviceListActivity();
                    }

                    return true;
                }

                return false;
            }
        });

        swipeArea.setOnTouchListener((v, event) -> mainNavigationGestureDetector.onTouchEvent(event));
    }

    private void switchToFolder(String selectedFolder) {
        if (isSnapshotRunning) {
            Toast.makeText(this, getString(R.string.toast_snapshot_blocks_scan), Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedFolder == null || selectedFolder.trim().isEmpty()) return;
        if (selectedFolder.equals(currentScanFolder)) return;

        boolean wasScanning = isScanning;

        if (wasScanning) {
            stopScanning(false);
        }

        currentScanFolder = selectedFolder;
        updateToolbarTitle(currentScanFolder);

        getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                .edit()
                .putString(KEY_CURRENT_FOLDER, currentScanFolder)
                .apply();

        if (wasScanning) {
            startScanning();
        }

        Toast.makeText(this, getString(R.string.toast_folder_short, currentScanFolder), Toast.LENGTH_SHORT).show();
    }

    private void requestBatteryOptimizationDisable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            PowerManager powerManager =
                    (PowerManager) getSystemService(Context.POWER_SERVICE);

            String packageName = getPackageName();

            if (powerManager != null
                    && !powerManager.isIgnoringBatteryOptimizations(packageName)) {

                try {
                    Intent intent = new Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + packageName)
                    );

                    startActivity(intent);

                } catch (Exception e) {
                    Log.e(TAG, "Battery optimization request error: " + e.getMessage());

                    Intent intent = new Intent(
                            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                    );

                    startActivity(intent);
                }
            }
        }
    }

}

