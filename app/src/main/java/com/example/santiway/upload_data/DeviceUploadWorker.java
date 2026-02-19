package com.example.santiway.upload_data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

public class DeviceUploadWorker extends Worker {
    private static final String TAG = "DeviceUploadWorker";

    public DeviceUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting device upload work");

        try {
            DeviceUploadManager uploadManager = new DeviceUploadManager(getApplicationContext());

            // Получаем данные для отправки
            List<ApiDevice> devices = uploadManager.getPendingDevicesBatch();

            if (devices.isEmpty()) {
                Log.d(TAG, "No devices to upload");
                return Result.success();
            }

            // Отправляем на сервер
            boolean success = uploadManager.uploadBatch(devices);

            if (success) {
                Log.i(TAG, "Successfully uploaded " + devices.size() + " devices");
                return Result.success();
            } else {
                Log.e(TAG, "Failed to upload devices");
                return Result.retry();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in upload work: " + e.getMessage(), e);
            return Result.failure();
        }
    }
}
