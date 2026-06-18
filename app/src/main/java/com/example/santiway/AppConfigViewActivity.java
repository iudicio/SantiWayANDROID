package com.example.santiway;

import com.example.santiway.host_database.*;
import com.example.santiway.upload_name_device.UserDeviceSyncManager;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;

import androidx.activity.ComponentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;
import java.util.regex.Pattern;

public class AppConfigViewActivity extends BaseLocalizedActivity {
    private AppSettingsRepository repository;
    private Spinner protocolSpinner;
    private Spinner scannerSpinner;
    private Spinner languageSpinner;
    private EditText intervalInput;
    private Switch enabledSwitch;
    private EditText signalStrengthInput;
    private TextView serverIpInput;
    private TextView apiKeyDisplay;
    private EditText deviceNameInput;
    private EditText mapPointLimitInput;
    private Switch targetDetectionSwitch;
    private Switch staticLocationSwitch;
    private EditText staticLatitudeInput;
    private EditText staticLongitudeInput;
    private Button selectStaticLocationBtn;

    // Допустимые значения для протокола
    private final String[] allowedProtocols = {"GSM", "GPS"};

    // Регулярное выражение для проверки IPv4
    private static final String IPV4_PATTERN =
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
    private static final Pattern pattern = Pattern.compile(IPV4_PATTERN);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_config);
        applyNavigationBarColor();

        repository = new AppSettingsRepository(this);
        scannerSpinner = findViewById(R.id.scanner_spinner);
        protocolSpinner = findViewById(R.id.protocol_spinner);
        languageSpinner = findViewById(R.id.language_spinner);
        intervalInput = findViewById(R.id.interval_input);
        enabledSwitch = findViewById(R.id.enabled_switch);
        signalStrengthInput = findViewById(R.id.signal_strength_input);
        serverIpInput = findViewById(R.id.server_ip_input);
        apiKeyDisplay = findViewById(R.id.api_key_display);
        deviceNameInput = findViewById(R.id.device_scanner);

        targetDetectionSwitch = findViewById(R.id.target_detection_switch);
        staticLocationSwitch = findViewById(R.id.static_location_switch);
        staticLatitudeInput = findViewById(R.id.static_latitude_input);
        staticLongitudeInput = findViewById(R.id.static_longitude_input);
        selectStaticLocationBtn = findViewById(R.id.select_static_location_btn);

        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);

        staticLocationSwitch.setChecked(prefs.getBoolean("static_location_enabled", false));
        staticLatitudeInput.setText(String.valueOf(prefs.getFloat("static_latitude", 0f)));
        staticLongitudeInput.setText(String.valueOf(prefs.getFloat("static_longitude", 0f)));

        selectStaticLocationBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, StaticLocationMapActivity.class);
            startActivity(intent);
        });

        boolean targetDetectionEnabled = getSharedPreferences("AppSettings", MODE_PRIVATE)
                .getBoolean("target_detection_enabled", true);

        targetDetectionSwitch.setChecked(targetDetectionEnabled);

        setupSpinners();
        setupAppSettingsUI();
        setupScannerSettingsUI();

        // Показать текущие значения
        //showCurrentValues();

        View root = findViewById(R.id.root_app_config);
        Toolbar toolbar = findViewById(R.id.toolbar);
        View content = findViewById(R.id.content_container);

        styleAllTextInputs(root);

        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);

        if (toolbar.getNavigationIcon() != null) {
            toolbar.getNavigationIcon().setTint(Color.WHITE);
        }

        toolbar.setNavigationOnClickListener(v -> finish());

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            ViewGroup.LayoutParams lp = toolbar.getLayoutParams();
            lp.height = bars.top + dpToPx(56);
            toolbar.setLayoutParams(lp);

            toolbar.setPadding(
                    toolbar.getPaddingLeft(),
                    bars.top,
                    toolbar.getPaddingRight(),
                    toolbar.getPaddingBottom()
            );

            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    bars.bottom
            );

            content.setPadding(
                    content.getPaddingLeft(),
                    content.getPaddingTop(),
                    content.getPaddingRight(),
                    content.getPaddingBottom()
            );

            return insets;
        });

        mapPointLimitInput = findViewById(R.id.map_point_limit_input);

        int pointLimit = getSharedPreferences("AppSettings", MODE_PRIVATE)
                .getInt("map_point_limit", 100);

        mapPointLimitInput.setText(String.valueOf(pointLimit));
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (staticLatitudeInput != null && staticLongitudeInput != null && staticLocationSwitch != null) {
            SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);

            staticLocationSwitch.setChecked(
                    prefs.getBoolean("static_location_enabled", false)
            );

            staticLatitudeInput.setText(String.valueOf(
                    prefs.getFloat("static_latitude", 0f)
            ));

            staticLongitudeInput.setText(String.valueOf(
                    prefs.getFloat("static_longitude", 0f)
            ));
        }
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

    private void setupSpinners() {
        setupLanguageSpinner();

        // Настройка Spinner для протоколов
        ArrayAdapter<String> protocolAdapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                allowedProtocols
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = view.findViewById(android.R.id.text1);
                if (text != null) {
                    text.setTextColor(Color.WHITE);
                    text.setTextSize(16);
                }
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView text = view.findViewById(android.R.id.text1);
                if (text != null) {
                    text.setTextColor(Color.WHITE);
                    text.setBackgroundColor(Color.parseColor("#0B1A2C"));
                }
                return view;
            }
        };

        protocolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        protocolSpinner.setAdapter(protocolAdapter);

        // Установить текущее значение протокола
        String currentProtocol = repository.getGeoProtocol();
        int protocolPosition = getIndexOf(allowedProtocols, currentProtocol);
        if (protocolPosition != -1) {
            protocolSpinner.setSelection(protocolPosition);
        }

        // Настройка Spinner для сканеров
        List<String> scanners = repository.getAllScanners();
        ArrayAdapter<String> scannerAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                scanners
        );
        scannerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        scannerSpinner.setAdapter(scannerAdapter);

        // Загрузить настройки для первого сканера при запуске
        if (!scanners.isEmpty()) {
            loadScannerSettings(scanners.get(0));
        }

        // Обработчик выбора сканера
        scannerSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selectedScanner = scanners.get(position);
                loadScannerSettings(selectedScanner);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void setupAppSettingsUI() {
        Button saveGeneralBtn = findViewById(R.id.save_general_btn);

        // Загружаем значения из strings.xml
        String defaultApiKey = getString(R.string.default_api_key);
        String defaultServerIp = getString(R.string.domen_server);

        // Отображаем API ключ (только для чтения)
        if (defaultApiKey != null && !defaultApiKey.isEmpty()) {
            String maskedApiKey = maskApiKey(defaultApiKey);
            apiKeyDisplay.setText(maskedApiKey);
        } else {
            apiKeyDisplay.setText(getString(R.string.error_api_key_not_configured));
        }

        // Отображаем IP сервера из strings.xml (только для чтения)
        serverIpInput.setText(defaultServerIp);
        serverIpInput.setEnabled(false); // Делаем поле недоступным для редактирования

        // Загружаем текущее имя устройства из репозитория
        String currentDeviceName = repository.getDeviceName();
        deviceNameInput.setText(currentDeviceName);

        saveGeneralBtn.setOnClickListener(v -> {
            // ПРОВЕРКА: Выбран ли допустимый протокол
            String selectedProtocol = (String) protocolSpinner.getSelectedItem();
            if (!isAllowedProtocol(selectedProtocol)) {
                showToast(getString(R.string.error_select_gsm_or_gps));
                return;
            }

            // Получаем и проверяем имя устройства
            String deviceName = deviceNameInput.getText().toString().trim();
            if (deviceName.isEmpty()) {
                deviceName = getString(R.string.default_device_name); // Если пустое, устанавливаем по умолчанию
                deviceNameInput.setText(deviceName);
            }

            // Сохраняем протокол и имя устройства
            String oldDeviceName = repository.getDeviceName();
            if (oldDeviceName == null || oldDeviceName.trim().isEmpty()) {
                oldDeviceName = getString(R.string.default_device_name);
            }

            int mapPointLimit = 100;

            try {
                mapPointLimit = Integer.parseInt(mapPointLimitInput.getText().toString().trim());
                if (mapPointLimit <= 0) mapPointLimit = 100;
            } catch (Exception e) {
                mapPointLimit = 100;
            }

            getSharedPreferences("AppSettings", MODE_PRIVATE)
                    .edit()
                    .putInt("map_point_limit", mapPointLimit)
                    .apply();

            repository.setGeoProtocol(selectedProtocol);

            getSharedPreferences("AppSettings", MODE_PRIVATE)
                    .edit()
                    .putInt("map_point_limit", mapPointLimit)
                    .putBoolean("target_detection_enabled", targetDetectionSwitch.isChecked())
                    .apply();

            if (!deviceName.equals(oldDeviceName)) {
                repository.setDeviceName(deviceName);
                new UserDeviceSyncManager(this).syncOwnerDevice();
                showToast(getString(R.string.toast_device_name_saved));
            } else {
                repository.setDeviceName(deviceName);
                showToast(getString(R.string.toast_settings_saved_device_name_not_changed));
            }

            float staticLat = 0f;
            float staticLon = 0f;

            try {
                staticLat = Float.parseFloat(staticLatitudeInput.getText().toString().trim());
                staticLon = Float.parseFloat(staticLongitudeInput.getText().toString().trim());
            } catch (Exception ignored) {}

            getSharedPreferences("AppSettings", MODE_PRIVATE)
                    .edit()
                    .putInt("map_point_limit", mapPointLimit)
                    .putBoolean("target_detection_enabled", targetDetectionSwitch.isChecked())
                    .putBoolean("static_location_enabled", staticLocationSwitch.isChecked())
                    .putFloat("static_latitude", staticLat)
                    .putFloat("static_longitude", staticLon)
                    .apply();

            //showCurrentValues();
        });
    }

    private void styleAllTextInputs(View view) {
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

        if (view instanceof TextInputLayout) {
            TextInputLayout inputLayout = (TextInputLayout) view;

            inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
            inputLayout.setBoxBackgroundColor(Color.parseColor("#172A46"));

            inputLayout.setBoxStrokeColorStateList(whiteStateList);
            inputLayout.setBoxStrokeColor(Color.WHITE);
            inputLayout.setBoxStrokeWidth(dpToPx(1));
            inputLayout.setBoxStrokeWidthFocused(dpToPx(1));

            inputLayout.setHintTextColor(whiteStateList);
            inputLayout.setDefaultHintTextColor(whiteStateList);

            inputLayout.setStartIconTintList(whiteStateList);
            inputLayout.setEndIconTintList(whiteStateList);
        }

        if (view instanceof TextInputEditText) {
            TextInputEditText editText = (TextInputEditText) view;

            editText.setTextColor(Color.WHITE);
            editText.setHintTextColor(Color.WHITE);
            editText.setBackgroundColor(Color.TRANSPARENT);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;

            for (int i = 0; i < group.getChildCount(); i++) {
                styleAllTextInputs(group.getChildAt(i));
            }
        }
    }

    private void setupScannerSettingsUI() {
        Button saveScannerBtn = findViewById(R.id.save_scanner_btn);

        saveScannerBtn.setOnClickListener(v -> {
            String selectedScanner = (String) scannerSpinner.getSelectedItem();
            String intervalText = intervalInput.getText().toString();
            String signalStrength = signalStrengthInput.getText().toString();

            // ПРОВЕРКА: Корректный ли интервал
            Float interval = parseFloat(intervalText);
            if (interval == null || interval < 0) {
                showToast(getString(R.string.error_enter_correct_interval));
                return;
            }

            // ПРОВЕРКА: корректность силы сигнала
            Float strength = parseFloat(signalStrength);
            if (strength == null || strength > 0 || strength < -120) {
                showToast(getString(R.string.error_signal_strength_range));;
                return;
            }

            ScannerSettings newSettings = new ScannerSettings(
                    selectedScanner,
                    enabledSwitch.isChecked(),
                    interval,
                    strength
            );

            if (repository.updateScannerSettings(newSettings)) {
                showToast(getString(R.string.toast_scanner_settings_saved, selectedScanner));
                updateScanningStatus();
                //showCurrentValues();
            } else {
                showToast(getString(R.string.error_saving_settings));
            }
        });
    }

    private void loadScannerSettings(String scannerName) {
        ScannerSettings settings = repository.getScannerSettings(scannerName);
        if (settings != null) {
            intervalInput.setText(String.valueOf(settings.getScanInterval()));
            signalStrengthInput.setText(String.valueOf(settings.getSignalStrength()));
            enabledSwitch.setChecked(settings.isEnabled());
        }
    }

    private void updateScanningStatus() {
        boolean isScanning = false;
        for (String scannerName : repository.getAllScanners()) {
            ScannerSettings settings = repository.getScannerSettings(scannerName);
            if (settings != null) {
                isScanning = isScanning || settings.isEnabled();
            }
        }
        repository.setScanning(isScanning);
    }

