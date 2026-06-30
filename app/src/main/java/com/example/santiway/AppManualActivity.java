package com.example.santiway;

import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

public class AppManualActivity extends BaseLocalizedActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.parseColor("#071427"));
        getWindow().setNavigationBarColor(Color.parseColor("#172A46"));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#071427"));

        TextView text = new TextView(this);
        text.setTextColor(Color.WHITE);
        text.setTextSize(15);
        text.setLineSpacing(dp(2), 1.08f);
        text.setPadding(dp(18), dp(28), dp(18), dp(28));

        text.setText(buildManualText());

        scrollView.addView(text, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(scrollView);
    }

    private String buildManualText() {
        StringBuilder sb = new StringBuilder();

        appendMainHeader(sb);

        appendSection(
                sb,
                R.string.manual_quick_start_title,
                R.string.manual_quick_start_body,
                R.string.manual_quick_start_detail
        );

        appendSection(
                sb,
                R.string.manual_scan_title,
                R.string.manual_scan_body,
                R.string.manual_scan_detail
        );

        appendSection(
                sb,
                R.string.manual_devices_title,
                R.string.manual_devices_body,
                R.string.manual_devices_detail
        );

        appendSection(
                sb,
                R.string.manual_map_title,
                R.string.manual_map_body,
                R.string.manual_map_detail
        );

        appendSection(
                sb,
                R.string.manual_esp32_title,
                R.string.manual_esp32_body,
                R.string.manual_esp32_detail
        );

        appendSection(
                sb,
                R.string.manual_esp32_map_title,
                R.string.manual_esp32_map_body,
                R.string.manual_esp32_map_detail
        );

        appendSection(
                sb,
                R.string.manual_firmware_title,
                R.string.manual_firmware_body,
                R.string.manual_firmware_detail
        );

        appendSection(
                sb,
                R.string.manual_alarm_title,
                R.string.manual_alarm_body,
                R.string.manual_alarm_detail
        );

        appendSection(
                sb,
                R.string.manual_notifications_title,
                R.string.manual_notifications_body,
                R.string.manual_notifications_detail
        );

        appendSection(
                sb,
                R.string.manual_opencellid_title,
                R.string.manual_opencellid_body,
                R.string.manual_opencellid_detail
        );

        appendSection(
                sb,
                R.string.manual_sync_title,
                R.string.manual_sync_body,
                R.string.manual_sync_detail
        );

        appendSection(
                sb,
                R.string.manual_background_title,
                R.string.manual_background_body,
                0
        );

        appendSection(
                sb,
                R.string.manual_databases_title,
                R.string.manual_databases_body,
                0
        );

        appendSection(
                sb,
                R.string.manual_limits_title,
                R.string.manual_limits_body,
                0
        );

        appendSection(
                sb,
                R.string.manual_settings_title,
                R.string.manual_settings_body,
                R.string.manual_settings_detail
        );

        appendSection(
                sb,
                R.string.manual_troubleshooting_title,
                R.string.manual_troubleshooting_body,
                R.string.manual_troubleshooting_detail
        );

        return sb.toString().trim();
    }

    private void appendMainHeader(StringBuilder sb) {
        appendIfNotEmpty(sb, getString(R.string.manual_title));
        appendBlankLine(sb);

        appendIfNotEmpty(sb, getString(R.string.manual_heading));
        appendBlankLine(sb);

        appendIfNotEmpty(sb, getString(R.string.manual_intro));
        appendDoubleBlankLine(sb);
    }

    private void appendSection(StringBuilder sb, int titleRes, int bodyRes, int detailRes) {
        appendIfNotEmpty(sb, getString(titleRes));
        appendBlankLine(sb);

        appendIfNotEmpty(sb, getString(bodyRes));

        if (detailRes != 0) {
            appendDoubleBlankLine(sb);
            appendIfNotEmpty(sb, getString(detailRes));
        }

        appendDoubleBlankLine(sb);
    }

    private void appendIfNotEmpty(StringBuilder sb, String value) {
        if (TextUtils.isEmpty(value)) return;
        sb.append(value.trim());
    }

    private void appendBlankLine(StringBuilder sb) {
        sb.append("\n");
    }

    private void appendDoubleBlankLine(StringBuilder sb) {
        sb.append("\n\n");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}