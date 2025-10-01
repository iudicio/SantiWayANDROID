package com.example.santiway;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.santiway.upload_data.MainDatabaseHelper;
import com.google.android.material.tabs.TabLayout;
import java.util.ArrayList;
import java.util.List;

public class DeviceListActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private TabLayout tabLayout;
    private RecyclerView devicesRecyclerView;
    private MainDatabaseHelper databaseHelper; // Изменено на MainDatabaseHelper
    private DeviceListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tab_layout);
        devicesRecyclerView = findViewById(R.id.devices_recycler_view);

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Database Tables");

        databaseHelper = new MainDatabaseHelper(this); // Изменено на MainDatabaseHelper
        devicesRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Инициализация RecyclerView с пустым списком
        adapter = new DeviceListAdapter(new ArrayList<>());
        devicesRecyclerView.setAdapter(adapter);

        // Динамическая загрузка вкладок из базы данных
        setupTabLayout();
    }

    private void setupTabLayout() {
        tabLayout.removeAllTabs();
        List<String> tables = databaseHelper.getAllTables();

        for (String tableName : tables) {
            tabLayout.addTab(tabLayout.newTab().setText(tableName));
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                String tableName = tab.getText().toString();
                loadDevicesForTable(tableName);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Ничего не делаем
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                String tableName = tab.getText().toString();
                loadDevicesForTable(tableName);
            }
        });

        // Загружаем данные для первой вкладки по умолчанию
        if (!tables.isEmpty()) {
            loadDevicesForTable(tables.get(0));
        }
    }

    private void loadDevicesForTable(String tableName) {
        List<Device> deviceList = databaseHelper.getAllDataFromTable(tableName);
        adapter.updateData(deviceList);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Вспомогательный класс Device, если он еще не был определен
    public static class Device {
        String name;
        String type;
        String location;
        String time;

        public Device(String name, String type, String location, String time) {
            this.name = name;
            this.type = type;
            this.location = location;
            this.time = time;
        }
    }
}