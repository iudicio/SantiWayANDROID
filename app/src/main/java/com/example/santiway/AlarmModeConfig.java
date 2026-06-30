package com.example.santiway;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/**
 * Конфигурация ALARM-режимов.
 *
 * Каналы:
 * 1) CHANNEL_TRACKING_DEVICES — подозрительные перемещения/трекеры.
 * 2) CHANNEL_CELL_TOWERS — неизвестные/подозрительные сотовые вышки.
 * 3) CHANNEL_MARKED_TARGETS — устройства, уже помеченные как TARGET.
 */
public final class AlarmModeConfig {
    private static final String PREFS_NAME = "AppSettings";

    private static final String KEY_ALARM_MODE = "alarm_mode";
    private static final String KEY_QUIET_MODE = "alarm_quiet_mode_enabled";
    private static final String KEY_QUIET_INCLUDE_CELL_TOWERS = "alarm_quiet_include_cell_towers";

    public static final int MODE_OFF = 0;

    public static final int CHANNEL_TRACKING_DEVICES = 1;
    public static final int CHANNEL_CELL_TOWERS = 1 << 1;
    public static final int CHANNEL_MARKED_TARGETS = 1 << 2;

    public static final int MODE_ALL =
            CHANNEL_TRACKING_DEVICES |
                    CHANNEL_CELL_TOWERS |
                    CHANNEL_MARKED_TARGETS;

    private AlarmModeConfig() {
    }

    public static int getMode(Context context) {
        if (context == null) return MODE_OFF;

        int mode = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_ALARM_MODE, MODE_OFF);

        return sanitizeMask(mode);
    }

    public static void saveMode(Context context, int mode) {
        if (context == null) return;

        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_ALARM_MODE, sanitizeMask(mode))
                .apply();
    }

    public static int sanitizeMask(int mode) {
        return mode & MODE_ALL;
    }

    public static boolean isTrackingDeviceAlarmEnabled(Context context) {
        return (getMode(context) & CHANNEL_TRACKING_DEVICES) != 0;
    }

    public static boolean isCellTowerAlarmEnabled(Context context) {
        return (getMode(context) & CHANNEL_CELL_TOWERS) != 0;
    }

    public static boolean isMarkedTargetAlarmEnabled(Context context) {
        return (getMode(context) & CHANNEL_MARKED_TARGETS) != 0;
    }

    public static boolean isQuietModeEnabled(Context context) {
        if (context == null) return false;

        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_QUIET_MODE, false);
    }

    public static void saveQuietModeEnabled(Context context, boolean enabled) {
        if (context == null) return;

        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_QUIET_MODE, enabled)
                .apply();
    }

    public static boolean isQuietModeIncludeCellTowers(Context context) {
        if (context == null) return false;

        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_QUIET_INCLUDE_CELL_TOWERS, false);
    }

    public static void saveQuietModeIncludeCellTowers(Context context, boolean enabled) {
        if (context == null) return;

        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_QUIET_INCLUDE_CELL_TOWERS, enabled)
                .apply();
    }

    public static int bellColorForMode(int mode) {
        mode = sanitizeMask(mode);

        if (mode == MODE_OFF) {
            return Color.parseColor("#6F839C");
        }

        if (mode == CHANNEL_TRACKING_DEVICES) {
            return Color.parseColor("#2D8CFF");
        }

        if (mode == CHANNEL_CELL_TOWERS) {
            return Color.parseColor("#F5C542");
        }

        if (mode == CHANNEL_MARKED_TARGETS) {
            return Color.parseColor("#FF3B30");
        }

        if (mode == MODE_ALL) {
            return Color.parseColor("#FF9800");
        }

        return Color.parseColor("#9C27B0");
    }
}
