package com.example.santiway;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import com.example.santiway.upload_folder_device.UserDeviceFolderSyncManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;

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
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.text.SimpleDateFormat;
import org.json.JSONArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class DeviceListActivity extends BaseLocalizedActivity implements DeviceListAdapter.OnDeviceClickListener, StatusUpdateListener {

    private static final String TAG = "DeviceListActivity";
    private static final String PREFS_APP = "app_prefs";
    private static final String KEY_LAST_SNAPSHOT_TRIGGER_FOLDER = "last_snapshot_trigger_folder";
    private static final String KEY_LAST_SNAPSHOT_TRIGGER_LAT = "last_snapshot_trigger_lat";
    private static final String KEY_LAST_SNAPSHOT_TRIGGER_LON = "last_snapshot_trigger_lon";
    private static final String KEY_LAST_SNAPSHOT_TRIGGER_HAS_ORIGIN = "last_snapshot_trigger_has_origin";
    private static final String KEY_LAST_SNAPSHOT_TRIGGER_ARMED = "last_snapshot_trigger_armed";
    private static final String KEY_LAST_SNAPSHOT_TRIGGER_APPLIED = "last_snapshot_trigger_applied";
    private static final float FOLDER_TRIGGER_DISTANCE_METERS = 500f;

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
    private static final String KEY_FOLDER_ORDER = "device_folder_order";
    private final List<String> orderedTables = new ArrayList<>();
    private final Map<View, ObjectAnimator> folderWiggleAnimations = new HashMap<>();
    private boolean folderMoveMode;

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
                handler.postDelayed(this, 5000);
            }
        }
    };
    private final Runnable folderTriggerCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing() && !isDestroyed()) {
                checkArmedFolderTrigger();
                handler.postDelayed(this, 15000L);
            }
        }
    };
    private Runnable searchDebounceRunnable;

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (folderMoveMode && event.getActionMasked() == MotionEvent.ACTION_DOWN
                && !isTouchInsideTabLayout(event.getRawX(), event.getRawY())) {
            stopFolderMoveMode();
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean isTouchInsideTabLayout(float rawX, float rawY) {
        int[] location = new int[2];
        tabLayout.getLocationOnScreen(location);
        return rawX >= location[0]
                && rawX <= location[0] + tabLayout.getWidth()
                && rawY >= location[1]
                && rawY <= location[1] + tabLayout.getHeight();
    }

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
                        .setMessage(getString(R.string.dialog_clear_folder_message, getDisplayTableName(currentFolder)))
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

                            Toast.makeText(this, getString(R.string.toast_folder_data_deleted, getDisplayTableName(currentFolder)), Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(getString(R.string.dialog_cancel_upper), null)
                        .create();

                dialog.show();

                // 3. Стилизация кнопок (после вызова .show())
                DialogStyleUtils.tintButtons(dialog);
            });
        }
        LinearLayout renameButton = findViewById(R.id.action_rename);

        if (renameButton != null) {
            renameButton.setOnClickListener(v -> {
                int selectedTabPos = tabLayout.getSelectedTabPosition();
                if (selectedTabPos == -1) return;
                String oldName = (String) tabLayout.getTabAt(selectedTabPos).getTag();
                showRenameFolderDialog(oldName);
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
        handler.post(folderTriggerCheckRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (folderMoveMode) stopFolderMoveMode();
        handler.removeCallbacks(autoRefreshRunnable);
        handler.removeCallbacks(folderTriggerCheckRunnable);
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
            dbHelper.close();
            Toast.makeText(this, getString(R.string.error_no_device_location_data), Toast.LENGTH_SHORT).show();
            return;
        }

        MainDatabaseHelper.DeviceLocation lastLocation = history.get(history.size() - 1);
        dbHelper.close();

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
        return FolderNameHelper.getDisplayName(this, tableName);
    }

    private void setupTabLayout() {
        cancelFolderWiggleAnimations();
        folderMoveMode = false;
        tabLayout.removeAllTabs();
        List<String> tables = databaseHelper.getAllTables();

        if (tables == null || tables.isEmpty()) {
            currentTable = "";
            adapter.clearData();
            return;
        }

        tables = applySavedFolderOrder(tables);
        orderedTables.clear();
        orderedTables.addAll(tables);

        for (String tableName : orderedTables) {
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

                scrollTabIntoView(tab.getPosition());

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
                .getString("current_folder", FolderNameHelper.MAIN_FOLDER_INTERNAL);

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
            scrollTabIntoView(selectedIndex);
        }

        tabLayout.post(this::bindFolderLongPressActions);
        tabLayout.post(this::updateFolderTriggerVisuals);
    }

    private void scrollTabIntoView(int position) {
        tabLayout.post(() -> {
            if (position < 0 || tabLayout.getChildCount() == 0
                    || !(tabLayout.getChildAt(0) instanceof ViewGroup)) return;
            ViewGroup tabStrip = (ViewGroup) tabLayout.getChildAt(0);
            if (position >= tabStrip.getChildCount()) return;
            View tabView = tabStrip.getChildAt(position);
            int visibleLeft = tabLayout.getScrollX();
            int visibleRight = visibleLeft + tabLayout.getWidth();
            if (tabView.getRight() > visibleRight) {
                tabLayout.smoothScrollTo(tabView.getRight() - tabLayout.getWidth(), 0);
            } else if (tabView.getLeft() < visibleLeft) {
                tabLayout.smoothScrollTo(tabView.getLeft(), 0);
            }
        });
    }

    private List<String> applySavedFolderOrder(List<String> databaseTables) {
        List<String> result = new ArrayList<>();
        String saved = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getString(KEY_FOLDER_ORDER, "[]");
        try {
            JSONArray json = new JSONArray(saved);
            for (int i = 0; i < json.length(); i++) {
                String folder = json.optString(i);
                if (databaseTables.contains(folder) && !result.contains(folder)) {
                    result.add(folder);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not restore folder order", e);
        }
        for (String folder : databaseTables) {
            if (!result.contains(folder)) result.add(folder);
        }
        return result;
    }

    private void saveFolderOrder() {
        JSONArray json = new JSONArray();
        for (String folder : orderedTables) json.put(folder);
        getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit()
                .putString(KEY_FOLDER_ORDER, json.toString())
                .apply();
    }

    private void bindFolderLongPressActions() {
        if (tabLayout.getChildCount() == 0 || !(tabLayout.getChildAt(0) instanceof ViewGroup)) return;
        ViewGroup tabStrip = (ViewGroup) tabLayout.getChildAt(0);
        int count = Math.min(tabStrip.getChildCount(), tabLayout.getTabCount());
        for (int i = 0; i < count; i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            View tabView = tabStrip.getChildAt(i);
            if (tab == null || tab.getTag() == null) continue;
            String folderName = (String) tab.getTag();
            tabView.setOnTouchListener(null);
            tabView.setOnLongClickListener(v -> {
                showFolderActionsDialog(folderName);
                return true;
            });
        }
    }

    private void showFolderActionsDialog(String folderName) {
        View content = getLayoutInflater().inflate(R.layout.dialog_folder_actions, null);
        TextView title = content.findViewById(R.id.folder_actions_dialog_title);
        title.setText(getString(R.string.folder_actions_title, getDisplayTableName(folderName)));

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setView(content)
                .create();

        content.findViewById(R.id.folder_action_move_button).setOnClickListener(v -> {
            dialog.dismiss();
            tabLayout.post(() -> enableFolderMove(folderName));
        });
        content.findViewById(R.id.folder_action_rename_button).setOnClickListener(v -> {
            dialog.dismiss();
            showRenameFolderDialog(folderName);
        });
        content.findViewById(R.id.folder_action_gray_search_button).setOnClickListener(v -> {
            dialog.dismiss();
            showGrayDeviceSearchDialog(folderName);
        });
        MaterialButton triggerButton = content.findViewById(R.id.folder_action_trigger_button);
        boolean triggerArmed = isFolderTriggerArmed(folderName);
        styleFolderTriggerActionButton(triggerButton, triggerArmed);
        triggerButton.setOnClickListener(v -> {
            dialog.dismiss();
            if (triggerArmed) {
                disarmFolderTrigger(folderName);
            } else {
                armFolderTrigger(folderName);
            }
        });
        content.findViewById(R.id.folder_action_delete_button).setOnClickListener(v -> {
            dialog.dismiss();
            showDeleteFolderDialog(folderName);
        });

        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9f);
                dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    private void armFolderTrigger(String folderName) {
        Location origin = null;
        try {
            origin = com.example.santiway.gsm_protocol.LocationManager
                    .getInstance(this)
                    .getBestEffortLocation();
        } catch (Exception e) {
            Log.e(TAG, "Could not get trigger origin: " + e.getMessage(), e);
        }

        if (origin == null) {
            Toast.makeText(this, R.string.toast_folder_trigger_location_missing, Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences.Editor editor = getSharedPreferences(PREFS_APP, MODE_PRIVATE).edit();
        editor.putString(KEY_LAST_SNAPSHOT_TRIGGER_FOLDER, folderName);
        editor.putFloat(KEY_LAST_SNAPSHOT_TRIGGER_LAT, (float) origin.getLatitude());
        editor.putFloat(KEY_LAST_SNAPSHOT_TRIGGER_LON, (float) origin.getLongitude());
        editor.putBoolean(KEY_LAST_SNAPSHOT_TRIGGER_HAS_ORIGIN, true);
        editor.putBoolean(KEY_LAST_SNAPSHOT_TRIGGER_ARMED, true);
        editor.putBoolean(KEY_LAST_SNAPSHOT_TRIGGER_APPLIED, false);
        editor.apply();

        Toast.makeText(
                this,
                getString(R.string.toast_folder_trigger_armed, getDisplayTableName(folderName)),
                Toast.LENGTH_LONG
        ).show();
        updateFolderTriggerVisuals();
    }

    private void disarmFolderTrigger(String folderName) {
        if (!isFolderTriggerArmed(folderName)) return;
        clearFolderTriggerState();
        updateFolderTriggerVisuals();
        Toast.makeText(
                this,
                getString(R.string.toast_folder_trigger_disarmed, getDisplayTableName(folderName)),
                Toast.LENGTH_SHORT
        ).show();
    }

    private boolean isFolderTriggerArmed(String folderName) {
        if (folderName == null) return false;
        SharedPreferences prefs = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        return prefs.getBoolean(KEY_LAST_SNAPSHOT_TRIGGER_ARMED, false)
                && folderName.equals(prefs.getString(KEY_LAST_SNAPSHOT_TRIGGER_FOLDER, ""));
    }

    private void clearFolderTriggerState() {
        getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                .edit()
                .remove(KEY_LAST_SNAPSHOT_TRIGGER_FOLDER)
                .remove(KEY_LAST_SNAPSHOT_TRIGGER_LAT)
                .remove(KEY_LAST_SNAPSHOT_TRIGGER_LON)
                .putBoolean(KEY_LAST_SNAPSHOT_TRIGGER_HAS_ORIGIN, false)
                .putBoolean(KEY_LAST_SNAPSHOT_TRIGGER_ARMED, false)
                .putBoolean(KEY_LAST_SNAPSHOT_TRIGGER_APPLIED, false)
                .apply();
    }

    private void updateArmedFolderNameOnRename(String oldName, String newName) {
        if (!isFolderTriggerArmed(oldName)) return;
        getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_SNAPSHOT_TRIGGER_FOLDER, newName)
                .apply();
    }

    private void styleFolderTriggerActionButton(MaterialButton button, boolean armed) {
        button.setText(armed ? R.string.folder_action_trigger_disable : R.string.folder_action_trigger);
        button.setIconTint(ColorStateList.valueOf(Color.parseColor(armed ? "#FF8A95" : "#F5C542")));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(armed ? "#4A2030" : "#172A46")));
        button.setStrokeColor(ColorStateList.valueOf(Color.parseColor(armed ? "#7A3348" : "#2D4566")));
    }

    private void checkArmedFolderTrigger() {
        SharedPreferences prefs = getSharedPreferences(PREFS_APP, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_LAST_SNAPSHOT_TRIGGER_ARMED, false)) return;
        if (!prefs.getBoolean(KEY_LAST_SNAPSHOT_TRIGGER_HAS_ORIGIN, false)) return;

        String folderName = prefs.getString(KEY_LAST_SNAPSHOT_TRIGGER_FOLDER, "");
        if (folderName == null || folderName.trim().isEmpty()) return;

        Location current = null;
        try {
            current = com.example.santiway.gsm_protocol.LocationManager
                    .getInstance(this)
                    .getBestEffortLocation();
        } catch (Exception e) {
            Log.e(TAG, "Could not check folder trigger distance: " + e.getMessage(), e);
        }
        if (current == null) return;

        float[] distance = new float[1];
        Location.distanceBetween(
                prefs.getFloat(KEY_LAST_SNAPSHOT_TRIGGER_LAT, 0f),
                prefs.getFloat(KEY_LAST_SNAPSHOT_TRIGGER_LON, 0f),
                current.getLatitude(),
                current.getLongitude(),
                distance
        );

        if (distance[0] >= FOLDER_TRIGGER_DISTANCE_METERS) {
            applyFolderTrigger(folderName);
        }
    }

    private void applyFolderTrigger(String folderName) {
        getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_LAST_SNAPSHOT_TRIGGER_ARMED, false)
                .apply();
        updateFolderTriggerVisuals();

        new Thread(() -> {
            int count = databaseHelper.updateAllDeviceStatusForTable(folderName, "TARGET");
            getSharedPreferences(PREFS_APP, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_LAST_SNAPSHOT_TRIGGER_APPLIED, true)
                    .apply();
            runOnUiThread(() -> {
                Toast.makeText(
                        this,
                        getString(R.string.toast_folder_trigger_applied, getDisplayTableName(folderName), count),
                        Toast.LENGTH_LONG
                ).show();
                if (currentTable != null && currentTable.equals(folderName) && !isLoading) {
                    loadDevicesForTable(currentTable, true);
                }
            });
        }).start();
    }

    private void showGrayDeviceSearchDialog(String sourceFolder) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPx(18);
        root.setPadding(padding, dpToPx(8), padding, 0);

        TextView description = createDialogText(R.string.gray_search_dialog_description, 14, false);
        root.addView(description);

        CheckBox firstUsePeriod = createDialogCheckBox(R.string.gray_search_first_period_title);
        root.addView(firstUsePeriod);
        LinearLayout firstPeriodRow = createPeriodRow(
                R.string.gray_search_period_start_hint,
                R.string.gray_search_period_end_hint);
        EditText firstStartInput = (EditText) firstPeriodRow.getChildAt(0);
        EditText firstEndInput = (EditText) firstPeriodRow.getChildAt(1);
        setPeriodInputsEnabled(firstPeriodRow, false);
        root.addView(firstPeriodRow);

        CheckBox secondUsePeriod = createDialogCheckBox(R.string.gray_search_second_period_title);
        root.addView(secondUsePeriod);
        LinearLayout secondPeriodRow = createPeriodRow(
                R.string.gray_search_period_start_hint,
                R.string.gray_search_period_end_hint);
        EditText secondStartInput = (EditText) secondPeriodRow.getChildAt(0);
        EditText secondEndInput = (EditText) secondPeriodRow.getChildAt(1);
        setPeriodInputsEnabled(secondPeriodRow, false);
        root.addView(secondPeriodRow);

        TextView formatHint = createDialogText(R.string.gray_search_date_format_hint, 12, false);
        formatHint.setTextColor(Color.WHITE);
        root.addView(formatHint);

        TextView foldersTitle = createDialogText(R.string.gray_search_second_folders_title, 14, true);
        foldersTitle.setPadding(0, dpToPx(14), 0, dpToPx(6));
        root.addView(foldersTitle);

        LinearLayout foldersContainer = new LinearLayout(this);
        foldersContainer.setOrientation(LinearLayout.VERTICAL);
        List<CheckBox> folderChecks = new ArrayList<>();
        for (String folder : databaseHelper.getAllTables()) {
            CheckBox checkBox = createDialogCheckBox(0);
            checkBox.setText(getDisplayTableName(folder));
            checkBox.setTag(folder);
            foldersContainer.addView(checkBox);
            folderChecks.add(checkBox);
        }

        ScrollView foldersScroll = new ScrollView(this);
        foldersScroll.addView(foldersContainer);
        root.addView(foldersScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(150)
        ));

        TextView foldersHint = createDialogText(R.string.gray_search_second_folders_hint, 12, false);
        foldersHint.setTextColor(Color.WHITE);
        root.addView(foldersHint);

        firstUsePeriod.setOnCheckedChangeListener((buttonView, isChecked) ->
                setPeriodInputsEnabled(firstPeriodRow, isChecked));
        secondUsePeriod.setOnCheckedChangeListener((buttonView, isChecked) ->
                setPeriodInputsEnabled(secondPeriodRow, isChecked));

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle(getString(R.string.gray_search_dialog_title, getDisplayTableName(sourceFolder)))
                .setView(root)
                .setPositiveButton(R.string.gray_search_start_action, null)
                .setNegativeButton(R.string.dialog_cancel, null)
                .create();

        dialog.setOnShowListener(ignored -> {
            DialogStyleUtils.tintButtons(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                Period firstPeriod = readPeriod(firstUsePeriod.isChecked(), firstStartInput, firstEndInput);
                if (!firstPeriod.valid) return;
                Period secondPeriod = readPeriod(secondUsePeriod.isChecked(), secondStartInput, secondEndInput);
                if (!secondPeriod.valid) return;

                ArrayList<String> selectedFolders = new ArrayList<>();
                for (CheckBox checkBox : folderChecks) {
                    if (checkBox.isChecked() && checkBox.getTag() instanceof String) {
                        selectedFolders.add((String) checkBox.getTag());
                    }
                }

                Intent intent = new Intent(this, GrayDeviceSearchActivity.class);
                intent.putExtra(GrayDeviceSearchActivity.EXTRA_SOURCE_FOLDER, sourceFolder);
                intent.putExtra(GrayDeviceSearchActivity.EXTRA_FIRST_HAS_PERIOD, firstUsePeriod.isChecked());
                intent.putExtra(GrayDeviceSearchActivity.EXTRA_FIRST_START, firstPeriod.start);
                intent.putExtra(GrayDeviceSearchActivity.EXTRA_FIRST_END, firstPeriod.end);
                intent.putExtra(GrayDeviceSearchActivity.EXTRA_SECOND_HAS_PERIOD, secondUsePeriod.isChecked());
                intent.putExtra(GrayDeviceSearchActivity.EXTRA_SECOND_START, secondPeriod.start);
                intent.putExtra(GrayDeviceSearchActivity.EXTRA_SECOND_END, secondPeriod.end);
                intent.putStringArrayListExtra(GrayDeviceSearchActivity.EXTRA_SECOND_FOLDERS, selectedFolders);
                startActivity(intent);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private TextView createDialogText(int stringRes, int textSizeSp, boolean bold) {
        TextView view = new TextView(this);
        if (stringRes != 0) {
            view.setText(stringRes);
        }
        view.setTextColor(Color.WHITE);
        view.setTextSize(textSizeSp);
        if (bold) {
            view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private CheckBox createDialogCheckBox(int stringRes) {
        CheckBox checkBox = new CheckBox(this);
        if (stringRes != 0) {
            checkBox.setText(stringRes);
        }
        checkBox.setTextColor(Color.WHITE);
        checkBox.setButtonTintList(ColorStateList.valueOf(Color.parseColor("#3DDC84")));
        return checkBox;
    }

    private LinearLayout createPeriodRow(int startHint, int endHint) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, dpToPx(8));

        EditText start = createPeriodInput(startHint);
        EditText end = createPeriodInput(endHint);
        row.addView(start, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams endParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        endParams.setMargins(dpToPx(8), 0, 0, 0);
        row.addView(end, endParams);
        return row;
    }

    private EditText createPeriodInput(int hintRes) {
        EditText input = new EditText(this);
        input.setHint(hintRes);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_NORMAL);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.WHITE);
        input.setTextSize(13);
        return input;
    }

    private void setPeriodInputsEnabled(LinearLayout row, boolean enabled) {
        for (int i = 0; i < row.getChildCount(); i++) {
            row.getChildAt(i).setEnabled(enabled);
            row.getChildAt(i).setAlpha(enabled ? 1f : 0.45f);
        }
    }

    private Period readPeriod(boolean enabled, EditText startInput, EditText endInput) {
        Period period = new Period();
        period.valid = true;
        if (!enabled) {
            return period;
        }

        String startText = startInput.getText() == null ? "" : startInput.getText().toString().trim();
        String endText = endInput.getText() == null ? "" : endInput.getText().toString().trim();
        if (startText.isEmpty() && endText.isEmpty()) {
            Toast.makeText(this, R.string.gray_search_period_required, Toast.LENGTH_SHORT).show();
            period.valid = false;
            return period;
        }

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        format.setLenient(false);
        try {
            if (!startText.isEmpty()) {
                Date parsed = format.parse(startText);
                period.start = parsed != null ? parsed.getTime() : -1L;
            }
            if (!endText.isEmpty()) {
                Date parsed = format.parse(endText);
                period.end = parsed != null ? parsed.getTime() : -1L;
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.gray_search_period_invalid, Toast.LENGTH_SHORT).show();
            period.valid = false;
            return period;
        }

        if (period.start > 0 && period.end > 0 && period.start > period.end) {
            Toast.makeText(this, R.string.gray_search_period_order_invalid, Toast.LENGTH_SHORT).show();
            period.valid = false;
        }
        return period;
    }

    private void enableFolderMove(String anchorFolderName) {
        if (tabLayout.getChildCount() == 0 || !(tabLayout.getChildAt(0) instanceof ViewGroup)) return;
        folderMoveMode = true;
        Toast.makeText(this, R.string.folder_move_instruction, Toast.LENGTH_LONG).show();
        setFolderDeleteControlsVisible(true);

        ViewGroup tabStrip = (ViewGroup) tabLayout.getChildAt(0);
        int count = Math.min(tabStrip.getChildCount(), tabLayout.getTabCount());
        for (int i = 0; i < count; i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab == null || tab.getTag() == null) continue;
            View tabView = tabStrip.getChildAt(i);
            startFolderTremble(tabView);
            bindFolderDrag(tabStrip, tabView, (String) tab.getTag());
        }
        focusFolderInMoveRibbon(anchorFolderName);
    }

    private void startFolderTremble(View tabView) {
        tabView.setPivotX(tabView.getWidth() / 2f);
        tabView.setPivotY(tabView.getHeight() / 2f);
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(
                tabView,
                PropertyValuesHolder.ofFloat(View.ROTATION, 0f, -0.35f, 0.35f, -0.22f, 0.22f, 0f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, -0.35f, 0.35f, -0.2f, 0.2f, 0f)
        );
        animator.setDuration(780L);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setRepeatMode(ObjectAnimator.RESTART);
        animator.start();
        folderWiggleAnimations.put(tabView, animator);
    }

    private void focusFolderInMoveRibbon(String folderName) {
        int index = findFolderTabIndex(folderName);
        if (index < 0) return;

        TabLayout.Tab tab = tabLayout.getTabAt(index);
        if (tab != null && !tab.isSelected()) {
            tab.select();
        }

        tabLayout.post(() -> {
            tabLayout.setScrollPosition(index, 0f, true);
            if (tabLayout.getChildCount() == 0 || !(tabLayout.getChildAt(0) instanceof ViewGroup)) return;
            ViewGroup tabStrip = (ViewGroup) tabLayout.getChildAt(0);
            if (index >= tabStrip.getChildCount()) return;

            View tabView = tabStrip.getChildAt(index);
            int targetScroll = tabView.getLeft() - (tabLayout.getWidth() - tabView.getWidth()) / 2;
            tabLayout.smoothScrollTo(Math.max(0, targetScroll), 0);
            tabView.requestFocus();
        });
    }

    private int findFolderTabIndex(String folderName) {
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null && folderName.equals(tab.getTag())) {
                return i;
            }
        }
        return -1;
    }

    private void bindFolderDrag(ViewGroup tabStrip, View tabView, String folderName) {
        final float[] touchStartX = new float[1];
        tabView.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartX[0] = event.getRawX();
                    ObjectAnimator wiggle = folderWiggleAnimations.remove(view);
                    if (wiggle != null) wiggle.cancel();
                    view.setRotation(0f);
                    view.setScaleX(1.03f);
                    view.setScaleY(1.03f);
                    view.setAlpha(0.96f);
                    ViewCompat.setElevation(view, dpToPx(8));
                    tabStrip.requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    view.setTranslationX(event.getRawX() - touchStartX[0]);
                    autoScrollTabsWhileDragging(event.getRawX());
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    int targetPosition = findDropPosition(tabStrip, event.getRawX());
                    float movedDistance = Math.abs(event.getRawX() - touchStartX[0]);
                    view.animate().translationX(0f).scaleX(1f).scaleY(1f).alpha(1f)
                            .setDuration(140).start();
                    ViewCompat.setElevation(view, 0f);
                    tabStrip.requestDisallowInterceptTouchEvent(false);
                    view.performClick();

                    int sourcePosition = orderedTables.indexOf(folderName);
                    if (event.getActionMasked() == MotionEvent.ACTION_UP
                            && movedDistance >= dpToPx(8)
                            && sourcePosition >= 0 && targetPosition >= 0
                            && sourcePosition != targetPosition) {
                        String moved = orderedTables.remove(sourcePosition);
                        orderedTables.add(targetPosition, moved);
                        saveFolderOrder();
                        getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                                .putString("current_folder", folderName).apply();
                        stopFolderMoveMode();
                        setupTabLayout();
                    } else {
                        stopFolderMoveMode();
                        if (event.getActionMasked() == MotionEvent.ACTION_UP
                                && movedDistance < dpToPx(8) && sourcePosition >= 0) {
                            TabLayout.Tab tappedTab = tabLayout.getTabAt(sourcePosition);
                            if (tappedTab != null) tappedTab.select();
                        }
                    }
                    return true;
                default:
                    return false;
            }
        });
    }

    private void stopFolderMoveMode() {
        folderMoveMode = false;
        cancelFolderWiggleAnimations();
        if (tabLayout.getChildCount() > 0 && tabLayout.getChildAt(0) instanceof ViewGroup) {
            ViewGroup tabStrip = (ViewGroup) tabLayout.getChildAt(0);
            for (int i = 0; i < tabStrip.getChildCount(); i++) {
                View child = tabStrip.getChildAt(i);
                child.setRotation(0f);
                child.setTranslationX(0f);
                child.setScaleX(1f);
                child.setScaleY(1f);
                child.setAlpha(1f);
                child.setOnTouchListener(null);
            }
        }
        setFolderDeleteControlsVisible(false);
        bindFolderLongPressActions();
        updateFolderTriggerVisuals();
    }

    private void setFolderDeleteControlsVisible(boolean visible) {
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab == null || tab.getTag() == null) continue;
            String folderName = (String) tab.getTag();
            if (visible) {
                tab.setCustomView(createFolderTabEditView(folderName));
            } else {
                tab.setCustomView((View) null);
                tab.setText(getDisplayTableName(folderName));
            }
        }
    }

    private View createFolderTabEditView(String folderName) {
        boolean canDelete = !FolderNameHelper.isMainFolder(folderName);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER);
        container.setClipChildren(false);
        container.setClipToPadding(false);
        container.setMinimumWidth(dpToPx(82));
        container.setPadding(dpToPx(10), dpToPx(2), dpToPx(10), dpToPx(2));
        if (isFolderTriggerArmed(folderName)) {
            container.setBackground(createFolderTabBackground());
        }

        if (canDelete) {
            TextView deleteButton = new TextView(this);
            deleteButton.setText("\u00D7");
            deleteButton.setTextColor(Color.WHITE);
            deleteButton.setTextSize(11);
            deleteButton.setGravity(Gravity.CENTER);
            deleteButton.setIncludeFontPadding(false);
            deleteButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            deleteButton.setContentDescription(getString(R.string.content_desc_delete_icon));

            GradientDrawable deleteBackground = new GradientDrawable();
            deleteBackground.setShape(GradientDrawable.OVAL);
            deleteBackground.setColor(Color.parseColor("#F0445E"));
            deleteBackground.setStroke(dpToPx(1), Color.parseColor("#FFE1E6"));
            deleteButton.setBackground(deleteBackground);
            deleteButton.setOnClickListener(v -> {
                stopFolderMoveMode();
                showDeleteFolderDialog(folderName);
            });

            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    dpToPx(18),
                    dpToPx(18)
            );
            deleteParams.setMargins(0, 0, dpToPx(8), 0);
            container.addView(deleteButton, deleteParams);
        }

        TextView label = new TextView(this);
        label.setText(getDisplayTableName(folderName));
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextSize(14);
        label.setIncludeFontPadding(false);
        label.setMaxWidth(dpToPx(150));
        label.setTextColor(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_selected}, new int[]{}},
                new int[]{Color.WHITE, Color.WHITE}
        ));
        container.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        return container;
    }

    private void updateFolderTriggerVisuals() {
        tabLayout.post(() -> {
            if (tabLayout.getChildCount() == 0 || !(tabLayout.getChildAt(0) instanceof ViewGroup)) return;
            ViewGroup tabStrip = (ViewGroup) tabLayout.getChildAt(0);
            int count = Math.min(tabStrip.getChildCount(), tabLayout.getTabCount());
            for (int i = 0; i < count; i++) {
                TabLayout.Tab tab = tabLayout.getTabAt(i);
                View tabView = tabStrip.getChildAt(i);
                if (tab == null || tab.getTag() == null) {
                    tabView.setBackground(null);
                    continue;
                }
                String folderName = (String) tab.getTag();
                tabView.setBackground(isFolderTriggerArmed(folderName) ? createFolderTabBackground() : null);
            }
        });
    }

    private GradientDrawable createFolderTabBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#B8FF3B30"));
        background.setCornerRadius(dpToPx(10));
        background.setStroke(dpToPx(1), Color.parseColor("#FFFF8A95"));
        return background;
    }

    private void cancelFolderWiggleAnimations() {
        for (Map.Entry<View, ObjectAnimator> entry : folderWiggleAnimations.entrySet()) {
            entry.getValue().cancel();
            entry.getKey().setRotation(0f);
        }
        folderWiggleAnimations.clear();
    }

    private void autoScrollTabsWhileDragging(float rawX) {
        int[] location = new int[2];
        tabLayout.getLocationOnScreen(location);
        float localX = rawX - location[0];
        int edge = dpToPx(48);
        int step = dpToPx(16);
        if (localX < edge) {
            tabLayout.scrollBy(-step, 0);
        } else if (localX > tabLayout.getWidth() - edge) {
            tabLayout.scrollBy(step, 0);
        }
    }

    private int findDropPosition(ViewGroup tabStrip, float rawX) {
        int target = tabStrip.getChildCount() - 1;
        int[] location = new int[2];
        for (int i = 0; i < tabStrip.getChildCount(); i++) {
            View child = tabStrip.getChildAt(i);
            child.getLocationOnScreen(location);
            if (rawX < location[0] + child.getWidth() / 2f) return i;
        }
        return Math.max(target, 0);
    }

    private void showRenameFolderDialog(String oldName) {
        if (oldName == null) return;
        if (FolderNameHelper.isMainFolder(oldName)) {
            Toast.makeText(this, R.string.error_main_folder_cannot_be_renamed, Toast.LENGTH_SHORT).show();
            return;
        }

        final EditText input = new EditText(this);
        input.setText(oldName);
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dpToPx(24);
        params.rightMargin = dpToPx(24);
        input.setLayoutParams(params);
        container.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle(R.string.dialog_rename_title)
                .setView(container)
                .setPositiveButton(R.string.dialog_change, null)
                .setNegativeButton(R.string.dialog_cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            DialogStyleUtils.tintButtons(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> renameFolder(oldName, input, dialog));
        });
        dialog.show();
    }

    private void renameFolder(String oldName, EditText input, AlertDialog dialog) {
        String newName = input.getText().toString().trim();
        if (newName.isEmpty()) {
            input.setError(getString(R.string.error_name_empty));
            return;
        }
        for (String existingName : orderedTables) {
            if (!existingName.equalsIgnoreCase(oldName) && existingName.equalsIgnoreCase(newName)) {
                input.setError(getString(R.string.error_folder_already_exists));
                return;
            }
        }

        databaseHelper.renameTable(oldName, newName);
        new UserDeviceFolderSyncManager(this).syncFolderRenamed(oldName, newName);
        updateArmedFolderNameOnRename(oldName, newName);
        int index = orderedTables.indexOf(oldName);
        if (index >= 0) orderedTables.set(index, newName);
        saveFolderOrder();
        currentTable = newName;
        getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                .putString("current_folder", newName).apply();
        Intent broadcast = new Intent("com.example.santiway.FOLDER_SWITCHED");
        broadcast.putExtra("newTableName", newName);
        sendBroadcast(broadcast);
        dialog.dismiss();
        setupTabLayout();
        Toast.makeText(this, R.string.toast_done, Toast.LENGTH_SHORT).show();
    }

    private void showDeleteFolderDialog(String folderName) {
        if (FolderNameHelper.isMainFolder(folderName)) {
            Toast.makeText(this, R.string.error_main_folder_cannot_be_deleted, Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle(R.string.dialog_confirm_delete_title)
                .setMessage(getString(R.string.dialog_confirm_delete_folder_message,
                        getDisplayTableName(folderName)))
                .setPositiveButton(R.string.dialog_yes, (d, which) -> deleteFolder(folderName))
                .setNegativeButton(R.string.dialog_no, null)
                .create();
        dialog.setOnShowListener(ignored -> DialogStyleUtils.tintButtons(dialog));
        dialog.show();
    }

    private void deleteFolder(String folderName) {
        if (!databaseHelper.deleteTable(folderName)) {
            Toast.makeText(this, R.string.error_folder_delete, Toast.LENGTH_SHORT).show();
            return;
        }
        if (isFolderTriggerArmed(folderName)) {
            clearFolderTriggerState();
        }
        new UserDeviceFolderSyncManager(this).syncFolderDeleted(folderName);
        orderedTables.remove(folderName);
        saveFolderOrder();

        if (folderName.equals(currentTable)) {
            currentTable = orderedTables.contains(FolderNameHelper.MAIN_FOLDER_INTERNAL)
                    ? FolderNameHelper.MAIN_FOLDER_INTERNAL
                    : (orderedTables.isEmpty() ? "" : orderedTables.get(0));
            getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                    .putString("current_folder", currentTable).apply();
            Intent broadcast = new Intent("com.example.santiway.FOLDER_SWITCHED");
            broadcast.putExtra("newTableName", currentTable);
            sendBroadcast(broadcast);
        }
        setupTabLayout();
        Toast.makeText(this, getString(R.string.toast_folder_deleted,
                getDisplayTableName(folderName)), Toast.LENGTH_SHORT).show();
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
        int loadOffset = isFirstLoad ? 0 : currentOffset;

        new Thread(() -> {
            List<Device> deviceList = new ArrayList<>();

            try {
                String uniqueTableName = getUniqueTableName(tableName);
                UniqueDevicesHelper uniqueHelper =
                        new UniqueDevicesHelper(DeviceListActivity.this, uniqueTableName);

                if (currentSearchQuery == null || currentSearchQuery.isEmpty()) {
                    deviceList = uniqueHelper.getDevicesPage(loadOffset, PAGE_SIZE);
                } else {
                    deviceList = uniqueHelper.getDevicesPageWithSearch(currentSearchQuery, loadOffset, PAGE_SIZE);
                }

                // Fallback: если unique-представление пустое, читаем из raw-таблицы
                if (deviceList == null || deviceList.isEmpty()) {
                    Log.w("LOAD_DEVICES", "Unique table is empty for " + tableName + ", fallback to raw table");

                    if (currentSearchQuery == null || currentSearchQuery.isEmpty()) {
                        deviceList = databaseHelper.getAllDataFromTableWithPagination(tableName, loadOffset, PAGE_SIZE);
                    } else {
                        deviceList = databaseHelper.getAllDataFromTableWithPaginationAndSearch(
                                tableName,
                                currentSearchQuery,
                                loadOffset,
                                PAGE_SIZE
                        );
                    }
                }

                Set<String> deviceKeys = new LinkedHashSet<>();
                for (Device device : deviceList) {
                    if (device.getMac() != null) {
                        deviceKeys.add(device.getMac());
                    }
                }
                Map<String, String> actualStatuses = databaseHelper.getStatusesFromServiceTables(deviceKeys);
                for (Device device : deviceList) {
                    String deviceKey = device.getMac();
                    if (deviceKey == null) continue;
                    String normalizedKey = deviceKey.trim().toUpperCase(Locale.US);
                    String actualStatus = actualStatuses.get(normalizedKey);
                    device.setStatus(actualStatus != null ? actualStatus : "GREY");
                }

            } catch (Exception e) {
                Log.e("LOAD_DEVICES", "Ошибка загрузки устройств: " + e.getMessage(), e);
            }

            List<Device> finalDeviceList = deviceList;
            boolean finalHasMore = finalDeviceList.size() >= PAGE_SIZE;
            Log.d("LOAD_DEVICES", "table=" + tableName + ", loaded=" + deviceList.size());
            runOnUiThread(() -> {
                adapter.hideLoading();
                if (isFirstLoad) {
                    allLoadedDevices.clear();
                }
                allLoadedDevices.addAll(finalDeviceList);
                currentOffset = allLoadedDevices.size();
                hasMoreData = finalHasMore;
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
        if ("TARGET".equalsIgnoreCase(status)) {
            showBulkTargetOptionsDialog(folder);
            return;
        }

        updateAllDevicesStatus(folder, status, true, true);
    }

    private void showBulkTargetOptionsDialog(String folder) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPx(20);
        content.setPadding(padding, dpToPx(8), padding, 0);

        TextView message = new TextView(this);
        message.setText(R.string.dialog_bulk_target_message);
        message.setTextColor(Color.WHITE);
        message.setTextSize(15);
        content.addView(message);

        CheckBox includeCellsCheckBox = new CheckBox(this);
        includeCellsCheckBox.setText(R.string.dialog_bulk_target_include_cells);
        includeCellsCheckBox.setTextColor(Color.WHITE);
        includeCellsCheckBox.setPadding(0, dpToPx(12), 0, 0);
        content.addView(includeCellsCheckBox);

        CheckBox includeSafeCheckBox = new CheckBox(this);
        includeSafeCheckBox.setText(R.string.dialog_bulk_target_include_safe);
        includeSafeCheckBox.setTextColor(Color.WHITE);
        includeSafeCheckBox.setPadding(0, dpToPx(8), 0, 0);
        content.addView(includeSafeCheckBox);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle(R.string.dialog_bulk_target_title)
                .setView(content)
                .setPositiveButton(R.string.dialog_bulk_target_apply, (d, which) ->
                        updateAllDevicesStatus(
                                folder,
                                "TARGET",
                                includeCellsCheckBox.isChecked(),
                                includeSafeCheckBox.isChecked()
                        ))
                .setNegativeButton(R.string.dialog_cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            DialogStyleUtils.tintButtons(dialog);
        });
        dialog.show();
    }

    private void updateAllDevicesStatus(String folder, String status,
                                        boolean includeCellTowers,
                                        boolean includeSafeDevices) {

        new Thread(() -> {
            int count = new MainDatabaseHelper(DeviceListActivity.this)
                    .updateAllDeviceStatusForTable(folder, status, includeCellTowers, includeSafeDevices);

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
                if (searchDebounceRunnable != null) {
                    handler.removeCallbacks(searchDebounceRunnable);
                }

                String query = s == null ? "" : s.toString().trim();
                searchDebounceRunnable = () -> {
                    currentSearchQuery = query;
                    resetPagination();
                    if (currentTable != null && !currentTable.isEmpty()) {
                        loadDevicesForTable(currentTable, true);
                    }
                };
                handler.postDelayed(searchDebounceRunnable, 300);
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
    private static class Period {
        boolean valid;
        long start = -1L;
        long end = -1L;
    }

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
