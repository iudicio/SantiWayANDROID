package com.example.santiway;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity implements NotificationsAdapter.NotificationActionListener {

    private RecyclerView recyclerView;
    private NotificationsAdapter adapter;
    private List<NotificationData> notificationList = new ArrayList<>();

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotificationsFromDb(); // Загрузка реальных данных
    }

    private void loadNotificationsFromDb() {
        NotificationDatabaseHelper dbHelper = new NotificationDatabaseHelper(this);
        List<NotificationData> fromDb = dbHelper.getAllNotifications();

        notificationList.clear();
        if (fromDb.isEmpty()) {
            notificationList.add(new NotificationData("0", "Входящие", "Уведомлений пока нет", new Date(), NotificationData.NotificationType.SYSTEM, null, null, 0.0, 0.0));
        } else {
            notificationList.addAll(fromDb);
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onNotificationClicked(NotificationData notification) {
        Intent intent = new Intent(this, NotificationDetailActivity.class);
        intent.putExtra("notification_data", notification);
        startActivity(intent);
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