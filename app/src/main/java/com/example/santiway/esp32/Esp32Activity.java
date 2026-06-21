package com.example.santiway.esp32;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.santiway.BaseLocalizedActivity;
import com.example.santiway.R;
import com.example.santiway.esp32.firmware.Esp32FirmwareActivity;
import com.google.android.material.button.MaterialButton;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Esp32Activity extends BaseLocalizedActivity {
    private static final int REQUEST_BLUETOOTH = 420;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Esp32DeviceItem> devices = new ArrayList<>();
    private Esp32DatabaseHelper database;
    private Esp32Adapter adapter;
    private RecyclerView deviceList;
    private View emptyState;
    private View statusIndicator;
    private TextView statusView;
    private final List<DiscoveredEsp> discoveredDevices = new ArrayList<>();
    private ArrayAdapter<String> discoveryAdapter;
    private AlertDialog discoveryDialog;

    private final BroadcastReceiver changedReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Esp32ConnectionService.ACTION_DEVICE_FOUND.equals(intent.getAction())) {
                updateDiscoveredDevice(intent.getStringExtra(Esp32ConnectionService.EXTRA_MAC),
                        intent.getStringExtra(Esp32ConnectionService.EXTRA_NAME),
                        intent.getIntExtra(Esp32ConnectionService.EXTRA_RSSI, 0));
            } else {
                renderDevices();
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esp32);
        getWindow().setNavigationBarColor(Color.parseColor("#172A46"));
        database = new Esp32DatabaseHelper(this);
        deviceList = findViewById(R.id.esp32_connections_list);
        emptyState = findViewById(R.id.esp32_empty_state);
        statusIndicator = findViewById(R.id.esp32_status_indicator);
        statusView = findViewById(R.id.esp32_connection_status);

        adapter = new Esp32Adapter();
        deviceList.setLayoutManager(new LinearLayoutManager(this));
        deviceList.setAdapter(adapter);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.esp32_management_title);
        }
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        findViewById(R.id.esp32_scan_button).setOnClickListener(v -> discoverEsp32());
        findViewById(R.id.esp32_program_firmware).setOnClickListener(v ->
                startActivity(new Intent(this, Esp32FirmwareActivity.class)));
        ensurePermissionsAndStart(false);
        renderDevices();
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void ensurePermissionsAndStart(boolean discover) {
        if (!hasPermissions()) {
            String[] permissions = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION}
                    : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
            ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH);
            return;
        }
        startConnectionService(discover ? Esp32ConnectionService.ACTION_DISCOVER : null, null);
    }

    private void discoverEsp32() {
        statusView.setText(R.string.esp32_status_searching);
        setStatusIndicatorColor("#F5A623");
        showDiscoveryDialog();
        ensurePermissionsAndStart(true);
    }

    private void showDiscoveryDialog() {
        discoveredDevices.clear();
        discoveryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        discoveryDialog = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle(R.string.esp32_available_title)
                .setAdapter(discoveryAdapter, (dialog, position) -> {
                    if (position < 0 || position >= discoveredDevices.size()) return;
                    DiscoveredEsp device = discoveredDevices.get(position);
                    statusView.setText(R.string.esp32_status_connecting);
                    startConnectionService(Esp32ConnectionService.ACTION_CONNECT, device.mac);
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .create();
        discoveryDialog.show();
    }

    private void updateDiscoveredDevice(String mac, String name, int rssi) {
        if (mac == null || discoveryAdapter == null || discoveryDialog == null || !discoveryDialog.isShowing()) return;
        int index = -1;
        for (int i = 0; i < discoveredDevices.size(); i++) {
            if (mac.equalsIgnoreCase(discoveredDevices.get(i).mac)) { index = i; break; }
        }
        DiscoveredEsp item = new DiscoveredEsp(mac, name == null ? "ESP32" : name, rssi);
        if (index >= 0) discoveredDevices.set(index, item); else discoveredDevices.add(item);
        discoveryAdapter.clear();
        for (DiscoveredEsp device : discoveredDevices) {
            discoveryAdapter.add(device.name + "\n" + device.mac + "   " + device.rssi + " dBm");
        }
        discoveryAdapter.notifyDataSetChanged();
    }

    private void startConnectionService(String action, String mac) {
        Intent intent = new Intent(this, Esp32ConnectionService.class);
        if (action != null) intent.setAction(action);
        if (mac != null) intent.putExtra(Esp32ConnectionService.EXTRA_MAC, mac);
        ContextCompat.startForegroundService(this, intent);
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(Esp32ConnectionService.ACTION_CHANGED);
        filter.addAction(Esp32ConnectionService.ACTION_DEVICE_FOUND);
        ContextCompat.registerReceiver(this, changedReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override protected void onStop() {
        try { unregisterReceiver(changedReceiver); } catch (Exception ignored) { }
        super.onStop();
    }

    private void renderDevices() {
        devices.clear();
        boolean anyConnected = false;
        try (Cursor cursor = database.getDevices()) {
            DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
            while (cursor.moveToNext()) {
                String mac = cursor.getString(cursor.getColumnIndexOrThrow("mac_address"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                boolean connected = cursor.getInt(cursor.getColumnIndexOrThrow("is_connected")) == 1;
                anyConnected |= connected;
                long connectedAt = cursor.isNull(cursor.getColumnIndexOrThrow("connected_at")) ? 0
                        : cursor.getLong(cursor.getColumnIndexOrThrow("connected_at"));
                long disconnectedAt = cursor.isNull(cursor.getColumnIndexOrThrow("disconnected_at")) ? 0
                        : cursor.getLong(cursor.getColumnIndexOrThrow("disconnected_at"));
                devices.add(new Esp32DeviceItem(name, mac,
                        connectedAt == 0 ? "-" : formatter.format(new Date(connectedAt)),
                        disconnectedAt == 0 ? "-" : formatter.format(new Date(disconnectedAt)),
                        connected,
                        cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")),
                        cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")),
                        cursor.getDouble(cursor.getColumnIndexOrThrow("altitude"))));
            }
        }

        statusView.setText(anyConnected ? R.string.esp32_status_connected : R.string.esp32_status_disconnected);
        setStatusIndicatorColor(anyConnected ? "#3DDC84" : "#6F839C");
        adapter.notifyDataSetChanged();
        boolean isEmpty = devices.isEmpty();
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        deviceList.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void setStatusIndicatorColor(String color) {
        statusIndicator.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(color)));
    }

    private void showSettings(String mac, String name, boolean connected, double lat, double lon, double alt) {
        View view = getLayoutInflater().inflate(R.layout.dialog_esp32_settings, null);
        EditText nameInput = view.findViewById(R.id.esp32_name_input);
        EditText latInput = view.findViewById(R.id.esp32_latitude_input);
        EditText lonInput = view.findViewById(R.id.esp32_longitude_input);
        EditText altInput = view.findViewById(R.id.esp32_altitude_input);
        nameInput.setText(name);
        latInput.setText(String.valueOf(lat));
        lonInput.setText(String.valueOf(lon));
        altInput.setText(String.valueOf(alt));
        ((TextView) view.findViewById(R.id.esp32_settings_mac)).setText(mac);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme).setView(view).create();
        view.findViewById(R.id.esp32_save_settings).setOnClickListener(v -> {
            try {
                database.updateSettings(mac, nameInput.getText().toString().trim(),
                        Double.parseDouble(latInput.getText().toString()),
                        Double.parseDouble(lonInput.getText().toString()),
                        Double.parseDouble(altInput.getText().toString()));
                dialog.dismiss();
                renderDevices();
            } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.error_invalid_coordinates, Toast.LENGTH_SHORT).show();
            }
        });
        MaterialButton connectionButton = view.findViewById(R.id.esp32_connection_action);
        connectionButton.setText(connected ? R.string.esp32_disconnect : R.string.esp32_connect);
        connectionButton.setOnClickListener(v -> {
            startConnectionService(connected ? Esp32ConnectionService.ACTION_DISCONNECT
                    : Esp32ConnectionService.ACTION_CONNECT, mac);
            dialog.dismiss();
        });
        view.findViewById(R.id.esp32_delete).setOnClickListener(v ->
                new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                        .setTitle(R.string.dialog_confirm_delete_title)
                        .setMessage(R.string.esp32_delete_confirmation)
                        .setPositiveButton(R.string.dialog_yes, (d, w) -> {
                            startConnectionService(Esp32ConnectionService.ACTION_DISCONNECT, mac);
                            handler.postDelayed(() -> {
                                database.deleteDevice(mac);
                                renderDevices();
                            }, 500);
                            dialog.dismiss();
                        })
                        .setNegativeButton(R.string.dialog_no, null)
                        .show());
        dialog.show();
    }

    private String formatCoordinate(double value) {
        return String.format(Locale.getDefault(), "%.6f", value);
    }

    private final class Esp32Adapter extends RecyclerView.Adapter<Esp32ViewHolder> {
        @NonNull
        @Override public Esp32ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            return new Esp32ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_esp32_device, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Esp32ViewHolder holder, int position) {
            Esp32DeviceItem item = devices.get(position);
            holder.name.setText(item.name.isEmpty() ? getString(R.string.column_device_type) : item.name);
            holder.mac.setText(item.mac);
            holder.status.setText(item.connected ? R.string.connection_true : R.string.connection_false);
            int color = Color.parseColor(item.connected ? "#3DDC84" : "#9FB3C8");
            holder.status.setTextColor(color);
            holder.statusBar.setBackgroundColor(color);
            holder.time.setText((item.connected ? getString(R.string.column_connected_at) + ": " + item.connectedAt
                    : getString(R.string.column_disconnected_at) + ": " + item.disconnectedAt));
            holder.location.setText(getString(R.string.esp32_coordinates_format,
                    formatCoordinate(item.latitude), formatCoordinate(item.longitude), formatCoordinate(item.altitude)));
            View.OnClickListener settingsClick = v -> showSettings(item.mac, item.name, item.connected,
                    item.latitude, item.longitude, item.altitude);
            holder.settings.setOnClickListener(settingsClick);
            holder.itemView.setOnClickListener(settingsClick);
        }

        @Override public int getItemCount() { return devices.size(); }
    }

    private static final class Esp32ViewHolder extends RecyclerView.ViewHolder {
        final View statusBar;
        final TextView name;
        final TextView mac;
        final TextView status;
        final TextView time;
        final TextView location;
        final ImageButton settings;

        Esp32ViewHolder(@NonNull View itemView) {
            super(itemView);
            statusBar = itemView.findViewById(R.id.esp32_item_status_bar);
            name = itemView.findViewById(R.id.esp32_item_name);
            mac = itemView.findViewById(R.id.esp32_item_mac);
            status = itemView.findViewById(R.id.esp32_item_status);
            time = itemView.findViewById(R.id.esp32_item_time);
            location = itemView.findViewById(R.id.esp32_item_location);
            settings = itemView.findViewById(R.id.esp32_item_settings);
        }
    }

    private static final class Esp32DeviceItem {
        final String name;
        final String mac;
        final String connectedAt;
        final String disconnectedAt;
        final boolean connected;
        final double latitude;
        final double longitude;
        final double altitude;

        Esp32DeviceItem(String name, String mac, String connectedAt, String disconnectedAt,
                        boolean connected, double latitude, double longitude, double altitude) {
            this.name = name == null ? "" : name;
            this.mac = mac;
            this.connectedAt = connectedAt;
            this.disconnectedAt = disconnectedAt;
            this.connected = connected;
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
        }
    }

    private static final class DiscoveredEsp {
        final String mac;
        final String name;
        final int rssi;
        DiscoveredEsp(String mac, String name, int rssi) {
            this.mac = mac;
            this.name = name;
            this.rssi = rssi;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                     @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH && hasPermissions()) startConnectionService(null, null);
    }

    @Override protected void onDestroy() {
        database.close();
        super.onDestroy();
    }
}
