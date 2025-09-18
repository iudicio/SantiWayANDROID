package com.yourpackage.anomalydetector.data.models

data class DetectedDevice(
    val macAddress: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val deviceClass: String, // Wi-Fi или Bluetooth
    val accuracyMeters: Float? = null // точность GPS
)
