package com.example.santiway.cell_scanner;

import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.CellIdentityNr;
import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.TelephonyManager;
import android.util.Log;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GsmNavigation {
    private static final String TAG = "GsmNavigation";
    
    private Context context;
    private TelephonyManager telephonyManager;
    private Map<String, CellTower> knownTowers;
    private List<CellTower> currentTowers;
    
    public GsmNavigation(Context context) {
        this.context = context;
        this.telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        this.knownTowers = new HashMap<>();
        this.currentTowers = new ArrayList<>();
    }
 
    public List<CellTower> getCurrentNavigationTowers() {
        List<CellTower> towers = new ArrayList<>();
        
        try {
            List<CellInfo> cellInfos = telephonyManager.getAllCellInfo();
            
            if (cellInfos != null) {
                for (CellInfo cellInfo : cellInfos) {
                    CellTower tower = parseCellInfoForNavigation(cellInfo);
                    if (tower != null) {
                        towers.add(tower);
                        knownTowers.put(tower.getUniqueId(), tower);
                    }
                }
            }
            
            currentTowers = towers;
            Log.d(TAG, "Found " + towers.size() + " navigation towers");
            
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when getting cell info: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error getting navigation towers: " + e.getMessage());
        }
        
        return towers;
    }

    private CellTower parseCellInfoForNavigation(CellInfo cellInfo) {
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
                
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && cellInfo instanceof CellInfoNr) {
                tower = parseNrCell((CellInfoNr) cellInfo);
            }
            
            String operatorName = telephonyManager.getNetworkOperatorName();
            tower.setOperatorName(operatorName != null ? operatorName : "Unknown");
            
            tower.setTimestamp(System.currentTimeMillis());
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing cell info for navigation: " + e.getMessage());
            return null;
        }
        
        return tower;
    }

    @RequiresApi(android.os.Build.VERSION_CODES.Q)
    private CellTower parseNrCell(CellInfoNr nrInfo) {
        CellTower tower = new CellTower();
        CellIdentityNr cellIdentity = (CellIdentityNr) nrInfo.getCellIdentity();
        CellSignalStrength signalStrength = nrInfo.getCellSignalStrength();

        tower.setCellId((int) cellIdentity.getNci());
        tower.setTac(cellIdentity.getTac());
        try {
            String mccStr = cellIdentity.getMccString();
            String mncStr = cellIdentity.getMncString();
            tower.setMcc(mccStr != null ? Integer.parseInt(mccStr) : -1);
            tower.setMnc(mncStr != null ? Integer.parseInt(mncStr) : -1);
        } catch (Exception ignored) {
            tower.setMcc(-1);
            tower.setMnc(-1);
        }
        tower.setPci(cellIdentity.getPci());
        tower.setEarfcn(cellIdentity.getNrarfcn());
        tower.setSignalStrength(signalStrength.getDbm());
        tower.setSignalQuality(signalStrength.getLevel());
        tower.setNetworkType("5G");
        tower.setRegistered(nrInfo.isRegistered());
        tower.setNeighbor(!nrInfo.isRegistered());

        String operatorName = telephonyManager.getNetworkOperatorName();
        tower.setOperatorName(operatorName != null ? operatorName : "Unknown");
        tower.setTimestamp(System.currentTimeMillis());
        return tower;
    }

    public CellTower getPrimaryTower() {
        for (CellTower tower : currentTowers) {
            if (tower.isRegistered()) {
                return tower;
            }
        }
        return null;
    }

    public List<CellTower> getNeighborTowers() {
        List<CellTower> neighbors = new ArrayList<>();
        for (CellTower tower : currentTowers) {
            if (tower.isNeighbor()) {
                neighbors.add(tower);
            }
        }
        return neighbors;
    }

    public List<CellTower> getTowersByNetworkType(String networkType) {
        List<CellTower> filteredTowers = new ArrayList<>();
        for (CellTower tower : currentTowers) {
            if (networkType.equals(tower.getNetworkType())) {
                filteredTowers.add(tower);
            }
        }
        return filteredTowers;
    }
 
    public List<CellTower> getStrongSignalTowers(int minSignalStrength) {
        List<CellTower> strongTowers = new ArrayList<>();
        for (CellTower tower : currentTowers) {
            if (tower.getSignalStrength() >= minSignalStrength) {
                strongTowers.add(tower);
            }
        }
        return strongTowers;
    }

    public Map<String, Integer> getTowerStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        
        for (CellTower tower : currentTowers) {
            String networkType = tower.getNetworkType();
            stats.put(networkType, stats.getOrDefault(networkType, 0) + 1);
        }
        
        return stats;
    }

    public String getCoverageInfo() {
        StringBuilder info = new StringBuilder();
        
        CellTower primaryTower = getPrimaryTower();
        if (primaryTower != null) {
            info.append("Основная станция: ").append(primaryTower.getNetworkType())
                .append(" (ID: ").append(primaryTower.getCellId())
                .append(", Сигнал: ").append(primaryTower.getSignalStrength()).append(" dBm)\n");
        }
        
        List<CellTower> neighbors = getNeighborTowers();
        if (!neighbors.isEmpty()) {
            info.append("Соседние станции: ").append(neighbors.size()).append("\n");
            
            Map<String, Integer> stats = getTowerStatistics();
            for (Map.Entry<String, Integer> entry : stats.entrySet()) {
                info.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" станций\n");
            }
        }
        
        return info.toString();
    }

    public boolean hasTowersChanged(List<CellTower> previousTowers) {
        if (previousTowers == null || previousTowers.size() != currentTowers.size()) {
            return true;
        }
        
        for (CellTower currentTower : currentTowers) {
            boolean found = false;
            for (CellTower previousTower : previousTowers) {
                if (currentTower.getUniqueId().equals(previousTower.getUniqueId())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return true;
            }
        }
        
        return false;
    }

    public List<CellTower> getNewTowers(List<CellTower> previousTowers) {
        List<CellTower> newTowers = new ArrayList<>();
        
        if (previousTowers == null) {
            return currentTowers;
        }
        
        for (CellTower currentTower : currentTowers) {
            boolean isNew = true;
            for (CellTower previousTower : previousTowers) {
                if (currentTower.getUniqueId().equals(previousTower.getUniqueId())) {
                    isNew = false;
                    break;
                }
            }
            if (isNew) {
                newTowers.add(currentTower);
            }
        }
        
        return newTowers;
    }

    public List<CellTower> getLostTowers(List<CellTower> previousTowers) {
        List<CellTower> lostTowers = new ArrayList<>();
        
        if (previousTowers == null) {
            return lostTowers;
        }
        
        for (CellTower previousTower : previousTowers) {
            boolean isLost = true;
            for (CellTower currentTower : currentTowers) {
                if (previousTower.getUniqueId().equals(currentTower.getUniqueId())) {
                    isLost = false;
                    break;
                }
            }
            if (isLost) {
                lostTowers.add(previousTower);
            }
        }
        
        return lostTowers;
    }

    public String getSignalQuality() {
        CellTower primaryTower = getPrimaryTower();
        if (primaryTower == null) {
            return "Неизвестно";
        }
        
        int signalStrength = primaryTower.getSignalStrength();
        if (signalStrength >= -50) {
            return "Отличный";
        } else if (signalStrength >= -70) {
            return "Хороший";
        } else if (signalStrength >= -85) {
            return "Удовлетворительный";
        } else if (signalStrength >= -100) {
            return "Слабый";
        } else {
            return "Очень слабый";
        }
    }

    public String getNetworkInfo() {
        StringBuilder info = new StringBuilder();
        
        String operatorName = telephonyManager.getNetworkOperatorName();
        if (operatorName != null) {
            info.append("Оператор: ").append(operatorName).append("\n");
        }
        
        String networkType = getNetworkType();
        info.append("Тип сети: ").append(networkType).append("\n");
        
        String signalQuality = getSignalQuality();
        info.append("Качество сигнала: ").append(signalQuality).append("\n");
        
        return info.toString();
    }

    private String getNetworkType() {
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
                case TelephonyManager.NETWORK_TYPE_NR:
                    return "5G";
                default:
                    return "Unknown";
            }
        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException getting network type: " + se.getMessage());
            return "Unknown";
        }
    }
    
    public void clearKnownTowers() {
        knownTowers.clear();
        Log.d(TAG, "Known towers cache cleared");
    }

    public int getKnownTowersCount() {
        return knownTowers.size();
    }
}
