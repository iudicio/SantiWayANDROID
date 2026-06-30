package com.example.santiway;

import android.graphics.Color;
import android.widget.Button;

/**
 * Единая стилизация кнопок AlertDialog.
 * Нужна для диалогов создания/удаления/переименования папок,
 * настроек ESP32, настроек приложения и других экранов.
 */
public final class DialogStyleUtils {
    private static final int POSITIVE_COLOR = Color.parseColor("#3DDC84");
    private static final int NEGATIVE_COLOR = Color.parseColor("#FF6B6B");
    private static final int NEUTRAL_COLOR = Color.WHITE;

    private DialogStyleUtils() {
    }

    public static void tintButtons(android.app.AlertDialog dialog) {
        if (dialog == null) return;

        tint(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE), POSITIVE_COLOR);
        tint(dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE), NEGATIVE_COLOR);
        tint(dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL), NEUTRAL_COLOR);
    }

    public static void tintButtons(androidx.appcompat.app.AlertDialog dialog) {
        if (dialog == null) return;

        tint(dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE), POSITIVE_COLOR);
        tint(dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE), NEGATIVE_COLOR);
        tint(dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL), NEUTRAL_COLOR);
    }

    private static void tint(Button button, int color) {
        if (button == null) return;
        button.setTextColor(color);
        button.setAllCaps(false);
    }
}
