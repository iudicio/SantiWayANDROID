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
import com.example.santiway.LocaleHelper;

import java.util.List;

public class DeviceUploadService extends Service {
    private static final String TAG = "DeviceUploadService";
    private static final int NOTIFICATION_ID = 1004;
    private static final String CHANNEL_ID = "upload_channel_id";
    private static final long UPLOAD_INTERVAL = 60000; // 1 минута

    private Handler handler;
    private Runnable uploadRunnable;
    private DeviceUploadManager uploadManager;
    private PhoneLocationUploadManager phoneLocationUploadManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "DeviceUploadService created");
        if (!ServerUploadConfig.isEnabled(this)) {
            Log.d(TAG, "Server upload disabled - stopping service");
            stopSelf();
            return;
        }

        uploadManager = new DeviceUploadManager(this);
        phoneLocationUploadManager = new PhoneLocationUploadManager(this);
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
        if (!ServerUploadConfig.isEnabled(this)) {
            Log.d(TAG, "Server upload disabled - skip upload tick");
            stopSelf();
            return;
        }
        if (!isNetworkConnected()) {
            Log.d(TAG, "No network connection - skip upload tick");
            return;
        }

        Log.d(TAG, "Checking for devices to upload.");
        new Thread(() -> {
            try {
                phoneLocationUploadManager.uploadCurrentLocation();
                
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
                    LocaleHelper.getString(this, R.string.upload_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(LocaleHelper.getString(this, R.string.upload_channel_description));

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(LocaleHelper.getString(this, R.string.upload_notification_text))
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
