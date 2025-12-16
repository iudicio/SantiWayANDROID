package com.example.santiway;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button; // Добавить импорт Button
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class DeviceListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_LOADING = 1;

    // ДОБАВЛЕНО: Интерфейс для обработки нажатий
    public interface OnInfoClickListener {
        void onInfoClick(DeviceListActivity.Device device);
    }

    private List<DeviceListActivity.Device> deviceList;
    private final OnInfoClickListener listener; // ДОБАВЛЕНО: Объект слушателя

    // ИЗМЕНЕНО: Конструктор теперь принимает слушателя
    public DeviceListAdapter(List<DeviceListActivity.Device> deviceList, OnInfoClickListener listener) {
        this.deviceList = deviceList;
        this.listener = listener; // Сохраняем слушателя
    }

    // Метод для обновления данных в адаптере
    public void updateData(List<DeviceListActivity.Device> newData) {
        this.deviceList.clear(); //
        this.deviceList.addAll(newData); //
        notifyDataSetChanged(); //
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
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_device, parent, false); //
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        DeviceListActivity.Device device = deviceList.get(position); //

        holder.deviceNameTextView.setText(device.name); //
        holder.deviceTypeTextView.setText(device.type); //
        holder.deviceLocationTextView.setText(device.location); //
        holder.deviceTimeTextView.setText(device.time); //

        // ДОБАВЛЕНО: Установка обработчика нажатия на кнопку "Info"
        holder.infoButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onInfoClick(device);
            }
        });
    }

    @Override
    public int getItemCount() {
        return deviceList.size(); //
    }

    public static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView deviceNameTextView;
        TextView deviceTypeTextView;
        TextView deviceLocationTextView;
        TextView deviceTimeTextView;
        Button infoButton; // ДОБАВЛЕНО

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceNameTextView = itemView.findViewById(R.id.device_name); //
            deviceTypeTextView = itemView.findViewById(R.id.device_type); //
            deviceLocationTextView = itemView.findViewById(R.id.device_location); //
            deviceTimeTextView = itemView.findViewById(R.id.device_time); //
            infoButton = itemView.findViewById(R.id.info_button); // ДОБАВЛЕНО
        }

        public void bind(DeviceListActivity.Device device) {
            deviceNameTextView.setText(device.name != null ? device.name : "Unknown");
            deviceTypeTextView.setText(device.type != null ? device.type : "N/A");
            deviceLocationTextView.setText(device.location != null ? device.location : "N/A");
            deviceTimeTextView.setText(device.time != null ? device.time : "N/A");
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