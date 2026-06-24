package com.example.santiway.activity_map;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
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
import com.example.santiway.LocaleHelper;
import com.example.santiway.BaseLocalizedActivity;

import org.osmdroid.util.GeoPoint;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ActivityMapActivity extends BaseLocalizedActivity {

    private ActivityMapOSM mapFragment;
    private List<GeoPoint> deviceHistoryPoints = new ArrayList<>();
    private List<Long> deviceTimestamps = new ArrayList<>();

    // UI элементы
    private View deviceInfoCard;
    private TextView tvMacAddress;
    private TextView tvDeviceName;
    private TextView tvDeviceType;
    private TextView tvFirstDetected;
    private TextView tvLastDetected;
    private TextView tvDetectionCount;
    private Button btnMakeTarget;
    private Button btnMakeSafe;
    private Toolbar toolbarMap;
    private CharSequence toolbarTitle;

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
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_activity);
        applyNavigationBarColor();

        // Инициализация UI
        initViews();

        View root = findViewById(R.id.root_activity_map);
        toolbarMap = findViewById(R.id.toolbar_map);
        View actionsContainer = findViewById(R.id.status_actions_container);
        View infoCard = findViewById(R.id.device_info_card);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            int toolbarHeight = bars.top + dpToPx(56);

            if (toolbarMap != null) {
                ViewGroup.LayoutParams toolbarLp = toolbarMap.getLayoutParams();
                toolbarLp.height = toolbarHeight;
                toolbarMap.setLayoutParams(toolbarLp);

                toolbarMap.setPadding(
                        toolbarMap.getPaddingLeft(),
                        bars.top,
                        toolbarMap.getPaddingRight(),
                        toolbarMap.getPaddingBottom()
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
        deviceInfoCard = findViewById(R.id.device_info_card);
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

    private void applyNavigationBarColor() {
        getWindow().setNavigationBarColor(Color.parseColor("#172A46"));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int flags = getWindow().getDecorView().getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    private void loadDeviceData() {
        MainDatabaseHelper dbHelper = new MainDatabaseHelper(this);
        int summaryPointLimit = getSharedPreferences("AppSettings", MODE_PRIVATE)
                .getInt("map_point_limit", 100);
        MainDatabaseHelper.DeviceHistorySummary summary =
                dbHelper.getDeviceHistorySummaryByKey(tableName, deviceMac, deviceType, summaryPointLimit);

        if (!summary.isEmpty()) {
            detectionCount = summary.detectionCount;
            firstDetectionTime = summary.firstTimestamp;
            lastDetectionTime = summary.lastTimestamp;

            if (deviceType == null) {
                deviceType = "Wi-Fi/Bluetooth";
            }

            if (currentStatus == null || "GREY".equalsIgnoreCase(currentStatus)) {
                currentStatus = dbHelper.getStatusFromServiceTables(deviceMac);
            }

            for (MainDatabaseHelper.DeviceLocation loc : summary.points) {
                deviceHistoryPoints.add(new GeoPoint(loc.latitude, loc.longitude));
                deviceTimestamps.add(Long.parseLong(loc.timestamp));
            }
            dbHelper.close();
            return;
        }

        // Получаем историю устройства
        List<MainDatabaseHelper.DeviceLocation> history =
                dbHelper.getDeviceHistoryByKey(tableName, deviceMac, deviceType);

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

            int pointLimit = getSharedPreferences("AppSettings", MODE_PRIVATE)
                    .getInt("map_point_limit", 100);

            int startIndex = Math.max(0, sortedHistory.size() - pointLimit);

            for (int i = startIndex; i < sortedHistory.size(); i++) {
                MainDatabaseHelper.DeviceLocation loc = sortedHistory.get(i);
                deviceHistoryPoints.add(new GeoPoint(loc.latitude, loc.longitude));
                deviceTimestamps.add(Long.parseLong(loc.timestamp));
            }
        }
        dbHelper.close();
    }

    public String getDeviceStatus(String tableName, String mac) {
        MainDatabaseHelper dbHelper = new MainDatabaseHelper(this);
        try {
            return dbHelper.getStatusFromServiceTables(mac);
        } finally {
            dbHelper.close();
        }
    }

    private void setupToolbar() {
        toolbarMap = findViewById(R.id.toolbar_map);
        setSupportActionBar(toolbarMap);
        toolbarMap.getNavigationIcon().setTint(Color.WHITE);
        if (getSupportActionBar() != null) {
            String title = (deviceName != null && !deviceName.isEmpty())
                    ? deviceName
                    : (deviceMac != null ? deviceMac : getString(R.string.device_map_title));
            toolbarTitle = title;
            getSupportActionBar().setTitle(toolbarTitle);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        toolbarMap.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupButtons() {
        updateStatusToggle();
    }

    private void updateStatusToggle() {
        String status = currentStatus != null ? currentStatus.toUpperCase(Locale.US) : "GREY";

        if ("TARGET".equals(status)) {
            setInfoCardColor("#CCFF3B30");

            setupStatusButton(btnMakeTarget, getString(R.string.grey_action), "#808080", "GREY");
            setupStatusButton(btnMakeSafe, getString(R.string.status_safe_en), "#34C759", "SAFE");

        } else if ("SAFE".equals(status)) {
            setInfoCardColor("#CC34C759");

            setupStatusButton(btnMakeTarget, getString(R.string.status_target_en), "#FF3B30", "TARGET");
            setupStatusButton(btnMakeSafe, getString(R.string.grey_action), "#808080", "GREY");

        } else {
            setInfoCardColor("#CC172A46");

            setupStatusButton(btnMakeTarget, getString(R.string.status_target_en), "#FF3B30", "TARGET");
            setupStatusButton(btnMakeSafe, getString(R.string.status_safe_en), "#34C759", "SAFE");
        }
    }

    private void setupStatusButton(Button button, String text, String colorHex, String statusToSet) {
        int backgroundColor = Color.parseColor(colorHex);
        button.setText(text);
        button.setAlpha(1f);
        button.setTextColor(readableTextColor(backgroundColor));
        button.setBackground(makeRoundedDrawable(colorHex, 18));

        button.setOnClickListener(v -> {
            if (!statusToSet.equalsIgnoreCase(currentStatus)) {
                changeDeviceStatus(statusToSet);
            }
        });
    }

    private void setInfoCardColor(String colorHex) {
        if (deviceInfoCard != null) {
            int backgroundColor = Color.parseColor(colorHex);
            deviceInfoCard.setBackground(makeRoundedDrawable(colorHex, 16));
            applyTextColorRecursive(deviceInfoCard, readableTextColor(backgroundColor));
            if (tvDetectionCount != null) {
                tvDetectionCount.setTextColor(Color.WHITE);
            }
        }
    }

    private void applyTextColorRecursive(View view, int textColor) {
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(textColor);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyTextColorRecursive(group.getChildAt(i), textColor);
            }
        }
    }

    private int readableTextColor(int backgroundColor) {
        double luminance = relativeLuminance(backgroundColor);
        double contrastWithBlack = (luminance + 0.05d) / 0.05d;
        double contrastWithWhite = 1.05d / (luminance + 0.05d);
        return contrastWithBlack >= contrastWithWhite ? Color.BLACK : Color.WHITE;
    }

    private double relativeLuminance(int color) {
        double red = linearColor(Color.red(color) / 255d);
        double green = linearColor(Color.green(color) / 255d);
        double blue = linearColor(Color.blue(color) / 255d);
        return 0.2126d * red + 0.7152d * green + 0.0722d * blue;
    }

    private double linearColor(double channel) {
        return channel <= 0.03928d
                ? channel / 12.92d
                : Math.pow((channel + 0.055d) / 1.055d, 2.4d);
    }

    private GradientDrawable makeRoundedDrawable(String colorHex, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(colorHex));
        drawable.setCornerRadius(dpToPx(radiusDp));
        return drawable;
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

                String message;
                if ("TARGET".equalsIgnoreCase(newStatus)) {
                    message = getString(R.string.toast_device_marked_target);
                } else if ("SAFE".equalsIgnoreCase(newStatus)) {
                    message = getString(R.string.toast_device_marked_safe);
                } else {
                    message = getString(R.string.toast_device_marked_grey);
                }

                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.error_status_update), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void displayDeviceInfo() {
        TextView tvSubtitle = findViewById(R.id.tv_device_subtitle);
        TextView tvCid = findViewById(R.id.tv_cid);
        View rowCid = findViewById(R.id.row_cid);

        tvDeviceName.setText(
                (deviceName != null && !deviceName.trim().isEmpty()) ? deviceName : getString(R.string.unknown_value)
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

        tvMacAddress.setText(deviceMac != null ? deviceMac : getString(R.string.unknown_value));
        tvDeviceType.setText(deviceType != null ? deviceType : getString(R.string.unknown_value));

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
            tvFirstDetected.setText(getString(R.string.unknown_value));
        }

        if (lastDetectionTime > 0) {
            tvLastDetected.setText(dateFormat.format(new Date(lastDetectionTime)));
        } else {
            tvLastDetected.setText(getString(R.string.unknown_value));
        }

        tvDetectionCount.setText(String.valueOf(detectionCount));
    }

    private String extractCidOrFallback() {
        if (deviceMac == null || deviceMac.trim().isEmpty()) {
            return getString(R.string.unknown_value);
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
        mapFragment.setDrawerOverlayBehavior(
                this::setDrawerChromeHidden,
                findViewById(R.id.status_actions_container),
                deviceInfoCard
        );

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

    private void setDrawerChromeHidden(boolean hidden) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(hidden ? "" : toolbarTitle);
        } else if (toolbarMap != null) {
            toolbarMap.setTitle(hidden ? "" : toolbarTitle);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
