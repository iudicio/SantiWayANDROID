package com.example.santiway.upload_data;

public class ApiDevice {
    private String device_id;
    private Double latitude;
    private Double longitude;
    private Integer signal_strength;
    private String network_type;
    private Boolean is_ignored;
    private Boolean is_alert;
    private String user_api;
    private String user_phone_mac;
    private String detected_at;
    private String folder_name;
    private String system_folder_name;

    // Конструктор
    public ApiDevice() {}

    // Геттеры и сеттеры
    public String getDevice_id() { return device_id; }
    public void setDevice_id(String device_id) { this.device_id = device_id; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Integer getSignal_strength() { return signal_strength; }
    public void setSignal_strength(Integer signal_strength) { this.signal_strength = signal_strength; }

    public String getNetwork_type() { return network_type; }
    public void setNetwork_type(String network_type) { this.network_type = network_type; }

    public Boolean getIs_ignored() { return is_ignored; }
    public void setIs_ignored(Boolean is_ignored) { this.is_ignored = is_ignored; }

    public Boolean getIs_alert() { return is_alert; }
    public void setIs_alert(Boolean is_alert) { this.is_alert = is_alert; }

    public String getUser_api() { return user_api; }
    public void setUser_api(String user_api) { this.user_api = user_api; }

    public String getUser_phone_mac() { return user_phone_mac; }
    public void setUser_phone_mac(String user_phone_mac) { this.user_phone_mac = user_phone_mac; }

    public String getDetected_at() { return detected_at; }
    public void setDetected_at(String detected_at) { this.detected_at = detected_at; }

    public String getFolder_name() { return folder_name; }
    public void setFolder_name(String folder_name) { this.folder_name = folder_name; }

    public String getSystem_folder_name() { return system_folder_name; }
    public void setSystem_folder_name(String system_folder_name) { this.system_folder_name = system_folder_name; }
}