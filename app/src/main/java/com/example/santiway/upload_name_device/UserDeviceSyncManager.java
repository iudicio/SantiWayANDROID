package com.example.santiway.upload_name_device;
import com.example.santiway.upload_data.ApiService;
import com.example.santiway.upload_data.ApiConfig;
import com.example.santiway.upload_data.ApiResponse;
import com.example.santiway.host_database.AppSettingsRepository;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import retrofit2.Retrofit;
import retrofit2.Response;
import retrofit2.converter.gson.GsonConverterFactory;

public class UserDeviceSyncManager {

    private static final String TAG = "UserDeviceSync";

    private static final String PREFS = "UserDeviceSyncPrefs";
    private static final String KEY_REGISTERED = "registered";
    private static final String KEY_LAST_NAME = "last_name";
    private static final String KEY_LAST_MAC = "last_mac";

    private final Context context;
    private final SharedPreferences prefs;
    private final ApiService apiService;
    private final AppSettingsRepository repository;

    public UserDeviceSyncManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.repository = new AppSettingsRepository(context);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ApiConfig.getBaseUrl(context))
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.apiService = retrofit.create(ApiService.class);
    }

    // === ГЛАВНЫЙ МЕТОД ===
    public void syncOwnerDevice() {
        new Thread(() -> {
            try {
                String apiKey = ApiConfig.getApiKey(context);
                String mac = ApiConfig.getPhoneMac(context);
                String name = repository.getDeviceName();

                if (name == null || name.trim().isEmpty()) {
                    name = "Telephone";
                }

                ensureRegistered(apiKey, mac);
                syncName(apiKey, mac, name);

            } catch (Exception e) {
                Log.e(TAG, "Sync error: " + e.getMessage(), e);
            }
        }).start();
    }

    // === 1. РЕГИСТРАЦИЯ ===
    private void ensureRegistered(String apiKey, String mac) {
        boolean registered = prefs.getBoolean(KEY_REGISTERED, false);
        String lastMac = prefs.getString(KEY_LAST_MAC, null);

        if (registered && mac.equals(lastMac)) {
            return;
        }

        try {
            Log.d(TAG, "Registering device...");

            RegisterUserDeviceRequest body =
                    new RegisterUserDeviceRequest(apiKey, mac);

            String authHeader = "Api-Key " + apiKey;

            Response<UserDeviceResponse> response =
                    apiService.registerDevice(authHeader, body).execute();

            if (response.isSuccessful()) {
                Log.d(TAG, "Device registered");

                prefs.edit()
                        .putBoolean(KEY_REGISTERED, true)
                        .putString(KEY_LAST_MAC, mac)
                        .apply();

            } else {
                Log.e(TAG, "Register failed: " + response.code());
            }

        } catch (Exception e) {
            Log.e(TAG, "Register error: " + e.getMessage());
        }
    }

    // === 2. СИНХРОНИЗАЦИЯ ИМЕНИ ===
    private void syncName(String apiKey, String mac, String currentName) {

        String lastName = prefs.getString(KEY_LAST_NAME, null);

        try {
            if (lastName == null) {
                // первый раз
                Log.d(TAG, "Setting initial name");

                String authHeader = "Api-Key " + apiKey;

                Response<ApiResponse> response =
                        apiService.setDeviceName(authHeader,
                                new DeviceNameRequest(mac, currentName)).execute();

                if (response.isSuccessful()) {
                    saveName(currentName);
                    Log.d(TAG, "Saved device!");
                }

            } else if (!lastName.equals(currentName)) {
                // имя изменилось
                Log.d(TAG, "Renaming device");

                String authHeader = "Api-Key " + apiKey;

                Response<ApiResponse> response =
                        apiService.renameDevice(authHeader,
                                new DeviceNameRequest(mac, currentName)).execute();

                if (response.isSuccessful()) {
                    saveName(currentName);
                    Log.d(TAG, "Renamed device!");
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Name sync error: " + e.getMessage());
        }
    }

    private void saveName(String name) {
        prefs.edit().putString(KEY_LAST_NAME, name).apply();
    }
}
