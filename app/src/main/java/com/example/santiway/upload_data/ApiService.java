package com.example.santiway.upload_data;

import com.example.santiway.upload_name_device.DeviceNameRequest;
import com.example.santiway.upload_name_device.RegisterUserDeviceRequest;
import com.example.santiway.upload_name_device.UserDeviceResponse;
import com.example.santiway.upload_folder_device.DeviceFolderRequest;
import com.example.santiway.upload_folder_device.DeviceFolderResponse;

import retrofit2.http.DELETE;
import retrofit2.http.HTTP;
import retrofit2.http.Query;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import java.util.List;

public interface ApiService {
    @POST("api/devices/")
    Call<ApiResponse> uploadDevices(
            @Header("Authorization") String authorization,
            @Header("Content-Type") String contentType,
            @Body List<ApiDevice> devices
    );

    @POST("api/user-devices/")
    Call<UserDeviceResponse> registerDevice(
            @Header("Authorization") String authorization,
            @Body RegisterUserDeviceRequest body
    );

    @POST("api/user-devices/set-name/")
    Call<ApiResponse> setDeviceName(
            @Header("Authorization") String authorization,
            @Body DeviceNameRequest body
    );

    @PATCH("api/user-devices/rename/")
    Call<ApiResponse> renameDevice(
            @Header("Authorization") String authorization,
            @Body DeviceNameRequest body
    );

    @POST("api/user-devices/create-folder/")
    Call<DeviceFolderResponse> createFolder(
            @Header("Authorization") String authorization,
            @Body DeviceFolderRequest body
    );

    @HTTP(method = "DELETE", path = "api/user-devices/delete-folder/", hasBody = true)
    Call<DeviceFolderResponse> deleteFolder(
            @Header("Authorization") String authorization,
            @Body DeviceFolderRequest body
    );
}