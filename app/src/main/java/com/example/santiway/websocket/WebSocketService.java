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

import com.example.santiway.LocaleHelper;
import com.example.santiway.R;
import com.example.santiway.upload_data.ApiConfig;

public class WebSocketService extends Service {
    private static final String TAG = "WebSocketService";
    private static final String CHANNEL_ID = "websocket_channel";
    private static final int NOTIFICATION_ID = 1001;
    private ApkAssembler apkAssembler;
    private android.content.BroadcastReceiver apkReceiver;

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
        String serverUrl = "ws://192.168.1.205:1411/ws/notify/";

        Log.d(TAG, "WebSocket URL: " + serverUrl);

        // Создаем и подключаем WebSocket клиент
        webSocketClient = new WebSocketNotificationClient(this, serverUrl, apiKey);
        webSocketClient.connect();
        apkAssembler = new ApkAssembler(this);
        registerApkReceiver();
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
        if (apkReceiver != null) {
            androidx.localbroadcastmanager.content.LocalBroadcastManager
                    .getInstance(this)
                    .unregisterReceiver(apkReceiver);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    LocaleHelper.getString(this, R.string.websocket_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(LocaleHelper.getString(this, R.string.websocket_channel_description));

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(LocaleHelper.getString(this, R.string.websocket_notification_title))
                .setContentText(LocaleHelper.getString(this, R.string.websocket_notification_connecting))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        return builder.build();
    }

    private void registerApkReceiver() {
        apkReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, Intent intent) {
                String action = intent.getAction();

                if (WebSocketNotificationClient.ACTION_APK_CHUNK_RECEIVED.equals(action)) {
                    String buildId = intent.getStringExtra(WebSocketNotificationClient.EXTRA_BUILD_ID);
                    int chunkIndex = intent.getIntExtra(WebSocketNotificationClient.EXTRA_CHUNK_INDEX, -1);
                    int chunkCount = intent.getIntExtra(WebSocketNotificationClient.EXTRA_CHUNK_COUNT, -1);
                    String filename = intent.getStringExtra(WebSocketNotificationClient.EXTRA_FILENAME);
                    byte[] data = intent.getByteArrayExtra(WebSocketNotificationClient.EXTRA_CHUNK_DATA);

                    if (buildId != null && data != null && chunkIndex >= 0 && chunkCount > 0) {
                        apkAssembler.addChunk(buildId, chunkIndex, chunkCount, filename, data);
                    }
                }

                if (WebSocketNotificationClient.ACTION_APK_COMPLETE.equals(action)) {
                    String apkPath = intent.getStringExtra(WebSocketNotificationClient.EXTRA_APK_PATH);
                    if (apkPath != null) {
                        installApk(apkPath);
                    }
                }
            }
        };

        android.content.IntentFilter filter = new android.content.IntentFilter();
        filter.addAction(WebSocketNotificationClient.ACTION_APK_CHUNK_RECEIVED);
        filter.addAction(WebSocketNotificationClient.ACTION_APK_COMPLETE);

        androidx.localbroadcastmanager.content.LocalBroadcastManager
                .getInstance(this)
                .registerReceiver(apkReceiver, filter);
    }

    private void installApk(String apkPath) {
        java.io.File apkFile = new java.io.File(apkPath);

        if (!apkFile.exists()) {
            Log.e(TAG, "APK file not found: " + apkPath);
            return;
        }

        android.net.Uri apkUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                apkFile
        );

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(intent);
    }
}
