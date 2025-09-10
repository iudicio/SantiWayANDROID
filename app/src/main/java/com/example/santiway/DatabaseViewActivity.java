package com.example.santiway;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.santiway.wifi_scanner.DatabaseHelper;
import com.example.santiway.wifi_scanner.WifiDevice;
import java.util.List;

public class DatabaseViewActivity extends AppCompatActivity {

    private TextView dbInfoTextView;
    private DatabaseHelper databaseHelper;
    private String currentTableName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_database_view);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Просмотр папки");

        dbInfoTextView = findViewById(R.id.dbInfoTextView);
        databaseHelper = new DatabaseHelper(this);

        currentTableName = getIntent().getStringExtra("TABLE_NAME");
        if (currentTableName != null) {
            getSupportActionBar().setSubtitle(currentTableName);
            displayTableData(currentTableName);
        } else {
            dbInfoTextView.setText("Ошибка: не указана таблица");
        }
    }

    private void displayTableData(String tableName) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Содержимое папки: ").append(tableName).append(" ===\n\n");

        List<WifiDevice> devices = databaseHelper.getAllDevices(tableName);
        if (devices.isEmpty()) {
            sb.append("Папка пуста\n");
        } else {
            sb.append("Найдено устройств: ").append(devices.size()).append("\n\n");
            for (int i = 0; i < devices.size(); i++) {
                WifiDevice device = devices.get(i);
                String timeString = formatTimestamp(device.getTimestamp());
                sb.append("Устройство ").append(i + 1).append(":\n")
                        .append("SSID: ").append(device.getSsid()).append("\n")
                        .append("BSSID: ").append(device.getBssid()).append("\n")
                        .append("Сила сигнала: ").append(device.getSignalStrength()).append(" dBm\n")
                        .append("Частота: ").append(device.getFrequency()).append(" MHz\n")
                        .append("Защита: ").append(device.getCapabilities()).append("\n")
                        .append("Производитель: ").append(device.getVendor()).append("\n")
                        .append("Координаты: ").append(String.format("%.6f, %.6f, %.6f", device.getLatitude(), device.getLongitude(), device.getAltitude())).append("\n")
                        .append("Точность: ").append(device.getLocationAccuracy()).append("m\n")
                        .append("Время: ").append(timeString).append("\n")
                        .append("----------------------------\n");
            }
        }
        dbInfoTextView.setText(sb.toString());
    }

    private String formatTimestamp(long timestamp) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault());
            return sdf.format(new java.util.Date(timestamp));
        } catch (Exception e) {
            return String.valueOf(timestamp);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_database_view, menu);

        // Скрываем кнопку удаления для папки по умолчанию
        MenuItem deleteItem = menu.findItem(R.id.action_delete);
        if (currentTableName != null && currentTableName.equals("default_table")) {
            deleteItem.setVisible(false);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_refresh) {
            refreshData();
            return true;
        } else if (id == R.id.action_clear) {
            clearCurrentTable();
            return true;
        } else if (id == R.id.action_delete) {
            deleteCurrentTable();
            return true;
        } else if (id == R.id.action_info) {
            showTableInfo();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void refreshData() {
        if (currentTableName != null) {
            displayTableData(currentTableName);
            Toast.makeText(this, "Данные обновлены", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearCurrentTable() {
        if (currentTableName != null) {
            new AlertDialog.Builder(this)
                    .setTitle("Очистка папки")
                    .setMessage("Очистить папку '" + currentTableName + "'?")
                    .setPositiveButton("Очистить", (dialog, which) -> {
                        databaseHelper.clearTable(currentTableName);
                        refreshData();
                        Toast.makeText(this, "Папка очищена", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        }
    }

    private void deleteCurrentTable() {
        if (currentTableName != null) {
            if (currentTableName.equals("default_table")) {
                Toast.makeText(this, "Нельзя удалить папку по умолчанию", Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Удаление папки")
                    .setMessage("Вы уверены, что хотите удалить папку '" + currentTableName + "'? Это действие нельзя отменить!")
                    .setPositiveButton("Удалить", (dialog, which) -> {
                        boolean success = databaseHelper.deleteTable(currentTableName);
                        if (success) {
                            Toast.makeText(this, "Папка удалена", Toast.LENGTH_SHORT).show();
                            finish(); // Закрываем активность после удаления
                        } else {
                            Toast.makeText(this, "Ошибка при удалении папки", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        }
    }

    private void showTableInfo() {
        if (currentTableName != null) {
            int count = databaseHelper.getDevicesCount(currentTableName);
            String message = "Папка: " + currentTableName + "\nЗаписей: " + count;
            new AlertDialog.Builder(this)
                    .setTitle("Информация о папке")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}