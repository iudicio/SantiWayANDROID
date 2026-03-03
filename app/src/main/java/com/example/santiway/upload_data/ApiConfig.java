package com.example.santiway.upload_data;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.util.Log;

import com.example.santiway.R;

/**
 * Central place for API configuration.
 *
 * Supports server address as:
 *  - domain: device-analysis.ru
 *  - full URL: https://device-analysis.ru
 *  - IP(+port): 192.168.0.10:8000
 */
public final class ApiConfig {
    private static final String TAG = "ApiConfig";

    /** Always ends with "/" (e.g. "https://device-analysis.ru/") */
    private static String apiBaseUrl;

    private static String apiKey;
    private static String phoneMac;

    private ApiConfig() {}

    /**
     * Init config from strings.xml (default_server_ip can contain BOTH IP or domain).
     */
    public static void initialize(Context context) {
        // --- API key ---
        apiKey = safeTrim(context.getString(R.string.default_api_key));
        if (apiKey == null) {
            Log.e(TAG, "API Key is not configured in strings.xml");
        } else {
            Log.d(TAG, "Using API key from strings.xml");
        }

        // --- Server address (IP or domain) ---
        // NOTE: resource name kept for backward compatibility.
        String defaultServerAddress = safeTrim(context.getString(R.string.domen_server));
        if (defaultServerAddress == null) {
            Log.e(TAG, "Server address is not configured in strings.xml, using fallback");
            defaultServerAddress = "192.168.110.49";
        }
        setServerAddress(defaultServerAddress);
        Log.d(TAG, "Using server address from strings.xml: " + defaultServerAddress);

        // --- Device ID / MAC (best effort) ---
        phoneMac = getSimpleMacAddress(context);
        Log.d(TAG, "Phone MAC: " + phoneMac);

        Log.d(TAG, "✅ API Base URL: " + apiBaseUrl);
        Log.d(TAG, "✅ API Key: " + (apiKey != null ? "configured" : "null"));
        Log.d(TAG, "✅ Phone MAC: " + phoneMac);
    }

    /**
     * Set server address at runtime (for local testing).
     *
     * Examples:
     *  - "device-analysis.ru"
     *  - "https://device-analysis.ru"
     *  - "192.168.0.5:8000"
     */
    public static void setServerAddress(String address) {
        apiBaseUrl = normalizeBaseUrl(address);
        Log.d(TAG, "API base URL set to: " + apiBaseUrl);
    }

    /**
     * Returns base url (always ends with "/").
     * Make sure initialize(context) was called in Application.onCreate().
     */
    public static String getApiBaseUrl() {
        return apiBaseUrl;
    }

    /**
     * Backward compatible method: returns base URL and initializes lazily if needed.
     */
    public static String getBaseUrl(Context context) {
        if (apiBaseUrl == null && context != null) {
            initialize(context);
        }
        return apiBaseUrl;
    }

    /**
     * Full URL for devices endpoint.
     */
    public static String getDevicesUrl() {
        return joinUrl(getApiBaseUrl(), "api/devices/");
    }

    /**
     * Get API key (initialized in initialize()).
     */
    public static String getApiKey() {
        return apiKey;
    }

    /**
     * Backward compatible method: reads API key from strings.xml if not initialized.
     */
    public static String getApiKey(Context context) {
        if (apiKey == null && context != null) {
            apiKey = safeTrim(context.getString(R.string.default_api_key));
        }
        if (apiKey == null) {
            Log.e(TAG, "API Key is not configured in strings.xml");
        }
        return apiKey;
    }

    /**
     * Get device MAC/ID (best effort).
     */
    public static String getPhoneMac(Context context) {
        if (phoneMac == null && context != null) {
            phoneMac = getSimpleMacAddress(context);
        }
        return phoneMac;
    }

    // -------------------------
    // Helpers
    // -------------------------

    private static String safeTrim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Normalizes user input into base URL:
     *  - adds http:// if scheme missing
     *  - removes trailing slashes
     *  - removes explicit :80
     *  - ensures exactly one trailing "/"
     */
    private static String normalizeBaseUrl(String address) {
        String a = safeTrim(address);
        // If scheme not specified, default to http (good for local IP testing).
        if (!a.startsWith("http://") && !a.startsWith("https://")) {
            a = "http://" + a;
        }

        // Remove trailing slashes
        while (a.endsWith("/")) {
            a = a.substring(0, a.length() - 1);
        }

        // Remove explicit :80
        a = a.replaceFirst(":80$", "");

        return a + "/";
    }

    private static String joinUrl(String baseUrl, String path) {
        String base = safeTrim(baseUrl);
        if (base == null) return null;

        String p = safeTrim(path);
        if (p == null) return base;

        // base already ends with "/"
        while (p.startsWith("/")) p = p.substring(1);
        return base + p;
    }

    /**
     * Best-effort MAC/ID for device identification.
     * NOTE: On Android 6+ real Wi-Fi MAC is often restricted and may return 02:00:00:00:00:00.
     */
    private static String getSimpleMacAddress(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) {
                Log.w(TAG, "WifiManager is null");
                return getFallbackId(context);
            }

            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo == null) {
                Log.w(TAG, "WifiInfo is null");
                return getFallbackId(context);
            }

            String mac = wifiInfo.getMacAddress();
            Log.d(TAG, "Raw MAC from WifiInfo: " + mac);

            if (mac != null && !mac.isEmpty() && !"02:00:00:00:00:00".equals(mac)) {
                return mac.toUpperCase();
            }

            Log.w(TAG, "Invalid MAC address (restricted/emulator): " + mac);
            return getFallbackId(context);

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception getting MAC: " + e.getMessage());
            return getFallbackId(context);
        } catch (Exception e) {
            Log.e(TAG, "Error getting MAC address: " + e.getMessage());
            return getFallbackId(context);
        }
    }

    /**
     * Fallback device identifier - Android ID (best effort).
     */
    private static String getFallbackId(Context context) {
        try {
            String androidId = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ANDROID_ID
            );
            if (androidId != null && !androidId.isEmpty()) {
                return "android-" + androidId;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting Android ID: " + e.getMessage());
        }

        return "android-" + System.currentTimeMillis();
    }
}
