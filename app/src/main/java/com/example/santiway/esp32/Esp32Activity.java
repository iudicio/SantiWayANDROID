package com.example.santiway.esp32;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.santiway.BaseLocalizedActivity;
import com.example.santiway.R;
import com.google.android.material.button.MaterialButton;

import java.text.DateFormat;
import java.util.Date;

public class Esp32Activity extends BaseLocalizedActivity {
    private static final int REQUEST_BLUETOOTH = 420;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Esp32DatabaseHelper database;
    private TableLayout table;
    private TextView statusView;

    private final BroadcastReceiver changedReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { renderDevices(); }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esp32);
        getWindow().setNavigationBarColor(Color.parseColor("#172A46"));
        database = new Esp32DatabaseHelper(this);
        table = findViewById(R.id.esp32_connections_table);
        statusView = findViewById(R.id.esp32_connection_status);
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.esp32_management_title);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
        findViewById(R.id.esp32_scan_button).setOnClickListener(v -> discoverEsp32());
        ensurePermissionsAndStart(false);
        renderDevices();
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
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
        ensurePermissionsAndStart(true);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(changedReceiver, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(changedReceiver, filter);
    }

    @Override protected void onStop() {
        try { unregisterReceiver(changedReceiver); } catch (Exception ignored) { }
        super.onStop();
    }

    private void renderDevices() {
        table.removeAllViews();
        addHeader(getString(R.string.column_device_type), getString(R.string.column_esp32_mac),
                getString(R.string.column_connected_at), getString(R.string.column_disconnected_at),
                getString(R.string.column_connection_status), getString(R.string.latitude_hint),
                getString(R.string.longitude_hint), getString(R.string.column_altitude),
                getString(R.string.settings_title));
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
                double lat = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
                double lon = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
                double alt = cursor.getDouble(cursor.getColumnIndexOrThrow("altitude"));
                addDeviceRow(name, mac, connectedAt == 0 ? "—" : formatter.format(new Date(connectedAt)),
                        disconnectedAt == 0 ? "—" : formatter.format(new Date(disconnectedAt)), connected,
                        lat, lon, alt);
            }
        }
        statusView.setText(anyConnected ? R.string.esp32_status_connected : R.string.esp32_status_disconnected);
    }

    private void addHeader(String... values) {
        TableRow row = new TableRow(this);
        row.setBackgroundColor(Color.parseColor("#071427"));
        for (String value : values) row.addView(createCell(value, true));
        table.addView(row);
    }

    private void addDeviceRow(String name, String mac, String connectedAt, String disconnectedAt,
                              boolean connected, double lat, double lon, double alt) {
        TableRow row = new TableRow(this);
        row.setBackgroundColor(Color.parseColor("#172A46"));
        row.addView(createCell(name, false));
        row.addView(createCell(mac, false));
        row.addView(createCell(connectedAt, false));
        row.addView(createCell(disconnectedAt, false));
        row.addView(createCell(getString(connected ? R.string.connection_true : R.string.connection_false), false));
        row.addView(createCell(formatCoordinate(lat), false));
        row.addView(createCell(formatCoordinate(lon), false));
        row.addView(createCell(formatCoordinate(alt), false));
        MaterialButton settings = new MaterialButton(this);
        settings.setText(R.string.settings_title);
        settings.setTextColor(Color.WHITE);
        settings.setOnClickListener(v -> showSettings(mac, name, connected, lat, lon, alt));
        row.addView(settings, new TableRow.LayoutParams(220, ViewGroup.LayoutParams.WRAP_CONTENT));
        table.addView(row);
    }

    private TextView createCell(String value, boolean header) {
        TextView cell = new TextView(this);
        cell.setText(value); cell.setTextColor(Color.WHITE); cell.setTextSize(header ? 14 : 13);
        cell.setPadding(16, 14, 16, 14); cell.setMinWidth(190);
        return cell;
    }

    private void showSettings(String mac, String name, boolean connected, double lat, double lon, double alt) {
        View view = getLayoutInflater().inflate(R.layout.dialog_esp32_settings, null);
        EditText nameInput = view.findViewById(R.id.esp32_name_input);
        EditText latInput = view.findViewById(R.id.esp32_latitude_input);
        EditText lonInput = view.findViewById(R.id.esp32_longitude_input);
        EditText altInput = view.findViewById(R.id.esp32_altitude_input);
        nameInput.setText(name); latInput.setText(String.valueOf(lat));
        lonInput.setText(String.valueOf(lon)); altInput.setText(String.valueOf(alt));
        TextView macView = view.findViewById(R.id.esp32_settings_mac);
        macView.setText(mac);
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme).setView(view).create();
        view.findViewById(R.id.esp32_save_settings).setOnClickListener(v -> {
            try {
                database.updateSettings(mac, nameInput.getText().toString().trim(),
                        Double.parseDouble(latInput.getText().toString()),
                        Double.parseDouble(lonInput.getText().toString()),
                        Double.parseDouble(altInput.getText().toString()));
                dialog.dismiss(); renderDevices();
            } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.error_invalid_coordinates, Toast.LENGTH_SHORT).show();
            }
        });
        MaterialButton connectionButton = view.findViewById(R.id.esp32_connection_action);
        connectionButton.setText(connected ? R.string.esp32_disconnect : R.string.esp32_connect);
        connectionButton.setOnClickListener(v -> {
            startConnectionService(connected ? Esp32ConnectionService.ACTION_DISCONNECT : Esp32ConnectionService.ACTION_CONNECT, mac);
            dialog.dismiss();
        });
        view.findViewById(R.id.esp32_delete).setOnClickListener(v -> new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle(R.string.dialog_confirm_delete_title).setMessage(R.string.esp32_delete_confirmation)
                .setPositiveButton(R.string.dialog_yes, (d, w) -> {
                    startConnectionService(Esp32ConnectionService.ACTION_DISCONNECT, mac);
                    handler.postDelayed(() -> { database.deleteDevice(mac); renderDevices(); }, 500);
                    dialog.dismiss();
                }).setNegativeButton(R.string.dialog_no, null).show());
        dialog.show();
    }

    private String formatCoordinate(double value) { return String.format(java.util.Locale.getDefault(), "%.6f", value); }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                     @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH && hasPermissions()) startConnectionService(null, null);
    }

    @Override protected void onDestroy() { database.close(); super.onDestroy(); }
}