//    private void showCurrentValues() {
//        StringBuilder stringBuilder = new StringBuilder();
//
//        // Общие настройки
//        stringBuilder.append("=== ОБЩИЕ НАСТРОЙКИ ===\n");
//
//        // IP сервера всегда из strings.xml
//        String serverIp = getString(R.string.domen_server);
//        stringBuilder.append("IPv4 сервера: ").append(serverIp).append("\n");
//
//        // API ключ всегда из strings.xml
//        String apiKey = getString(R.string.default_api_key);
//        stringBuilder.append("API Key: ").append(apiKey != null && !apiKey.isEmpty() ? "настроен" : "не настроен").append("\n");
//        stringBuilder.append("Протокол: ").append(repository.getGeoProtocol()).append("\n");
//        stringBuilder.append("Сканирование активно: ").append(repository.isScanning()).append("\n");
//
//        // Имя устройства из репозитория
//        String deviceName = repository.getDeviceName();
//        if (deviceName == null || deviceName.isEmpty()) {
//            deviceName = "Telephone (по умолчанию)";
//        }
//        stringBuilder.append("Имя устройства: ").append(deviceName).append("\n");
//
//        // Настройки сканеров
//        stringBuilder.append("\n=== НАСТРОЙКИ СКАНЕРОВ ===\n");
//        for (String scannerName : repository.getAllScanners()) {
//            ScannerSettings settings = repository.getScannerSettings(scannerName);
//            if (settings != null) {
//                stringBuilder.append(scannerName)
//                        .append(": interval=").append(settings.getScanInterval()).append("s, ")
//                        .append("signal strength=").append(settings.getSignalStrength()).append(", ")
//                        .append("enabled=").append(settings.isEnabled()).append("\n");
//            }
//        }
//
//    }

    private void setupLanguageSpinner() {
        String[] languageNames = {
                getString(R.string.language_russian),
                getString(R.string.language_english),
                getString(R.string.language_arabic),
                getString(R.string.language_chinese)
        };

        String[] languageCodes = {"ru", "en", "ar", "zh"};

        ArrayAdapter<String> languageAdapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                languageNames
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = view.findViewById(android.R.id.text1);

                if (text != null) {
                    text.setTextColor(Color.WHITE);
                    text.setTextSize(16);
                    text.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
                }

                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView text = view.findViewById(android.R.id.text1);

                if (text != null) {
                    text.setTextColor(Color.WHITE);
                    text.setTextSize(16);
                    text.setBackgroundColor(Color.parseColor("#172A46"));
                    text.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
                }

                return view;
            }
        };

        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(languageAdapter);

        String currentLanguage = LocaleHelper.getCurrentLanguage(this);
        int currentPosition = getIndexOf(languageCodes, currentLanguage);

        if (currentPosition != -1) {
            languageSpinner.setSelection(currentPosition, false);
        }

        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedLanguage = languageCodes[position];
                String currentLanguage = LocaleHelper.getCurrentLanguage(AppConfigViewActivity.this);

                if (!selectedLanguage.equals(currentLanguage)) {
                    LocaleHelper.setLocale(AppConfigViewActivity.this, selectedLanguage);

                    Intent intent = getIntent();
                    finish();
                    overridePendingTransition(0, 0);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void showToast(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        View view = toast.getView();

        if (view != null) {
            TextView text = view.findViewById(android.R.id.message);
            if (text != null) {
                text.setTextColor(android.graphics.Color.WHITE);
            }
        }

        toast.show();
    }

    private boolean isAllowedProtocol(String protocol) {
        for (String allowedProtocol : allowedProtocols) {
            if (allowedProtocol.equals(protocol)) {
                return true;
            }
        }
        return false;
    }

    private Integer getIndexOf(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) {
                return i;
            }
        }
        return -1;
    }

    private Float parseFloat(String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Метод для проверки корректности IPv4 адреса
    private boolean isValidIPv4(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        return pattern.matcher(ip).matches();
    }

    // Метод для маскирования API ключа (безопасность)
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 12) {
            return apiKey;
        }
        return apiKey.substring(0, 8) + "..." + apiKey.substring(apiKey.length() - 4);
    }
}
