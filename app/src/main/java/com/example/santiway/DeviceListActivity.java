package com.example.santiway;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.santiway.activity_map.ActivityMapActivity;
import com.example.santiway.upload_data.MainDatabaseHelper;
import com.google.android.material.tabs.TabLayout;
import java.util.ArrayList;
import java.util.List;
import android.os.Parcel;
import android.os.Parcelable;
import android.widget.Toast;

public class DeviceListActivity extends AppCompatActivity implements DeviceListAdapter.OnDeviceClickListener {

    private Toolbar toolbar;
    private TabLayout tabLayout;
    private RecyclerView devicesRecyclerView;
    private MainDatabaseHelper databaseHelper;
    private DeviceListAdapter adapter;
    private LinearLayoutManager layoutManager;

    // Для пагинации
    private boolean isLoading = false;
    private boolean hasMoreData = true;
    private int currentOffset = 0;
    private final int PAGE_SIZE = 50; // Количество элементов на странице
    private String currentTable = "";

    // Хендлер для задержки
    private Handler handler = new Handler();
    @Override
    public void onDeviceClick(Device device, String tableName, int position) {
        openDeviceMap(device, tableName, position);
    }


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

        databaseHelper = new MainDatabaseHelper(this);

        // Инициализация LayoutManager
        layoutManager = new LinearLayoutManager(this);
        devicesRecyclerView.setLayoutManager(layoutManager);

        // Инициализация адаптера - передаем this как слушатель
        adapter = new DeviceListAdapter(new ArrayList<>(), this, this);
        devicesRecyclerView.setAdapter(adapter);

        // Добавление слушателя для бесконечного скроллинга
        devicesRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (!isLoading && hasMoreData && dy > 0) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                    // Проверяем, достигли ли мы конца списка
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0) {

                        loadMoreData();
                    }
                }
            }
        });

        // Динамическая загрузка вкладок из базы данных
        setupTabLayout();
    }

    // Метод для открытия карты устройства
    private void openDeviceMap(Device device, String tableName, int position) {
        if (tableName == null || tableName.isEmpty()) {
            Toast.makeText(this, "Ошибка: имя таблицы не указано", Toast.LENGTH_SHORT).show();
            return;
        }

        // Получаем полные данные устройства с MAC адресом
        Device fullDevice = databaseHelper.getDeviceWithMac(tableName, position);

        if (fullDevice == null) {
            Toast.makeText(this, "Не удалось получить данные устройства", Toast.LENGTH_SHORT).show();
            return;
        }

        // Получаем историю устройства по MAC адресу
        List<MainDatabaseHelper.DeviceLocation> history =
                databaseHelper.getDeviceHistoryByMac(tableName, fullDevice.getMac());

        if (history.isEmpty()) {
            Toast.makeText(this, "Нет данных о местоположении устройства", Toast.LENGTH_SHORT).show();
            return;
        }

        // Берем последнюю точку из истории
        MainDatabaseHelper.DeviceLocation lastLocation = history.get(0);

        // Открываем новую Activity с картой и информацией
        Intent intent = new Intent(this, ActivityMapActivity.class);
        intent.putExtra("latitude", lastLocation.latitude);
        intent.putExtra("longitude", lastLocation.longitude);
        intent.putExtra("device_name", device.getName());
        intent.putExtra("device_mac", fullDevice.getMac());
        intent.putExtra("device_type", device.getType());
        intent.putExtra("table_name", tableName);
        startActivity(intent);
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
                // Сброс состояния пагинации при смене вкладки
                resetPagination();
                currentTable = tab.getText().toString();
                loadDevicesForTable(currentTable, true);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Ничего не делаем
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                String tableName = tab.getText().toString();
                if (!tableName.equals(currentTable)) {
                    resetPagination();
                    currentTable = tableName;
                    loadDevicesForTable(currentTable, true);
                }
            }
        });

        // Загружаем данные для первой вкладки по умолчанию
        if (!tables.isEmpty()) {
            currentTable = tables.get(0);
            loadDevicesForTable(currentTable, true);
        }
    }

    private void resetPagination() {
        currentOffset = 0;
        hasMoreData = true;
        isLoading = false;
        adapter.clearData();
    }

    private void loadDevicesForTable(String tableName, boolean isFirstLoad) {
        if (isFirstLoad) {
            currentOffset = 0;
            hasMoreData = true;
            adapter.showLoading(true);
        }

        // УСТАНАВЛИВАЕМ ТЕКУЩУЮ ТАБЛИЦУ В АДАПТЕР!
        adapter.setCurrentTableName(tableName);

        isLoading = true;

        // Загрузка данных в фоне
        new Thread(() -> {
            List<Device> deviceList = databaseHelper.getAllDataFromTableWithPagination(
                    tableName,
                    currentOffset,
                    PAGE_SIZE
            );

            // Обновляем UI в главном потоке
            runOnUiThread(() -> {
                adapter.hideLoading();

                if (isFirstLoad) {
                    adapter.updateData(deviceList);
                } else {
                    adapter.addData(deviceList);
                }

                // Проверяем, есть ли ещё данные
                if (deviceList.size() < PAGE_SIZE) {
                    hasMoreData = false;
                }

                currentOffset += deviceList.size();
                isLoading = false;
            });
        }).start();
    }

    private void loadMoreData() {
        if (!isLoading && hasMoreData && !currentTable.isEmpty()) {
            isLoading = true;
            adapter.showLoading(false);

            // Имитация задержки для плавности
            handler.postDelayed(() -> {
                loadDevicesForTable(currentTable, false);
            }, 500);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    // Вспомогательный класс Device (расширенный с добавлением полей из dev)
    public static class Device implements Parcelable {
        String name;
        String type;
        String location;
        String time;
        String mac;
        String status;

        public Device(String name, String type, String location, String time) {
            this.name = name;
            this.type = type;
            this.location = location;
            this.time = time;
        }

        public Device(String name, String type, String location, String time, String mac, String status) {
            this.name = name;
            this.type = type;
            this.location = location;
            this.time = time;
            this.mac = mac;
            this.status = status;
        }

        // Parcelable implementation
        protected Device(Parcel in) {
            name = in.readString();
            type = in.readString();
            location = in.readString();
            time = in.readString();
            mac = in.readString();
            status = in.readString();
        }

        public static final Creator<Device> CREATOR = new Creator<Device>() {
            @Override
            public Device createFromParcel(Parcel in) {
                return new Device(in);
            }

            @Override
            public Device[] newArray(int size) {
                return new Device[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(name);
            dest.writeString(type);
            dest.writeString(location);
            dest.writeString(time);
            dest.writeString(mac);
            dest.writeString(status);
        }

        // Getters
        public String getName() { return name; }
        public String getType() { return type; }
        public String getLocation() { return location; }
        public String getTime() { return time; }
        public String getMac() { return mac; }
        public String getStatus() { return status; }

        // Setters
        public void setStatus(String status) { this.status = status; }
        public void setMac(String mac) { this.mac = mac; }
    }
}