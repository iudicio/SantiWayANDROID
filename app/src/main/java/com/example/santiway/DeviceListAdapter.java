package com.example.santiway;

import android.content.Context;
import android.graphics.Color;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DeviceListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_LOADING = 1;

    private final List<DeviceListActivity.Device> deviceList;
    private final Context context;
    private boolean showLoading = false;
    private boolean isFirstLoad = true;

    public interface OnDeviceClickListener {
        void onDeviceClick(DeviceListActivity.Device device, String tableName, int position);
    }

    private final OnDeviceClickListener deviceClickListener;
    private String currentTableName;

    public DeviceListAdapter(List<DeviceListActivity.Device> deviceList, Context context,
                             OnDeviceClickListener listener) {
        this.deviceList = new ArrayList<>(deviceList);
        this.context = context;
        this.deviceClickListener = listener;
    }

    public void setCurrentTableName(String tableName) {
        this.currentTableName = tableName;
    }

    public void updateData(List<DeviceListActivity.Device> newData) {
        this.deviceList.clear();
        this.deviceList.addAll(newData);
        isFirstLoad = false;
        notifyDataSetChanged();
    }

    public void addData(List<DeviceListActivity.Device> newData) {
        int startPosition = deviceList.size();
        deviceList.addAll(newData);
        notifyItemRangeInserted(startPosition, newData.size());
    }

    public void clearData() {
        deviceList.clear();
        showLoading = false;
        isFirstLoad = true;
        notifyDataSetChanged();
    }

    public void showLoading(boolean isFirstLoad) {
        this.isFirstLoad = isFirstLoad;
        if (!showLoading) {
            showLoading = true;
            if (isFirstLoad) {
                notifyDataSetChanged();
            } else {
                notifyItemInserted(deviceList.size());
            }
        }
    }

    public void hideLoading() {
        if (showLoading) {
            showLoading = false;
            if (isFirstLoad) {
                notifyDataSetChanged();
            } else {
                notifyItemRemoved(deviceList.size());
            }
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_ITEM) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_device, parent, false);
            return new DeviceViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_loading, parent, false);
            return new LoadingViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof DeviceViewHolder) {
            DeviceListActivity.Device device = deviceList.get(position);
            ((DeviceViewHolder) holder).bind(device, deviceClickListener, currentTableName, position);
        }
    }

    @Override
    public int getItemCount() {
        return deviceList.size() + (showLoading ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        if (showLoading && position == deviceList.size()) {
            return VIEW_TYPE_LOADING;
        }
        return VIEW_TYPE_ITEM;
    }

    public static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView deviceNameTextView;
        TextView deviceMacTextView;
        TextView deviceTypeTextView;
        TextView deviceLocationTextView;
        TextView deviceTimeTextView;
        Button infoButton;
        View statusBar;
        ImageButton shareButton;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceNameTextView = itemView.findViewById(R.id.device_name);
            deviceMacTextView = itemView.findViewById(R.id.device_mac);
            deviceTypeTextView = itemView.findViewById(R.id.device_type);
            deviceTimeTextView = itemView.findViewById(R.id.device_time);
            infoButton = itemView.findViewById(R.id.info_button);
            statusBar = itemView.findViewById(R.id.device_status_bar);
            shareButton = itemView.findViewById(R.id.share_button);
        }

        private String formatRelativeTime(long diffMillis) {
            long minutes = diffMillis / (60 * 1000);
            long hours = diffMillis / (60 * 60 * 1000);
            long days = diffMillis / (24 * 60 * 60 * 1000);
            long months = days / 30;

            if (minutes < 60) return Math.max(minutes, 1) + " Мин.";
            if (hours < 24) return hours + " Ч.";
            if (days < 30) return days + " Д.";
            return Math.max(months, 1) + " М.";
        }

        private String formatRelativeTime(String source) {
            if (source == null || source.trim().isEmpty()) return "N/A";

            return source.trim()
                    .replace("минут", "Мин.")
                    .replace("минута", "Мин.")
                    .replace("минуту", "Мин.")
                    .replace("мин", "Мин.")
                    .replace("час", "Ч.")
                    .replace("часа", "Ч.")
                    .replace("часов", "Ч.")
                    .replace("день", "Д.")
                    .replace("дня", "Д.")
                    .replace("дней", "Д.")
                    .replace("месяц", "М.")
                    .replace("месяца", "М.")
                    .replace("месяцев", "М.");
        }

        public void bind(DeviceListActivity.Device device, OnDeviceClickListener listener, String tableName, int position) {
            String deviceName = device.getName() != null ? device.getName().trim() : "Unknown";
            if (deviceName.length() > 30) {
                deviceName = deviceName.substring(0, 30).trim() + "...";
            }
            deviceNameTextView.setText(deviceName);

            String deviceMac = device.getMac() != null ? device.getMac().trim() : "";
            if (deviceMacTextView != null) {
                if (deviceMac.isEmpty()) {
                    deviceMacTextView.setVisibility(View.GONE);
                } else {
                    deviceMacTextView.setVisibility(View.VISIBLE);
                    deviceMacTextView.setText(deviceMac);
                }
            }

            deviceTypeTextView.setText(device.getType() != null ? device.getType() : "N/A");

            if (device.getTimestamp() > 0) {
                long diff = System.currentTimeMillis() - device.getTimestamp();
                deviceTimeTextView.setText(formatRelativeTime(diff));
            } else {
                deviceTimeTextView.setText(formatRelativeTime(device.getTime()));
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeviceClick(device, tableName, position);
                }
            });

            if (shareButton != null) {
                shareButton.setOnClickListener(v -> {
                    Context viewContext = v.getContext();
                    if (viewContext instanceof DeviceListActivity) {
                        ((DeviceListActivity) viewContext).shareDeviceAsJson(device);
                    }
                });
            }

            if (statusBar != null) {
                statusBar.clearAnimation();

                String status = device.getStatus() != null
                        ? device.getStatus().trim().toUpperCase()
                        : "GREY";

                switch (status) {
                    case "TARGET":
                        statusBar.setBackgroundColor(Color.parseColor("#FF3B30"));

                        AlphaAnimation pulse = new AlphaAnimation(1.0f, 0.3f);
                        pulse.setDuration(800);
                        pulse.setRepeatMode(Animation.REVERSE);
                        pulse.setRepeatCount(Animation.INFINITE);
                        statusBar.startAnimation(pulse);
                        break;

                    case "SAFE":
                        statusBar.setBackgroundColor(Color.parseColor("#34C759"));
                        break;

                    case "GREY":
                    default:
                        statusBar.setBackgroundColor(Color.parseColor("#808080"));
                        break;
                }
            }

            if (infoButton != null) {
                infoButton.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onDeviceClick(device, tableName, position);
                    }
                });
            }
        }
    }

    public static class LoadingViewHolder extends RecyclerView.ViewHolder {
        ProgressBar progressBar;

        public LoadingViewHolder(@NonNull View itemView) {
            super(itemView);
            progressBar = itemView.findViewById(R.id.progress_bar);
        }
    }
}