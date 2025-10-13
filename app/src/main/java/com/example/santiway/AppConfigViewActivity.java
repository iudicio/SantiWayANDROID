package com.example.santiway;

import com.example.santiway.host_database.*;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import java.util.List;
import java.util.regex.Pattern;

public class AppConfigViewActivity extends ComponentActivity {
    private AppSettingsRepository repository;
    private TextView resultText;
    private Spinner protocolSpinner;
    private Spinner scannerSpinner;
    private EditText intervalInput;
    private Switch enabledSwitch;
    private EditText signalStrengthInput;
    private EditText serverIpInput;
    private TextView apiKeyDisplay; // Изменено с EditText на TextView

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

        repository = new AppSettingsRepository(this);
        resultText = findViewById(R.id.result_text);
        scannerSpinner = findViewById(R.id.scanner_spinner);
        protocolSpinner = findViewById(R.id.protocol_spinner);
        intervalInput = findViewById(R.id.interval_input);
        enabledSwitch = findViewById(R.id.enabled_switch);
        signalStrengthInput = findViewById(R.id.signal_strength_input);
        serverIpInput = findViewById(R.id.server_ip_input);
        apiKeyDisplay = findViewById(R.id.api_key_display); // Новый ID

        setupSpinners();
        setupAppSettingsUI();
        setupScannerSettingsUI();

        // Показать текущие значения
        showCurrentValues();
    }

    private void setupSpinners() {
        // Настройка Spinner для протоколов
        ArrayAdapter<String> protocolAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                allowedProtocols
        );
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

        // Загружаем API ключ из strings.xml
        String defaultApiKey = getString(R.string.default_api_key);

        // Отображаем API ключ (только для чтения)
        if (defaultApiKey != null && !defaultApiKey.isEmpty()) {
            // Показываем только первые 8 и последние 4 символа для безопасности
            String maskedApiKey = maskApiKey(defaultApiKey);
            apiKeyDisplay.setText(maskedApiKey);
        } else {
            apiKeyDisplay.setText("API ключ не настроен в приложении");
        }

        // Загрузить текущие значения сервера
        serverIpInput.setText(repository.getServerIp() != null ? repository.getServerIp() : "");

        saveGeneralBtn.setOnClickListener(v -> {
            // ПРОВЕРКА: Корректный ли IPv4 адрес
            String serverIp = serverIpInput.getText().toString().trim();
            if (!isValidIPv4(serverIp)) {
                showToast("Ошибка: введите корректный IPv4 адрес сервера");
                return;
            }

            // ПРОВЕРКА: Выбран ли допустимый протокол
            String selectedProtocol = (String) protocolSpinner.getSelectedItem();
            if (!isAllowedProtocol(selectedProtocol)) {
                showToast("Ошибка: выберите GSM или GPS");
                return;
            }

            // Сохраняем настройки (API ключ не сохраняем, он берется из strings.xml)
            repository.setServerIp(serverIp);
            repository.setGeoProtocol(selectedProtocol);

            showToast("Общие настройки сохранены!");
            showCurrentValues();
        });
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
                showToast("Ошибка: введите корректный интервал");
                return;
            }

            // ПРОВЕРКА: корректность силы сигнала
            Float strength = parseFloat(signalStrength);
            if (strength == null || strength > 0 || strength < -120) {
                showToast("Ошибка: сила сигнала должна быть в диапазоне от -120 до 0 дБм");
                return;
            }

            ScannerSettings newSettings = new ScannerSettings(
                    selectedScanner,
                    enabledSwitch.isChecked(),
                    interval,
                    strength
            );

            if (repository.updateScannerSettings(newSettings)) {
                showToast("Настройки сканера '" + selectedScanner + "' сохранены!");
                updateScanningStatus();
                showCurrentValues();
            } else {
                showToast("Ошибка при сохранении настроек");
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

    private void showCurrentValues() {
        StringBuilder stringBuilder = new StringBuilder();

        // Общие настройки
        stringBuilder.append("=== ОБЩИЕ НАСТРОЙКИ ===\n");
        stringBuilder.append("IPv4 сервера: ").append(repository.getServerIp() != null ? repository.getServerIp() : "не установлен").append("\n");
        stringBuilder.append("API Key: ").append(getString(R.string.default_api_key) != null ? "настроен в приложении" : "не настроен").append("\n");
        stringBuilder.append("Протокол: ").append(repository.getGeoProtocol()).append("\n");
        stringBuilder.append("Сканирование активно: ").append(repository.isScanning()).append("\n");

        // Настройки сканеров
        stringBuilder.append("\n=== НАСТРОЙКИ СКАНЕРОВ ===\n");
        for (String scannerName : repository.getAllScanners()) {
            ScannerSettings settings = repository.getScannerSettings(scannerName);
            if (settings != null) {
                stringBuilder.append(scannerName)
                        .append(": interval=").append(settings.getScanInterval()).append("s, ")
                        .append("signal strength=").append(settings.getSignalStrength()).append(", ")
                        .append("enabled=").append(settings.isEnabled()).append("\n");
            }
        }

        resultText.setText(stringBuilder.toString());
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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