package com.example.batterymonitor.model

data class SettingsRequest(
    val capacity_ah: Float? = null,
    val voltage_offset: Float? = null,
    val current_offset: Float? = null,
    val mv_per_amp: Float? = null,
    val charge_threshold: Float? = null,
    val discharge_threshold: Float? = null,
    val soc: Float,
    val current_deadzone: Float,
)