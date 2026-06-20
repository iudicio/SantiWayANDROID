package com.example.santiway.esp32;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class Esp32BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        try (Esp32DatabaseHelper db = new Esp32DatabaseHelper(context)) {
            if (db.getAutoConnectMacs().isEmpty()) return;
        }
        Intent service = new Intent(context, Esp32ConnectionService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service);
        else context.startService(service);
    }
}
