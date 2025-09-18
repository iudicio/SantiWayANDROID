package com.yourpackage.anomalydetector.data.models

import java.util.ArrayDeque

data class SessionState(
    var startTimestamp: Long,
    var lastSignificant: DetectedDevice,
    var totalDistanceMeters: Double = 0.0,
    private val speedsWindow: ArrayDeque<Double> = ArrayDeque(),
    var alerted: Boolean = false // флаг для одноразового алерта
) {
    fun addSpeed(v: Double, maxWindow: Int = 5) {
        if (v.isFinite() && v > 0) {
            speedsWindow.addLast(v)
            while (speedsWindow.size > maxWindow) speedsWindow.removeFirst()
        }
    }
    
    fun medianSpeed(): Double {
        if (speedsWindow.isEmpty()) return 0.0
        val sorted = speedsWindow.sorted()
        return sorted[sorted.size / 2]
    }
    
    fun hasSpeedData(): Boolean = speedsWindow.isNotEmpty()
    
    fun averageSpeed(): Double {
        return if (speedsWindow.isEmpty()) 0.0 else speedsWindow.average()
    }
    
    fun maxSpeed(): Double {
        return if (speedsWindow.isEmpty()) 0.0 else speedsWindow.maxOrNull() ?: 0.0
    }
    
    fun minSpeed(): Double {
        return if (speedsWindow.isEmpty()) 0.0 else speedsWindow.minOrNull() ?: 0.0
    }
}
