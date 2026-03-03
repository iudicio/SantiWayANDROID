package com.example.santiway.upload_data;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.santiway.R;

import java.util.List;

public class DeviceUploadService extends Service {
    private static final String TAG = "DeviceUploadService";
    private static final int NOTIFICATION_ID = 2001;
    private static final String CHANNEL_ID = "upload_channel_id";
    private static final long UPLOAD_INTERVAL = 60000; // 1 минута

    private Handler handler;
    private Runnable uploadRunnable;
    private DeviceUploadManager uploadManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "DeviceUploadService created");

        uploadManager = new DeviceUploadManager(this);
        createNotificationChannel();

        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);

        startPeriodicUpload();
    }

    private void startPeriodicUpload() {
        handler = new Handler(Looper.getMainLooper());
        uploadRunnable = new Runnable() {
            @Override
            public void run() {
                performUpload();
                handler.postDelayed(this, UPLOAD_INTERVAL);
            }
        };
        handler.post(uploadRunnable);
    }

//Проверка подключения к сети
    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    private void performUpload() {
        if (!isNetworkConnected()) {
            Log.d(TAG, "No network connection - skip upload tick");
            return;
        }

        Log.d(TAG, "Checking for devices to upload.");
        new Thread(() -> {
            try {
                int pendingCount = uploadManager.getPendingDevicesCount();
                Log.d(TAG, "Pending devices: " + pendingCount);
                if (pendingCount == 0) return;

                List<DeviceUploadManager.PendingUpload> items = uploadManager.getPendingUploadsBatch();
                Log.d(TAG, "Found " + items.size() + " devices to upload");
                if (items.isEmpty()) return;

                boolean success = uploadManager.uploadBatch(items);
                Log.d(TAG, "Upload result: " + success);

            } catch (Exception e) {
                Log.e(TAG, "Error in upload: " + e.getMessage(), e);
            }
        }).start();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Загрузка данных",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Сервис для загрузки данных устройства");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SantiWay")
                .setContentText("Загрузка данных устройства")
                .setSmallIcon(R.drawable.ic_pause)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "DeviceUploadService destroyed");

        if (handler != null && uploadRunnable != null) {
            handler.removeCallbacks(uploadRunnable);
        }

        stopForeground(true);
    }
}