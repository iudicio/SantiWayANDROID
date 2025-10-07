package com.example.santiway.upload_data;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import java.util.List;

public interface ApiService {
    @POST("api/devices/")
    Call<ApiResponse> uploadDevices(
            @Header("Authorization") String authorization,
            @Header("Content-Type") String contentType,
            @Body List<ApiDevice> devices
    );
}