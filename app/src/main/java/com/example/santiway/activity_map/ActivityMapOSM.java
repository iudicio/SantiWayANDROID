package com.example.santiway.activity_map;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import com.example.santiway.R;

import java.util.ArrayList;
import java.util.List;

public class ActivityMapOSM extends Fragment {

    private MapView mapView;
    private MyLocationNewOverlay myLocationOverlay;
    private IMapController mapController;

    private List<GeoPoint> deviceHistoryPoints = new ArrayList<>();
    private List<String> deviceHistoryTimestamps = new ArrayList<>();
    private List<Marker> historyMarkers = new ArrayList<>();
    private Polyline historyPolyline;
    private String deviceMac;
    private String deviceName;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Получаем историю устройства
        Bundle args = getArguments();
        if (args != null && args.containsKey("history_latitudes")) {
            double[] lats = args.getDoubleArray("history_latitudes");
            double[] lons = args.getDoubleArray("history_longitudes");
            String[] timestamps = args.getStringArray("history_timestamps");
            deviceMac = args.getString("device_mac");
            deviceName = args.getString("device_name", "Устройство");

            if (lats != null && lons != null && lats.length == lons.length) {
                for (int i = 0; i < lats.length; i++) {
                    deviceHistoryPoints.add(new GeoPoint(lats[i], lons[i]));
                    if (timestamps != null && i < timestamps.length) {
                        deviceHistoryTimestamps.add(timestamps[i]);
                    } else {
                        deviceHistoryTimestamps.add("Запись #" + (i + 1));
                    }
                }
            }
        }

        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Создаем MapView
        mapView = new MapView(getContext());
        mapView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return mapView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupMap();
    }

    private void setupMap() {
        // Настройка источника тайлов (онлайн карта)
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);

        // Добавляем возможность поворота карты
        RotationGestureOverlay rotationOverlay = new RotationGestureOverlay(mapView);
        rotationOverlay.setEnabled(true);
        mapView.getOverlays().add(rotationOverlay);

        // Настройка контроллера карты
        mapController = mapView.getController();
        mapController.setZoom(15.0);

        // Добавляем слой текущего местоположения
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), mapView);
        myLocationOverlay.enableMyLocation();
        mapView.getOverlays().add(myLocationOverlay);

        // Устанавливаем центр карты и добавляем маркеры устройства
        myLocationOverlay.runOnFirstFix(() -> requireActivity().runOnUiThread(() -> {
            GeoPoint myLocation = myLocationOverlay.getMyLocation();
            if (myLocation != null) {
                mapController.setCenter(myLocation);
            } else {
                // Если нет местоположения, центрируем на первой точке устройства
                if (!deviceHistoryPoints.isEmpty()) {
                    mapController.setCenter(deviceHistoryPoints.get(0));
                } else {
                    // Или на Москве по умолчанию
                    mapController.setCenter(new GeoPoint(55.7558, 37.6173));
                }
            }

            // Добавляем маркеры устройства
            addDeviceHistoryMarkers();
        }));
    }

    private void addDeviceHistoryMarkers() {
        if (deviceHistoryPoints.isEmpty() || mapView == null) {
            return;
        }

        // Очищаем старые маркеры
        for (Marker marker : historyMarkers) {
            mapView.getOverlays().remove(marker);
        }
        historyMarkers.clear();

        // Удаляем старую линию
        if (historyPolyline != null) {
            mapView.getOverlays().remove(historyPolyline);
        }

        // Создаем маркеры для каждой точки истории
        for (int i = 0; i < deviceHistoryPoints.size(); i++) {
            GeoPoint point = deviceHistoryPoints.get(i);
            Marker marker = new Marker(mapView);
            marker.setPosition(point);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

            // Для первой (текущей/последней) точки используем другой маркер
            if (i == 0) {
                marker.setTitle(deviceName + " (Текущее/Последнее)");
                marker.setSnippet("MAC: " + (deviceMac != null ? deviceMac : "N/A"));
                try {
                    // Используем вашу иконку ic_mark
                    marker.setIcon(getResources().getDrawable(R.drawable.ic_mark));
                } catch (Exception e) {
                    marker.setIcon(getResources().getDrawable(android.R.drawable.ic_dialog_map));
                }
            } else {
                marker.setTitle(deviceName + " (" + deviceHistoryTimestamps.get(i) + ")");
                marker.setSnippet("Историческая запись");
                marker.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_mylocation));
            }

            mapView.getOverlays().add(marker);
            historyMarkers.add(marker);
        }

        // Если есть более одной точки, рисуем линию
        if (deviceHistoryPoints.size() > 1) {
            drawHistoryLine();
        }

        // Центрируем карту на всех точках
        centerMapOnHistory();

        mapView.invalidate();
    }

    private void drawHistoryLine() {
        if (deviceHistoryPoints.size() < 2) {
            return;
        }

        // Создаем линию
        historyPolyline = new Polyline();
        historyPolyline.setPoints(deviceHistoryPoints);
        historyPolyline.setColor(Color.parseColor("#3DDC84")); // Зеленый цвет как в дизайне
        historyPolyline.setWidth(8f);
        historyPolyline.setGeodesic(true);

        mapView.getOverlays().add(historyPolyline);
    }

    private void centerMapOnHistory() {
        if (deviceHistoryPoints.isEmpty()) {
            return;
        }

        // Вычисляем границы всех точек
        double minLat = deviceHistoryPoints.get(0).getLatitude();
        double maxLat = deviceHistoryPoints.get(0).getLatitude();
        double minLon = deviceHistoryPoints.get(0).getLongitude();
        double maxLon = deviceHistoryPoints.get(0).getLongitude();

        for (GeoPoint point : deviceHistoryPoints) {
            minLat = Math.min(minLat, point.getLatitude());
            maxLat = Math.max(maxLat, point.getLatitude());
            minLon = Math.min(minLon, point.getLongitude());
            maxLon = Math.max(maxLon, point.getLongitude());
        }

        // Центрируем карту на всем маршруте
        GeoPoint center = new GeoPoint(
                (minLat + maxLat) / 2,
                (minLon + maxLon) / 2
        );

        mapController.setCenter(center);
        mapController.animateTo(center);

        // Устанавливаем зум, чтобы все точки были видны
        double latSpan = maxLat - minLat;
        double lonSpan = maxLon - minLon;
        double span = Math.max(latSpan, lonSpan);

        // Рассчитываем подходящий зум
        if (span > 0) {
            double zoomLevel = Math.max(10.0, 15.0 - Math.log10(span * 100));
            mapController.setZoom(zoomLevel);
        } else {
            mapController.setZoom(17.0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (myLocationOverlay != null) {
            myLocationOverlay.disableMyLocation();
        }
    }
}