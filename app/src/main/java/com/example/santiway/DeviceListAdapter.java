package com.example.santiway;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

public class DeviceListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_LOADING = 1;

    private List<DeviceListActivity.Device> deviceList;
    private Context context;
    private boolean showLoading = false;
    private boolean isFirstLoad = true;

    public interface OnDeviceClickListener {
        void onDeviceClick(DeviceListActivity.Device device, String tableName, int position);
    }

    private OnDeviceClickListener deviceClickListener;
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

    // КЛАСС ДЛЯ ОБЫЧНОГО УСТРОЙСТВА
    public static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView deviceNameTextView, deviceTypeTextView, deviceLocationTextView, deviceTimeTextView;
        Button infoButton;
        View statusBar;
        ImageButton shareButton;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceNameTextView = itemView.findViewById(R.id.device_name);
            deviceTypeTextView = itemView.findViewById(R.id.device_type);
            deviceLocationTextView = itemView.findViewById(R.id.device_location);
            deviceTimeTextView = itemView.findViewById(R.id.device_time);
            infoButton = itemView.findViewById(R.id.info_button);
            statusBar = itemView.findViewById(R.id.device_status_bar);
            shareButton = itemView.findViewById(R.id.share_button);
        }

        public void bind(DeviceListActivity.Device device, OnDeviceClickListener listener, String tableName, int position) {
            deviceNameTextView.setText(device.getName() != null ? device.getName() : "Unknown");
            deviceTypeTextView.setText(device.getType() != null ? device.getType() : "N/A");
            deviceLocationTextView.setText(device.getLocation() != null ? device.getLocation() : "N/A");
            deviceTimeTextView.setText(device.getTime() != null ? device.getTime() : "N/A");

            // Клик по всей карточке -> открываем карту/инфо
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeviceClick(device, tableName, position);
                }
            });

            // Клик по кнопке SHARE -> экспорт JSON
            if (shareButton != null) {
                shareButton.setOnClickListener(v -> {
                    Context viewContext = v.getContext();
                    if (viewContext instanceof DeviceListActivity) {
                        ((DeviceListActivity) viewContext).shareDeviceAsJson(device);
                    }
                });
            }

            // Логика индикатора статуса
            if (statusBar != null) {
                statusBar.clearAnimation();
                String status = (device.getStatus() != null) ? device.getStatus().trim().toUpperCase() : "SCANNED";

                if (status.equals("SAFE")) {
                    statusBar.setBackgroundColor(Color.parseColor("#3DDC84"));
                } else if (status.equals("ALARM")) {
                    statusBar.setBackgroundColor(Color.parseColor("#64B5F6"));
                } else {
                    statusBar.setBackgroundColor(Color.parseColor("#FF6B6B"));
                    AlphaAnimation pulse = new AlphaAnimation(1.0f, 0.3f);
                    pulse.setDuration(800);
                    pulse.setRepeatMode(Animation.REVERSE);
                    pulse.setRepeatCount(Animation.INFINITE);
                    statusBar.startAnimation(pulse);
                }
            }

            if (infoButton != null) {
                infoButton.setOnClickListener(v -> {
                    if (listener != null) listener.onDeviceClick(device, tableName, position);
                });
            }
        }
    }

    // КЛАСС ДЛЯ ЗАГРУЗКИ (НИЗ СПИСКА)
    public static class LoadingViewHolder extends RecyclerView.ViewHolder {
        ProgressBar progressBar;
        public LoadingViewHolder(@NonNull View itemView) {
            super(itemView);
            progressBar = itemView.findViewById(R.id.progress_bar);
        }
    }
}