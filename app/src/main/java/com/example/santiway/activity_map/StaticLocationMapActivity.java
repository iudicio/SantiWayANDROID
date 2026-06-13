package com.example.santiway;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;

public class StaticLocationMapActivity extends AppCompatActivity {

    private MapView mapView;
    private Button selectButton;
    private TextView zoomInButton;
    private TextView zoomOutButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_static_location);

        Configuration.getInstance().setUserAgentValue(getPackageName());

        mapView = findViewById(R.id.static_map);
        selectButton = findViewById(R.id.btn_select_static_location);
        zoomInButton = findViewById(R.id.btn_zoom_in);
        zoomOutButton = findViewById(R.id.btn_zoom_out);

        mapView.setMultiTouchControls(true);

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

        zoomInButton.setOnClickListener(v -> {
            mapView.getController().zoomIn();
        });

        zoomOutButton.setOnClickListener(v -> {
            mapView.getController().zoomOut();
        });

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

        Toast.makeText(this, "Координата выбрана", Toast.LENGTH_SHORT).show();

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