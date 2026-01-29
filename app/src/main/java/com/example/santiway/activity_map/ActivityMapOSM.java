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

import com.graphhopper.util.shapes.GHPoint;

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
    private RoutingHelper routingHelper;
    private Polyline routeOverlay;
    private MyLocationNewOverlay myLocationOverlay;
    private IMapController mapController;

    private GeoPoint startPoint = null;
    private GeoPoint endPoint = null;
    private Marker startMarker;
    private Marker endMarker;
    private GeoPoint devicePoint = null;
    private String deviceMarkerName = null;
    private Marker deviceMarker;

    private boolean isSelectingStart = true;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Получаем координаты устройства
        Bundle args = getArguments();
        if (args != null && args.containsKey("device_latitude") && args.containsKey("device_longitude")) {
            double lat = args.getDouble("device_latitude");
            double lon = args.getDouble("device_longitude");
            devicePoint = new GeoPoint(lat, lon);
            deviceMarkerName = args.getString("device_name", "Устройство");
        }

        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());
        routingHelper = new RoutingHelper();
        routingHelper.initialize(requireContext(), "moscow");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // ВРЕМЕННО: используем программное создание MapView
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
        setupSimpleClickListeners();
    }

    private void addDeviceMarker() {
        if (devicePoint != null && mapView != null) {
            // Удаляем старый маркер, если есть
            if (deviceMarker != null) {
                mapView.getOverlays().remove(deviceMarker);
            }

            // Создаем новый маркер
            deviceMarker = new Marker(mapView);
            deviceMarker.setPosition(devicePoint);
            deviceMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            deviceMarker.setTitle(deviceMarkerName != null ? deviceMarkerName : "Устройство");
            deviceMarker.setSnippet("MAC: " + (deviceMarkerName != null ? deviceMarkerName : ""));

            // Устанавливаем кастомную иконку (можно использовать свою)
            try {
                deviceMarker.setIcon(getResources().getDrawable(R.drawable.ic_device_marker));
            } catch (Exception e) {
                // Используем стандартную иконку
                deviceMarker.setIcon(getResources().getDrawable(android.R.drawable.ic_dialog_map));
            }

            // Добавляем маркер на карту
            mapView.getOverlays().add(deviceMarker);

            // Центрируем карту на маркере
            mapController.setCenter(devicePoint);
            mapController.animateTo(devicePoint);

            // Увеличиваем зум для лучшего обзора
            mapController.setZoom(17.0);

            mapView.invalidate();
        }
    }

    private void setupMap() {
        // Настройка источника тайлов
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

        // Устанавливаем центр карты на текущее местоположение
        myLocationOverlay.runOnFirstFix(() -> requireActivity().runOnUiThread(() -> {
            GeoPoint myLocation = myLocationOverlay.getMyLocation();
            if (myLocation != null) {
                mapController.setCenter(myLocation);
                startPoint = myLocation; // Устанавливаем стартовую точку как текущее местоположение
                addStartMarker(myLocation);
            } else {
                // Устанавливаем центр на Москву по умолчанию
                GeoPoint moscow = new GeoPoint(55.7558, 37.6173);
                mapController.setCenter(moscow);
                startPoint = moscow;
                addStartMarker(moscow);
            }
        }));

        // После установки центра карты добавляем маркер устройства
        myLocationOverlay.runOnFirstFix(() -> requireActivity().runOnUiThread(() -> {
            GeoPoint myLocation = myLocationOverlay.getMyLocation();
            if (myLocation != null) {
                mapController.setCenter(myLocation);
                startPoint = myLocation;
                addStartMarker(myLocation);
            } else {
                GeoPoint moscow = new GeoPoint(55.7558, 37.6173);
                mapController.setCenter(moscow);
                startPoint = moscow;
                addStartMarker(moscow);
            }

            // Добавляем маркер устройства (после установки стартовой точки)
            addDeviceMarker();
        }));
    }

    private void setupSimpleClickListeners() {
        // Простой обработчик нажатий на карту
        mapView.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                GeoPoint geoPoint = (GeoPoint) mapView.getProjection().fromPixels(
                        (int) event.getX(),
                        (int) event.getY()
                );

                handleMapClick(geoPoint);
                return true;
            }
            return false;
        });
    }

    private void handleMapClick(GeoPoint geoPoint) {
        if (isSelectingStart) {
            // Устанавливаем стартовую точку
            if (startMarker != null) {
                mapView.getOverlays().remove(startMarker);
            }
            startPoint = geoPoint;
            addStartMarker(geoPoint);
            isSelectingStart = false;
            Toast.makeText(getContext(), "Выберите конечную точку", Toast.LENGTH_SHORT).show();
        } else {
            // Устанавливаем конечную точку
            if (endMarker != null) {
                mapView.getOverlays().remove(endMarker);
            }
            endPoint = geoPoint;
            addEndMarker(geoPoint);
            isSelectingStart = true;

            // Строим маршрут
            if (startPoint != null && endPoint != null && routingHelper.isReady()) {
                buildRoute();
            }
        }
    }

    private void addStartMarker(GeoPoint point) {
        startMarker = new Marker(mapView);
        startMarker.setPosition(point);
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        startMarker.setTitle("Старт");
        startMarker.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_mylocation));
        mapView.getOverlays().add(startMarker);
        mapView.invalidate();
    }

    private void addEndMarker(GeoPoint point) {
        endMarker = new Marker(mapView);
        endMarker.setPosition(point);
        endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        endMarker.setTitle("Конец");
        endMarker.setIcon(getResources().getDrawable(android.R.drawable.ic_dialog_map));
        mapView.getOverlays().add(endMarker);
        mapView.invalidate();
    }

    private void buildRoute() {
        if (startPoint == null || endPoint == null) {
            return;
        }

        // Преобразуем GeoPoint в GHPoint
        GHPoint startGH = new GHPoint(startPoint.getLatitude(), startPoint.getLongitude());
        GHPoint endGH = new GHPoint(endPoint.getLatitude(), endPoint.getLongitude());

        // Строим маршрут в отдельном потоке
        new Thread(() -> {
            try {
                List<GHPoint> routePoints = routingHelper.calculateRoute(startGH, endGH, "car");

                requireActivity().runOnUiThread(() -> {
                    if (routePoints != null && !routePoints.isEmpty()) {
                        drawRouteOnMap(routePoints);
                    } else {
                        Toast.makeText(requireContext(),
                                "Не удалось построить маршрут",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(),
                                "Ошибка построения маршрута",
                                Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void drawRouteOnMap(List<GHPoint> ghPoints) {
        // Удаляем старый маршрут, если есть
        if (routeOverlay != null) {
            mapView.getOverlays().remove(routeOverlay);
        }

        // Конвертируем GHPoint в GeoPoint
        List<GeoPoint> geoPoints = new ArrayList<>();
        for (GHPoint p : ghPoints) {
            geoPoints.add(new GeoPoint(p.getLat(), p.getLon()));
        }

        // Создаем Polyline из точек
        routeOverlay = new Polyline();
        routeOverlay.setPoints(geoPoints);
        routeOverlay.setColor(Color.parseColor("#500084F5"));
        routeOverlay.setWidth(12f);

        mapView.getOverlays().add(routeOverlay);
        mapView.invalidate();
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