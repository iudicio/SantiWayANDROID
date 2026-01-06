// File: com.example.santiway.notifications.NotificationsAdapter.java
package com.example.santiway;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.santiway.R;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder> {

    private final List<NotificationData> notificationList;
    private final Context context;
    private final NotificationActionListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());

    public interface NotificationActionListener {
        void onNotificationClicked(NotificationData notification);
        void onDeleteClicked(NotificationData notification);
    }

    public NotificationsAdapter(Context context, List<NotificationData> notificationList, NotificationActionListener listener) {
        this.context = context;
        this.notificationList = notificationList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        NotificationData notification = notificationList.get(position);

        holder.titleTextView.setText(notification.getTitle());
        holder.dateTextView.setText(dateFormat.format(notification.getTimestamp()));
        holder.colorBar.setBackgroundColor(notification.getType().getColor());

        // Логика кнопки "Удалить"
        if (notification.isDeletable()) {
            holder.deleteButton.setVisibility(View.VISIBLE);
            holder.deleteButton.setOnClickListener(v -> listener.onDeleteClicked(notification));
        } else {
            holder.deleteButton.setVisibility(View.INVISIBLE); // Или View.GONE
        }

        // Обработчик нажатия на весь элемент
        holder.itemView.setOnClickListener(v -> listener.onNotificationClicked(notification));
    }

    @Override
    public int getItemCount() {
        return notificationList.size();
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        final View colorBar;
        final TextView titleTextView;
        final TextView dateTextView;
        final ImageButton deleteButton;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            colorBar = itemView.findViewById(R.id.notification_color_bar);
            titleTextView = itemView.findViewById(R.id.notification_title);
            dateTextView = itemView.findViewById(R.id.notification_date);
            deleteButton = itemView.findViewById(R.id.notification_delete_button);
        }
    }
}