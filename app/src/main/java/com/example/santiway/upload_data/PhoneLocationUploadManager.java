package com.example.santiway.upload_data;

import android.content.Context;
import android.location.Location;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.example.santiway.gsm_protocol.LocationManager;

import org.json.JSONObject;

import java.util.Locale;

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
        if (!ServerUploadConfig.isEnabled(context)) {
            Log.d(TAG, "Server upload disabled - skip phone location upload");
            return;
        }
        try {
            LocationManager locationManager = LocationManager.getInstance(context);
            locationManager.startLocationUpdates();

            Location location = locationManager.getBestEffortLocation();
            if (location == null) {
                location = locationManager.getFreshLocation();
            }

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

            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            JSONObject json = new JSONObject();
            json.put("device_id", phoneId);
            json.put("latitude", location.getLatitude());
            json.put("longitude", location.getLongitude());
            json.put("located_at", isoFormat.format(new Date()));

            RequestBody body = RequestBody.create(json.toString(), JSON);

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Authorization", "Api-Key " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Device-MAC", phoneId)
                    .build();

            try (okhttp3.Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    Log.d(TAG, "Геопозиция телефона отправлена: " + json);
                } else {
                    Log.e(TAG, "Ошибка отправки геопозиции: "
                            + response.code() + " "
                            + response.message()
                            + "\nBODY: " + responseBody);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Ошибка uploadCurrentLocation: " + e.getMessage(), e);
        }
    }
}
