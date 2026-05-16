package com.example.santiway.upload_folder_device;

public class DeviceFolderRequest {
    private String user_phone_mac;
    private String name;
    private String created_at;

    public DeviceFolderRequest(String user_phone_mac, String name, String created_at) {
        this.user_phone_mac = user_phone_mac;
        this.name = name;
        this.created_at = created_at;
    }
}