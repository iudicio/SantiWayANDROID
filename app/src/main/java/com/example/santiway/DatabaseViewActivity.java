package com.example.santiway;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseViewActivity extends AppCompatActivity {
    private TextView dbInfoTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_database_view);

        dbInfoTextView = findViewById(R.id.dbInfoTextView);
        displayDatabaseInfo();
    }

    private void displayDatabaseInfo() {
        DatabaseHelper dbHelper = new DatabaseHelper(this);

        try {
            int deviceCount = dbHelper.getDevicesCount();
            List<WifiDevice> devices = dbHelper.getAllWifiDevices();

            StringBuilder sb = new StringBuilder();
            sb.append("Total devices: ").append(deviceCount).append("\n\n");

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

            for (WifiDevice device : devices) {
                sb.append("SSID: ").append(device.getSsid() != null ? device.getSsid() : "Hidden").append("\n");
                sb.append("BSSID: ").append(device.getBssid()).append("\n");
                sb.append("Signal: ").append(device.getSignalStrength()).append("dBm\n");
                sb.append("Frequency: ").append(device.getFrequency()).append("MHz\n");
                sb.append("Vendor: ").append(device.getVendor()).append("\n");

                // Безопасное преобразование времени
                String timeStr = sdf.format(new Date(device.getTimestamp()));
                sb.append("Time: ").append(timeStr).append("\n");
                sb.append("------------------------\n");
            }

            if (devices.isEmpty()) {
                sb.append("No devices found in database.\n");
                sb.append("Make sure Wi-Fi scanning is running and devices are detected.");
            }

            dbInfoTextView.setText(sb.toString());

        } catch (Exception e) {
            Log.e("DatabaseView", "Error reading database: " + e.getMessage());
            dbInfoTextView.setText("Error reading database: " + e.getMessage() +
                    "\n\nPlease make sure Wi-Fi scanning has been running.");
        } finally {
            dbHelper.close();
        }
    }
}