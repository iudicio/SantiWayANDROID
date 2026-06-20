package com.example.santiway;
import android.animation.ObjectAnimator;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class DeviceListActivity extends BaseLocalizedActivity implements DeviceListAdapter.OnDeviceClickListener, StatusUpdateListener {

    private static final String TAG = "DeviceListActivity";

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
                handler.postDelayed(this, 1500); // 1.5 сек
            }
        }
    };

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
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#FF6B6B"));
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#3DDC84"));
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
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (folderMoveMode) stopFolderMoveMode();
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
        }

        tabLayout.post(this::bindFolderLongPressActions);
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
            tabLayout.post(this::enableFolderMove);
        });
        content.findViewById(R.id.folder_action_rename_button).setOnClickListener(v -> {
            dialog.dismiss();
            showRenameFolderDialog(folderName);
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

    private void enableFolderMove() {
        if (tabLayout.getChildCount() == 0 || !(tabLayout.getChildAt(0) instanceof ViewGroup)) return;
        folderMoveMode = true;
        Toast.makeText(this, R.string.folder_move_instruction, Toast.LENGTH_LONG).show();

        ViewGroup tabStrip = (ViewGroup) tabLayout.getChildAt(0);
        int count = Math.min(tabStrip.getChildCount(), tabLayout.getTabCount());
        for (int i = 0; i < count; i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab == null || tab.getTag() == null) continue;
            View tabView = tabStrip.getChildAt(i);
            startFolderWiggle(tabView);
            bindFolderDrag(tabStrip, tabView, (String) tab.getTag());
        }
    }

    private void startFolderWiggle(View tabView) {
        tabView.setPivotX(tabView.getWidth() / 2f);
        tabView.setPivotY(tabView.getHeight() / 2f);
        ObjectAnimator animator = ObjectAnimator.ofFloat(tabView, View.ROTATION,
                0f, -20f, 20f, 0f);
        animator.setDuration(1500L);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setRepeatMode(ObjectAnimator.RESTART);
        animator.start();
        folderWiggleAnimations.put(tabView, animator);
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
                    view.setScaleX(1.06f);
                    view.setScaleY(1.06f);
                    view.setAlpha(0.9f);
                    ViewCompat.setElevation(view, dpToPx(12));
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
        bindFolderLongPressActions();
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
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> renameFolder(oldName, input, dialog)));
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
        new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle(R.string.dialog_confirm_delete_title)
                .setMessage(getString(R.string.dialog_confirm_delete_folder_message,
                        getDisplayTableName(folderName)))
                .setPositiveButton(R.string.dialog_yes, (dialog, which) -> deleteFolder(folderName))
                .setNegativeButton(R.string.dialog_no, null)
                .show();
    }

    private void deleteFolder(String folderName) {
        if (!databaseHelper.deleteTable(folderName)) {
            Toast.makeText(this, R.string.error_folder_delete, Toast.LENGTH_SHORT).show();
            return;
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
