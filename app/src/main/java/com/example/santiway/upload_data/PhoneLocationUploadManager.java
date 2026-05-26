package com.example.santiway.upload_data;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.example.santiway.gsm_protocol.LocationManager;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class PhoneLocationUploadManager {
    private static final String TAG = "PhoneLocationUpload";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final Context context;
    private final OkHttpClient client = new OkHttpClient();

    public PhoneLocationUploadManager(Context context) {
        this.context = context.getApplicationContext();
        ApiConfig.initialize(this.context);
    }

    public void uploadCurrentLocation() {
        try {
            LocationManager locationManager = LocationManager.getInstance(context);
            locationManager.startLocationUpdates();

            Location location = locationManager.getFreshLocation();

            if (location == null && locationManager.hasValidLocation()) {
                location = locationManager.getCurrentLocation();
            }

            if (location == null) {
                Log.w(TAG, "Нет координат телефона для отправки");
                return;
            }

            String apiKey = ApiConfig.getApiKey(context);
            String phoneId = ApiConfig.getPhoneMac(context);

            String url = ApiConfig.getBaseUrl(context) + "api/device-locations/";

            JSONObject json = new JSONObject();
            json.put("device_id", phoneId);
            json.put("latitude", location.getLatitude());
            json.put("longitude", location.getLongitude());

            RequestBody body = RequestBody.create(json.toString(), JSON);

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Api-Key " + apiKey)
                    .addHeader("X-API-Key", apiKey)
                    .build();

            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Геопозиция телефона отправлена: " + json);
                } else {
                    Log.e(TAG, "Ошибка отправки геопозиции: " + response.code() + " " + response.message());
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Ошибка uploadCurrentLocation: " + e.getMessage(), e);
        }
    }
}