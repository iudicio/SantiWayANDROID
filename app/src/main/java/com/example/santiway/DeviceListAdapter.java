package com.example.santiway;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button; // Добавить импорт Button
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder> {

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
    }
}