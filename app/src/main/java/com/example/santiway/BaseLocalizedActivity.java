package com.example.santiway;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class BaseLocalizedActivity extends AppCompatActivity {

    private String currentLanguage;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentLanguage = LocaleHelper.getCurrentLanguage(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        String newLanguage = LocaleHelper.getCurrentLanguage(this);

        if (currentLanguage != null && !currentLanguage.equals(newLanguage)) {
            currentLanguage = newLanguage;
            recreate();
        }
    }
}
