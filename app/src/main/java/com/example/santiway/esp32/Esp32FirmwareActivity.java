package com.example.santiway.esp32.firmware;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.example.santiway.BaseLocalizedActivity;
import com.example.santiway.R;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class Esp32FirmwareActivity extends BaseLocalizedActivity {
    private static final UUID OTA_SERVICE_UUID =
            UUID.fromString("7a1e1001-8e7f-4d8d-a7f4-2c6e6d520001");

    private static final UUID OTA_CONTROL_UUID =
            UUID.fromString("7a1e1002-8e7f-4d8d-a7f4-2c6e6d520001");

    private static final UUID OTA_DATA_UUID =
            UUID.fromString("7a1e1003-8e7f-4d8d-a7f4-2c6e6d520001");

    private static final int REQUEST_BLE = 7010;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView statusText;
    private TextView fileText;
    private TextView targetText;
    private ProgressBar progressBar;
    private Button chooseButton;
    private Button scanButton;
    private Button uploadButton;

    private byte[] firmwareBytes;
    private String firmwareName = "";

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothDevice selectedDevice;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic controlCharacteristic;
    private BluetoothGattCharacteristic dataCharacteristic;

    private boolean uploadInProgress;
    private int currentOffset;
    private int chunkSize = 180;

    private final List<BluetoothDevice> discoveredDevices = new ArrayList<>();

    private final ActivityResultLauncher<String> firmwarePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    loadFirmware(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager != null ? manager.getAdapter() : BluetoothAdapter.getDefaultAdapter();

        buildUi();
        updateButtons();
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Color.parseColor("#071427"));
        getWindow().setNavigationBarColor(Color.parseColor("#172A46"));

        ScrollView scrollView = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));
        root.setBackgroundColor(Color.parseColor("#071427"));

        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("ESP32 Firmware");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, 0, 0, dp(18));
        root.addView(title);

        statusText = makeText("Статус: ожидание");
        fileText = makeText("Файл: не выбран");
        targetText = makeText("ESP32: не выбрана");

        root.addView(statusText);
        root.addView(fileText);
        root.addView(targetText);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);

        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(24)
        );
        progressParams.setMargins(0, dp(16), 0, dp(16));
        root.addView(progressBar, progressParams);

        chooseButton = makeButton("Выбрать .bin прошивку");
        scanButton = makeButton("Найти ESP32 OTA");
        uploadButton = makeButton("Загрузить прошивку");

        chooseButton.setOnClickListener(v -> firmwarePicker.launch("*/*"));
        scanButton.setOnClickListener(v -> startScan());
        uploadButton.setOnClickListener(v -> startUpload());

        root.addView(chooseButton);
        root.addView(scanButton);
        root.addView(uploadButton);

        setContentView(scrollView);
    }

    private TextView makeText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(15);
        view.setPadding(0, dp(6), 0, dp(6));
        return view;
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.parseColor("#172A46"));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        params.setMargins(0, dp(8), 0, dp(8));
        button.setLayoutParams(params);

        return button;
    }

    private void loadFirmware(Uri uri) {
        try {
            firmwareName = resolveFileName(uri);

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IllegalStateException("Не удалось открыть файл");
                }

                byte[] buffer = new byte[8192];
                int read;

                while ((read = input.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            firmwareBytes = out.toByteArray();

            if (firmwareBytes.length == 0) {
                throw new IllegalStateException("Файл пустой");
            }

            fileText.setText("Файл: " + firmwareName + " (" + firmwareBytes.length + " bytes)");
            statusText.setText("Статус: файл прошивки выбран");
            progressBar.setProgress(0);

            updateButtons();
        } catch (Exception e) {
            firmwareBytes = null;
            firmwareName = "";
            fileText.setText("Файл: ошибка чтения");
            statusText.setText("Статус: " + e.getMessage());
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            updateButtons();
        }
    }

    private String resolveFileName(Uri uri) {
        String result = null;

        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    result = cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
        }

        if (result == null || result.trim().isEmpty()) {
            result = "firmware.bin";
        }

        return result;
    }

    private boolean hasBlePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }

        return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_BLE
            );
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_BLE
            );
        }
    }

    private void startScan() {
        if (!hasBlePermissions()) {
            requestBlePermissions();
            return;
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth выключен или недоступен", Toast.LENGTH_SHORT).show();
            return;
        }

        scanner = bluetoothAdapter.getBluetoothLeScanner();

        if (scanner == null) {
            Toast.makeText(this, "BLE scanner недоступен", Toast.LENGTH_SHORT).show();
            return;
        }

        discoveredDevices.clear();
        statusText.setText("Статус: поиск ESP32 OTA...");

        scanner.startScan(scanCallback);

        handler.postDelayed(() -> {
            try {
                if (scanner != null) {
                    scanner.stopScan(scanCallback);
                }
            } catch (Exception ignored) {
            }

            showDevicePicker();
        }, 8000);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result == null || result.getDevice() == null) return;

            BluetoothDevice device = result.getDevice();

            for (BluetoothDevice existing : discoveredDevices) {
                if (existing.getAddress().equalsIgnoreCase(device.getAddress())) {
                    return;
                }
            }

            discoveredDevices.add(device);
        }
    };

    private void showDevicePicker() {
        if (discoveredDevices.isEmpty()) {
            statusText.setText("Статус: ESP32 OTA не найдена");
            Toast.makeText(this, "ESP32 OTA не найдена", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = new String[discoveredDevices.size()];

        for (int i = 0; i < discoveredDevices.size(); i++) {
            BluetoothDevice device = discoveredDevices.get(i);

            String name = "ESP32";
            if (hasBlePermissions()) {
                try {
                    String btName = device.getName();
                    if (btName != null && !btName.trim().isEmpty()) {
                        name = btName;
                    }
                } catch (SecurityException ignored) {
                }
            }

            items[i] = name + "\n" + device.getAddress();
        }

        new AlertDialog.Builder(this)
                .setTitle("Выбери ESP32")
                .setItems(items, (dialog, which) -> {
                    selectedDevice = discoveredDevices.get(which);
                    targetText.setText("ESP32: " + selectedDevice.getAddress());
                    statusText.setText("Статус: устройство выбрано");
                    updateButtons();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void startUpload() {
        if (firmwareBytes == null || firmwareBytes.length == 0) {
            Toast.makeText(this, "Сначала выбери .bin файл", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedDevice == null) {
            Toast.makeText(this, "Сначала выбери ESP32", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!hasBlePermissions()) {
            requestBlePermissions();
            return;
        }

        uploadInProgress = true;
        currentOffset = 0;
        progressBar.setProgress(0);
        statusText.setText("Статус: подключение к ESP32...");
        updateButtons();

        try {
            gatt = selectedDevice.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (SecurityException e) {
            uploadInProgress = false;
            statusText.setText("Статус: нет Bluetooth-разрешений");
            updateButtons();
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread(() -> statusText.setText("Статус: подключено, запрос MTU..."));

                try {
                    gatt.requestMtu(247);
                } catch (SecurityException e) {
                    failUpload("Нет разрешения BLUETOOTH_CONNECT");
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (uploadInProgress) {
                    failUpload("ESP32 отключилась");
                }
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            chunkSize = Math.max(20, Math.min(220, mtu - 7));

            try {
                gatt.discoverServices();
            } catch (SecurityException e) {
                failUpload("Нет разрешения на поиск сервисов");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattService service = gatt.getService(OTA_SERVICE_UUID);

            if (service == null) {
                failUpload("OTA-сервис не найден. Проверь прошивку ESP32.");
                return;
            }

            controlCharacteristic = service.getCharacteristic(OTA_CONTROL_UUID);
            dataCharacteristic = service.getCharacteristic(OTA_DATA_UUID);

            if (controlCharacteristic == null || dataCharacteristic == null) {
                failUpload("OTA-характеристики не найдены");
                return;
            }

            runOnUiThread(() -> statusText.setText("Статус: отправка START"));

            writeControlPacket((byte) 0x01, firmwareBytes.length);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failUpload("Ошибка записи BLE: " + status);
                return;
            }

            if (characteristic.getUuid().equals(OTA_CONTROL_UUID)) {
                if (currentOffset == 0) {
                    sendNextChunk();
                } else {
                    completeUpload();
                }
            } else if (characteristic.getUuid().equals(OTA_DATA_UUID)) {
                sendNextChunk();
            }
        }
    };

    private void sendNextChunk() {
        if (!uploadInProgress) return;

        if (currentOffset >= firmwareBytes.length) {
            runOnUiThread(() -> statusText.setText("Статус: отправка END"));
            writeControlPacket((byte) 0x02, firmwareBytes.length);
            return;
        }

        int remaining = firmwareBytes.length - currentOffset;
        int len = Math.min(chunkSize, remaining);

        byte[] chunk = new byte[len];
        System.arraycopy(firmwareBytes, currentOffset, chunk, 0, len);

        currentOffset += len;

        int percent = Math.round(currentOffset * 100f / firmwareBytes.length);

        runOnUiThread(() -> {
            progressBar.setProgress(percent);
            statusText.setText(String.format(
                    Locale.getDefault(),
                    "Статус: загрузка %d%%",
                    percent
            ));
        });

        writeCharacteristic(dataCharacteristic, chunk);
    }

    private void writeControlPacket(byte command, int size) {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(command);
        buffer.putInt(size);

        writeCharacteristic(controlCharacteristic, buffer.array());
    }

    private void writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] value) {
        if (gatt == null || characteristic == null) {
            failUpload("BLE-соединение не готово");
            return;
        }

        try {
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            characteristic.setValue(value);

            boolean started = gatt.writeCharacteristic(characteristic);

            if (!started) {
                failUpload("Не удалось начать BLE-запись");
            }
        } catch (SecurityException e) {
            failUpload("Нет Bluetooth-разрешения");
        }
    }

    private void completeUpload() {
        uploadInProgress = false;

        runOnUiThread(() -> {
            progressBar.setProgress(100);
            statusText.setText("Статус: прошивка отправлена. ESP32 должна перезагрузиться.");
            Toast.makeText(this, "Прошивка отправлена", Toast.LENGTH_LONG).show();
            updateButtons();
        });

        closeGatt();
    }

    private void failUpload(String message) {
        uploadInProgress = false;

        runOnUiThread(() -> {
            statusText.setText("Статус: ошибка — " + message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            updateButtons();
        });

        closeGatt();
    }

    private void closeGatt() {
        try {
            if (gatt != null && hasBlePermissions()) {
                gatt.disconnect();
                gatt.close();
            }
        } catch (Exception ignored) {
        }

        gatt = null;
        controlCharacteristic = null;
        dataCharacteristic = null;
    }

    private void updateButtons() {
        if (chooseButton != null) chooseButton.setEnabled(!uploadInProgress);
        if (scanButton != null) scanButton.setEnabled(!uploadInProgress);
        if (uploadButton != null) {
            uploadButton.setEnabled(!uploadInProgress && firmwareBytes != null && selectedDevice != null);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        try {
            if (scanner != null && hasBlePermissions()) {
                scanner.stopScan(scanCallback);
            }
        } catch (Exception ignored) {
        }

        closeGatt();
        handler.removeCallbacksAndMessages(null);

        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_BLE && hasBlePermissions()) {
            Toast.makeText(this, "Bluetooth-разрешения получены", Toast.LENGTH_SHORT).show();
        }
    }
}
