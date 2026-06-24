package com.example.santiway.cell_scanner;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.CellIdentityNr;
import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.NeighboringCellInfo;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.telephony.cdma.CdmaCellLocation;
import android.util.Log;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CellScanner {
    private static final String TAG = "CellScanner";
    
    private Context context;
    private TelephonyManager telephonyManager;
    private List<CellTower> cellTowers;
    private Set<String> knownTowerIds;
    
    public CellScanner(Context context) {
        this.context = context;
        this.telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        this.cellTowers = new ArrayList<>();
        this.knownTowerIds = new HashSet<>();
    }
    
    public CellTower getCurrentCellTower() {
        CellTower cellTower = new CellTower();
        
        try {
            if (telephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    throw new SecurityException("Location permission not granted for getCellLocation");
                }
                GsmCellLocation gsmCellLocation = (GsmCellLocation) telephonyManager.getCellLocation();
                if (gsmCellLocation != null) {
                    cellTower.setCellId(gsmCellLocation.getCid());
                    cellTower.setLac(gsmCellLocation.getLac());
                    cellTower.setNetworkType("GSM");
                }
            } else if (telephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    throw new SecurityException("Location permission not granted for getCellLocation");
                }
                CdmaCellLocation cdmaCellLocation = (CdmaCellLocation) telephonyManager.getCellLocation();
                if (cdmaCellLocation != null) {
                    cellTower.setCellId(cdmaCellLocation.getBaseStationId());
                    cellTower.setNetworkType("CDMA");
                }
            }
            
            String operatorName = telephonyManager.getNetworkOperatorName();
            cellTower.setOperatorName(operatorName != null ? operatorName : "Unknown");
            
            // MCC и MNC
            String networkOperator = telephonyManager.getNetworkOperator();
            if (networkOperator != null && networkOperator.length() >= 5) {
                cellTower.setMcc(Integer.parseInt(networkOperator.substring(0, 3)));
                cellTower.setMnc(Integer.parseInt(networkOperator.substring(3)));
            }
            
            int signalStrength = getSignalStrength();
            cellTower.setSignalStrength(signalStrength);
            
            cellTower.setRegistered(true);
            cellTower.setTimestamp(System.currentTimeMillis());
            
            Log.d(TAG, "Current cell tower: " + cellTower.getDescription());
            
        } catch (SecurityException e) {
            Log.w(TAG, "Permission error getting current cell tower: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error getting current cell tower: " + e.getMessage());
        }
        
        return cellTower;
    }

    public List<CellTower> getAllCellTowers() {
        List<CellTower> towers = new ArrayList<>();
        Set<String> currentTowerIds = new HashSet<>();
        
        try {
            if (!hasLocationPermission()) {
                Log.w(TAG, "Location permission not granted for getAllCellInfo");
                return towers;
            }
            List<CellInfo> cellInfos = getFreshCellInfo();
            
            if (cellInfos != null) {
                Log.d(TAG, "Raw cell info count: " + cellInfos.size());
                
                for (CellInfo cellInfo : cellInfos) {
                    CellTower tower = parseCellInfo(cellInfo);
                    if (tower != null && isValidTower(tower)) {
                        String towerId = tower.getUniqueId();
                        currentTowerIds.add(towerId);
                        
                        boolean isNewTower = !knownTowerIds.contains(towerId);
                        if (isNewTower) {
                            Log.d(TAG, "New cell tower discovered: " + tower.getDescription());
                        }
                        
                        towers.add(tower);
                    } else if (tower != null) {
                        Log.d(TAG, "Rejected cell tower: " + tower.getDescription());
                    }
                }
            } else {
                Log.d(TAG, "TelephonyManager returned no cell info");
            }

            appendNeighboringCellInfo(towers, currentTowerIds);
            
            knownTowerIds = currentTowerIds;
            
            Log.d(TAG, "Found " + towers.size() + " valid cell towers");
            
            CellTower currentTower = getCurrentCellTower();
            if (currentTower != null && isValidTower(currentTower)) {
                String currentTowerId = currentTower.getUniqueId();
                boolean alreadyInList = towers.stream()
                    .anyMatch(t -> t.getUniqueId().equals(currentTowerId));
                
                if (!alreadyInList) {
                    Log.d(TAG, "Adding current cell tower: " + currentTower.getDescription());
                    towers.add(currentTower);
                    currentTowerIds.add(currentTowerId);
                }
            }
            
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when getting cell info: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error getting cell towers: " + e.getMessage());
        }
        
        return towers;
    }

    @SuppressWarnings("deprecation")
    private void appendNeighboringCellInfo(List<CellTower> towers, Set<String> currentTowerIds) {
        try {
            Object result = TelephonyManager.class
                    .getMethod("getNeighboringCellInfo")
                    .invoke(telephonyManager);
            if (!(result instanceof List)) {
                Log.d(TAG, "TelephonyManager.getNeighboringCellInfo returned no neighbors");
                return;
            }

            List<?> neighbors = (List<?>) result;
            if (neighbors.isEmpty()) {
                Log.d(TAG, "TelephonyManager.getNeighboringCellInfo returned empty neighbors");
                return;
            }
            Log.d(TAG, "Raw neighboring cell info count: " + neighbors.size());
            for (Object item : neighbors) {
                if (!(item instanceof NeighboringCellInfo)) continue;
                NeighboringCellInfo neighbor = (NeighboringCellInfo) item;
                CellTower tower = parseNeighboringCellInfo(neighbor);
                if (tower == null || !isValidTower(tower)) {
                    if (tower != null) Log.d(TAG, "Rejected neighboring cell tower: " + tower.getDescription());
                    continue;
                }
                String towerId = tower.getUniqueId();
                if (currentTowerIds.contains(towerId)) continue;
                currentTowerIds.add(towerId);
                towers.add(tower);
                Log.d(TAG, "Added neighboring cell tower: " + tower.getDescription());
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Permission error getting neighboring cell info: " + e.getMessage());
        } catch (Exception e) {
            Log.w(TAG, "Error getting neighboring cell info: " + e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private CellTower parseNeighboringCellInfo(NeighboringCellInfo neighbor) {
        if (neighbor == null) return null;
        int cid = neighbor.getCid();
        if (cid <= 0 || cid == NeighboringCellInfo.UNKNOWN_CID || cid == 2147483647) return null;

        CellTower tower = new CellTower();
        tower.setCellId(cid);
        tower.setLac(neighbor.getLac());
        tower.setPsc(neighbor.getPsc());
        tower.setSignalStrength(neighborSignalDbm(neighbor));
        tower.setSignalQuality(neighbor.getRssi());
        tower.setNetworkType(networkTypeFromRadio(neighbor.getNetworkType()));
        tower.setRegistered(false);
        tower.setNeighbor(true);
        String operatorName = telephonyManager.getNetworkOperatorName();
        tower.setOperatorName(operatorName != null ? operatorName : "Unknown");
        applyNetworkOperatorFallback(tower);
        tower.setTimestamp(System.currentTimeMillis());
        return tower;
    }

    private String networkTypeFromRadio(int networkType) {
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return "GSM";
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "UMTS";
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "LTE";
            default:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        networkType == TelephonyManager.NETWORK_TYPE_NR) {
                    return "5G";
                }
                return "CELL";
        }
    }

    @SuppressWarnings("deprecation")
    private int neighborSignalDbm(NeighboringCellInfo neighbor) {
        int rssi = neighbor.getRssi();
        if (rssi == NeighboringCellInfo.UNKNOWN_RSSI || rssi < 0) return -999;
        return -113 + (2 * rssi);
    }
    
    private boolean isValidTower(CellTower tower) {
        // Проверяем Cell ID
        if (tower.getCellId() <= 0 || tower.getCellId() == 2147483647) {
            Log.d(TAG, "Invalid Cell ID: " + tower.getCellId());
            return false;
        }

        // Проверяем MCC (должен быть 3 цифры)
        if (tower.getMcc() < 100 || tower.getMcc() > 999) {
            Log.d(TAG, "Invalid MCC: " + tower.getMcc());
            return false;
        }

        // Проверяем MNC (обычно 2-3 цифры)
        if (tower.getMnc() < 0 || tower.getMnc() > 999) {
            Log.d(TAG, "Invalid MNC: " + tower.getMnc());
            return false;
        }

        // Некоторые устройства/операторы скрывают LAC/TAC у соседних сот.
        // Такие соты всё равно полезны для обнаружения, поэтому не отбрасываем их целиком.
        if ("LTE".equals(tower.getNetworkType()) || "5G".equals(tower.getNetworkType())) {
            if (tower.getTac() <= 0 || tower.getTac() == 2147483647) {
                Log.d(TAG, "Missing/invalid TAC, keeping tower anyway: " + tower.getTac());
            }
        } else {
            if (tower.getLac() <= 0 || tower.getLac() == 2147483647) {
                Log.d(TAG, "Missing/invalid LAC, keeping tower anyway: " + tower.getLac());
            }
        }
        
        return true;
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private List<CellInfo> getFreshCellInfo() {
        List<CellInfo> cachedCellInfo = telephonyManager.getAllCellInfo();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !hasLocationPermission()) {
            return cachedCellInfo;
        }

        AtomicReference<List<CellInfo>> updatedCellInfo = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        try {
            telephonyManager.requestCellInfoUpdate(
                    command -> new Thread(command, "CellInfoCallback").start(),
                    new TelephonyManager.CellInfoCallback() {
                        @Override
                        public void onCellInfo(List<CellInfo> cellInfo) {
                            updatedCellInfo.set(cellInfo);
                            latch.countDown();
                        }

                        @Override
                        public void onError(int errorCode, Throwable detail) {
                            Log.w(TAG, "Cell info update failed: " + errorCode +
                                    (detail == null ? "" : ", " + detail.getMessage()));
                            latch.countDown();
                        }
                    });
            latch.await(2500, TimeUnit.MILLISECONDS);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            Log.w(TAG, "Fresh cell info request failed: " + e.getMessage());
        }

        List<CellInfo> freshCellInfo = updatedCellInfo.get();
        return freshCellInfo != null && !freshCellInfo.isEmpty() ? freshCellInfo : cachedCellInfo;
    }

    private CellTower parseCellInfo(CellInfo cellInfo) {
        CellTower tower = new CellTower();
        
        try {
            if (cellInfo instanceof CellInfoGsm) {
                CellInfoGsm gsmInfo = (CellInfoGsm) cellInfo;
                CellSignalStrengthGsm gsmSignal = gsmInfo.getCellSignalStrength();
                
                tower.setCellId(gsmInfo.getCellIdentity().getCid());
                tower.setLac(gsmInfo.getCellIdentity().getLac());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    tower.setMcc(parseOperatorPart(gsmInfo.getCellIdentity().getMccString()));
                    tower.setMnc(parseOperatorPart(gsmInfo.getCellIdentity().getMncString()));
                } else {
                    tower.setMcc(gsmInfo.getCellIdentity().getMcc());
                    tower.setMnc(gsmInfo.getCellIdentity().getMnc());
                }
                tower.setArfcn(gsmInfo.getCellIdentity().getArfcn());
                tower.setSignalStrength(gsmSignal.getDbm());
                tower.setSignalQuality(gsmSignal.getLevel());
                tower.setNetworkType("GSM");
                tower.setRegistered(cellInfo.isRegistered());
                tower.setNeighbor(!cellInfo.isRegistered());
                
                Log.d(TAG, "Parsed GSM cell: CID=" + tower.getCellId() + 
                          ", LAC=" + tower.getLac() + 
                          ", Signal=" + tower.getSignalStrength() + "dBm");
                
            } else if (cellInfo instanceof CellInfoWcdma) {
                CellInfoWcdma wcdmaInfo = (CellInfoWcdma) cellInfo;
                CellSignalStrengthWcdma wcdmaSignal = wcdmaInfo.getCellSignalStrength();
                
                tower.setCellId(wcdmaInfo.getCellIdentity().getCid());
                tower.setLac(wcdmaInfo.getCellIdentity().getLac());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    tower.setMcc(parseOperatorPart(wcdmaInfo.getCellIdentity().getMccString()));
                    tower.setMnc(parseOperatorPart(wcdmaInfo.getCellIdentity().getMncString()));
                } else {
                    tower.setMcc(wcdmaInfo.getCellIdentity().getMcc());
                    tower.setMnc(wcdmaInfo.getCellIdentity().getMnc());
                }
                tower.setPsc(wcdmaInfo.getCellIdentity().getPsc());
                tower.setArfcn(wcdmaInfo.getCellIdentity().getUarfcn());
                tower.setSignalStrength(wcdmaSignal.getDbm());
                tower.setSignalQuality(wcdmaSignal.getLevel());
                tower.setNetworkType("UMTS");
                tower.setRegistered(cellInfo.isRegistered());
                tower.setNeighbor(!cellInfo.isRegistered());
                
                Log.d(TAG, "Parsed UMTS cell: CID=" + tower.getCellId() + 
                          ", LAC=" + tower.getLac() + 
                          ", PSC=" + tower.getPsc() +
                          ", Signal=" + tower.getSignalStrength() + "dBm");
                
            } else if (cellInfo instanceof CellInfoLte) {
                CellInfoLte lteInfo = (CellInfoLte) cellInfo;
                CellSignalStrengthLte lteSignal = lteInfo.getCellSignalStrength();
                
                tower.setCellId(lteInfo.getCellIdentity().getCi());
                tower.setTac(lteInfo.getCellIdentity().getTac());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    tower.setMcc(parseOperatorPart(lteInfo.getCellIdentity().getMccString()));
                    tower.setMnc(parseOperatorPart(lteInfo.getCellIdentity().getMncString()));
                } else {
                    tower.setMcc(lteInfo.getCellIdentity().getMcc());
                    tower.setMnc(lteInfo.getCellIdentity().getMnc());
                }
                tower.setPci(lteInfo.getCellIdentity().getPci());
                tower.setEarfcn(lteInfo.getCellIdentity().getEarfcn());
                tower.setSignalStrength(lteSignal.getDbm());
                tower.setSignalQuality(lteSignal.getLevel());
                tower.setNetworkType("LTE");
                tower.setRegistered(cellInfo.isRegistered());
                tower.setNeighbor(!cellInfo.isRegistered());
                
                Log.d(TAG, "Parsed LTE cell: CI=" + tower.getCellId() + 
                          ", TAC=" + tower.getTac() + 
                          ", PCI=" + tower.getPci() +
                          ", Signal=" + tower.getSignalStrength() + "dBm");
                
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo instanceof CellInfoNr) {
                CellInfoNr nrInfo = (CellInfoNr) cellInfo;
                CellSignalStrength signalStrength = nrInfo.getCellSignalStrength();
                
                CellIdentityNr cellIdentity = (CellIdentityNr) nrInfo.getCellIdentity();
                tower.setCellId(cellIdentity.getNci());
                tower.setTac(cellIdentity.getTac());
                String mccStr = cellIdentity.getMccString();
                String mncStr = cellIdentity.getMncString();
                tower.setMcc(parseOperatorPart(mccStr));
                tower.setMnc(parseOperatorPart(mncStr));
                tower.setPci(cellIdentity.getPci());
                tower.setEarfcn(cellIdentity.getNrarfcn());
                tower.setSignalStrength(signalStrength.getDbm());
                tower.setSignalQuality(signalStrength.getLevel());
                tower.setNetworkType("5G");
                tower.setRegistered(cellInfo.isRegistered());
                tower.setNeighbor(!cellInfo.isRegistered());
                
                Log.d(TAG, "Parsed 5G cell: NCI=" + tower.getCellId() + 
                          ", TAC=" + tower.getTac() + 
                          ", PCI=" + tower.getPci() +
                          ", Signal=" + tower.getSignalStrength() + "dBm");
            } else if (cellInfo instanceof CellInfoCdma) {
                CellInfoCdma cdmaInfo = (CellInfoCdma) cellInfo;
                CellSignalStrengthCdma cdmaSignal = cdmaInfo.getCellSignalStrength();

                tower.setCellId(cdmaInfo.getCellIdentity().getBasestationId());
                tower.setLac(cdmaInfo.getCellIdentity().getNetworkId());
                tower.setMnc(cdmaInfo.getCellIdentity().getSystemId());
                tower.setSignalStrength(cdmaSignal.getDbm());
                tower.setSignalQuality(cdmaSignal.getLevel());
                tower.setNetworkType("CDMA");
                tower.setRegistered(cellInfo.isRegistered());
                tower.setNeighbor(!cellInfo.isRegistered());

                Log.d(TAG, "Parsed CDMA cell: BID=" + tower.getCellId() +
                          ", NID=" + tower.getLac() +
                          ", SID=" + tower.getMnc() +
                          ", Signal=" + tower.getSignalStrength() + "dBm");
            } else {
                Log.w(TAG, "Unknown cell info type: " + cellInfo.getClass().getSimpleName());
                return null;
            }
            
            String operatorName = telephonyManager.getNetworkOperatorName();
            tower.setOperatorName(operatorName != null ? operatorName : "Unknown");
            applyNetworkOperatorFallback(tower);
            
            tower.setTimestamp(System.currentTimeMillis());
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing cell info: " + e.getMessage());
            return null;
        }
        
        return tower;
    }

    private void applyNetworkOperatorFallback(CellTower tower) {
        String networkOperator = telephonyManager.getNetworkOperator();
        if (networkOperator == null || networkOperator.length() < 5) return;
        try {
            if (tower.getMcc() < 100 || tower.getMcc() > 999) {
                tower.setMcc(Integer.parseInt(networkOperator.substring(0, 3)));
            }
            if (tower.getMnc() < 0 || tower.getMnc() > 999) {
                tower.setMnc(Integer.parseInt(networkOperator.substring(3)));
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private int parseOperatorPart(String value) {
        if (value == null || value.trim().isEmpty()) return -1;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
    
    private int getSignalStrength() {
        try {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("Location permission not granted for getAllCellInfo");
            }
            List<CellInfo> cellInfos = telephonyManager.getAllCellInfo();
            if (cellInfos != null) {
                for (CellInfo cellInfo : cellInfos) {
                    if (cellInfo.isRegistered()) {
                        if (cellInfo instanceof CellInfoGsm) {
                            return ((CellInfoGsm) cellInfo).getCellSignalStrength().getDbm();
                        } else if (cellInfo instanceof CellInfoWcdma) {
                            return ((CellInfoWcdma) cellInfo).getCellSignalStrength().getDbm();
                        } else if (cellInfo instanceof CellInfoLte) {
                            return ((CellInfoLte) cellInfo).getCellSignalStrength().getDbm();
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo instanceof CellInfoNr) {
                            CellSignalStrength signalStrength = ((CellInfoNr) cellInfo).getCellSignalStrength();
                            return signalStrength.getDbm();
                        } else if (cellInfo instanceof CellInfoCdma) {
                            return ((CellInfoCdma) cellInfo).getCellSignalStrength().getDbm();
                        }
                    }
                }
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Permission error getting signal strength: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error getting signal strength: " + e.getMessage());
        }
        
        return -999;
    }

    public boolean isScanningAvailable() {
        return telephonyManager != null && 
               telephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
    }

    public String getNetworkType() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            return "Unknown";
        }
        try {
            int networkType = telephonyManager.getNetworkType();
            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                    return "2G";
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    return "3G";
                case TelephonyManager.NETWORK_TYPE_LTE:
                    return "4G";
                default:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        networkType == TelephonyManager.NETWORK_TYPE_NR) {
                        return "5G";
                    }
                    return "Unknown";
            }
        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException getting network type: " + se.getMessage());
            return "Unknown";
        }
    }

    public String getOperatorName() {
        try {
            String operatorName = telephonyManager.getNetworkOperatorName();
            return operatorName != null ? operatorName : "Unknown";
        } catch (Exception e) {
            Log.e(TAG, "Error getting operator name: " + e.getMessage());
            return "Unknown";
        }
    }

    public void clearKnownTowers() {
        knownTowerIds.clear();
        Log.d(TAG, "Cleared known towers cache");
    }

    public int getKnownTowersCount() {
        return knownTowerIds.size();
    }

    public String getNetworkTypeStatistics() {
        List<CellTower> towers = getAllCellTowers();
        int gsmCount = 0, umtsCount = 0, lteCount = 0, nrCount = 0;
        
        for (CellTower tower : towers) {
            switch (tower.getNetworkType()) {
                case "GSM":
                    gsmCount++;
                    break;
                case "UMTS":
                    umtsCount++;
                    break;
                case "LTE":
                    lteCount++;
                    break;
                case "5G":
                    nrCount++;
                    break;
            }
        }
        
        return String.format(Locale.getDefault(), "GSM: %d, UMTS: %d, LTE: %d, 5G: %d", 
                           gsmCount, umtsCount, lteCount, nrCount);
    }

    public String getCoverageInfo() {
        List<CellTower> towers = getAllCellTowers();
        if (towers.isEmpty()) {
            return "Нет доступных базовых станций";
        }
        
        StringBuilder info = new StringBuilder();
        info.append("Найдено станций: ").append(towers.size()).append("\n");
        
        int registered = 0, neighbors = 0;
        for (CellTower tower : towers) {
            if (tower.isRegistered()) {
                registered++;
            } else {
                neighbors++;
            }
        }
        
        info.append("Зарегистрированных: ").append(registered).append("\n");
        info.append("Соседних: ").append(neighbors).append("\n");
        info.append("Статистика: ").append(getNetworkTypeStatistics());
        
        return info.toString();
    }
}
