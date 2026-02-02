package com.example.santiway.upload_data;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.santiway.R;  // Импортируйте ваш R

import java.util.concurrent.TimeUnit;

public class DeviceUploadService extends Service {
    private static final String TAG = "DeviceUploadService";
    private static final String UPLOAD_WORK_NAME = "DeviceUploadWork";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "upload_channel_id";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "DeviceUploadService created");

        // Создаем канал уведомлений для Android 8+
        createNotificationChannel();

        // Создаем и показываем уведомление
        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);  // ВАЖНО: этот вызов

        startPeriodicUpload();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "DeviceUploadService started");

        // ВАЖНО: Если сервис перезапускается, нужно снова вызвать startForeground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification notification = createNotification();
            startForeground(NOTIFICATION_ID, notification);
        }

        return START_STICKY;
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

    private void startPeriodicUpload() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // Используйте 15 минут как минимальный интервал для PeriodicWorkRequest
        PeriodicWorkRequest uploadWork =
                new PeriodicWorkRequest.Builder(DeviceUploadWorker.class, 15, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                UPLOAD_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                uploadWork);

        Log.d(TAG, "Periodic upload work scheduled every 15 minutes");
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
        stopForeground(true);  // Удаляем уведомление при остановке сервиса
    }
}