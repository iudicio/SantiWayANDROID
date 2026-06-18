package com.example.santiway;
import com.example.santiway.upload_folder_device.UserDeviceFolderSyncManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.text.Editable;
import android.text.TextWatcher;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.santiway.activity_map.ActivityMapActivity;
import com.example.santiway.upload_data.MainDatabaseHelper;
import com.example.santiway.upload_data.UniqueDevicesHelper;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

public class DeviceListActivity extends BaseLocalizedActivity implements DeviceListAdapter.OnDeviceClickListener, StatusUpdateListener {

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
    private GestureDetector folderGestureDetector;
    private float downX;
    private float downY;
    private static final int SWIPE_THRESHOLD = 120;

    // Хендлер для задержки
    private Handler handler = new Handler();

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }
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
        applyNavigationBarColor();

        toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tab_layout);
        devicesRecyclerView = findViewById(R.id.devices_recycler_view);

        btnFilterTarget = findViewById(R.id.btn_filter_target);
        btnFilterSafe = findViewById(R.id.btn_filter_safe);
        btnFilterAll = findViewById(R.id.btn_filter_all);
        etSearchDevice = findViewById(R.id.et_search_device);

        TextInputLayout searchInputLayout = findViewById(R.id.search_input_layout);

        int[][] states = new int[][]{
                new int[]{android.R.attr.state_focused},
                new int[]{android.R.attr.state_hovered},
                new int[]{android.R.attr.state_enabled},
                new int[]{}
        };

        int[] colors = new int[]{
                Color.WHITE,
                Color.WHITE,
                Color.WHITE,
                Color.WHITE
        };

        ColorStateList whiteStateList = new ColorStateList(states, colors);

        searchInputLayout.setBoxStrokeColorStateList(whiteStateList);
        searchInputLayout.setBoxStrokeColor(Color.WHITE);
        searchInputLayout.setBoxStrokeWidth(dpToPx(1));
        searchInputLayout.setBoxStrokeWidthFocused(dpToPx(1));
        searchInputLayout.setHintTextColor(whiteStateList);
        searchInputLayout.setDefaultHintTextColor(whiteStateList);
        searchInputLayout.setBoxCornerRadii(
                dpToPx(8),
                dpToPx(8),
                dpToPx(8),
                dpToPx(8)
        );

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.getNavigationIcon().setTint(Color.WHITE);
        getSupportActionBar().setTitle(getString(R.string.device_list_title));

        View root = findViewById(R.id.root_device_list);
        View bottomActionsCard = findViewById(R.id.bottom_actions_card);
        RecyclerView recyclerView = findViewById(R.id.devices_recycler_view);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            ViewGroup.LayoutParams toolbarLp = toolbar.getLayoutParams();
            toolbarLp.height = bars.top + dpToPx(56);
            toolbar.setLayoutParams(toolbarLp);

            toolbar.setPadding(
                    toolbar.getPaddingLeft(),
                    bars.top,
                    toolbar.getPaddingRight(),
                    toolbar.getPaddingBottom()
            );

            ViewGroup.LayoutParams lp = bottomActionsCard.getLayoutParams();
            if (lp instanceof ConstraintLayout.LayoutParams) {
                ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) lp;
                params.bottomMargin = bars.bottom;
                bottomActionsCard.setLayoutParams(params);
            }

            recyclerView.setPadding(
                    recyclerView.getPaddingLeft(),
                    recyclerView.getPaddingTop(),
                    recyclerView.getPaddingRight(),
                    bars.bottom + dpToPx(96)
            );

            return insets;
        });

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
                        .setTitle(getString(R.string.dialog_clear_title))
                        .setMessage(getString(R.string.dialog_clear_folder_message, currentFolder))
                        .setPositiveButton(getString(R.string.dialog_delete_upper), (d, which) -> {

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

                            Toast.makeText(this, getString(R.string.toast_folder_data_deleted, currentFolder), Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(getString(R.string.dialog_cancel_upper), null)
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
                        .setTitle(getString(R.string.dialog_rename_title))
                        .setView(container)
                        .setPositiveButton(getString(R.string.dialog_save_upper), null)
                        .setNegativeButton(getString(R.string.dialog_cancel_upper), null)
                        .create();

                dialog.show();

                // Переопределяем нажатие на "СОХРАНИТЬ", чтобы контролировать закрытие диалога
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    String newName = input.getText().toString().trim();

                    // 1. Проверка на пустое имя
                    if (newName.isEmpty()) {
                        input.setError(getString(R.string.error_name_empty));
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
                        input.setError(getString(R.string.error_folder_already_exists));
                        Toast.makeText(this, getString(R.string.toast_name_already_taken), Toast.LENGTH_SHORT).show();
                    } else {
                        // 3. Если всё ок — переименовываем
                        MainDatabaseHelper dbHelper = new MainDatabaseHelper(this); // запрос на сервер
                        dbHelper.renameTable(oldName, newName);


                        new UserDeviceFolderSyncManager(this).syncFolderRenamed(oldName, newName);

                        // обновляем вкладку и tag, иначе currentTable может остаться старым
                        tabLayout.getTabAt(selectedTabPos).setText(newName);
                        tabLayout.getTabAt(selectedTabPos).setTag(newName);

                        currentTable = newName;

                        getSharedPreferences("app_prefs", MODE_PRIVATE)
                                .edit()
                                .putString("current_folder", newName)
                                .apply();

                        Intent broadcastIntent = new Intent("com.example.santiway.FOLDER_SWITCHED");
                        broadcastIntent.putExtra("newTableName", newName);
                        sendBroadcast(broadcastIntent);

                        dialog.dismiss();
                        Toast.makeText(this, getString(R.string.toast_done), Toast.LENGTH_SHORT).show();
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
        setupRecyclerSwipe();

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

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
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
            Toast.makeText(this, getString(R.string.error_table_name_missing), Toast.LENGTH_SHORT).show();
            return;
        }

        if (device == null || device.getMac() == null || device.getMac().trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.error_device_identifier_missing), Toast.LENGTH_SHORT).show();
            return;
        }

        String deviceKey = device.getMac().trim();
        String deviceStatus = device.getStatus() != null ? device.getStatus() : "GREY";

        MainDatabaseHelper dbHelper = new MainDatabaseHelper(this);
        List<MainDatabaseHelper.DeviceLocation> history =
                dbHelper.getDeviceHistoryByKey(tableName, deviceKey, device.getType());

        if (history == null || history.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_no_device_location_data), Toast.LENGTH_SHORT).show();
            return;
        }

        MainDatabaseHelper.DeviceLocation lastLocation = history.get(history.size() - 1);

        Intent intent = new Intent(this, ActivityMapActivity.class);
        intent.putExtra("latitude", lastLocation.latitude);
        intent.putExtra("longitude", lastLocation.longitude);
        intent.putExtra("device_name", device.getName());
        intent.putExtra("device_mac", deviceKey);
        intent.putExtra("device_type", device.getType());
        intent.putExtra("table_name", tableName);
        intent.putExtra("device_status", deviceStatus);
        startActivity(intent);
    }

    public void shareDeviceAsJson(Device device) {
        String jsonString = databaseHelper.getDeviceExportJson(currentTable, device.getMac());

        if (jsonString == null || jsonString.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_no_export_data), Toast.LENGTH_SHORT).show();
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
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_json_title)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_with_message, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private String getDisplayTableName(String tableName) {
        return tableName;
    }

    private void setupTabLayout() {
        tabLayout.removeAllTabs();
        List<String> tables = databaseHelper.getAllTables();

        if (tables == null || tables.isEmpty()) {
            currentTable = "";
            adapter.clearData();
            return;
        }

        for (String tableName : tables) {
            TabLayout.Tab tab = tabLayout.newTab()
                    .setText(getDisplayTableName(tableName));
            tab.setTag(tableName);
            tabLayout.addTab(tab);
        }

        tabLayout.clearOnTabSelectedListeners();
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab == null || tab.getTag() == null) return;

                resetPagination();
                currentTable = (String) tab.getTag();

                getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .edit()
                        .putString("current_folder", currentTable)
                        .apply();

                loadDevicesForTable(currentTable, true);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                if (tab == null || tab.getTag() == null) return;

                currentTable = (String) tab.getTag();
                resetPagination();
                loadDevicesForTable(currentTable, true);
            }
        });

        String intentFolder = getIntent().getStringExtra("selected_folder");
        String savedFolder = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getString("current_folder", "Основная");

        String folderToOpen = intentFolder != null && !intentFolder.trim().isEmpty()
                ? intentFolder
                : savedFolder;

        int selectedIndex = -1;
        for (int i = 0; i < tables.size(); i++) {
            if (tables.get(i).equals(folderToOpen)) {
                selectedIndex = i;
                break;
            }
        }

        if (selectedIndex == -1) {
            selectedIndex = 0;
        }

        TabLayout.Tab initialTab = tabLayout.getTabAt(selectedIndex);
        if (initialTab != null) {
            initialTab.select();
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

                // Fallback: если unique-представление пустое, читаем из raw-таблицы
                if (deviceList == null || deviceList.isEmpty()) {
                    Log.w("LOAD_DEVICES", "Unique table is empty for " + tableName + ", fallback to raw table");

                    if (currentSearchQuery == null || currentSearchQuery.isEmpty()) {
                        deviceList = databaseHelper.getAllDataFromTableWithPagination(tableName, 0, PAGE_SIZE);
                    } else {
                        deviceList = databaseHelper.getAllDataFromTableWithPaginationAndSearch(
                                tableName,
                                currentSearchQuery,
                                0,
                                PAGE_SIZE
                        );
                    }
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
            Log.d("LOAD_DEVICES", "table=" + tableName + ", loaded=" + deviceList.size());
            runOnUiThread(() -> {
                adapter.hideLoading();
                allLoadedDevices.clear();
                allLoadedDevices.addAll(finalDeviceList);

                applyCurrentFilter();
                isLoading = false;
                Log.d("LOAD_DEVICES", "apply to adapter: table=" + tableName + ", count=" + finalDeviceList.size());
            });
        }).start();
    }

    private void updateAllDevicesStatus(String status) {
        int pos = tabLayout.getSelectedTabPosition();
        if (pos == -1) {
            Toast.makeText(this, getString(R.string.error_no_selected_tab), Toast.LENGTH_SHORT).show();
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
                        getString(R.string.toast_devices_status_updated, count, status),
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
    private void setupRecyclerSwipe() {
        devicesRecyclerView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    return false;

                case MotionEvent.ACTION_UP:
                    float upX = event.getX();
                    float upY = event.getY();

                    float diffX = upX - downX;
                    float diffY = upY - downY;

                    boolean isHorizontalSwipe =
                            Math.abs(diffX) > 160 &&
                                    Math.abs(diffX) > Math.abs(diffY) * 2.5f;

                    if (isHorizontalSwipe) {
                        if (diffX > 0) {
                            switchTabBySwipe(-1);
                        } else {
                            switchTabBySwipe(1);
                        }
                        return true;
                    }

                    return false;
            }

            return false;
        });
    }

    private void switchTabBySwipe(int direction) {
        if (tabLayout == null || tabLayout.getTabCount() == 0) return;

        int currentIndex = tabLayout.getSelectedTabPosition();
        if (currentIndex == -1) currentIndex = 0;

        int newIndex = currentIndex + direction;

        if (newIndex < 0) {
            newIndex = tabLayout.getTabCount() - 1;
        } else if (newIndex >= tabLayout.getTabCount()) {
            newIndex = 0;
        }

        TabLayout.Tab tab = tabLayout.getTabAt(newIndex);
        if (tab != null) {
            tab.select();
        }
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