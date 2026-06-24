package com.example.santiway;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.example.santiway.BaseLocalizedActivity;
import com.example.santiway.activity_map.MapLayerManager;


import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;

public class StaticLocationMapActivity extends BaseLocalizedActivity {

    private MapView mapView;
    private Button selectButton;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_static_location);

        Configuration.getInstance().setUserAgentValue(getPackageName());

        mapView = findViewById(R.id.static_map);
        selectButton = findViewById(R.id.btn_select_static_location);

        mapView.setMultiTouchControls(true);
        MapLayerManager.applySavedLayer(this, mapView);
        FrameLayout root = findViewById(R.id.root_static_location_map);
        root.addView(MapLayerManager.createOsmControls(this, mapView, 16, null, selectButton));

        // Убираем стандартные кнопки приближения/отдаления osmdroid
        mapView.getZoomController().setVisibility(
                CustomZoomButtonsController.Visibility.NEVER
        );

        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);

        double lat = prefs.getFloat("static_latitude", 55.7558f);
        double lon = prefs.getFloat("static_longitude", 37.6173f);

        GeoPoint startPoint = new GeoPoint(lat, lon);

        mapView.getController().setZoom(15.0);
        mapView.getController().setCenter(startPoint);

        selectButton.setOnClickListener(v -> {
            GeoPoint centerPoint = (GeoPoint) mapView.getMapCenter();
            savePoint(centerPoint);
        });
    }

    private void savePoint(GeoPoint point) {
        getSharedPreferences("AppSettings", MODE_PRIVATE)
                .edit()
                .putBoolean("static_location_enabled", true)
                .putFloat("static_latitude", (float) point.getLatitude())
                .putFloat("static_longitude", (float) point.getLongitude())
                .apply();

        Toast.makeText(this, getString(R.string.toast_coordinate_selected), Toast.LENGTH_SHORT).show();

        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }

        super.onPause();
    }
}
