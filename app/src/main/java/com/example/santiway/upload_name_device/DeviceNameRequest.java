package com.example.santiway.upload_name_device;

public class DeviceNameRequest {
    private String mac;
    private String name;

    public DeviceNameRequest(String mac, String name) {
        this.mac = mac;
        this.name = name;
    }
}
