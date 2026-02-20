package com.example.santiway;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
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
import android.os.Parcel;
import android.os.Parcelable;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
        // 1. Находим кнопку Alarm в макете DeviceListActivity
        LinearLayout alarmButton = findViewById(R.id.action_alarm);

        if (alarmButton != null) {
            alarmButton.setOnClickListener(v -> {
                // 2. Определяем, какая папка сейчас выбрана в TabLayout
                int selectedTabPos = tabLayout.getSelectedTabPosition();
                if (selectedTabPos != -1) {
                    String currentFolder = tabLayout.getTabAt(selectedTabPos).getText().toString();

                    // 3. Используем твой MainDatabaseHelper (без изменений метода)
                    MainDatabaseHelper dbHelper = new MainDatabaseHelper(DeviceListActivity.this);
                    int rowsAffected = dbHelper.updateAllDeviceStatusForTable(currentFolder, "ALARM");

                    // 4. Показываем результат, чтобы убедиться, что всё сработало
                    Toast.makeText(DeviceListActivity.this,
                            "Обновлено устройств: " + rowsAffected + " в папке " + currentFolder,
                            Toast.LENGTH_SHORT).show();

                    // 5. Опционально: обнови список на экране, если нужно сразу увидеть изменения
                    // refreshListData(currentFolder);
                } else {
                    Toast.makeText(DeviceListActivity.this, "Ошибка: вкладка не выбрана", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            // Если в Logcat будет эта ошибка, значит кнопка не найдена в XML
            Log.e("ALARM_ERROR", "Кнопка action_alarm не найдена в DeviceListActivity");
        }
        LinearLayout clearButton = findViewById(R.id.action_clear);

        if (clearButton != null) {
            clearButton.setOnClickListener(v -> {
                // 1. Получаем имя текущей папки (таблицы)
                int selectedTabPos = tabLayout.getSelectedTabPosition();
                if (selectedTabPos == -1) return;
                String currentFolder = tabLayout.getTabAt(selectedTabPos).getText().toString();

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
            safeButton.setOnClickListener(v -> {
                int pos = tabLayout.getSelectedTabPosition();
                if (pos != -1) {
                    String folder = tabLayout.getTabAt(pos).getText().toString();
                    int count = new MainDatabaseHelper(this).updateAllDeviceStatusForTable(folder, "SAFE");
                    Toast.makeText(this, count + " устройств теперь SAFE", Toast.LENGTH_SHORT).show();
                }
            });
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

        adapter.setCurrentTableName(tableName);
        isLoading = true;

        new Thread(() -> {
            List<Device> deviceList;

            // ДЛЯ УНИКАЛЬНЫХ УСТРОЙСТВ используем отдельную логику
            if ("unique_devices".equals(tableName)) {
                // Получаем из UniqueDevicesHelper
                UniqueDevicesHelper uniqueHelper = new UniqueDevicesHelper(DeviceListActivity.this);
                deviceList = uniqueHelper.getAllDevices();
                // Для уникальных устройств пагинация не нужна
                hasMoreData = false;
            } else {
                // Для обычных таблиц используем пагинацию
                deviceList = databaseHelper.getAllDataFromTableWithPagination(
                        tableName,
                        currentOffset,
                        PAGE_SIZE
                );

                if (deviceList.size() < PAGE_SIZE) {
                    hasMoreData = false;
                }
                currentOffset += deviceList.size();
            }

            final List<Device> finalDeviceList = deviceList;

            runOnUiThread(() -> {
                adapter.hideLoading();

                if (isFirstLoad) {
                    adapter.updateData(finalDeviceList);
                } else {
                    adapter.addData(finalDeviceList);
                }

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