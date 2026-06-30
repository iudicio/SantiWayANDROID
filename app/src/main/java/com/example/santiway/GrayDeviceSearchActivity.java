package com.example.santiway;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class GrayDeviceSearchActivity extends BaseLocalizedActivity {

    public static final String EXTRA_SOURCE_FOLDER = "extra_source_folder";

    public static final String EXTRA_FIRST_HAS_PERIOD = "extra_first_has_period";
    public static final String EXTRA_FIRST_START = "extra_first_start";
    public static final String EXTRA_FIRST_END = "extra_first_end";

    public static final String EXTRA_SECOND_HAS_PERIOD = "extra_second_has_period";
    public static final String EXTRA_SECOND_START = "extra_second_start";
    public static final String EXTRA_SECOND_END = "extra_second_end";

    public static final String EXTRA_SECOND_FOLDERS = "extra_second_folders";

    private String sourceFolder;
    private boolean firstHasPeriod;
    private long firstStart;
    private long firstEnd;
    private boolean secondHasPeriod;
    private long secondStart;
    private long secondEnd;
    private ArrayList<String> secondFolders;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        readExtras();
        buildUi();
    }

    private void readExtras() {
        Intent intent = getIntent();

        sourceFolder = intent.getStringExtra(EXTRA_SOURCE_FOLDER);

        firstHasPeriod = intent.getBooleanExtra(EXTRA_FIRST_HAS_PERIOD, false);
        firstStart = intent.getLongExtra(EXTRA_FIRST_START, -1L);
        firstEnd = intent.getLongExtra(EXTRA_FIRST_END, -1L);

        secondHasPeriod = intent.getBooleanExtra(EXTRA_SECOND_HAS_PERIOD, false);
        secondStart = intent.getLongExtra(EXTRA_SECOND_START, -1L);
        secondEnd = intent.getLongExtra(EXTRA_SECOND_END, -1L);

        secondFolders = intent.getStringArrayListExtra(EXTRA_SECOND_FOLDERS);
        if (secondFolders == null) {
            secondFolders = new ArrayList<>();
        }
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Color.parseColor("#071427"));
        getWindow().setNavigationBarColor(Color.parseColor("#172A46"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(28), dp(18), dp(18));
        root.setBackgroundColor(Color.parseColor("#071427"));

        TextView title = new TextView(this);
        title.setText("Поиск серых устройств");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setPadding(0, 0, 0, dp(18));
        root.addView(title);

        TextView info = new TextView(this);
        info.setTextColor(Color.WHITE);
        info.setTextSize(15);
        info.setText(buildInfoText());
        root.addView(info);

        Button closeButton = new Button(this);
        closeButton.setText("Назад");
        closeButton.setTextColor(Color.WHITE);
        closeButton.setBackgroundColor(Color.parseColor("#172A46"));
        closeButton.setOnClickListener(v -> finish());

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        btnParams.setMargins(0, dp(20), 0, 0);
        root.addView(closeButton, btnParams);

        setContentView(root);
    }

    private String buildInfoText() {
        StringBuilder sb = new StringBuilder();

        sb.append("Исходная папка: ")
                .append(sourceFolder == null || sourceFolder.isEmpty() ? "-" : sourceFolder)
                .append("\n\n");

        sb.append("Первый период: ")
                .append(firstHasPeriod ? "включён" : "выключен")
                .append("\n");

        if (firstHasPeriod) {
            sb.append("Начало: ").append(formatTime(firstStart)).append("\n");
            sb.append("Конец: ").append(formatTime(firstEnd)).append("\n");
        }

        sb.append("\nВторой период: ")
                .append(secondHasPeriod ? "включён" : "выключен")
                .append("\n");

        if (secondHasPeriod) {
            sb.append("Начало: ").append(formatTime(secondStart)).append("\n");
            sb.append("Конец: ").append(formatTime(secondEnd)).append("\n");
        }

        sb.append("\nПапки сравнения:\n");

        if (secondFolders.isEmpty()) {
            sb.append("-");
        } else {
            for (String folder : secondFolders) {
                sb.append("• ").append(folder).append("\n");
            }
        }

        sb.append("\nЭкран создан для восстановления сборки и дальнейшего расширения логики поиска GREY-устройств.");

        return sb.toString();
    }

    private String formatTime(long value) {
        if (value <= 0) return "-";

        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}