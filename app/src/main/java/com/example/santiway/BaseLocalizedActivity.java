package com.example.santiway;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class BaseLocalizedActivity extends AppCompatActivity {

    private static final int STATUS_BAR_COLOR = Color.parseColor("#071427");
    private static final int NAVIGATION_BAR_COLOR = Color.parseColor("#172A46");

    private String currentLanguage;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentLanguage = LocaleHelper.getCurrentLanguage(this);
        applySystemBarTheme();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applySystemBarTheme();

        String newLanguage = LocaleHelper.getCurrentLanguage(this);

        if (currentLanguage != null && !currentLanguage.equals(newLanguage)) {
            currentLanguage = newLanguage;
            recreate();
        }
    }

    private void applySystemBarTheme() {
        Window window = getWindow();
        if (window == null) return;

        window.setStatusBarColor(STATUS_BAR_COLOR);
        window.setNavigationBarColor(NAVIGATION_BAR_COLOR);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decorView = window.getDecorView();
            int flags = decorView.getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decorView.setSystemUiVisibility(flags);
        }
    }
}
