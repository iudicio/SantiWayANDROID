package com.example.santiway.activity_map;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.Manifest;
import android.graphics.Path;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
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
    private String deviceStatus = "scanned";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Получаем статус устройства
        Bundle args = getArguments();
        if (args != null && args.containsKey("device_status")) {
            deviceStatus = args.getString("device_status");
        }
        // Оптимизация производительности osmdroid
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        // Уменьшаем кэш тайлов для быстрой загрузки
        Configuration.getInstance().setTileDownloadThreads((short) 2);
        Configuration.getInstance().setTileFileSystemThreads((short) 2);
        Configuration.getInstance().setTileDownloadMaxQueueSize((short) 10);
        Configuration.getInstance().setCacheMapTileCount((short) 20);
        Configuration.getInstance().setCacheMapTileOvershoot((short) 5);

        // Получаем историю устройства
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

        // Сначала настраиваем минимальную конфигурацию
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapController = mapView.getController();

        // Сразу устанавливаем приближение для быстрой загрузки
        mapController.setZoom(15.0);

        // Определяем начальную точку для центрирования
        GeoPoint initialCenter = getInitialCenterPoint();
        mapController.setCenter(initialCenter);

        // Затем настраиваем остальное
        setupMapFeatures();

        // Отображаем карту
        mapView.invalidate();
    }

    private GeoPoint getInitialCenterPoint() {
        // 1. Приоритет: последняя точка устройства
        if (!deviceHistoryPoints.isEmpty()) {
            return deviceHistoryPoints.get(0);
        }

        // 2. Резерв: текущее местоположение пользователя (если есть GPS)
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            android.location.LocationManager locationManager =
                    (android.location.LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);

            if (locationManager != null) {
                Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastKnownLocation == null) {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }

                if (lastKnownLocation != null) {
                    return new GeoPoint(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                }
            }
        }

        // 3. Запасной вариант: центр города или координаты по умолчанию
        return new GeoPoint(55.7558, 37.6173); // Москва
    }

    private void setupMapFeatures() {
        // Добавляем возможность поворота карты
        RotationGestureOverlay rotationOverlay = new RotationGestureOverlay(mapView);
        rotationOverlay.setEnabled(true);
        mapView.getOverlays().add(rotationOverlay);

        // Добавляем слой текущего местоположения (если есть разрешение)
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), mapView);
            myLocationOverlay.enableMyLocation();
            mapView.getOverlays().add(myLocationOverlay);

            // Когда получим местоположение, слегка подкорректируем центр
            myLocationOverlay.runOnFirstFix(() -> requireActivity().runOnUiThread(() -> {
                if (!deviceHistoryPoints.isEmpty()) {
                    // Оставляем центр на устройстве, просто добавляем точку пользователя
                    addDeviceHistoryMarkers();
                }
            }));
        } else {
            // Если нет разрешения на геолокацию, просто показываем устройство
            addDeviceHistoryMarkers();
        }
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

        // Создаем стрелочки для маршрута
        for (int i = 0; i < deviceHistoryPoints.size(); i++) {
            GeoPoint currentPoint = deviceHistoryPoints.get(i);
            Marker marker = new Marker(mapView);
            marker.setPosition(currentPoint);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);

            // Если есть следующая точка, рисуем стрелочку в ее направлении
            if (i < deviceHistoryPoints.size() - 1) {
                GeoPoint nextPoint = deviceHistoryPoints.get(i + 1);
                float bearing = calculateBearing(
                        currentPoint.getLatitude(), currentPoint.getLongitude(),
                        nextPoint.getLatitude(), nextPoint.getLongitude()
                );

                // Поворачиваем иконку в направлении движения
                marker.setRotation(bearing);

                // Используем кастомную иконку стрелочки
                try {
                    // Создаем Bitmap стрелочки программно
                    Bitmap arrowBitmap = createArrowBitmap(
                            ContextCompat.getColor(requireContext(),
                                    getStatusColor(deviceStatus))
                    );
                    marker.setIcon(new BitmapDrawable(getResources(), arrowBitmap));
                } catch (Exception e) {
                    // Если не получилось, используем стандартную иконку
                    marker.setIcon(ContextCompat.getDrawable(requireContext(),
                            R.drawable.ic_arrow_direction));
                }

                marker.setTitle("Точка " + (i + 1));
                if (deviceHistoryTimestamps != null && i < deviceHistoryTimestamps.size()) {
                    marker.setSnippet("Время: " + deviceHistoryTimestamps.get(i));
                }
            } else {
                // Последняя точка - другая иконка
                marker.setTitle(deviceName + " (Последнее)");
                marker.setSnippet("MAC: " + (deviceMac != null ? deviceMac : "N/A"));
                try {
                    marker.setIcon(getResources().getDrawable(R.drawable.ic_mark));
                } catch (Exception e) {
                    marker.setIcon(getResources().getDrawable(android.R.drawable.ic_dialog_map));
                }
            }

            mapView.getOverlays().add(marker);
            historyMarkers.add(marker);
        }

        // Рисуем линии между точками
        if (deviceHistoryPoints.size() > 1) {
            drawHistoryLine();
        }

        // Центрируем карту
        centerMapOnHistory();

        mapView.invalidate();
    }

    private void centerMapOnHistory() {
        if (deviceHistoryPoints.isEmpty()) {
            return;
        }

        // Если всего одна точка, центрируем на ней
        if (deviceHistoryPoints.size() == 1) {
            mapController.setCenter(deviceHistoryPoints.get(0));
            mapController.setZoom(18.0); // Близкий зум для одной точки
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

        // Автоматически рассчитываем оптимальный зум
        double latDiff = maxLat - minLat;
        double lonDiff = maxLon - minLon;
        double maxDiff = Math.max(latDiff, lonDiff);

        // Формула для расчета зума на основе расстояния
        if (maxDiff < 0.001) { // ~100 метров
            mapController.setZoom(18.0);
        } else if (maxDiff < 0.01) { // ~1 км
            mapController.setZoom(16.0);
        } else if (maxDiff < 0.1) { // ~10 км
            mapController.setZoom(13.0);
        } else {
            mapController.setZoom(11.0);
        }
    }

    private int getStatusColor(String status) {
        switch (status) {
            case "Target":
                return Color.parseColor("#FF3D3D"); // Красный
            case "SAFE":
                return Color.parseColor("#4CAF50"); // Зеленый
            case "CLEAR":
                return Color.parseColor("#2196F3"); // Синий
            default:
                return Color.parseColor("#9E9E9E"); // Серый
        }
    }

    private Bitmap createArrowBitmap(int color) {
        int size = 48; // Размер стрелочки
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();

        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);

        // Рисуем треугольник (стрелочку)
        Path path = new Path();
        path.moveTo(size / 2, 0); // Верхняя точка
        path.lineTo(size, size);  // Правая нижняя
        path.lineTo(0, size);     // Левая нижняя
        path.close();

        canvas.drawPath(path, paint);

        return bitmap;
    }

    private float calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

        float bearing = (float) Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
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