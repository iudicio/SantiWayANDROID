// File: com.example.santiway.notifications.NotificationsActivity.java
package com.example.santiway;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.santiway.R;
import com.example.santiway.NotificationData.NotificationType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import android.content.Intent;
import android.view.MenuItem;

public class NotificationsActivity extends AppCompatActivity implements NotificationsAdapter.NotificationActionListener {

    private RecyclerView recyclerView;
    private NotificationsAdapter adapter;
    private List<NotificationData> notificationList;

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

        // TODO: Замените на реальную загрузку данных с WEB-сервера
        notificationList = createMockData();

        adapter = new NotificationsAdapter(this, notificationList, this);
        recyclerView.setAdapter(adapter);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // --- Интерфейс NotificationsAdapter.NotificationActionListener ---

    @Override
    public void onNotificationClicked(NotificationData notification) {
        // Переход на экран деталей
        Intent intent = new Intent(this, NotificationDetailActivity.class);
        intent.putExtra("notification_data", notification);
        startActivity(intent);
    }

    @Override
    public void onDeleteClicked(NotificationData notification) {
        if (notification.isDeletable()) {
            int position = notificationList.indexOf(notification);
            if (position != -1) {
                notificationList.remove(position);
                adapter.notifyItemRemoved(position);
                Toast.makeText(this, "Удалено: " + notification.getTitle(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Системное уведомление не может быть удалено", Toast.LENGTH_SHORT).show();
        }
    }

    // --- Временные моковые данные для примера ---

    private List<NotificationData> createMockData() {
        List<NotificationData> list = new ArrayList<>();

        // --- 1. ALARM (Простое уведомление без файлов) ---
        list.add(new NotificationData(
                "id1", // 1. id
                "Критическое оповещение", // 2. title
                "Обнаружена несанкционированная активность на сервере. Требуется немедленное реагирование.", // 3. text
                new Date(System.currentTimeMillis() - 3600000), // 4. timestamp (час назад)
                NotificationData.NotificationType.ALARM, // 5. type
                null, // 6. binaryContents
                null, // 7. binaryMimeTypes
                null, // 8. latitude
                null  // 9. longitude
        ));

        // --- 2. SYSTEM (Уведомление с координатами) ---
        list.add(new NotificationData(
                "id2",
                "Сервис GPS запущен",
                "Служба геолокации успешно запущена. Позиция: 48.85, 2.35 (Париж).",
                new Date(System.currentTimeMillis() - 7200000), // два часа назад
                NotificationData.NotificationType.SYSTEM,
                null,
                null,
                48.8566, // 8. latitude
                2.3522  // 9. longitude
        ));

        // --- 3. INFO (Уведомление с тестовой картинкой) ---
        // ВНИМАНИЕ: Для реального отображения вам нужен массив байтов реальной картинки.
        // Я использую небольшой непустой массив, чтобы проверить, что контейнер и Glide вызываются.
        byte[] IMAGE_BYTES = new byte[]{0x45, 0x4E, 0x44, 0x45};

        list.add(new NotificationData(
                "id3",
                "Новая конфигурация Wi-Fi (с картинкой)",
                "Получены и применены новые параметры сканирования Wi-Fi.",
                new Date(System.currentTimeMillis() - 1200000),
                NotificationData.NotificationType.INFO,
                Arrays.asList(IMAGE_BYTES), // 6. binaryContents
                Arrays.asList("image/jpeg"), // 7. binaryMimeTypes (используйте image/png или image/jpeg)
                55.7558,
                37.6176
        ));

        // --- 4. ALARM (Уведомление с файлом APK) ---
        // ВНИМАНИЕ: Для теста кнопки достаточно непустого массива, но для установки APK нужны реальные байты!
        byte[] APK_BYTES = new byte[]{0x50, 0x4B, 0x03, 0x04};

        list.add(new NotificationData(
                "id4",
                "Доступно новое обновление!",
                "Для продолжения работы требуется установка обновления системы.",
                new Date(System.currentTimeMillis()),
                NotificationData.NotificationType.ALARM,
                Arrays.asList(APK_BYTES), // 6. binaryContents
                Arrays.asList("application/vnd.android.package-archive"), // 7. binaryMimeTypes
                null,
                null
        ));


        return list;
    }
}