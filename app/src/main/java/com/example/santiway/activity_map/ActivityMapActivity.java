package com.example.santiway.activity_map;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.santiway.R;
import com.example.santiway.upload_data.MainDatabaseHelper;
import com.example.santiway.DeviceListActivity;

import org.osmdroid.util.GeoPoint;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ActivityMapActivity extends AppCompatActivity {

    private ActivityMapOSM mapFragment;
    private List<GeoPoint> deviceHistoryPoints = new ArrayList<>();
    private List<Long> deviceTimestamps = new ArrayList<>();

    // UI элементы
    private View statusHeader;
    private TextView tvMacAddress;
    private TextView tvDeviceName;
    private TextView tvDeviceType;
    private TextView tvFirstDetected;
    private TextView tvLastDetected;
    private TextView tvDetectionCount;
    private Button btnMakeTarget;
    private Button btnMakeSafe;

    // Данные устройства
    private String deviceMac;
    private String deviceName;
    private String deviceType = "Wi-Fi";
    private static final String TAG = "ActivityMapActivity";
    private String tableName;
    private String currentStatus = "GREY";
    private long firstDetectionTime = 0;
    private long lastDetectionTime = 0;
    private int detectionCount = 0;

    // Форматы даты
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_activity);

        // Инициализация UI
        initViews();

        View root = findViewById(R.id.root_activity_map);
        Toolbar toolbar = findViewById(R.id.toolbar_map);
        View actionsContainer = findViewById(R.id.status_actions_container);
        View infoCard = findViewById(R.id.device_info_card);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            int toolbarHeight = bars.top + dpToPx(56);

            if (toolbar != null) {
                ViewGroup.LayoutParams toolbarLp = toolbar.getLayoutParams();
                toolbarLp.height = toolbarHeight;
                toolbar.setLayoutParams(toolbarLp);

                toolbar.setPadding(
                        toolbar.getPaddingLeft(),
                        bars.top,
                        toolbar.getPaddingRight(),
                        toolbar.getPaddingBottom()
                );
            }

            if (actionsContainer != null) {
                actionsContainer.setPadding(
                        actionsContainer.getPaddingLeft(),
                        actionsContainer.getPaddingTop(),
                        actionsContainer.getPaddingRight(),
                        actionsContainer.getPaddingBottom()
                );
            }

            if (infoCard != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) infoCard.getLayoutParams();
                lp.bottomMargin = dpToPx(16) + bars.bottom;
                infoCard.setLayoutParams(lp);
            }

            return insets;
        });

        // Получаем данные из Intent
        Intent intent = getIntent();
        if (intent != null) {
            double latitude = intent.getDoubleExtra("latitude", 0);
            double longitude = intent.getDoubleExtra("longitude", 0);
            deviceName = intent.getStringExtra("device_name");
            deviceMac = intent.getStringExtra("device_mac");
            deviceType = intent.getStringExtra("device_type");
            tableName = intent.getStringExtra("table_name");

            String deviceStatus = intent.getStringExtra("device_status");
            if (deviceStatus != null && !deviceStatus.trim().isEmpty()) {
                currentStatus = deviceStatus;
            }

            if (deviceMac != null && tableName != null) {
                loadDeviceData();
            }
        }

        // Настройка Toolbar
        setupToolbar();

        // Настройка кнопок
        setupButtons();

        // Отображение данных
        displayDeviceInfo();

        // Создаем и отображаем фрагмент с картой
        showMapFragment();
    }

    private void initViews() {
        statusHeader = findViewById(R.id.status_header);
        tvMacAddress = findViewById(R.id.tv_mac_address);
        tvDeviceName = findViewById(R.id.tv_device_name);
        tvDeviceType = findViewById(R.id.tv_device_type);
        tvFirstDetected = findViewById(R.id.tv_first_detected);
        tvLastDetected = findViewById(R.id.tv_last_detected);
        tvDetectionCount = findViewById(R.id.tv_detection_count);
        btnMakeTarget = findViewById(R.id.btn_make_target);
        btnMakeSafe = findViewById(R.id.btn_make_safe);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void loadDeviceData() {
        MainDatabaseHelper dbHelper = new MainDatabaseHelper(this);

        // Получаем историю устройства
        List<MainDatabaseHelper.DeviceLocation> history =
                dbHelper.getDeviceHistoryByMac(tableName, deviceMac);

        if (!history.isEmpty()) {
            detectionCount = history.size();

            // Ищем самую старую и самую новую запись
            MainDatabaseHelper.DeviceLocation firstLocation = history.get(0);
            MainDatabaseHelper.DeviceLocation lastLocation = history.get(0);
            long minTimestamp = Long.parseLong(firstLocation.timestamp);
            long maxTimestamp = Long.parseLong(firstLocation.timestamp);

            for (MainDatabaseHelper.DeviceLocation loc : history) {
                long timestamp = Long.parseLong(loc.timestamp);

                // Ищем самую старую запись (минимальное время)
                if (timestamp < minTimestamp) {
                    minTimestamp = timestamp;
                    firstLocation = loc;
                }

                // Ищем самую новую запись (максимальное время)
                if (timestamp > maxTimestamp) {
                    maxTimestamp = timestamp;
                    lastLocation = loc;
                }
            }

            firstDetectionTime = minTimestamp;
            lastDetectionTime = maxTimestamp;

            // Получаем тип устройства из первой записи
            if (deviceType == null) {
                // Можно определить по MAC или другим параметрам
                deviceType = "Wi-Fi/Bluetooth";
            }

            // Получаем текущий статус устройства из БД
            if (currentStatus == null || "GREY".equalsIgnoreCase(currentStatus)) {
                currentStatus = getDeviceStatus(tableName, deviceMac);
            }

            // Собираем точки для карты в хронологическом порядке
            // Сортируем историю по времени для правильного отображения на карте
            List<MainDatabaseHelper.DeviceLocation> sortedHistory = new ArrayList<>(history);
            sortedHistory.sort((loc1, loc2) -> {
                long time1 = Long.parseLong(loc1.timestamp);
                long time2 = Long.parseLong(loc2.timestamp);
                return Long.compare(time1, time2);
            });

            for (MainDatabaseHelper.DeviceLocation loc : sortedHistory) {
                deviceHistoryPoints.add(new GeoPoint(loc.latitude, loc.longitude));
                deviceTimestamps.add(Long.parseLong(loc.timestamp));
            }
        }
    }

    public String getDeviceStatus(String tableName, String mac) {
        MainDatabaseHelper dbHelper = new MainDatabaseHelper(this);
        return dbHelper.getStatusFromServiceTables(mac);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar_map);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            String title = (deviceName != null && !deviceName.isEmpty())
                    ? deviceName
                    : (deviceMac != null ? deviceMac : "Карта устройства");
            getSupportActionBar().setTitle(title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupButtons() {
        updateStatusToggle();

        btnMakeTarget.setOnClickListener(v -> {
            if (!"TARGET".equalsIgnoreCase(currentStatus)) {
                changeDeviceStatus("TARGET");
            }
        });

        btnMakeSafe.setOnClickListener(v -> {
            if (!"SAFE".equalsIgnoreCase(currentStatus)) {
                changeDeviceStatus("SAFE");
            }
        });
    }

    private void updateStatusToggle() {
        String status = currentStatus != null ? currentStatus.toUpperCase(Locale.US) : "GREY";
        boolean isTarget = "TARGET".equals(status);
        boolean isSafe = "SAFE".equals(status);

        if (isTarget) {
            statusHeader.setBackgroundColor(Color.parseColor("#FF3B30"));
        } else if (isSafe) {
            statusHeader.setBackgroundColor(Color.parseColor("#34C759"));
        } else {
            statusHeader.setBackgroundColor(Color.parseColor("#808080"));
        }

        btnMakeTarget.setAlpha(isTarget ? 0.45f : 1f);
        btnMakeSafe.setAlpha(isSafe ? 0.45f : 1f);
    }

    private void changeDeviceStatus(String newStatus) {
        MainDatabaseHelper dbHelper = new MainDatabaseHelper(this);

        if (deviceMac != null && tableName != null) {
            int affected = dbHelper.updateDeviceStatus(tableName, deviceMac, newStatus);

            if (affected >= 0) {
                currentStatus = newStatus;
                updateStatusToggle();
                displayDeviceInfo();
                showMapFragment();

                String message = "TARGET".equalsIgnoreCase(newStatus)
                        ? "Устройство помечено как TARGET"
                        : "Устройство помечено как SAFE";

                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Ошибка обновления статуса", Toast.LENGTH_SHORT).show();
            }
        }


    }

    private void displayDeviceInfo() {
        TextView tvSubtitle = findViewById(R.id.tv_device_subtitle);
        TextView tvCid = findViewById(R.id.tv_cid);
        View rowCid = findViewById(R.id.row_cid);

        tvDeviceName.setText(
                (deviceName != null && !deviceName.trim().isEmpty()) ? deviceName : "Unknown"
        );

        String subtitle = "";
        if (deviceType != null && !deviceType.trim().isEmpty()) {
            subtitle = deviceType;
        }
        if (detectionCount > 0) {
            subtitle = subtitle.isEmpty()
                    ? "[" + detectionCount + "]"
                    : subtitle + " [" + detectionCount + "]";
        }
        tvSubtitle.setText(subtitle);

        tvMacAddress.setText(deviceMac != null ? deviceMac : "Неизвестно");
        tvDeviceType.setText(deviceType != null ? deviceType : "Неизвестно");

        boolean isCell = deviceType != null &&
                (deviceType.equalsIgnoreCase("Cell")
                        || deviceType.equalsIgnoreCase("Cellular")
                        || deviceType.equalsIgnoreCase("LTE")
                        || deviceType.equalsIgnoreCase("GSM"));

        if (isCell) {
            rowCid.setVisibility(View.VISIBLE);
            tvCid.setText(extractCidOrFallback());
        } else {
            rowCid.setVisibility(View.GONE);
        }

        if (firstDetectionTime > 0) {
            tvFirstDetected.setText(dateFormat.format(new Date(firstDetectionTime)));
        } else {
            tvFirstDetected.setText("Неизвестно");
        }

        if (lastDetectionTime > 0) {
            tvLastDetected.setText(dateFormat.format(new Date(lastDetectionTime)));
        } else {
            tvLastDetected.setText("Неизвестно");
        }

        tvDetectionCount.setText(String.valueOf(detectionCount));
    }

    private String extractCidOrFallback() {
        if (deviceMac == null || deviceMac.trim().isEmpty()) {
            return "Неизвестно";
        }

        // Если у тебя cell-id хранится как 250_1_36340_18168327
        if (deviceMac.contains("_")) {
            String[] parts = deviceMac.split("_");
            if (parts.length > 0) {
                return parts[parts.length - 1];
            }
        }

        return deviceMac;
    }

    private void showMapFragment() {
        mapFragment = new ActivityMapOSM();

        // Передаем данные во фрагмент
        Bundle args = new Bundle();
        if (!deviceHistoryPoints.isEmpty()) {
            double[] lats = new double[deviceHistoryPoints.size()];
            double[] lons = new double[deviceHistoryPoints.size()];
            String[] timestamps = new String[deviceTimestamps.size()];

            for (int i = 0; i < deviceHistoryPoints.size(); i++) {
                lats[i] = deviceHistoryPoints.get(i).getLatitude();
                lons[i] = deviceHistoryPoints.get(i).getLongitude();
            }

            for (int i = 0; i < deviceTimestamps.size(); i++) {
                timestamps[i] = timeFormat.format(new Date(deviceTimestamps.get(i)));
            }

            args.putDoubleArray("history_latitudes", lats);
            args.putDoubleArray("history_longitudes", lons);
            args.putStringArray("history_timestamps", timestamps);
            args.putString("device_mac", deviceMac);
            args.putString("device_name", deviceName);
            args.putInt("history_count", deviceHistoryPoints.size());
            args.putString("device_status", currentStatus); // Передаем статус для маркеров
        }
        mapFragment.setArguments(args);

        // Добавляем фрагмент
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.map_container, mapFragment)
                .commit();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}