package com.example.santiway.upload_data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Хранит глобальную настройку: разрешена ли выгрузка данных на сервер.
 * Используется MainActivity, AppConfigViewActivity, DeviceUploadService,
 * UserDeviceSyncManager и UserDeviceFolderSyncManager.
 */
public final class ServerUploadConfig {
    private static final String PREFS_NAME = "AppSettings";
    private static final String KEY_SERVER_UPLOAD_ENABLED = "server_upload_enabled";

    private ServerUploadConfig() {
    }

    public static boolean isEnabled(Context context) {
        if (context == null) return false;

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        return prefs.getBoolean(KEY_SERVER_UPLOAD_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        if (context == null) return;

        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SERVER_UPLOAD_ENABLED, enabled)
                .apply();
    }
}