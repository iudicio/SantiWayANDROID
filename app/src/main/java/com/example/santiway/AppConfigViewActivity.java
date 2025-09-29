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

public class AppConfigViewActivity extends ComponentActivity {
    private AppSettingsRepository repository;
    private TextView resultText;
    private Spinner protocolSpinner;
    private Spinner scannerSpinner;
    private EditText intervalInput;
    private Switch enabledSwitch;
    private EditText signalStrengthInput;

    // Допустимые значения для протокола
    private final String[] allowedProtocols = {"GSM", "GPS"};

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
        EditText apiKeyInput = findViewById(R.id.api_key_input);
        Button saveGeneralBtn = findViewById(R.id.save_general_btn);

        // Загрузить текущие значения
        apiKeyInput.setText(repository.getApiKey() != null ? repository.getApiKey() : "");

        saveGeneralBtn.setOnClickListener(v -> {
            // ПРОВЕРКА: Выбран ли допустимый протокол
            String selectedProtocol = (String) protocolSpinner.getSelectedItem();
            if (!isAllowedProtocol(selectedProtocol)) {
                showToast("Ошибка: выберите GSM или GPS");
                return;
            }

            repository.setApiKey(apiKeyInput.getText().toString());
            repository.setGeoProtocol(selectedProtocol); // Сохраняем выбранное значение из Spinner
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
        stringBuilder.append("API Key: ").append(repository.getApiKey() != null ? repository.getApiKey() : "не установлен").append("\n");
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
}