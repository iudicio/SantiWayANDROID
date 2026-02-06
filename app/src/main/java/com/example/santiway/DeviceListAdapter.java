package com.example.santiway;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

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
    private String currentTableName; // Поле для текущей таблицы

    public DeviceListAdapter(List<DeviceListActivity.Device> deviceList, Context context,
                             OnDeviceClickListener listener) {
        this.deviceList = new ArrayList<>(deviceList);
        this.context = context;
        this.deviceClickListener = listener;
    }

    // Метод для установки текущей таблицы
    public void setCurrentTableName(String tableName) {
        this.currentTableName = tableName;
    }

    // Метод для обновления данных в адаптере
    public void updateData(List<DeviceListActivity.Device> newData) {
        this.deviceList.clear();
        this.deviceList.addAll(newData);
        isFirstLoad = false;
        notifyDataSetChanged();
    }

    // Метод для добавления данных (пагинация)
    public void addData(List<DeviceListActivity.Device> newData) {
        int startPosition = deviceList.size();
        deviceList.addAll(newData);
        notifyItemRangeInserted(startPosition, newData.size());
    }

    // Очистить все данные
    public void clearData() {
        deviceList.clear();
        showLoading = false;
        isFirstLoad = true;
        notifyDataSetChanged();
    }

    // Показать/скрыть индикатор загрузки
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
            return new DeviceViewHolder(view, deviceClickListener);
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
        } else if (holder instanceof LoadingViewHolder) {
            ((LoadingViewHolder) holder).bind();
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
        TextView deviceTypeTextView;
        TextView deviceLocationTextView;
        TextView deviceTimeTextView;
        Button infoButton; // Добавим кнопку Info

        public DeviceViewHolder(@NonNull View itemView, OnDeviceClickListener listener) {
            super(itemView);
            deviceNameTextView = itemView.findViewById(R.id.device_name);
            deviceTypeTextView = itemView.findViewById(R.id.device_type);
            deviceLocationTextView = itemView.findViewById(R.id.device_location);
            deviceTimeTextView = itemView.findViewById(R.id.device_time);
            infoButton = itemView.findViewById(R.id.info_button); // Находим кнопку

            // Обработчик клика на кнопку Info
            if (infoButton != null) {
                infoButton.setOnClickListener(v -> {
                    if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                        // Нужно передать таблицу и позицию - это сделаем в onBindViewHolder
                    }
                });
            }
        }

        public void bind(DeviceListActivity.Device device, OnDeviceClickListener listener, String tableName, int position) {
            deviceNameTextView.setText(device.name != null ? device.name : "Unknown");
            deviceTypeTextView.setText(device.type != null ? device.type : "N/A");
            deviceLocationTextView.setText(device.location != null ? device.location : "N/A");
            deviceTimeTextView.setText(device.time != null ? device.time : "N/A");

            // Устанавливаем обработчик для кнопки Info
            if (infoButton != null) {
                infoButton.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onDeviceClick(device, tableName, position);
                    }
                });
            }

            // Также делаем кликабельной всю карточку
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeviceClick(device, tableName, position);
                }
            });
        }
    }

    public static class LoadingViewHolder extends RecyclerView.ViewHolder {
        ProgressBar progressBar;

        public LoadingViewHolder(@NonNull View itemView) {
            super(itemView);
            progressBar = itemView.findViewById(R.id.progress_bar);
        }

        public void bind() {
            // Прогресс бар уже отображается
        }
    }
}