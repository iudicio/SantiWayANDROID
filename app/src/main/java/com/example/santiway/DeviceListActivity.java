package com.example.santiway;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.text.Editable;
import android.text.TextWatcher;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.santiway.activity_map.ActivityMapActivity;
import com.example.santiway.upload_data.MainDatabaseHelper;
import com.example.santiway.upload_data.UniqueDevicesHelper;
import com.google.android.material.tabs.TabLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

public class DeviceListActivity extends AppCompatActivity implements DeviceListAdapter.OnDeviceClickListener, StatusUpdateListener {

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
    private MaterialButton btnFilterTarget, btnFilterSafe, btnFilterAll;

    private final List<Device> allLoadedDevices = new ArrayList<>();
    private String currentStatusFilter = "ALL";
    private TextInputEditText etSearchDevice;
    private String currentSearchQuery = "";
    private boolean autoRefreshStarted = false;

    // Хендлер для задержки
    private Handler handler = new Handler();
    private final BroadcastReceiver devicesChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            String changedTable = intent.getStringExtra(MainDatabaseHelper.EXTRA_TABLE_NAME);
            if (changedTable == null || currentTable == null) return;
            if (!changedTable.equals(currentTable)) return;
            if (isLoading) return;

            handler.removeCallbacks(refreshRunnable);
            handler.postDelayed(refreshRunnable, 200);
        }
    };

    private final Runnable refreshRunnable = () -> {
        if (currentTable != null && !currentTable.isEmpty() && !isLoading) {
            loadDevicesForTable(currentTable, true);
        }
    };
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing() && !isDestroyed() && currentTable != null && !currentTable.isEmpty()) {
                if (!isLoading) {
                    loadDevicesForTable(currentTable, true);
                }
                handler.postDelayed(this, 1500); // 1.5 сек
            }
        }
    };
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
        btnFilterTarget = findViewById(R.id.btn_filter_target);
        btnFilterSafe = findViewById(R.id.btn_filter_safe);
        btnFilterAll = findViewById(R.id.btn_filter_all);
        etSearchDevice = findViewById(R.id.et_search_device);

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Database Tables");

        databaseHelper = new MainDatabaseHelper(this);
        LinearLayout clearButton = findViewById(R.id.action_clear);

        if (clearButton != null) {
            clearButton.setOnClickListener(v -> {
                // 1. Получаем имя текущей папки (таблицы)
                int selectedTabPos = tabLayout.getSelectedTabPosition();
                if (selectedTabPos == -1) return;
                String currentFolder = (String) tabLayout.getTabAt(selectedTabPos).getTag();

                // 2. Создаем диалог подтверждения
                AlertDialog dialog = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                        .setTitle("Очистка")
                        .setMessage("Удалить все записи из " + currentFolder + "?")
                        .setPositiveButton("УДАЛИТЬ", (d, which) -> {

                            // --- ЛОГИКА ОЧИСТКИ ---

                            // А. Удаляем данные из базы данных
                            MainDatabaseHelper dbHelper = new MainDatabaseHelper(this);
                            dbHelper.clearTableData(currentFolder);

                            // Б. Очищаем экран (через адаптер)
                            // Метод clearData() внутри вызывает notifyDataSetChanged()
                            if (adapter != null) {
                                adapter.clearData();
                            }

                            // В. Сбрасываем пагинацию
                            // Это важно, чтобы при добавлении новых данных загрузка началась с 0
                            currentOffset = 0;
                            hasMoreData = false;

                            Toast.makeText(this, "Данные в " + currentFolder + " удалены", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("ОТМЕНА", null)
                        .create();

                dialog.show();

                // 3. Стилизация кнопок (после вызова .show())
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#FF6B6B"));
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#3DDC84"));
            });
        }
        LinearLayout renameButton = findViewById(R.id.action_rename);

        if (renameButton != null) {
            renameButton.setOnClickListener(v -> {
                int selectedTabPos = tabLayout.getSelectedTabPosition();
                if (selectedTabPos == -1) return;

                String oldName = tabLayout.getTabAt(selectedTabPos).getText().toString();

                // Поле ввода
                final EditText input = new EditText(this);
                input.setText(oldName);
                input.setTextColor(Color.WHITE);

                FrameLayout container = new FrameLayout(this);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.leftMargin = 50; params.rightMargin = 50;
                input.setLayoutParams(params);
                container.addView(input);

                AlertDialog dialog = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                        .setTitle("Переименовать")
                        .setView(container)
                        .setPositiveButton("СОХРАНИТЬ", null) // Ставим null, чтобы диалог не закрылся сам при ошибке
                        .setNegativeButton("ОТМЕНА", null)
                        .create();

                dialog.show();

                // Переопределяем нажатие на "СОХРАНИТЬ", чтобы контролировать закрытие диалога
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    String newName = input.getText().toString().trim();

                    // 1. Проверка на пустое имя
                    if (newName.isEmpty()) {
                        input.setError("Имя не может быть пустым");
                        return;
                    }

                    // 2. ПРОВЕРКА НА СУЩЕСТВУЮЩЕЕ ИМЯ
                    boolean alreadyExists = false;
                    for (int i = 0; i < tabLayout.getTabCount(); i++) {
                        if (tabLayout.getTabAt(i).getText().toString().equalsIgnoreCase(newName)) {
                            alreadyExists = true;
                            break;
                        }
                    }

                    if (alreadyExists && !newName.equalsIgnoreCase(oldName)) {
                        input.setError("Папка с таким именем уже есть!");
                        Toast.makeText(this, "Название уже занято", Toast.LENGTH_SHORT).show();
                    } else {
                        // 3. Если всё ок — переименовываем
                        MainDatabaseHelper dbHelper = new MainDatabaseHelper(this);
                        dbHelper.renameTable(oldName, newName);

                        tabLayout.getTabAt(selectedTabPos).setText(newName);
                        dialog.dismiss(); // Закрываем только если переименовали успешно
                        Toast.makeText(this, "Готово!", Toast.LENGTH_SHORT).show();
                    }
                });

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#3DDC84"));
            });
        }
        LinearLayout safeButton = findViewById(R.id.action_safe);
        if (safeButton != null) {
            safeButton.setOnClickListener(v -> updateAllDevicesStatus("SAFE"));
        }

        LinearLayout targetButton = findViewById(R.id.action_alarm);
        if (targetButton != null) {
            targetButton.setOnClickListener(v -> updateAllDevicesStatus("TARGET"));
        }

        LinearLayout greyButton = findViewById(R.id.action_grey);
        if (greyButton != null) {
            greyButton.setOnClickListener(v -> updateAllDevicesStatus("GREY"));
        }

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
        setupFilterButtons();
        setupSearch();
    }

    @Override
    protected void onStart() {
        super.onStart();

        IntentFilter filter = new IntentFilter(MainDatabaseHelper.ACTION_DEVICES_CHANGED);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(devicesChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(devicesChangedReceiver, filter);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            unregisterReceiver(devicesChangedReceiver);
        } catch (Exception ignored) { }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!autoRefreshStarted) {
            handler.post(autoRefreshRunnable);
            autoRefreshStarted = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        handler.removeCallbacks(autoRefreshRunnable);
        autoRefreshStarted = false;
    }

    @Override
    public void onStatusUpdated() {
        runOnUiThread(() -> {
            if (currentTable != null && !currentTable.isEmpty() && !isLoading) {
                loadDevicesForTable(currentTable, true);
            }
        });
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
    // В файле DeviceListActivity.java
    public void shareDeviceAsJson(Device device) {
        // Исправлено: используем currentTable (твоя глобальная переменная)
        String jsonString = databaseHelper.getDeviceExportJson(currentTable, device.getMac());

        if (jsonString == null || jsonString.isEmpty()) {
            Toast.makeText(this, "Нет данных для экспорта", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            java.io.File cachePath = new java.io.File(getExternalCacheDir(), "exports");
            cachePath.mkdirs();
            java.io.File tempFile = new java.io.File(cachePath, "device_" + device.getMac().replace(":", "") + ".json");

            java.io.FileOutputStream stream = new java.io.FileOutputStream(tempFile);
            stream.write(jsonString.getBytes());
            stream.close();

            android.net.Uri contentUri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".provider", tempFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Отправить JSON"));
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getDisplayTableName(String tableName) {
        return tableName;
    }

    private void setupTabLayout() {
        tabLayout.removeAllTabs();
        List<String> tables = databaseHelper.getAllTables();

        for (String tableName : tables) {
            TabLayout.Tab tab = tabLayout.newTab()
                    .setText(getDisplayTableName(tableName));
            tab.setTag(tableName); // реальное имя таблицы
            tabLayout.addTab(tab);
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                resetPagination();
                currentTable = (String) tab.getTag();
                loadDevicesForTable(currentTable, true);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                String tableName = (String) tab.getTag();
                if (!tableName.equals(currentTable)) {
                    resetPagination();
                    currentTable = tableName;
                    loadDevicesForTable(currentTable, true);
                }
            }
        });

        if (!tables.isEmpty()) {
            currentTable = tables.get(0);
            loadDevicesForTable(currentTable, true);
        }
    }

    private void resetPagination() {
        currentOffset = 0;
        hasMoreData = true;
        isLoading = false;
        allLoadedDevices.clear();
        currentStatusFilter = "ALL";
        adapter.clearData();
        updateFilterButtonsUI();
    }

    private String getUniqueTableName(String folderName) {
        return folderName + "_unique";
    }

    private void loadDevicesForTable(String tableName, boolean isFirstLoad) {
        if (isFirstLoad) {
            currentOffset = 0;
            hasMoreData = true;
            adapter.showLoading(true);
        }

        adapter.setCurrentTableName(tableName);
        isLoading = true;

        new Thread(() -> {
            List<Device> deviceList = new ArrayList<>();

            try {
                String uniqueTableName = getUniqueTableName(tableName);
                UniqueDevicesHelper uniqueHelper =
                        new UniqueDevicesHelper(DeviceListActivity.this, uniqueTableName);

                if (currentSearchQuery == null || currentSearchQuery.isEmpty()) {
                    deviceList = uniqueHelper.getAllDevices();
                } else {
                    deviceList = uniqueHelper.getAllDevicesWithSearch(currentSearchQuery);
                }

                for (Device device : deviceList) {
                    String deviceKey = device.getMac();
                    String actualStatus = databaseHelper.getStatusFromServiceTables(deviceKey);
                    device.setStatus(actualStatus);
                }

                hasMoreData = false;
            } catch (Exception e) {
                Log.e("LOAD_DEVICES", "Ошибка загрузки устройств: " + e.getMessage(), e);
            }

            List<Device> finalDeviceList = deviceList;
            runOnUiThread(() -> {
                adapter.hideLoading();
                allLoadedDevices.clear();
                allLoadedDevices.addAll(finalDeviceList);

                applyCurrentFilter();
                isLoading = false;
            });
        }).start();
    }

    private void updateAllDevicesStatus(String status) {
        int pos = tabLayout.getSelectedTabPosition();
        if (pos == -1) {
            Toast.makeText(this, "Вкладка не выбрана", Toast.LENGTH_SHORT).show();
            return;
        }

        if (tabLayout.getTabAt(pos) == null) return;
        String folder = (String) tabLayout.getTabAt(pos).getTag();

        new Thread(() -> {
            int count = new MainDatabaseHelper(DeviceListActivity.this)
                    .updateAllDeviceStatusForTable(folder, status);

            runOnUiThread(() -> {
                Toast.makeText(
                        DeviceListActivity.this,
                        count + " устройств теперь " + status,
                        Toast.LENGTH_SHORT
                ).show();

                resetPagination();
                loadDevicesForTable(folder, true);
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

    private void setupFilterButtons() {
        if (btnFilterTarget != null) {
            btnFilterTarget.setOnClickListener(v -> {
                currentStatusFilter = "TARGET";
                applyCurrentFilter();
                updateFilterButtonsUI();
            });
        }

        if (btnFilterSafe != null) {
            btnFilterSafe.setOnClickListener(v -> {
                currentStatusFilter = "SAFE";
                applyCurrentFilter();
                updateFilterButtonsUI();
            });
        }

        if (btnFilterAll != null) {
            btnFilterAll.setOnClickListener(v -> {
                currentStatusFilter = "ALL";
                applyCurrentFilter();
                updateFilterButtonsUI();
            });
        }

        updateFilterButtonsUI();
    }

    private void setupSearch() {
        if (etSearchDevice == null) return;

        etSearchDevice.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacksAndMessages(null);

                handler.postDelayed(() -> {
                    currentSearchQuery = s.toString().trim();
                    resetPagination();
                    if (currentTable != null && !currentTable.isEmpty()) {
                        loadDevicesForTable(currentTable, true);
                    }
                }, 300);
            }
        });
    }

    private void applyCurrentFilter() {
        List<Device> filteredList = new ArrayList<>();

        if ("ALL".equalsIgnoreCase(currentStatusFilter)) {
            filteredList.addAll(allLoadedDevices);
        } else {
            for (Device device : allLoadedDevices) {
                String status = device.getStatus() != null
                        ? device.getStatus().trim().toUpperCase(Locale.ROOT)
                        : "GREY";

                if (status.equals(currentStatusFilter)) {
                    filteredList.add(device);
                }
            }
        }

        adapter.updateData(filteredList);
    }

    private void updateFilterButtonsUI() {
        if (btnFilterTarget == null || btnFilterSafe == null || btnFilterAll == null) return;

        btnFilterTarget.setAlpha("TARGET".equals(currentStatusFilter) ? 1.0f : 0.5f);
        btnFilterSafe.setAlpha("SAFE".equals(currentStatusFilter) ? 1.0f : 0.5f);
        btnFilterAll.setAlpha("ALL".equals(currentStatusFilter) ? 1.0f : 0.5f);
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
        long timestamp;

        public Device(String name, String type, String location, String time) {
            this.name = name;
            this.type = type;
            this.location = location;
            this.time = time;
            this.timestamp = 0L;
        }

        public Device(String name, String type, String location, String time, String mac, String status) {
            this.name = name;
            this.type = type;
            this.location = location;
            this.time = time;
            this.mac = mac;
            this.status = status;
            this.timestamp = 0L;
        }

        public Device(String name, String type, String location, String time, String mac, String status, long timestamp) {
            this.name = name;
            this.type = type;
            this.location = location;
            this.time = time;
            this.mac = mac;
            this.status = status;
            this.timestamp = timestamp;
        }

        protected Device(Parcel in) {
            name = in.readString();
            type = in.readString();
            location = in.readString();
            time = in.readString();
            mac = in.readString();
            status = in.readString();
            timestamp = in.readLong();
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
            dest.writeLong(timestamp);
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public String getLocation() { return location; }
        public String getTime() { return time; }
        public String getMac() { return mac; }
        public String getStatus() { return status; }
        public long getTimestamp() { return timestamp; }

        public void setStatus(String status) { this.status = status; }
        public void setMac(String mac) { this.mac = mac; }
    }
}