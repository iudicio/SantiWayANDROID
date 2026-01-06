// File: com.example.santiway.notifications.NotificationData.java
package com.example.santiway;

import android.graphics.Color;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

public class NotificationData implements Serializable {
    private String id; // Идентификатор уведомления, нужен для удаления
    private String title;
    private String text;
    private Date timestamp;
    private NotificationType type;
    private List<byte[]> binaryContents; // Массив бинарных данных
    private List<String> binaryMimeTypes; // Типы контента (e.g., "image/jpeg", "application/vnd.android.package-archive")
    private Double latitude;
    private Double longitude;

    public enum NotificationType {
        ALARM, SYSTEM, INFO;

        public int getColor() {
            switch (this) {
                case ALARM:
                    return Color.parseColor("#FF6B6B"); // Красный
                case SYSTEM:
                    return Color.parseColor("#3DDC84"); // Зеленый (как в вашем стиле)
                case INFO:
                    return Color.parseColor("#64B5F6"); // Синий
                default:
                    return Color.parseColor("#FFFFFF");
            }
        }
    }

    public NotificationData(String id, String title, String text, Date timestamp, NotificationType type, List<byte[]> binaryContents, List<String> binaryMimeTypes, Double latitude, Double longitude) {
        this.id = id;
        this.title = title;
        this.text = text;
        this.timestamp = timestamp;
        this.type = type;
        this.binaryContents = binaryContents;
        this.binaryMimeTypes = binaryMimeTypes;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Геттеры
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getText() { return text; }
    public Date getTimestamp() { return timestamp; }
    public NotificationType getType() { return type; }
    public List<byte[]> getBinaryContents() { return binaryContents; }
    public List<String> getBinaryMimeTypes() { return binaryMimeTypes; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }

    public boolean isDeletable() {
        return this.type != NotificationType.SYSTEM;
    }
}