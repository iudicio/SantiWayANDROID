package com.example.santiway.upload_name_device;

public class UserDeviceResponse {
    private int id;
    private String api_key_uuid;
    private String user_phone_mac;
    private String device_name;
    private String created_at;

    public String getDevice_name() {
        return device_name;
    }
}
