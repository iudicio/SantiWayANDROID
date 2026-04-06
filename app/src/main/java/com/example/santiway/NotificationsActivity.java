package com.example.santiway;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.santiway.activity_map.ActivityMapActivity;
import com.example.santiway.upload_data.MainDatabaseHelper;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity implements NotificationsAdapter.NotificationActionListener {

    private RecyclerView recyclerView;
    private NotificationsAdapter adapter;
    private List<NotificationData> notificationList = new ArrayList<>();
    private NotificationData currentNotification;
    private TextView emptyStateText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Уведомления");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.notifications_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new NotificationsAdapter(this, notificationList, this);
        recyclerView.setAdapter(adapter);

        Button clearAllButton = findViewById(R.id.btn_clear_all);
        clearAllButton.setOnClickListener(v -> clearAllNotifications());
        emptyStateText = findViewById(R.id.empty_state_text);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotificationsFromDb();
    }

    private void loadNotificationsFromDb() {
        NotificationDatabaseHelper dbHelper = new NotificationDatabaseHelper(this);
        List<NotificationData> fromDb = dbHelper.getAllNotifications();

        notificationList.clear();
        notificationList.addAll(fromDb);
        adapter.notifyDataSetChanged();

        if (emptyStateText != null) {
            if (notificationList.isEmpty()) {
                emptyStateText.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                emptyStateText.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onNotificationClicked(NotificationData notification) {
        currentNotification = notification;
        String deviceId = notification.getDeviceId();

        if (deviceId != null && !deviceId.isEmpty()) {
            openDeviceMap(deviceId);
        } else {
            // Если нет deviceId, открываем детали уведомления
            Intent intent = new Intent(this, NotificationDetailActivity.class);
            intent.putExtra("notification_data", notification);
            startActivity(intent);
        }
    }

    private void clearAllNotifications() {
        // 1. Очищаем системную шторку уведомлений
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancelAll();
        }

        // 2. Очищаем базу данных уведомлений
        NotificationDatabaseHelper dbHelper = new NotificationDatabaseHelper(this);
        List<NotificationData> allNotifications = dbHelper.getAllNotifications();
        for (NotificationData notification : allNotifications) {
            if (notification.isDeletable()) {
                dbHelper.deleteNotification(notification.getId());
            }
        }

        SharedPreferences prefs = getSharedPreferences("notif_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("is_first_alert", true).apply();

        // 3. Очищаем список в адаптере и обновляем UI
        notificationList.clear();
        adapter.notifyDataSetChanged();
        if (emptyStateText != null) {
            if (notificationList.isEmpty()) {
                emptyStateText.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                emptyStateText.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        }

        Toast.makeText(this, "Все уведомления очищены", Toast.LENGTH_SHORT).show();
    }

    private void openDeviceMap(String deviceId) {
        try {
            MainDatabaseHelper dbHelper = new MainDatabaseHelper(this);
            MainDatabaseHelper.DeviceInfo deviceInfo = dbHelper.getDeviceInfoForNotification(deviceId);

            if (deviceInfo != null && deviceInfo.latitude != 0 && deviceInfo.longitude != 0) {
                Intent intent = new Intent(this, ActivityMapActivity.class);
                intent.putExtra("latitude", deviceInfo.latitude);
                intent.putExtra("longitude", deviceInfo.longitude);
                intent.putExtra("device_name", deviceInfo.name);
                intent.putExtra("device_mac", deviceInfo.mac);
                intent.putExtra("device_type", deviceInfo.type);
                intent.putExtra("table_name", deviceInfo.tableName);
                intent.putExtra("device_status", deviceInfo.status);
                startActivity(intent);
            } else {
                // Если не нашли координаты, показываем детали уведомления
                Toast.makeText(this, "Нет данных о местоположении устройства", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, NotificationDetailActivity.class);
                intent.putExtra("notification_data", currentNotification);
                startActivity(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка при открытии карты", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, NotificationDetailActivity.class);
            intent.putExtra("notification_data", currentNotification);
            startActivity(intent);
        }
    }

    @Override
    public void onDeleteClicked(NotificationData notification) {
        if (notification.isDeletable()) {
            NotificationDatabaseHelper dbHelper = new NotificationDatabaseHelper(this);
            dbHelper.deleteNotification(notification.getId());
            loadNotificationsFromDb();
            Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}