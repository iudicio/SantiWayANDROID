package com.example.santiway.upload_data;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class DeviceUploadService extends Service {
    private static final String TAG = "DeviceUploadService";
    private static final String UPLOAD_WORK_NAME = "DeviceUploadWork";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "DeviceUploadService created");
        startPeriodicUpload();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "DeviceUploadService started");
        // Перезапускаем сервис если он был убит системой
        return START_STICKY;
    }

    private void startPeriodicUpload() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // Уменьшить интервал для более частой отправки (например, 5 минут)
        PeriodicWorkRequest uploadWork =
                new PeriodicWorkRequest.Builder(DeviceUploadWorker.class, 1, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                UPLOAD_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                uploadWork);

        Log.d(TAG, "Periodic upload work scheduled every 5 minutes");
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
    }
}