package com.example.santiway.websocket;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.santiway.upload_data.ApiConfig;

public class WebSocketService extends Service {
    private static final String TAG = "WebSocketService";
    private static final String CHANNEL_ID = "websocket_channel";
    private static final int NOTIFICATION_ID = 1001;

    private WebSocketNotificationClient webSocketClient;
    private final IBinder binder = new WebSocketBinder();

    public class WebSocketBinder extends Binder {
        public WebSocketService getService() {
            return WebSocketService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Создаем notification channel для Android 8+
        createNotificationChannel();

        // Запускаем как foreground service с уведомлением
        startForeground(NOTIFICATION_ID, createNotification());

        // Инициализируем ApiConfig для получения API ключа
        ApiConfig.initialize(this);

        // Получаем API ключ через ApiConfig (из strings.xml)
        String apiKey = ApiConfig.getApiKey(this);

        if (apiKey == null || apiKey.isEmpty()) {
            Log.e(TAG, "API key not found in strings.xml");
            return;
        }

        Log.d(TAG, "API Key: " + apiKey);

        // Формируем правильный WebSocket URL с путем /ws/notify/
        String serverUrl = "ws://192.168.110.49:8000/ws/notifications/";

        Log.d(TAG, "WebSocket URL: " + serverUrl);

        // Создаем и подключаем WebSocket клиент
        webSocketClient = new WebSocketNotificationClient(this, serverUrl, apiKey);
        webSocketClient.connect();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public WebSocketNotificationClient getWebSocketClient() {
        return webSocketClient;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (webSocketClient != null) {
            webSocketClient.disconnect();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "WebSocket Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Channel for WebSocket connection service");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WebSocket Service")
                .setContentText("Подключение к серверу уведомлений...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        return builder.build();
    }
}