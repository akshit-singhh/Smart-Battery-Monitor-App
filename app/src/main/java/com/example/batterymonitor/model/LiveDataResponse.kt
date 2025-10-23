package com.example.batterymonitor.model

data class LiveDataResponse(
    val voltage: Float,
    val current: Float,
    val soc: Float,
    val power: Float,
    val runtime: String,
    val status: String,
    val rssi: Int, // Wi-Fi signal strength
    val mode: String, // "AP" or "STA"
    val ip: String   // New: IP address
)
