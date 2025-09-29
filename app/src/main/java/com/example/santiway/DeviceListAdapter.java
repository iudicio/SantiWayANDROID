package com.example.santiway;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder> {

    private List<DeviceListActivity.Device> deviceList;

    public DeviceListAdapter(List<DeviceListActivity.Device> deviceList) {
        this.deviceList = deviceList;
    }

    // Метод для обновления данных в адаптере
    public void updateData(List<DeviceListActivity.Device> newData) {
        this.deviceList.clear();
        this.deviceList.addAll(newData);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        DeviceListActivity.Device device = deviceList.get(position);
        holder.deviceNameTextView.setText(device.name);
        holder.deviceTypeTextView.setText(device.type);
        holder.deviceLocationTextView.setText(device.location);
        holder.deviceTimeTextView.setText(device.time);
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    public static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView deviceNameTextView;
        TextView deviceTypeTextView;
        TextView deviceLocationTextView;
        TextView deviceTimeTextView;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceNameTextView = itemView.findViewById(R.id.device_name);
            deviceTypeTextView = itemView.findViewById(R.id.device_type);
            deviceLocationTextView = itemView.findViewById(R.id.device_location);
            deviceTimeTextView = itemView.findViewById(R.id.device_time);
        }
    }
}