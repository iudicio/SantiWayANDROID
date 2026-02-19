package com.example.santiway.cell_scanner;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.CellIdentityNr;
import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthWcdma;
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
            List<CellInfo> cellInfos = telephonyManager.getAllCellInfo();
            
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
                    }
                }
            }
            
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
                }
            }
            
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when getting cell info: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error getting cell towers: " + e.getMessage());
        }
        
        return towers;
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

        // Для LTE проверяем TAC
        if ("LTE".equals(tower.getNetworkType()) || "5G".equals(tower.getNetworkType())) {
            if (tower.getTac() <= 0 || tower.getTac() == 2147483647) {
                Log.d(TAG, "Invalid TAC: " + tower.getTac());
                return false;
            }
        } else {
            // Для GSM/UMTS проверяем LAC
            if (tower.getLac() <= 0 || tower.getLac() == 2147483647) {
                Log.d(TAG, "Invalid LAC: " + tower.getLac());
                return false;
            }
        }
        
        return true;
    }

    private CellTower parseCellInfo(CellInfo cellInfo) {
        CellTower tower = new CellTower();
        
        try {
            if (cellInfo instanceof CellInfoGsm) {
                CellInfoGsm gsmInfo = (CellInfoGsm) cellInfo;
                CellSignalStrengthGsm gsmSignal = gsmInfo.getCellSignalStrength();
                
                tower.setCellId(gsmInfo.getCellIdentity().getCid());
                tower.setLac(gsmInfo.getCellIdentity().getLac());
                tower.setMcc(gsmInfo.getCellIdentity().getMcc());
                tower.setMnc(gsmInfo.getCellIdentity().getMnc());
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
                tower.setMcc(wcdmaInfo.getCellIdentity().getMcc());
                tower.setMnc(wcdmaInfo.getCellIdentity().getMnc());
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
                tower.setMcc(lteInfo.getCellIdentity().getMcc());
                tower.setMnc(lteInfo.getCellIdentity().getMnc());
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
                tower.setCellId((int) cellIdentity.getNci());
                tower.setTac(cellIdentity.getTac());
                String mccStr = cellIdentity.getMccString();
                String mncStr = cellIdentity.getMncString();
                try {
                    tower.setMcc(mccStr != null ? Integer.parseInt(mccStr) : -1);
                } catch (NumberFormatException nfe) {
                    tower.setMcc(-1);
                }
                try {
                    tower.setMnc(mncStr != null ? Integer.parseInt(mncStr) : -1);
                } catch (NumberFormatException nfe) {
                    tower.setMnc(-1);
                }
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
            } else {
                Log.w(TAG, "Unknown cell info type: " + cellInfo.getClass().getSimpleName());
                return null;
            }
            
            String operatorName = telephonyManager.getNetworkOperatorName();
            tower.setOperatorName(operatorName != null ? operatorName : "Unknown");
            
            tower.setTimestamp(System.currentTimeMillis());
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing cell info: " + e.getMessage());
            return null;
        }
        
        return tower;
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
