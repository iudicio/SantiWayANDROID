package com.example.santiway.activity_map;

import android.content.Context;
import android.util.Log;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RoutingHelper {
    private static final String TAG = "RoutingHelper";
    private GraphHopper graphHopper;
    private boolean isInitialized = false;

    //Инициализация GraphHopper
    public void initialize(Context context, String regionName) {
        new Thread(() -> {
            try {
                // 1. Определяем путь для хранения данных карты
                File mapsFolder = new File(context.getExternalFilesDir(null), "maps");
                if (!mapsFolder.exists()) {
                    mapsFolder.mkdirs();
                }

                // 2. Предполагаем, что файл с данными (например, "moscow.pbf") уже скачан
                // и конвертирован в граф GraphHopper.
                // На практике вам нужно:
                // - Скачать .pbf файл региона с geofabrik.de
                // - Использовать GraphHopper CLI для конвертации в граф
                File graphFolder = new File(mapsFolder, regionName + "-gh");

                // 3. Создаем и настраиваем экземпляр GraphHopper
                graphHopper = new GraphHopper();
                graphHopper.setOSMFile(new File(mapsFolder, regionName + ".pbf").getAbsolutePath());
                graphHopper.setGraphHopperLocation(graphFolder.getAbsolutePath());

                // 4. Добавляем профили маршрутизации
                graphHopper.setProfiles(
                        new Profile("car").setVehicle("car").setWeighting("fastest"),
                        new Profile("bike").setVehicle("bike").setWeighting("fastest"),
                        new Profile("foot").setVehicle("foot").setWeighting("fastest")
                );

                // 5. Импортируем данные и создаем граф
                graphHopper.importOrLoad();
                isInitialized = true;
                Log.i(TAG, "GraphHopper initialized successfully for: " + regionName);

            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize GraphHopper", e);
                isInitialized = false;
            }
        }).start();
    }

    // Основной метод для построения маршрута
    public List<GHPoint> calculateRoute(GHPoint start, GHPoint end, String profile) {
        if (!isInitialized || graphHopper == null) {
            Log.e(TAG, "GraphHopper is not initialized");
            return null;
        }

        List<GHPoint> routePoints = new ArrayList<>();
        try {
            GHRequest request = new GHRequest(start, end);
            request.setProfile(profile);
            request.setLocale(Locale.getDefault());

            GHResponse response = graphHopper.route(request);

            if (response.hasErrors()) {
                Log.e(TAG, "Routing errors: " + response.getErrors());
                return null;
            }

            // Извлекаем точки маршрута из ответа
            PointList points = response.getBest().getPoints();
            for (int i = 0; i < points.size(); i++) {
                routePoints.add(new GHPoint(points.getLat(i), points.getLon(i)));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error calculating route", e);
        }
        return routePoints;
    }

    // Утилитарный метод для расчета расстояния и времени
    public RouteInfo getRouteInfo(GHPoint start, GHPoint end, String profile) {
        // ... аналогично calculateRoute, но возвращаем объект с distance, time ...
        return null;
    }

    public boolean isReady() {
        return isInitialized;
    }

    // Класс-модель для информации о маршруте
    public static class RouteInfo {
        public double distance; // метры
        public long time; // миллисекунды
        public List<GHPoint> points;
    }
}
