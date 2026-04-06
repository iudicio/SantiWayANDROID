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
import java.util.Locale;

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

        // Определяем начальную точку для центрирования (ПОСЛЕДНЯЯ точка)
        GeoPoint initialCenter = getInitialCenterPoint();
        mapController.setCenter(initialCenter);

        // Затем настраиваем остальное
        setupMapFeatures();

        // Отображаем карту
        mapView.invalidate();
    }

    private GeoPoint getInitialCenterPoint() {
        // 1. Приоритет: ПОСЛЕДНЯЯ точка устройства (индекс size() - 1)
        if (!deviceHistoryPoints.isEmpty()) {
            return deviceHistoryPoints.get(deviceHistoryPoints.size() - 1);
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

        // Создаем маркеры для всех точек
        for (int i = 0; i < deviceHistoryPoints.size(); i++) {
            GeoPoint currentPoint = deviceHistoryPoints.get(i);
            Marker marker = new Marker(mapView);
            marker.setPosition(currentPoint);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);

            // Последняя точка (i == size-1) - главный маркер
            if (i == deviceHistoryPoints.size() - 1) {
                // Последняя точка - главный маркер
                marker.setTitle(deviceName + " (Последнее местоположение)");
                marker.setSnippet("MAC: " + (deviceMac != null ? deviceMac : "N/A") +
                        "\nВремя: " + (deviceHistoryTimestamps.size() > i ? deviceHistoryTimestamps.get(i) : "Неизвестно"));
                try {
                    // Используем цвет в зависимости от статуса
                    int color = getStatusColor(deviceStatus);
                    Bitmap customMarker = createCustomMarker(color);
                    marker.setIcon(new BitmapDrawable(getResources(), customMarker));
                } catch (Exception e) {
                    marker.setIcon(getResources().getDrawable(R.drawable.ic_mark));
                }
            } else {
                // Промежуточные точки - маленькие маркеры
                marker.setTitle("Точка " + (i + 1));
                if (deviceHistoryTimestamps != null && i < deviceHistoryTimestamps.size()) {
                    marker.setSnippet("Время: " + deviceHistoryTimestamps.get(i));
                }
                try {
                    Bitmap smallMarker = createSmallMarker();
                    marker.setIcon(new BitmapDrawable(getResources(), smallMarker));
                } catch (Exception e) {
                    // Используем стандартную иконку
                }
            }

            mapView.getOverlays().add(marker);
            historyMarkers.add(marker);
        }

        // Рисуем линии между точками
        if (deviceHistoryPoints.size() > 1) {
            drawHistoryLine();
        }

        // Центрируем карту на ПОСЛЕДНЕЙ точке
        centerMapOnLastPoint();

        mapView.invalidate();
    }

    // Создание кастомного маркера для последней точки
    private Bitmap createCustomMarker(int color) {
        int size = 64; // Размер маркера
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();

        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);

        // Рисуем круг
        canvas.drawCircle(size / 2, size / 2, size / 2 - 4, paint);

        // Рисуем обводку
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        canvas.drawCircle(size / 2, size / 2, size / 2 - 4, paint);

        // Рисуем точку в центре
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(size / 2, size / 2, size / 6, paint);

        return bitmap;
    }

    // Создание маленького маркера для промежуточных точек
    private Bitmap createSmallMarker() {
        int size = 24;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();

        paint.setColor(Color.parseColor("#3DDC84"));
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);

        canvas.drawCircle(size / 2, size / 2, size / 2 - 2, paint);

        return bitmap;
    }

    private void centerMapOnLastPoint() {
        if (deviceHistoryPoints.isEmpty()) {
            return;
        }

        // Центрируем на ПОСЛЕДНЕЙ точке
        GeoPoint lastPoint = deviceHistoryPoints.get(deviceHistoryPoints.size() - 1);
        mapController.setCenter(lastPoint);
        mapController.setZoom(18.0); // Близкий зум для последней точки
    }

    private int getStatusColor(String status) {
        if (status == null) status = "GREY";

        switch (status.toUpperCase(Locale.US)) {
            case "TARGET":
                return Color.parseColor("#FF3B30");
            case "SAFE":
                return Color.parseColor("#34C759");
            case "GREY":
            default:
                return Color.parseColor("#808080");
        }
    }

    private Bitmap createArrowBitmap(int color) {
        int size = 48;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();

        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);

        Path path = new Path();
        path.moveTo(size / 2, 0);
        path.lineTo(size, size);
        path.lineTo(0, size);
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

        historyPolyline = new Polyline();
        historyPolyline.setPoints(deviceHistoryPoints);
        historyPolyline.setColor(Color.parseColor("#3DDC84"));
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