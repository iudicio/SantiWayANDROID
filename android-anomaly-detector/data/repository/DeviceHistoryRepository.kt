package com.yourpackage.anomalydetector.data.repository

import com.yourpackage.anomalydetector.data.models.DetectedDevice
import com.yourpackage.anomalydetector.data.models.SessionState
import com.yourpackage.anomalydetector.utils.LocationUtils
import java.util.concurrent.ConcurrentHashMap

class DeviceHistoryRepository {

    data class UpdateResult(
        val mac: String,
        val distanceAdded: Double,
        val lastSpeedKmh: Double,
        val totalDistanceMeters: Double,
        val smoothedSpeedKmh: Double,
        val sessionStartTime: Long,
        val sessionDurationMs: Long
    )

    private val sessions = ConcurrentHashMap<String, SessionState>()

    @Synchronized
    fun updateSession(
        device: DetectedDevice,
        minDistanceMeters: Double = 10.0,
        idleResetMs: Long = 15 * 60 * 1000L,
        maxSpeedKmh: Double = 300.0,
        maxAccuracyMeters: Float = 100f
    ): UpdateResult? {
        
        // Фильтр по точности GPS
        if (device.accuracyMeters != null && device.accuracyMeters > maxAccuracyMeters) {
            return null
        }
        
        val sess = sessions[device.macAddress]
        
        // Новая сессия или сброс по таймауту
        if (sess == null || device.timestamp - sess.lastSignificant.timestamp > idleResetMs) {
            sessions[device.macAddress] = SessionState(
                startTimestamp = device.timestamp,
                lastSignificant = device
            )
            return null
        }

        val prev = sess.lastSignificant
        val d = LocationUtils.calculateDistance(
            prev.latitude, prev.longitude, 
            device.latitude, device.longitude
        )
        val dt = (device.timestamp - prev.timestamp).coerceAtLeast(1L)
        val v = LocationUtils.calculateSpeed(d, dt)

        // Фильтры: «дрожание» GPS и нереалистичные скачки
        if (d < minDistanceMeters || v > maxSpeedKmh) {
            return null
        }

        // Накапливаем только значимые перемещения
        sess.totalDistanceMeters += d
        sess.lastSignificant = device
        sess.addSpeed(v)

        return UpdateResult(
            mac = device.macAddress,
            distanceAdded = d,
            lastSpeedKmh = v,
            totalDistanceMeters = sess.totalDistanceMeters,
            smoothedSpeedKmh = sess.medianSpeed(),
            sessionStartTime = sess.startTimestamp,
            sessionDurationMs = device.timestamp - sess.startTimestamp
        )
    }

    @Synchronized
    fun getSession(mac: String): SessionState? = sessions[mac]

    @Synchronized
    fun getAllActiveSessions(): Map<String, SessionState> = sessions.toMap()

    @Synchronized
    fun getSessionDuration(mac: String): Long? {
        val session = sessions[mac] ?: return null
        return session.lastSignificant.timestamp - session.startTimestamp
    }

    @Synchronized
    fun getTotalDistance(mac: String): Double? {
        return sessions[mac]?.totalDistanceMeters
    }

    @Synchronized
    fun cleanOldRecords(olderThanMs: Long = 24 * 60 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - olderThanMs
        sessions.entries.removeIf { (_, s) ->
            s.lastSignificant.timestamp < cutoff
        }
    }

    @Synchronized
    fun removeSession(mac: String) {
        sessions.remove(mac)
    }

    @Synchronized
    fun clear() = sessions.clear()

    @Synchronized
    fun getSessionCount(): Int = sessions.size

    @Synchronized
    fun hasActiveSession(mac: String): Boolean = sessions.containsKey(mac)
}
