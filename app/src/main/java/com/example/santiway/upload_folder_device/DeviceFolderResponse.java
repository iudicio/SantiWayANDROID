package com.example.santiway.upload_folder_device;

public class DeviceFolderResponse {
    private long id;
    private int device_id;
    private String name;
    private String created_at;
    private boolean is_deleted;
    private boolean deleted;
    private String error;

    public long getId() { return id; }
    public int getDevice_id() { return device_id; }
    public String getName() { return name; }
    public String getCreated_at() { return created_at; }
    public boolean isDeleted() { return deleted; }
    public String getError() { return error; }
}