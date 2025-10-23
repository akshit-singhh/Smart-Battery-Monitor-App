package com.example.batterymonitor.model

data class SettingsResponse(
    val capacity_ah: Float,
    val voltage_offset: Float,
    val current_offset: Float,
    val mv_per_amp: Float,
    val charge_threshold: Float,
    val discharge_threshold: Float,
    val soc: Float,
    val current_deadzone: Float
)
