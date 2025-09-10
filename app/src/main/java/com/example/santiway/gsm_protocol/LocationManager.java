package com.example.santiway.gsm_protocol;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class LocationManager {
    private static final String TAG = "LocationManager";

    private Context context;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location currentLocation;

    // Интервалы обновления
    private static final long UPDATE_INTERVAL = 15000; // 15 секунд ✅

    public LocationManager(Context context) {
        this.context = context;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        setupLocationCallback();
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;

                for (Location location : locationResult.getLocations()) {
                    updateLocation(location);
                }
            }
        };
    }

    private void updateLocation(Location location) {
        this.currentLocation = location;
        Log.d(TAG, "Location updated: " + location.getLatitude() + ", " + location.getLongitude() +
                (location.hasAltitude() ? ", Altitude: " + location.getAltitude() : "") +
                ", Accuracy: " + location.getAccuracy());

        if (onLocationUpdateListener != null) {
            onLocationUpdateListener.onLocationUpdate(location);
        }
    }

    public void startLocationUpdates() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permissions not granted");
            if (onLocationUpdateListener != null) {
                onLocationUpdateListener.onPermissionDenied();
            }
            return;
        }

        try {
            LocationRequest locationRequest = createLocationRequest();
            fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
            );
            Log.d(TAG, "Location updates started (interval: " + UPDATE_INTERVAL + "ms)");
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: Permission revoked during runtime: " + e.getMessage());
            if (onLocationUpdateListener != null) {
                onLocationUpdateListener.onPermissionDenied();
            }
        }
    }

    public void stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            Log.d(TAG, "Location updates stopped");
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when stopping updates: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error stopping location updates: " + e.getMessage());
        }
    }

    private LocationRequest createLocationRequest() {
        return new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL)
                .setWaitForAccurateLocation(true)
                .setMinUpdateIntervalMillis(UPDATE_INTERVAL)
                .build();
    }

    //Проверяем что хотя бы одно разрешение есть
    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context,
                        Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    // Проверка точного местоположения (если нужно)
    public boolean hasFineLocationPermission() {
        return ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    // Геттеры для location data
    public Location getCurrentLocation() {
        return currentLocation;
    }

    public double getLatitude() {
        return currentLocation != null ? currentLocation.getLatitude() : 0.0;
    }

    public double getLongitude() {
        return currentLocation != null ? currentLocation.getLongitude() : 0.0;
    }

    public double getAltitude() {
        return currentLocation != null && currentLocation.hasAltitude() ?
                currentLocation.getAltitude() : 0.0;
    }

    public float getAccuracy() {
        return currentLocation != null ? currentLocation.getAccuracy() : 0.0f;
    }

    public boolean hasValidLocation() {
        return currentLocation != null;
    }

    // Интерфейс для колбэков
    public interface OnLocationUpdateListener {
        void onLocationUpdate(Location location);
        void onPermissionDenied();
        void onLocationError(String error);
    }

    private OnLocationUpdateListener onLocationUpdateListener;

    public void setOnLocationUpdateListener(OnLocationUpdateListener listener) {
        this.onLocationUpdateListener = listener;
    }

    // Получение последней известной локации с обработкой ошибок
    public void getLastKnownLocation() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Cannot get last location: permissions not granted");
            if (onLocationUpdateListener != null) {
                onLocationUpdateListener.onPermissionDenied();
            }
            return;
        }

        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            updateLocation(location);
                        } else {
                            Log.w(TAG, "Last location is null");
                            if (onLocationUpdateListener != null) {
                                onLocationUpdateListener.onLocationError("Last location unavailable");
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error getting last location: " + e.getMessage());
                        if (onLocationUpdateListener != null) {
                            onLocationUpdateListener.onLocationError(e.getMessage());
                        }
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException in getLastKnownLocation: " + e.getMessage());
            if (onLocationUpdateListener != null) {
                onLocationUpdateListener.onPermissionDenied();
            }
        }
    }

    // Очистка ресурсов
    public void cleanup() {
        stopLocationUpdates();
        onLocationUpdateListener = null;
    }
}