package com.example.batterymonitor.network

import com.example.batterymonitor.model.LiveDataResponse
import com.example.batterymonitor.model.SettingsRequest
import com.example.batterymonitor.model.SettingsResponse
import com.example.batterymonitor.model.WiFiRequest
import retrofit2.Response
import retrofit2.http.*
import okhttp3.ResponseBody

interface ApiService {

    @GET("live_data")
    suspend fun getLiveData(): LiveDataResponse

    @GET("settings")
    suspend fun getSettings(): SettingsResponse

    @POST("settings")
    suspend fun updateSettings(@Body settings: SettingsRequest): Response<ResponseBody>

    @Headers("Content-Type: application/json")
    @POST("wifi_config")
    suspend fun updateWiFi(@Body request: WiFiRequest): Response<ResponseBody>

    @POST("reboot")
    suspend fun rebootDevice(): Response<Void>

    @GET("serial_log")
    suspend fun getSerialLogs(): Response<ResponseBody>

    @GET("sta_ip")
    suspend fun getStaIp(): Response<ResponseBody> // okhttp3.ResponseBody

    @POST("set_soc")
    suspend fun setSoc(@Body body: Map<String, Float>): Response<ResponseBody>


}

