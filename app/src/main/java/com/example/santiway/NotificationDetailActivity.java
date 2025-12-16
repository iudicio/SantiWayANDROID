package com.example.santiway;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class NotificationDetailActivity extends AppCompatActivity implements OnMapReadyCallback {

    private NotificationData notification;
    private SupportMapFragment mapFragment;
    private static final String TAG = "NotifDetailActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_detail);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Детали уведомления");
        }

        notification = (NotificationData) getIntent().getSerializableExtra("notification_data");

        if (notification == null) {
            Toast.makeText(this, "Ошибка загрузки данных", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        displayNotificationData();
    }

    private void displayNotificationData() {
        TextView title = findViewById(R.id.detail_title);
        TextView text = findViewById(R.id.detail_text);
        TextView timestamp = findViewById(R.id.detail_timestamp);
        LinearLayout binaryContainer = findViewById(R.id.detail_binary_container);
        View mapCard = findViewById(R.id.map_card);

        title.setText(notification.getTitle());
        text.setText(notification.getText());

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        timestamp.setText("Дата и время: " + dateFormat.format(notification.getTimestamp()));

        // --- Обработка Бинарного контента ---
        if (notification.getBinaryContents() != null && !notification.getBinaryContents().isEmpty()) {

            // Убеждаемся, что метка видна
            findViewById(R.id.binary_label).setVisibility(View.VISIBLE);

            for (int i = 0; i < notification.getBinaryContents().size(); i++) {

                // --- Создаем эффективно конечные копии переменных для лямбды ---
                final byte[] finalContent = notification.getBinaryContents().get(i);
                final String finalMimeType = notification.getBinaryMimeTypes().get(i);
                final int finalIndex = i;
                // ------------------------------------------------------------------

                // Дополнительное логирование для отладки
                Log.d(TAG, "Processing file: Type=" + finalMimeType + ", Size=" + finalContent.length);

                if (finalMimeType.startsWith("image/")) {
                    // Отображение картинки
                    ImageView imageView = new ImageView(this);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 500);
                    params.bottomMargin = 16;
                    imageView.setLayoutParams(params);
                    imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    binaryContainer.addView(imageView);

                    // Используем Glide
                    Glide.with(this)
                            .load(finalContent)
                            .into(imageView);

                } else if (finalMimeType.equals("application/vnd.android.package-archive")) {
                    // Кнопка для установки APK
                    Button installButton = new Button(this);
                    installButton.setText("Установить APK");
                    installButton.setTextColor(getResources().getColor(android.R.color.white));
                    installButton.setBackgroundColor(notification.getType().getColor());
                    binaryContainer.addView(installButton);

                    // Исправленная лямбда-функция
                    installButton.setOnClickListener(v -> installApk(
                            finalContent,
                            notification.getTitle() + "_" + finalIndex + ".apk"
                    ));

                } else {
                    // !!! УСИЛЕННЫЙ БЛОК ДЛЯ ДИАГНОСТИКИ !!!
                    // Общий случай: если тип не совпал, отображаем текстовое предупреждение
                    TextView fileInfo = new TextView(this);
                    fileInfo.setText(String.format(
                            "Файл: %s (%d байт). Тип не распознан для прямого отображения. Если этот текст виден, MIME-тип не соответствует 'image/*' или 'application/vnd.android.package-archive'.",
                            finalMimeType,
                            finalContent.length
                    ));
                    fileInfo.setTextColor(getResources().getColor(android.R.color.white));
                    fileInfo.setPadding(0, 16, 0, 16);
                    binaryContainer.addView(fileInfo);
                    Log.w(TAG, "Unrecognized MIME type: " + finalMimeType);
                }
            }
        } else {
            // Если данных нет, метка скрывается
            findViewById(R.id.binary_label).setVisibility(View.GONE);
        }


        // --- Обработка Координат и Карты ---
        if (notification.getLatitude() != null && notification.getLongitude() != null) {
            mapCard.setVisibility(View.VISIBLE);
            mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
            if (mapFragment != null) {
                mapFragment.getMapAsync(this);
            }
        } else {
            mapCard.setVisibility(View.GONE);
        }
    }

    private void installApk(byte[] apkBytes, String fileName) {
        try {
            // 1. Сохраняем байты в файл
            File file = new File(getExternalFilesDir(null), fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(apkBytes);
                fos.flush();
            }

            // 2. Получаем Uri с помощью FileProvider
            Uri apkUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", file);

            // 3. Создаем Intent для установки
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(apkUri, "application/vnd.android.package-archive");
            install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(install);

        } catch (IOException e) {
            Log.e(TAG, "Error saving/installing APK", e);
            Toast.makeText(this, "Ошибка при установке APK: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (notification.getLatitude() != null && notification.getLongitude() != null) {
            LatLng location = new LatLng(notification.getLatitude(), notification.getLongitude());
            googleMap.addMarker(new MarkerOptions().position(location).title(notification.getTitle()));
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}