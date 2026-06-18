package com.example.santiway;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

public class LocaleHelper {

    public static final String PREFS_NAME = "AppSettings";
    public static final String KEY_LANGUAGE = "app_language";

    public static Context wrap(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String languageCode = prefs.getString(KEY_LANGUAGE, "ru");

        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);

        // Чтобы арабский текст был арабским, но интерфейс не разворачивался справа налево
        config.setLayoutDirection(new Locale("en"));

        return context.createConfigurationContext(config);
    }

    public static void setLocale(Context context, String languageCode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE, languageCode)
                .apply();
    }

    public static String getCurrentLanguage(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, "ru");
    }
}