package com.example.santiway.upload_data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

public class DeviceUploadWorker extends Worker {
    private static final String TAG = "DeviceUploadWorker";

    private DeviceUploadManager uploadManager;

    public DeviceUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        uploadManager = new DeviceUploadManager(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "DeviceUploadWorker started");

        try {
            if (!NetworkUtils.isNetworkAvailable(getApplicationContext())) {
                Log.d(TAG, "No network available, skipping upload");
                return Result.retry();
            }

            List<RabbitMQDevice> batch = uploadManager.getPendingDevicesBatch();

            if (batch.isEmpty()) {
                Log.d(TAG, "No pending devices to upload");
                return Result.success();
            }

            boolean success = uploadManager.uploadBatch(batch);

            if (success) {
                Log.d(TAG, "Successfully uploaded batch of " + batch.size() + " devices to RabbitMQ");
                uploadManager.markDevicesAsUploaded(batch);
                return Result.success();
            } else {
                Log.e(TAG, "Failed to upload batch to RabbitMQ, will retry later");
                return Result.retry();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in DeviceUploadWorker: " + e.getMessage(), e);
            return Result.retry();
        } finally {
            uploadManager.cleanup();
        }
    }
}
