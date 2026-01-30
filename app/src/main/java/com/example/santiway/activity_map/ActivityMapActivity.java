package com.example.santiway.activity_map;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import com.example.santiway.upload_data.MainDatabaseHelper;
import androidx.appcompat.widget.Toolbar;

import com.example.santiway.R;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;

public class ActivityMapActivity extends AppCompatActivity {

    private ActivityMapOSM mapFragment;
    private List<GeoPoint> deviceHistoryPoints = new ArrayList<>();
    private String deviceMac;
    private String deviceName;
    private String tableName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_activity);

        // Получаем данные из Intent
        Intent intent = getIntent();
        if (intent != null) {
            double latitude = intent.getDoubleExtra("latitude", 0);
            double longitude = intent.getDoubleExtra("longitude", 0);
            deviceName = intent.getStringExtra("device_name");
            deviceMac = intent.getStringExtra("device_mac");
            tableName = intent.getStringExtra("table_name");

            // Получаем историю устройства из базы данных
            if (deviceMac != null && tableName != null) {
                MainDatabaseHelper dbHelper = new MainDatabaseHelper(this);
                List<MainDatabaseHelper.DeviceLocation> history =
                        dbHelper.getDeviceHistoryByMac(tableName, deviceMac);

                // Добавляем все точки из истории
                for (MainDatabaseHelper.DeviceLocation loc : history) {
                    deviceHistoryPoints.add(new GeoPoint(loc.latitude, loc.longitude));
                }

                // Если есть текущие координаты, добавляем их тоже
                if (latitude != 0 && longitude != 0) {
                    deviceHistoryPoints.add(0, new GeoPoint(latitude, longitude)); // Добавляем в начало как текущую
                }
            }
        }

        // Настройка Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar_map);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(deviceName != null ? deviceName : "Карта устройства");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Создаем фрагмент с картой
        mapFragment = new ActivityMapOSM();

        // Передаем историю точек во фрагмент
        Bundle args = new Bundle();
        if (!deviceHistoryPoints.isEmpty()) {
            // Преобразуем список точек в массивы
            double[] lats = new double[deviceHistoryPoints.size()];
            double[] lons = new double[deviceHistoryPoints.size()];
            String[] timestamps = new String[deviceHistoryPoints.size()];

            for (int i = 0; i < deviceHistoryPoints.size(); i++) {
                lats[i] = deviceHistoryPoints.get(i).getLatitude();
                lons[i] = deviceHistoryPoints.get(i).getLongitude();
                timestamps[i] = "Запись #" + (i + 1);
            }

            args.putDoubleArray("history_latitudes", lats);
            args.putDoubleArray("history_longitudes", lons);
            args.putStringArray("history_timestamps", timestamps);
            args.putString("device_mac", deviceMac);
            args.putString("device_name", deviceName);
            args.putInt("history_count", deviceHistoryPoints.size());
        }
        mapFragment.setArguments(args);

        // Добавляем фрагмент в контейнер
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