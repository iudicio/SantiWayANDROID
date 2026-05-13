package com.example.santiway.upload_folder_device;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.santiway.upload_data.ApiConfig;
import com.example.santiway.upload_data.ApiService;
import com.example.santiway.upload_folder_device.DeviceFolderRequest;
import com.example.santiway.upload_folder_device.DeviceFolderResponse;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class UserDeviceFolderSyncManager {

    private static final String TAG = "FolderSync";
    private static final String PREFS = "UserDeviceFolderSyncPrefs";

    private final Context context;
    private final SharedPreferences prefs;
    private final ApiService apiService;

    public UserDeviceFolderSyncManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ApiConfig.getBaseUrl(context))
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.apiService = retrofit.create(ApiService.class);
    }

    public void syncFolderCreated(String folderName) {
        new Thread(() -> {
            try {
                String apiKey = ApiConfig.getApiKey(context);
                String authHeader = "Api-Key " + apiKey;
                String mac = ApiConfig.getPhoneMac(context);

                String createdAt = getOrCreateCreatedAt(folderName);

                Response<DeviceFolderResponse> response = apiService.createFolder(
                        authHeader,
                        new DeviceFolderRequest(mac, folderName, createdAt)
                ).execute();

                if (response.isSuccessful()) {
                    Log.d(TAG, "Folder created on server: " + folderName);
                } else {
                    Log.e(TAG, "Create folder failed: " + response.code());
                }

            } catch (Exception e) {
                Log.e(TAG, "Create folder error: " + e.getMessage(), e);
            }
        }).start();
    }

    public void syncFolderDeleted(String folderName) {
        new Thread(() -> {
            try {
                String apiKey = ApiConfig.getApiKey(context);
                String authHeader = "Api-Key " + apiKey;
                String mac = ApiConfig.getPhoneMac(context);

                String createdAt = prefs.getString(keyCreatedAt(folderName), null);
                if (createdAt == null) {
                    Log.e(TAG, "No created_at for folder: " + folderName);
                    return;
                }

                Response<DeviceFolderResponse> response = apiService.deleteFolder(
                        authHeader,
                        new DeviceFolderRequest(mac, folderName, createdAt)
                ).execute();

                if (response.isSuccessful()) {
                    prefs.edit().remove(keyCreatedAt(folderName)).apply();
                    Log.d(TAG, "Folder deleted on server: " + folderName);
                } else {
                    Log.e(TAG, "Delete folder failed: " + response.code());
                }

            } catch (Exception e) {
                Log.e(TAG, "Delete folder error: " + e.getMessage(), e);
            }
        }).start();
    }

    public void syncFolderRenamed(String oldName, String newName) {
        new Thread(() -> {
            try {
                String apiKey = ApiConfig.getApiKey(context);
                String authHeader = "Api-Key " + apiKey;
                String mac = ApiConfig.getPhoneMac(context);

                String oldCreatedAt = prefs.getString(keyCreatedAt(oldName), null);

                if (oldCreatedAt == null) {
                    Log.e(TAG, "Cannot rename folder: no created_at for old folder: " + oldName);
                    return;
                }

                Response<DeviceFolderResponse> deleteResponse = apiService.deleteFolder(
                        authHeader,
                        new DeviceFolderRequest(mac, oldName, oldCreatedAt)
                ).execute();

                if (!deleteResponse.isSuccessful()) {
                    String error = deleteResponse.errorBody() != null
                            ? deleteResponse.errorBody().string()
                            : "empty error body";

                    Log.e(TAG, "Rename failed. Old folder not deleted: "
                            + deleteResponse.code() + " / " + error);
                    return;
                }

                Response<DeviceFolderResponse> createResponse = apiService.createFolder(
                        authHeader,
                        new DeviceFolderRequest(mac, newName, oldCreatedAt)
                ).execute();

                if (createResponse.isSuccessful()) {
                    prefs.edit()
                            .remove(keyCreatedAt(oldName))
                            .putString(keyCreatedAt(newName), oldCreatedAt)
                            .apply();

                    Log.d(TAG, "Folder renamed on server: " + oldName + " -> " + newName);
                } else {
                    String error = createResponse.errorBody() != null
                            ? createResponse.errorBody().string()
                            : "empty error body";

                    Log.e(TAG, "New folder create failed after delete: "
                            + createResponse.code() + " / " + error);
                }

            } catch (Exception e) {
                Log.e(TAG, "Rename folder error: " + e.getMessage(), e);
            }
        }).start();
    }

    private String getOrCreateCreatedAt(String folderName) {
        String key = keyCreatedAt(folderName);
        String value = prefs.getString(key, null);

        if (value == null) {
            value = nowIsoUtc();
            prefs.edit().putString(key, value).apply();
        }

        return value;
    }

    private String keyCreatedAt(String folderName) {
        return "folder_created_at_" + folderName;
    }

    private String nowIsoUtc() {
        SimpleDateFormat format =
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}