package com.example.santiway;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast; // НОВЫЙ ИМПОРТ
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.santiway.upload_data.MainDatabaseHelper;
import com.google.android.material.tabs.TabLayout;
import java.util.ArrayList;
import java.util.List;
import androidx.appcompat.app.AlertDialog;
// ДОБАВЛЕНЫ ИМПОРТЫ ДЛЯ Parcelable
import android.os.Parcel;
import android.os.Parcelable;
// НОВЫЙ ИМПОРТ ИНТЕРФЕЙСА
import com.example.santiway.StatusUpdateListener;

// Активити реализует оба интерфейса
public class DeviceListActivity extends AppCompatActivity implements DeviceListAdapter.OnInfoClickListener, StatusUpdateListener {
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

    private Toolbar toolbar;
    private TabLayout tabLayout;
    private RecyclerView devicesRecyclerView;
    private MainDatabaseHelper databaseHelper;
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

        databaseHelper = new MainDatabaseHelper(this);
        devicesRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Инициализация RecyclerView с передачей 'this' как слушателя
        adapter = new DeviceListAdapter(new ArrayList<>(), this);
        devicesRecyclerView.setAdapter(adapter);

        // Динамическая загрузка вкладок из базы данных
        setupTabLayout();
    }

    private void setupTabLayout() {
        // ИСПРАВЛЕНО: Меняем getTables() на getAllTables()
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
        // databaseHelper.getAllDataFromTable() теперь возвращает полный список данных
        List<Device> deviceList = databaseHelper.getAllDataFromTable(tableName);
        adapter.updateData(deviceList);
    }

    // Реализация метода интерфейса DeviceListAdapter.OnInfoClickListener
    @Override
    public void onInfoClick(Device device) {
        // ИСПРАВЛЕНО: Вызываем BottomSheet вместо AlertDialog
        showDeviceDetailsBottomSheet(device);
    }

    /**
     * Показывает BottomSheetDialogFragment с деталями устройства и картой.
     */
    private void showDeviceDetailsBottomSheet(Device device) {
        DeviceDetailsBottomSheet bottomSheet = DeviceDetailsBottomSheet.newInstance(device);
        // show() требует FragmentManager
        bottomSheet.show(getSupportFragmentManager(), DeviceDetailsBottomSheet.TAG);
    }


    // РЕАЛИЗАЦИЯ ИНТЕРФЕЙСА: Обновление списка после изменения статуса
    @Override
    public void onStatusUpdated() {
        // Обновляем текущий список
        if (tabLayout.getSelectedTabPosition() != TabLayout.Tab.INVALID_POSITION) {
            TabLayout.Tab selectedTab = tabLayout.getTabAt(tabLayout.getSelectedTabPosition());
            if (selectedTab != null && selectedTab.getText() != null) {
                String tableName = selectedTab.getText().toString();
                loadDevicesForTable(tableName); // КЛЮЧЕВОЙ ВЫЗОВ ДЛЯ ПЕРЕЗАГРУЗКИ
            }
        }
    }


    /**
     * НОВЫЙ МЕТОД: Обрабатывает клик по элементу меню "Make Safe".
     * Этот метод должен быть вызван из вашей основной Activity,
     * когда пользователь нажимает на nav_make_safe в Drawer.
     */
    public void handleMakeSafeClick() {
        if (tabLayout.getSelectedTabPosition() != TabLayout.Tab.INVALID_POSITION) {
            TabLayout.Tab selectedTab = tabLayout.getTabAt(tabLayout.getSelectedTabPosition());
            if (selectedTab != null && selectedTab.getText() != null) {
                String tableName = selectedTab.getText().toString();

                // Вызываем массовое обновление в базе данных
                int affectedRows = databaseHelper.updateAllDeviceStatusForTable(tableName, "SAFE");

                // Уведомляем пользователя
                Toast.makeText(this, affectedRows + " устройств в '" + tableName + "' помечены как SAFE.", Toast.LENGTH_SHORT).show();

                // Обновляем список на экране
                onStatusUpdated();
            } else {
                Toast.makeText(this, "Не выбрана активная таблица для обновления.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Не выбрана активная таблица для обновления.", Toast.LENGTH_SHORT).show();
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

    // Расширенный вспомогательный класс Device с MAC и Status
    // Код Parcelable остается без изменений
    public static class Device implements Parcelable {
        String name;
        String type;
        String location;
        String time;
        String mac;
        String status;

        public Device(String name, String type, String location, String time, String mac, String status) {
            this.name = name;
            this.type = type;
            this.location = location;
            this.time = time;
            this.mac = mac;
            this.status = status;
        }

        // --- РЕАЛИЗАЦИЯ PARCELABLE ---
        // (Остальной код Parcelable...)
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
    }
}