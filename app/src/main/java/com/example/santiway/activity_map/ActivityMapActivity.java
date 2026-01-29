package com.example.santiway.activity_map;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.santiway.R;
import com.example.santiway.DeviceListActivity;

import org.osmdroid.util.GeoPoint;

public class ActivityMapActivity extends AppCompatActivity {

    private ActivityMapOSM mapFragment;
    private GeoPoint deviceLocation;
    private String deviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // Получаем данные из Intent
        Intent intent = getIntent();
        if (intent != null) {
            double latitude = intent.getDoubleExtra("latitude", 0);
            double longitude = intent.getDoubleExtra("longitude", 0);
            deviceName = intent.getStringExtra("device_name");

            if (latitude != 0 && longitude != 0) {
                deviceLocation = new GeoPoint(latitude, longitude);
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

        // Передаем координаты устройства во фрагмент
        Bundle args = new Bundle();
        if (deviceLocation != null) {
            args.putDouble("device_latitude", deviceLocation.getLatitude());
            args.putDouble("device_longitude", deviceLocation.getLongitude());
            args.putString("device_name", deviceName);
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