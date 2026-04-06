package com.example.santiway.activity_map;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

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
    private Button btnMakeClear;

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

        // Получаем данные из Intent
        Intent intent = getIntent();
        if (intent != null) {
            double latitude = intent.getDoubleExtra("latitude", 0);
            double longitude = intent.getDoubleExtra("longitude", 0);
            deviceName = intent.getStringExtra("device_name");
            deviceMac = intent.getStringExtra("device_mac");
            deviceType = intent.getStringExtra("device_type");
            tableName = intent.getStringExtra("table_name");

            // Получаем полную историю устройства из базы данных
            if (deviceMac != null && tableName != null) {
                loadDeviceData();
            }
        }

        String deviceStatus = intent.getStringExtra("device_status");
        if (deviceStatus != null) {
            currentStatus = deviceStatus;
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
        btnMakeClear = findViewById(R.id.btn_make_clear);
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
            currentStatus = getDeviceStatus(tableName, deviceMac);

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
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT status FROM \"" + tableName + "\" WHERE bssid = ? ORDER BY timestamp DESC LIMIT 1",
                    new String[]{mac}
            );

            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex("status");
                if (index != -1) {
                    String status = cursor.getString(index);
                    return (status != null && !status.isEmpty()) ? status : "GREY";
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting device status: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }

        return "GREY";
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
        // Настройка видимости кнопок в зависимости от статуса
        updateButtonVisibility();

        // Обработчики нажатий
        btnMakeTarget.setOnClickListener(v -> changeDeviceStatus("TARGET"));
        btnMakeSafe.setOnClickListener(v -> changeDeviceStatus("SAFE"));
        btnMakeClear.setOnClickListener(v -> changeDeviceStatus("GREY"));
    }

    private void updateButtonVisibility() {
        String status = currentStatus != null ? currentStatus.toUpperCase(Locale.US) : "GREY";

        switch (status) {
            case "TARGET":
                statusHeader.setBackgroundColor(Color.parseColor("#FF3B30"));
                btnMakeTarget.setVisibility(View.GONE);
                btnMakeSafe.setVisibility(View.VISIBLE);
                btnMakeClear.setVisibility(View.VISIBLE);
                break;

            case "SAFE":
                statusHeader.setBackgroundColor(Color.parseColor("#34C759"));
                btnMakeTarget.setVisibility(View.VISIBLE);
                btnMakeSafe.setVisibility(View.GONE);
                btnMakeClear.setVisibility(View.VISIBLE);
                break;

            case "GREY":
            default:
                statusHeader.setBackgroundColor(Color.parseColor("#808080"));
                btnMakeTarget.setVisibility(View.VISIBLE);
                btnMakeSafe.setVisibility(View.VISIBLE);
                btnMakeClear.setVisibility(View.GONE);
                break;
        }
    }

    private void changeDeviceStatus(String newStatus) {
        MainDatabaseHelper dbHelper = new MainDatabaseHelper(this);

        if (deviceMac != null && tableName != null) {
            int affected = dbHelper.updateDeviceStatus(tableName, deviceMac, newStatus);

            if (affected > 0) {
                currentStatus = newStatus;
                updateButtonVisibility();

                String message = "";
                switch (newStatus) {
                    case "TARGET":
                        message = "Устройство помечено как TARGET";
                        break;
                    case "SAFE":
                        message = "Устройство помечено как SAFE";
                        break;
                    default:
                        message = "Устройство переведено в GREY";
                        break;
                }

                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

                // Обновляем статус в DeviceListActivity через Broadcast
                sendStatusUpdateBroadcast();
            } else {
                Toast.makeText(this, "Ошибка обновления статуса", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void sendStatusUpdateBroadcast() {
        Intent broadcast = new Intent("DEVICE_STATUS_UPDATED");
        broadcast.putExtra("mac", deviceMac);
        broadcast.putExtra("status", currentStatus);
        sendBroadcast(broadcast);
    }

    private void displayDeviceInfo() {
        // MAC адрес
        if (deviceMac != null) {
            tvMacAddress.setText(deviceMac);
        }

        // Название устройства
        if (deviceName != null && !deviceName.isEmpty()) {
            tvDeviceName.setText(deviceName);
        } else {
            tvDeviceName.setText("Неизвестно");
        }

        // Тип устройства
        if (deviceType != null) {
            tvDeviceType.setText(deviceType);
        }

        // Время первого обнаружения
        if (firstDetectionTime > 0) {
            String firstTime = dateFormat.format(new Date(firstDetectionTime));
            tvFirstDetected.setText(firstTime);
        } else {
            tvFirstDetected.setText("Неизвестно");
        }

        // Время последнего обнаружения
        if (lastDetectionTime > 0) {
            String lastTime = dateFormat.format(new Date(lastDetectionTime));
            tvLastDetected.setText(lastTime);
        } else {
            tvLastDetected.setText("Неизвестно");
        }

        // Количество обнаружений
        tvDetectionCount.setText(String.valueOf(detectionCount));
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