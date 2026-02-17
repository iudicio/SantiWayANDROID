package com.example.santiway.upload_data;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class DeviceUploadWorker extends Worker {
    private static final String TAG = "DeviceUploadWorker";
    private static final int BATCH_SIZE = 200; // Максимальный размер батча
    private static final long UPLOAD_INTERVAL_MS = 60000; // 1 минута в миллисекундах

    private DeviceUploadManager uploadManager;
    public static long lastUploadTime = 0;

    public DeviceUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        uploadManager = new DeviceUploadManager(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "🔄 DeviceUploadWorker started");

        try {
            if (!NetworkUtils.isNetworkAvailable(getApplicationContext())) {
                Log.d(TAG, "📡 No network available, skipping upload");
                return Result.retry();
            }

            // Получаем все ожидающие устройства
            List<ApiDevice> pendingDevices = uploadManager.getPendingDevicesBatch();
            int pendingCount = pendingDevices.size();

            long currentTime = System.currentTimeMillis();
            long timeSinceLastUpload = currentTime - lastUploadTime;

            Log.d(TAG, String.format("📊 Pending: %d devices, Time since last: %d ms",
                    pendingCount, timeSinceLastUpload));

            // Условия для отправки:
            boolean shouldUpload = false;
            String reason = "";

            // 1. Проверка по количеству (достигли лимита)
            if (pendingCount >= BATCH_SIZE) {
                shouldUpload = true;
                reason = "batch size reached (" + pendingCount + "/" + BATCH_SIZE + ")";
            }
            // 2. Проверка по времени (прошла минута И есть хоть что-то для отправки)
            else if (pendingCount > 0 && timeSinceLastUpload >= UPLOAD_INTERVAL_MS) {
                shouldUpload = true;
                reason = "time interval reached (" + timeSinceLastUpload + "ms)";
            }
            // 3. Если прошло больше 2 минут, отправляем даже пустой батч (для проверки)
            else if (timeSinceLastUpload >= UPLOAD_INTERVAL_MS * 2) {
                shouldUpload = true;
                reason = "forced check (no data for 2+ minutes)";
            }

            if (!shouldUpload) {
                Log.d(TAG, "⏳ Conditions not met for upload. " +
                        "Waiting for " + (BATCH_SIZE - pendingCount) + " more devices or " +
                        (UPLOAD_INTERVAL_MS - timeSinceLastUpload) + "ms");
                return Result.success();
            }

            // Если нечего отправлять, просто обновляем время
            if (pendingCount == 0) {
                Log.d(TAG, "⏱️ No devices to upload, updating timer");
                lastUploadTime = currentTime;
                return Result.success();
            }

            // Отправляем данные
            Log.d(TAG, "📤 Upload triggered: " + reason);
            boolean success = uploadManager.uploadBatch(pendingDevices, "unified_data");

            if (success) {
                Log.i(TAG, String.format("✅ Successfully uploaded %d devices (%s)",
                        pendingDevices.size(), reason));

                // Обновляем время последней отправки
                lastUploadTime = System.currentTimeMillis();

                // Отправляем broadcast об успешной отправке
                Intent intent = new Intent("com.example.santiway.UPLOAD_COMPLETED");
                intent.putExtra("device_count", pendingDevices.size());
                intent.putExtra("reason", reason);
                intent.putExtra("timestamp", lastUploadTime);
                getApplicationContext().sendBroadcast(intent);

                return Result.success();
            } else {
                Log.e(TAG, "❌ Failed to upload batch, will retry later");
                return Result.retry();
            }

        } catch (Exception e) {
            Log.e(TAG, "💥 Error in DeviceUploadWorker: " + e.getMessage(), e);
            return Result.retry();
        } finally {
            uploadManager.cleanup();
        }
    }
}