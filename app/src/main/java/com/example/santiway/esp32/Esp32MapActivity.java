package com.example.santiway.esp32;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.example.santiway.BaseLocalizedActivity;
import com.example.santiway.activity_map.MapLayerManager;
import com.example.santiway.gsm_protocol.LocationManager;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class Esp32MapActivity extends BaseLocalizedActivity {

    private MapView mapView;
    private Esp32DatabaseHelper database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.parseColor("#071427"));
        getWindow().setNavigationBarColor(Color.parseColor("#172A46"));

        Configuration.getInstance().setUserAgentValue(getPackageName());

        database = new Esp32DatabaseHelper(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#071427"));

        mapView = new MapView(this);
        mapView.setMultiTouchControls(true);
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);

        root.addView(mapView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView title = new TextView(this);
        title.setText("ESP32 map");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setBackgroundColor(Color.parseColor("#CC071427"));
        title.setPadding(dp(14), dp(10), dp(14), dp(10));

        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.leftMargin = dp(12);
        titleParams.topMargin = dp(24);
        root.addView(title, titleParams);

        root.addView(MapLayerManager.createOsmControls(this, mapView, 16, () -> {
            MapLayerManager.applySavedLayer(this, mapView);
            mapView.invalidate();
        }));

        setContentView(root);

        MapLayerManager.applySavedLayer(this, mapView);
        mapView.getController().setZoom(15.0);

        loadMarkers();
    }

    private void loadMarkers() {
        GeoPoint fallbackCenter = getFallbackCenter();
        GeoPoint firstRealPoint = null;
        boolean hasAny = false;
        int zeroOffsetIndex = 0;

        mapView.getOverlays().clear();

        try (Cursor cursor = database.getMapDevices()) {
            while (cursor.moveToNext()) {
                String mac = cursor.getString(cursor.getColumnIndexOrThrow("mac_address"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                double lat = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
                double lon = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
                double alt = cursor.getDouble(cursor.getColumnIndexOrThrow("altitude"));
                int connected = cursor.getInt(cursor.getColumnIndexOrThrow("is_connected"));
                int mode = cursor.getInt(cursor.getColumnIndexOrThrow("coordinates_mode"));

                boolean hasRealCoordinates = isValidCoordinate(lat, lon);

                GeoPoint point;

                if (hasRealCoordinates) {
                    point = new GeoPoint(lat, lon);
                    if (firstRealPoint == null) firstRealPoint = point;
                } else {
                    point = offsetPoint(fallbackCenter, zeroOffsetIndex++);
                }

                Marker marker = new Marker(mapView);
                marker.setPosition(point);
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                marker.setTitle((name == null || name.isEmpty()) ? "ESP32" : name);

                String modeText;
                if (mode == Esp32DatabaseHelper.COORDINATES_MANUAL) {
                    modeText = "manual";
                } else if (mode == Esp32DatabaseHelper.COORDINATES_AUTO) {
                    modeText = "auto";
                } else {
                    modeText = "not set";
                }

                marker.setSnippet(
                        mac +
                                "\nConnected: " + (connected == 1 ? "yes" : "no") +
                                "\nCoordinates: " + (hasRealCoordinates
                                ? String.format(java.util.Locale.US, "%.6f, %.6f, %.1f", lat, lon, alt)
                                : "not set, shown near fallback point") +
                                "\nMode: " + modeText
                );

                int color;
                if (!hasRealCoordinates) {
                    color = Color.parseColor("#F5C542");
                } else if (connected == 1) {
                    color = Color.parseColor("#3DDC84");
                } else {
                    color = Color.parseColor("#6F839C");
                }

                marker.setIcon(MapLayerManager.markerDrawable(this, color, 1));
                MapLayerManager.styleOsmMarkerInfoWindow(this, mapView, marker);
                mapView.getOverlays().add(marker);

                hasAny = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (firstRealPoint != null) {
            mapView.getController().setCenter(firstRealPoint);
            mapView.getController().setZoom(17.0);
        } else {
            mapView.getController().setCenter(fallbackCenter);
            mapView.getController().setZoom(hasAny ? 16.0 : 12.0);
        }

        mapView.invalidate();
    }

    private GeoPoint getFallbackCenter() {
        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);

        if (prefs.getBoolean("static_location_enabled", false)) {
            float lat = prefs.getFloat("static_latitude", 0f);
            float lon = prefs.getFloat("static_longitude", 0f);

            if (isValidCoordinate(lat, lon)) {
                return new GeoPoint(lat, lon);
            }
        }

        try {
            Location location = LocationManager.getInstance(this).getBestEffortLocation();
            if (location != null && isValidCoordinate(location.getLatitude(), location.getLongitude())) {
                return new GeoPoint(location.getLatitude(), location.getLongitude());
            }
        } catch (Exception ignored) {
        }

        return new GeoPoint(55.7558, 37.6173);
    }

    private GeoPoint offsetPoint(GeoPoint center, int index) {
        double angle = Math.toRadians((index % 12) * 30.0);
        double radiusMeters = 35.0 + (index / 12) * 25.0;

        double dLat = (Math.sin(angle) * radiusMeters) / 110540.0;
        double dLon = (Math.cos(angle) * radiusMeters) /
                (111320.0 * Math.max(0.2, Math.cos(Math.toRadians(center.getLatitude()))));

        return new GeoPoint(center.getLatitude() + dLat, center.getLongitude() + dLon);
    }

    private boolean isValidCoordinate(double lat, double lon) {
        return !Double.isNaN(lat)
                && !Double.isNaN(lon)
                && !Double.isInfinite(lat)
                && !Double.isInfinite(lon)
                && Math.abs(lat) <= 90.0
                && Math.abs(lon) <= 180.0
                && !(lat == 0.0 && lon == 0.0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
            loadMarkers();
        }
    }

    @Override
    protected void onPause() {
        if (mapView != null) mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (database != null) database.close();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}