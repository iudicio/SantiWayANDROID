package com.example.santiway;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.util.Log;

public class MainActivity extends AppCompatActivity {

    private Button startScanButton;
    private Button stopScanButton;
    private Button viewDatabaseButton;
    private static final int PERMISSION_REQUEST_CODE = 100;

    // Современный способ запроса разрешений
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    permissions -> {
                        boolean allGranted = true;
                        for (Boolean isGranted : permissions.values()) {
                            if (!isGranted) {
                                allGranted = false;
                                break;
                            }
                        }

                        if (allGranted) {
                            Log.d("MainActivity", "All permissions granted, starting scan");
                            startWifiScanning();
                        } else {
                            Log.w("MainActivity", "Some permissions were denied");
                            Toast.makeText(this, "Permissions denied. Cannot start scanning.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        startScanButton = findViewById(R.id.startScanButton);
        stopScanButton = findViewById(R.id.stopScanButton);
        viewDatabaseButton = findViewById(R.id.viewDatabaseButton);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        startScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissionsAndStartScanning();
            }
        });

        stopScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopWifiScanning();
            }
        });

        viewDatabaseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewDatabase();
            }
        });
    }

    private void checkPermissionsAndStartScanning() {
        Log.d("MainActivity", "Checking permissions");

        if (checkAllPermissions()) {
            Log.d("MainActivity", "All permissions already granted");
            startWifiScanning();
        } else {
            Log.d("MainActivity", "Requesting permissions");
            requestNecessaryPermissions();
        }
    }

    private boolean checkAllPermissions() {
        // Проверяем все необходимые разрешения
        boolean hasLocationPermission = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        boolean hasNotificationPermission = true;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }

        return hasLocationPermission && hasNotificationPermission;
    }

    private void requestNecessaryPermissions() {
        // Создаем список разрешений для запроса
        String[] permissionsToRequest;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest = new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            permissionsToRequest = new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            };
        }

        // Проверяем, нужно ли показывать объяснение
        boolean shouldShowRationale = false;
        for (String permission : permissionsToRequest) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                shouldShowRationale = true;
                break;
            }
        }

        if (shouldShowRationale) {
            // Показываем объяснение пользователю
            Toast.makeText(this,
                    "This app needs location permission to scan for Wi-Fi networks and notification permission to show background scanning status",
                    Toast.LENGTH_LONG).show();

            // Даем время прочитать сообщение, затем запрашиваем разрешения
            new android.os.Handler().postDelayed(() -> {
                requestPermissionLauncher.launch(permissionsToRequest);
            }, 2000);
        } else {
            // Сразу запрашиваем разрешения
            requestPermissionLauncher.launch(permissionsToRequest);
        }
    }

    private void startWifiScanning() {
        Log.d("MainActivity", "Start button clicked - starting service");
        Intent serviceIntent = new Intent(this, WifiForegroundService.class);
        serviceIntent.setAction("START_SCAN");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Toast.makeText(this, "Wi-Fi scanning started", Toast.LENGTH_SHORT).show();
    }

    private void stopWifiScanning() {
        Log.d("MainActivity", "Stop button clicked");
        Intent serviceIntent = new Intent(this, WifiForegroundService.class);
        serviceIntent.setAction("STOP_SCAN");
        startService(serviceIntent);
        Toast.makeText(this, "Wi-Fi scanning stopped", Toast.LENGTH_SHORT).show();
    }

    // Обработка результата запроса разрешений (для обратной совместимости)
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                startWifiScanning();
            } else {
                Toast.makeText(this, "Permissions denied. Cannot start scanning.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void viewDatabase() {
        Intent intent = new Intent(this, DatabaseViewActivity.class);
        startActivity(intent);
    }
}