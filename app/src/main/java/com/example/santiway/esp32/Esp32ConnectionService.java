package com.example.santiway.esp32;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
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

import android.location.Location;
import com.example.santiway.gsm_protocol.LocationManager;
import com.example.santiway.FolderNameHelper;
import com.example.santiway.R;
import com.example.santiway.upload_data.MainDatabaseHelper;
import com.example.santiway.wifi_scanner.WifiDevice;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Esp32ConnectionService extends Service {
    public static final UUID SERVICE_UUID = UUID.fromString("7a1e0001-8e7f-4d8d-a7f4-2c6e6d520001");
    private static final UUID DATA_UUID = UUID.fromString("7a1e0002-8e7f-4d8d-a7f4-2c6e6d520001");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final int PHONE_BEACON_MANUFACTURER_ID = 0x02E5;
    private static final long POSITION_REFRESH_MS = 15000L;
    public static final String PREFS_MESH_LOCATION = "esp32_mesh_location";
    public static final String ACTION_DISCOVER = "com.example.santiway.esp32.DISCOVER";
    public static final String ACTION_CONNECT = "com.example.santiway.esp32.CONNECT";
    public static final String ACTION_DISCONNECT = "com.example.santiway.esp32.DISCONNECT";
    public static final String ACTION_CHANGED = "com.example.santiway.esp32.CHANGED";
    public static final String ACTION_DEVICE_FOUND = "com.example.santiway.esp32.DEVICE_FOUND";
    public static final String EXTRA_MAC = "mac";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_RSSI = "rssi";
    private static final String CHANNEL_ID = "esp32_connections";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, BluetoothGatt> gatts = new ConcurrentHashMap<>();
    private final Set<String> connecting = ConcurrentHashMap.newKeySet();
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothLeAdvertiser advertiser;
    private String phoneBeaconId;
    private Esp32DatabaseHelper database;
    private MainDatabaseHelper mainDatabase;
    private long discoverNewUntil;

    @Override public void onCreate() {
        super.onCreate();
        database = new Esp32DatabaseHelper(this);
        database.markAllDisconnected();
        mainDatabase = new MainDatabaseHelper(this);
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager != null ? manager.getAdapter() : null;
        phoneBeaconId = getOrCreatePhoneBeaconId();
        createChannel();
        Intent notificationIntent = new Intent(this, Esp32Activity.class);
        notificationIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                3201,
                notificationIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_device)
                .setContentTitle(getString(R.string.esp32_service_title))
                .setContentText(getString(R.string.esp32_service_text))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(3201, notification);
        startPhoneBeacon();
        handler.post(scanCycle);
        handler.post(positionRefreshCycle);
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
                        && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                        && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED);
    }

    private String getOrCreatePhoneBeaconId() {
        SharedPreferences prefs = getSharedPreferences(PREFS_MESH_LOCATION, MODE_PRIVATE);
        String existing = prefs.getString("phone_beacon_id", null);
        if (existing != null && !existing.isEmpty()) return existing;
        String id = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.US);
        prefs.edit().putString("phone_beacon_id", id).apply();
        return id;
    }

    @android.annotation.SuppressLint("MissingPermission")
    private void startPhoneBeacon() {
        if (adapter == null || !adapter.isEnabled() || !hasPermissions()) return;
        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) return;
        byte[] marker = ("SWP" + phoneBeaconId).getBytes(StandardCharsets.UTF_8);
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build();
        AdvertiseData data = new AdvertiseData.Builder()
                .addManufacturerData(PHONE_BEACON_MANUFACTURER_ID, marker)
                .build();
        advertiser.startAdvertising(settings, data, advertiseCallback);
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override public void onStartFailure(int errorCode) { advertiser = null; }
    };

    private final Runnable scanCycle = new Runnable() {
        @Override public void run() {
            if (adapter != null && adapter.isEnabled() && hasPermissions()) {
                if (advertiser == null) startPhoneBeacon();
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

    private final Runnable positionRefreshCycle = new Runnable() {
        @Override public void run() {
            if (database != null) database.autoPositionUnknownDevices();
            handler.postDelayed(this, POSITION_REFRESH_MS);
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            String mac = result.getDevice().getAddress().toUpperCase();
            Set<String> saved = database.getAutoConnectMacs();
            boolean serviceMatch = result.getScanRecord() != null && result.getScanRecord().getServiceUuids() != null
                    && result.getScanRecord().getServiceUuids().stream().anyMatch(p -> SERVICE_UUID.equals(p.getUuid()));
            boolean discoverNew = System.currentTimeMillis() < discoverNewUntil;
            if (discoverNew && serviceMatch && !gatts.containsKey(mac) && !connecting.contains(mac)) {
                broadcastDeviceFound(result);
            }
            if (saved.contains(mac) && !discoverNew && !gatts.containsKey(mac) && !connecting.contains(mac)) {
                connect(result.getDevice());
            }
        }
    };

    @android.annotation.SuppressLint("MissingPermission")
    private void broadcastDeviceFound(ScanResult result) {
        if (!hasPermissions()) return;
        String name = result.getDevice().getName();
        Intent intent = new Intent(ACTION_DEVICE_FOUND).setPackage(getPackageName());
        intent.putExtra(EXTRA_MAC, result.getDevice().getAddress().toUpperCase());
        intent.putExtra(EXTRA_NAME, name == null || name.trim().isEmpty() ? "ESP32" : name);
        intent.putExtra(EXTRA_RSSI, result.getRssi());
        sendBroadcast(intent);
    }

    private void connect(BluetoothDevice device) {
        if (device == null || !hasPermissions()) return;
        String mac = device.getAddress().toUpperCase();
        connecting.add(mac);
        BluetoothGatt gatt = device.connectGatt(this, false, new DeviceGattCallback(mac), BluetoothDevice.TRANSPORT_LE);
        gatts.put(mac, gatt);
        handler.postDelayed(() -> {
            if (!connecting.contains(mac)) return;
            BluetoothGatt pending = gatts.remove(mac);
            connecting.remove(mac);
            if (pending != null && hasPermissions()) { pending.disconnect(); pending.close(); }
            database.setConnected(mac, false);
            broadcastChanged();
        }, 12000);
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
                double[] coords = getFallbackAnchorCoordinates();

                database.upsertDevice(
                        mac,
                        "ESP32 " + mac.substring(Math.max(0, mac.length() - 5)),
                        coords[0],
                        coords[1],
                        coords[2]
                );
                database.setConnected(mac, true);
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

    private void processRecord(String sourceMac, byte[] bytes) {
        if (bytes == null) return;
        String[] fields = new String(bytes, StandardCharsets.UTF_8).trim().split("\\|", 5);
        if (fields.length < 4) return;
        if ("R".equals(fields[0])) {
            processRelayedRecord(sourceMac, fields);
            return;
        }
        if ("M".equals(fields[0])) {
            processMeshLink(sourceMac, fields, 0);
            return;
        }
        if ("P".equals(fields[0])) {
            processPhoneSample(sourceMac, fields, 0);
            return;
        }
        if (fields[1].equalsIgnoreCase(sourceMac)) return;
        try {
            int rssi = Integer.parseInt(fields[2]);
            String transport = "W".equals(fields[0]) ? "Wi-Fi" : "B".equals(fields[0]) ? "Bluetooth" : "";
            if (transport.isEmpty()) return;
            database.saveObservation(sourceMac, transport, fields[1], rssi, fields.length > 4 ? fields[4] : fields[3]);
            if (!database.triangulateObservedDeviceNow(transport, fields[1])) {
                saveAsRegularScan(sourceMac, fields, rssi);
            }
            broadcastChanged();
        } catch (NumberFormatException ignored) { }
    }

    private double[] getFallbackAnchorCoordinates() {
        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);

        if (prefs.getBoolean("static_location_enabled", false)) {
            float lat = prefs.getFloat("static_latitude", 0f);
            float lon = prefs.getFloat("static_longitude", 0f);

            if (lat != 0f || lon != 0f) {
                return new double[]{lat, lon, 0.0};
            }
        }

        try {
            Location location = LocationManager.getInstance(this).getBestEffortLocation();
            if (location != null && (location.getLatitude() != 0.0 || location.getLongitude() != 0.0)) {
                return new double[]{
                        location.getLatitude(),
                        location.getLongitude(),
                        location.hasAltitude() ? location.getAltitude() : 0.0
                };
            }
        } catch (Exception ignored) {
        }

        return new double[]{0.0, 0.0, 0.0};
    }

    private void processRelayedRecord(String proxyMac, String[] fields) {
        if (fields.length < 5) return;
        String origin = fields[1].toUpperCase();
        int hops;
        try { hops = Integer.parseInt(fields[2]); } catch (NumberFormatException e) { return; }
        database.upsertDevice(origin, "ESP32 " + origin.substring(Math.max(0, origin.length() - 5)), 0, 0, 0);
        database.saveMeshLink(origin, proxyMac, -70, hops);
        processRecordFromSource(origin, fields[3], fields[4], hops);
    }

    private void processRecordFromSource(String sourceMac, String type, String payload, int hops) {
        String[] nested = (type + "|" + payload).split("\\|", 5);
        if (nested.length < 4) return;
        if ("M".equals(nested[0])) {
            processMeshLink(sourceMac, nested, hops);
            return;
        }
        if ("P".equals(nested[0])) {
            processPhoneSample(sourceMac, nested, hops);
            return;
        }
        try {
            int rssi = Integer.parseInt(nested[2]);
            String transport = "W".equals(nested[0]) ? "Wi-Fi" : "B".equals(nested[0]) ? "Bluetooth" : "";
            if (transport.isEmpty()) return;
            database.saveObservation(sourceMac, transport, nested[1], rssi,
                    nested.length > 4 ? nested[4] : nested[3]);
            if (!database.triangulateObservedDeviceNow(transport, nested[1])) {
                saveAsRegularScan(sourceMac, nested, rssi);
            }
            database.autoPositionUnknownDevices();
            broadcastChanged();
        } catch (NumberFormatException ignored) { }
    }

    private void processPhoneSample(String sourceMac, String[] fields, int hopsToPhone) {
        if (fields.length < 3) return;
        try {
            String phoneId = fields[1];
            int rssi = Integer.parseInt(fields[2]);
            Esp32DatabaseHelper.PhonePosition position =
                    database.savePhoneSampleAndEstimate(phoneId, sourceMac, rssi, hopsToPhone);
            if (position != null && phoneId.equalsIgnoreCase(phoneBeaconId)) {
                getSharedPreferences(PREFS_MESH_LOCATION, MODE_PRIVATE).edit()
                        .putFloat("latitude", (float) position.latitude)
                        .putFloat("longitude", (float) position.longitude)
                        .putFloat("altitude", (float) position.altitude)
                        .putLong("updated_at", position.updatedAt)
                        .putInt("anchor_count", position.anchorCount)
                        .apply();
            }
            broadcastChanged();
        } catch (NumberFormatException ignored) { }
    }

    private void processMeshLink(String sourceMac, String[] fields, int hopsToPhone) {
        if (fields.length < 4) return;
        try {
            String source = fields[1].equalsIgnoreCase("SELF") ? sourceMac : fields[1];
            String neighbor = fields[2];
            int rssi = Integer.parseInt(fields[3]);
            if (source == null || neighbor == null || source.equalsIgnoreCase(neighbor)) return;
            database.upsertDevice(source.toUpperCase(),
                    "ESP32 " + source.substring(Math.max(0, source.length() - 5)), 0, 0, 0);
            database.upsertDevice(neighbor.toUpperCase(),
                    "ESP32 " + neighbor.substring(Math.max(0, neighbor.length() - 5)), 0, 0, 0);
            database.saveMeshLink(source, neighbor, rssi, hopsToPhone);
            database.saveMeshLink(neighbor, source, rssi, hopsToPhone);
            database.autoPositionUnknownDevices();
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
        if (advertiser != null && hasPermissions()) advertiser.stopAdvertising(advertiseCallback);
        for (BluetoothGatt gatt : gatts.values()) { if (hasPermissions()) gatt.disconnect(); gatt.close(); }
        database.markAllDisconnected();
        database.close(); mainDatabase.close();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
