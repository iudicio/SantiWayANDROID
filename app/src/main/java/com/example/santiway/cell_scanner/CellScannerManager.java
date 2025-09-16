package com.example.santiway.cell_scanner;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;
import com.example.santiway.gsm_protocol.LocationManager;
import java.util.List;

public class CellScannerManager {
    private static final String TAG = "CellScannerManager";
    
    private Context context;
    private CellScanner cellScanner;
    private CellDatabaseHelper databaseHelper;
    private GsmNavigation gsmNavigation;
    private LocationManager locationManager;
    
    public CellScannerManager(Context context) {
        this.context = context;
        this.cellScanner = new CellScanner(context);
        this.databaseHelper = new CellDatabaseHelper(context);
        this.gsmNavigation = new GsmNavigation(context);
        this.locationManager = new LocationManager(context);
    }

    public void startScanning(String tableName) {
        Log.d(TAG, "Starting cell scanning for table: " + tableName);
        
        locationManager.startLocationUpdates();
        
        Intent intent = new Intent(context, CellForegroundService.class);
        intent.setAction("START_CELL_SCAN");
        intent.putExtra("tableName", tableName);
        
        Location currentLocation = locationManager.getCurrentLocation();
        if (currentLocation != null) {
            intent.putExtra("latitude", currentLocation.getLatitude());
            intent.putExtra("longitude", currentLocation.getLongitude());
            intent.putExtra("altitude", currentLocation.getAltitude());
            intent.putExtra("accuracy", currentLocation.getAccuracy());
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public void stopScanning() {
        Log.d(TAG, "Stopping cell scanning");
        
        Intent intent = new Intent(context, CellForegroundService.class);
        intent.setAction("STOP_CELL_SCAN");
        context.startService(intent);
    }

    public List<CellTower> getCurrentTowers() {
        return cellScanner.getAllCellTowers();
    }

    public CellTower getPrimaryTower() {
        return gsmNavigation.getPrimaryTower();
    }

    public List<CellTower> getNeighborTowers() {
        return gsmNavigation.getNeighborTowers();
    }

    public List<CellTower> getTowersByNetworkType(String networkType) {
        return gsmNavigation.getTowersByNetworkType(networkType);
    }

    public String getCoverageInfo() {
        return gsmNavigation.getCoverageInfo();
    }

    public String getNetworkInfo() {
        return gsmNavigation.getNetworkInfo();
    }

    public String getSignalQuality() {
        return gsmNavigation.getSignalQuality();
    }
    
    public String getTowerStatistics() {
        return databaseHelper.getNetworkTypeStatistics("default_cell_table");
    }

    public int getTowersCount(String tableName) {
        return databaseHelper.getCellTowersCount(tableName);
    }

    public List<CellTower> getAllTowersFromDatabase(String tableName) {
        return databaseHelper.getAllCellTowers(tableName);
    }

    public List<CellTower> getTowersByNetworkTypeFromDatabase(String tableName, String networkType) {
        return databaseHelper.getCellTowersByNetworkType(tableName, networkType);
    }

    public void createTable(String tableName) {
        databaseHelper.createCellTableIfNotExists(tableName);
    }

    public boolean deleteTable(String tableName) {
        return databaseHelper.deleteCellTable(tableName);
    }

    public void clearTable(String tableName) {
        databaseHelper.clearCellTable(tableName);
    }

    public List<String> getAllTables() {
        return databaseHelper.getAllCellTables();
    }

    public double getCurrentLatitude() {
        Location location = locationManager.getCurrentLocation();
        return location != null ? location.getLatitude() : 0.0;
    }
    
    public double getCurrentLongitude() {
        Location location = locationManager.getCurrentLocation();
        return location != null ? location.getLongitude() : 0.0;
    }
    
    public double getCurrentAltitude() {
        Location location = locationManager.getCurrentLocation();
        return location != null ? location.getAltitude() : 0.0;
    }
    
    public float getCurrentAccuracy() {
        Location location = locationManager.getCurrentLocation();
        return location != null ? location.getAccuracy() : 0.0f;
    }

    public boolean isGpsAvailable() {
        return locationManager.isGpsAvailable();
    }

    public boolean isNetworkLocationAvailable() {
        return locationManager.isNetworkLocationAvailable();
    }

    public boolean isCellScanningAvailable() {
        return cellScanner.isScanningAvailable();
    }

    public String getNetworkType() {
        return cellScanner.getNetworkType();
    }

    public String getOperatorName() {
        return cellScanner.getOperatorName();
    }

    public boolean isLocationFresh(long maxAge) {
        Location location = locationManager.getCurrentLocation();
        if (location == null) return false;
        return (System.currentTimeMillis() - location.getTime()) < maxAge;
    }

    public float getDistanceTo(double latitude, double longitude) {
        Location currentLocation = locationManager.getCurrentLocation();
        if (currentLocation == null) return -1;
        
        float[] results = new float[1];
        Location.distanceBetween(currentLocation.getLatitude(), currentLocation.getLongitude(), 
                                latitude, longitude, results);
        return results[0];
    }

    public String getScanningStatus() {
        StringBuilder status = new StringBuilder();
        
        status.append("GPS доступен: ").append(isGpsAvailable() ? "Да" : "Нет").append("\n");
        status.append("Сетевое местоположение: ").append(isNetworkLocationAvailable() ? "Да" : "Нет").append("\n");
        status.append("Сканирование базовых станций: ").append(isCellScanningAvailable() ? "Доступно" : "Недоступно").append("\n");
        status.append("Тип сети: ").append(getNetworkType()).append("\n");
        status.append("Оператор: ").append(getOperatorName()).append("\n");
        
        if (getCurrentLatitude() != 0.0 && getCurrentLongitude() != 0.0) {
            status.append("Координаты: ").append(String.format(java.util.Locale.getDefault(), "%.6f, %.6f", getCurrentLatitude(), getCurrentLongitude())).append("\n");
            status.append("Точность: ").append(String.format(java.util.Locale.getDefault(), "%.1f м", getCurrentAccuracy())).append("\n");
        } else {
            status.append("Координаты: Неизвестны\n");
        }
        
        return status.toString();
    }

    public String getDetailedTowerInfo() {
        StringBuilder info = new StringBuilder();
        
        List<CellTower> towers = getCurrentTowers();
        if (towers.isEmpty()) {
            info.append("Базовые станции не найдены\n");
            return info.toString();
        }
        
        info.append("Найдено базовых станций: ").append(towers.size()).append("\n\n");
        
        for (CellTower tower : towers) {
            info.append("Тип: ").append(tower.getNetworkType()).append("\n");
            info.append("ID: ").append(tower.getCellId()).append("\n");
            info.append("LAC: ").append(tower.getLac()).append("\n");
            info.append("MCC: ").append(tower.getMcc()).append("\n");
            info.append("MNC: ").append(tower.getMnc()).append("\n");
            info.append("Сигнал: ").append(tower.getSignalStrength()).append(" dBm\n");
            info.append("Статус: ").append(tower.isRegistered() ? "Зарегистрирована" : "Соседняя").append("\n");
            info.append("Оператор: ").append(tower.getOperatorName()).append("\n");
            info.append("---\n");
        }
        
        return info.toString();
    }

    public void cleanup() {
        if (locationManager != null) {
            locationManager.cleanup();
        }
        
        if (databaseHelper != null) {
            databaseHelper.close();
        }
        
        Log.d(TAG, "CellScannerManager cleanup completed");
    }
}
