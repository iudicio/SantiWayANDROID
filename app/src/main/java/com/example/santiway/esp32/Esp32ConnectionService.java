package com.example.santiway.esp32;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.santiway.FolderNameHelper;
import com.example.santiway.R;
import com.example.santiway.upload_data.MainDatabaseHelper;
import com.example.santiway.wifi_scanner.WifiDevice;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Esp32ConnectionService extends Service {
    public static final UUID SERVICE_UUID = UUID.fromString("7a1e0001-8e7f-4d8d-a7f4-2c6e6d520001");
    private static final UUID DATA_UUID = UUID.fromString("7a1e0002-8e7f-4d8d-a7f4-2c6e6d520001");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final String ACTION_DISCOVER = "com.example.santiway.esp32.DISCOVER";
    public static final String ACTION_CONNECT = "com.example.santiway.esp32.CONNECT";
    public static final String ACTION_DISCONNECT = "com.example.santiway.esp32.DISCONNECT";
    public static final String ACTION_CHANGED = "com.example.santiway.esp32.CHANGED";
    public static final String EXTRA_MAC = "mac";
    private static final String CHANNEL_ID = "esp32_connections";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, BluetoothGatt> gatts = new ConcurrentHashMap<>();
    private final Set<String> connecting = ConcurrentHashMap.newKeySet();
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private Esp32DatabaseHelper database;
    private MainDatabaseHelper mainDatabase;
    private FusedLocationProviderClient locationClient;
    private long discoverNewUntil;

    @Override public void onCreate() {
        super.onCreate();
        database = new Esp32DatabaseHelper(this);
        database.markAllDisconnected();
        mainDatabase = new MainDatabaseHelper(this);
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager != null ? manager.getAdapter() : null;
        createChannel();
        startForeground(3201, new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_device)
                .setContentTitle(getString(R.string.esp32_service_title))
                .setContentText(getString(R.string.esp32_service_text))
                .setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build());
        handler.post(scanCycle);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            String mac = intent.getStringExtra(EXTRA_MAC);
            if (ACTION_DISCOVER.equals(action)) discoverNewUntil = System.currentTimeMillis() + 30000;
            if (ACTION_CONNECT.equals(action) && mac != null) {
                database.setAutoConnect(mac, true);
                if (adapter != null && hasPermissions()) connect(adapter.getRemoteDevice(mac));
            }
            if (ACTION_DISCONNECT.equals(action) && mac != null) disconnect(mac, true);
        }
        handler.removeCallbacks(scanCycle);
        handler.post(scanCycle);
        return START_STICKY;
    }

    private boolean hasPermissions() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                        && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED);
    }

    private final Runnable scanCycle = new Runnable() {
        @Override public void run() {
            if (adapter != null && adapter.isEnabled() && hasPermissions()) {
                scanner = adapter.getBluetoothLeScanner();
                if (scanner != null) {
                    scanner.startScan(scanCallback);
                    handler.postDelayed(() -> {
                        if (scanner != null && hasPermissions()) scanner.stopScan(scanCallback);
                        scanner = null;
                    }, 8000);
                }
            }
            handler.postDelayed(this, 12000);
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            String mac = result.getDevice().getAddress().toUpperCase();
            Set<String> saved = database.getAutoConnectMacs();
            boolean serviceMatch = result.getScanRecord() != null && result.getScanRecord().getServiceUuids() != null
                    && result.getScanRecord().getServiceUuids().stream().anyMatch(p -> SERVICE_UUID.equals(p.getUuid()));
            boolean discoverNew = System.currentTimeMillis() < discoverNewUntil;
            if ((saved.contains(mac) || (discoverNew && serviceMatch)) && !gatts.containsKey(mac) && !connecting.contains(mac)) {
                connect(result.getDevice());
            }
        }
    };

    private void connect(BluetoothDevice device) {
        if (device == null || !hasPermissions()) return;
        String mac = device.getAddress().toUpperCase();
        connecting.add(mac);
        BluetoothGatt gatt = device.connectGatt(this, false, new DeviceGattCallback(mac), BluetoothDevice.TRANSPORT_LE);
        gatts.put(mac, gatt);
    }

    private void disconnect(String mac, boolean disableAutoConnect) {
        String key = mac.toUpperCase();
        if (disableAutoConnect) database.setAutoConnect(key, false);
        BluetoothGatt gatt = gatts.remove(key);
        connecting.remove(key);
        if (gatt != null && hasPermissions()) { gatt.disconnect(); gatt.close(); }
        database.setConnected(key, false);
        broadcastChanged();
    }

    private class DeviceGattCallback extends BluetoothGattCallback {
        private final String mac;
        DeviceGattCallback(String mac) { this.mac = mac; }

        @Override public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connecting.remove(mac);
                database.upsertDevice(mac, "ESP32 " + mac.substring(Math.max(0, mac.length() - 5)), 0, 0, 0);
                database.setConnected(mac, true);
                updateDefaultLocation(mac);
                if (!gatt.requestMtu(247)) gatt.discoverServices();
                broadcastChanged();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connecting.remove(mac);
                gatts.remove(mac);
                database.setConnected(mac, false);
                gatt.close();
                broadcastChanged();
                handler.removeCallbacks(scanCycle);
                handler.postDelayed(scanCycle, 1500);
            }
        }

        @Override public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) { gatt.discoverServices(); }

        @Override public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            if (service == null) return;
            BluetoothGattCharacteristic data = service.getCharacteristic(DATA_UUID);
            gatt.setCharacteristicNotification(data, true);
            BluetoothGattDescriptor descriptor = data.getDescriptor(CCCD_UUID);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }
        }

        @Override public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic c) { processRecord(mac, c.getValue()); }
        @Override public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
                @NonNull BluetoothGattCharacteristic c, @NonNull byte[] value) { processRecord(mac, value); }
    }

    private void updateDefaultLocation(String mac) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        locationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) database.updateDefaultCoordinatesIfUnset(mac, location.getLatitude(),
                    location.getLongitude(), location.hasAltitude() ? location.getAltitude() : 0);
            broadcastChanged();
        });
    }

    private void processRecord(String sourceMac, byte[] bytes) {
        if (bytes == null) return;
        String[] fields = new String(bytes, StandardCharsets.UTF_8).trim().split("\\|", 5);
        if (fields.length < 4 || fields[1].equalsIgnoreCase(sourceMac)) return;
        try {
            int rssi = Integer.parseInt(fields[2]);
            String transport = "W".equals(fields[0]) ? "Wi-Fi" : "B".equals(fields[0]) ? "Bluetooth" : "";
            if (transport.isEmpty()) return;
            database.saveObservation(sourceMac, transport, fields[1], rssi, fields.length > 4 ? fields[4] : fields[3]);
            saveAsRegularScan(sourceMac, fields, rssi);
            broadcastChanged();
        } catch (NumberFormatException ignored) { }
    }

    private void saveAsRegularScan(String sourceMac, String[] fields, int rssi) {
        double[] coordinates = database.getCoordinates(sourceMac);
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        String folder = prefs.getString("current_folder", FolderNameHelper.MAIN_FOLDER_INTERNAL);
        long now = System.currentTimeMillis();
        if ("W".equals(fields[0])) {
            int channel;
            try { channel = Integer.parseInt(fields[3]); } catch (Exception e) { channel = 0; }
            WifiDevice device = new WifiDevice();
            device.setBssid(fields[1]);
            device.setSsid(fields.length > 4 ? fields[4] : "");
            device.setSignalStrength(rssi);
            device.setFrequency(channel == 14 ? 2484 : channel > 0 ? 2407 + channel * 5 : 0);
            device.setCapabilities("ESP32");
            device.setVendor("ESP32");
            device.setLatitude(coordinates[0]); device.setLongitude(coordinates[1]); device.setAltitude(coordinates[2]);
            device.setLocationAccuracy(0); device.setTimestamp(now);
            mainDatabase.addWifiDevice(device, folder);
        } else {
            com.example.santiway.bluetooth_scanner.BluetoothDevice device =
                    new com.example.santiway.bluetooth_scanner.BluetoothDevice();
            device.setMacAddress(fields[1]);
            device.setSignalStrength(rssi);
            device.setDeviceName(fields.length > 4 ? fields[4] : "");
            device.setVendor("ESP32");
            device.setLatitude(coordinates[0]); device.setLongitude(coordinates[1]); device.setAltitude(coordinates[2]);
            device.setLocationAccuracy(0); device.setTimestamp(now);
            mainDatabase.addBluetoothDevice(device, folder);
        }
    }

    private void broadcastChanged() { sendBroadcast(new Intent(ACTION_CHANGED).setPackage(getPackageName())); }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.esp32_service_title), NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (scanner != null && hasPermissions()) scanner.stopScan(scanCallback);
        for (BluetoothGatt gatt : gatts.values()) { if (hasPermissions()) gatt.disconnect(); gatt.close(); }
        database.markAllDisconnected();
        database.close(); mainDatabase.close();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
