package com.example.santiway;

import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.santiway.opencellid.OpenCellIdSyncScheduler;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class OpenCellIdStatusActivity extends BaseLocalizedActivity {

    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.parseColor("#071427"));
        getWindow().setNavigationBarColor(Color.parseColor("#172A46"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(28), dp(18), dp(18));
        root.setBackgroundColor(Color.parseColor("#071427"));

        TextView title = new TextView(this);
        title.setText("OpenCellID");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setPadding(0, 0, 0, dp(18));
        root.addView(title);

        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(15);
        statusText.setPadding(0, 0, 0, dp(18));
        root.addView(statusText);

        Button syncButton = new Button(this);
        syncButton.setText("Запустить синхронизацию");
        syncButton.setTextColor(Color.WHITE);
        syncButton.setBackgroundColor(Color.parseColor("#172A46"));
        syncButton.setOnClickListener(v -> {
            OpenCellIdSyncScheduler.enqueueNow(this);
            statusText.setText("Синхронизация запущена. Вернись на экран позже, чтобы увидеть результат.");
        });

        root.addView(syncButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        ));

        setContentView(root);
        renderStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderStatus();
    }

    private void renderStatus() {
        File dbFile = OpenCellIdSyncScheduler.getLocalDbFile(this);
        long lastSync = OpenCellIdSyncScheduler.getLastSyncTime(this);
        int count = OpenCellIdSyncScheduler.getImportedCount(this);
        String result = OpenCellIdSyncScheduler.getLastResult(this);
        String sourceUrl = OpenCellIdSyncScheduler.getSourceUrl(this);

        String lastSyncText = lastSync > 0
                ? new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(new Date(lastSync))
                : "никогда";

        String text =
                "Файл базы: " + dbFile.getAbsolutePath() + "\n\n" +
                        "Файл существует: " + (dbFile.exists() ? "да" : "нет") + "\n" +
                        "Размер файла: " + (dbFile.exists() ? dbFile.length() : 0) + " bytes\n" +
                        "Импортировано вышек: " + count + "\n" +
                        "Последняя синхронизация: " + lastSyncText + "\n" +
                        "Последний результат: " + (result == null || result.isEmpty() ? "-" : result) + "\n\n" +
                        "Источник: " + (sourceUrl == null || sourceUrl.isEmpty()
                        ? "локальный asset opencellid_known_towers.csv или пустая база"
                        : sourceUrl);

        statusText.setText(text);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
