package com.example.santiway.upload_name_device;

public class RegisterUserDeviceRequest {
    private String api_key;
    private String user_phone_mac;

    public RegisterUserDeviceRequest(String api_key, String user_phone_mac) {
        this.api_key = api_key;
        this.user_phone_mac = user_phone_mac;
    }
}
