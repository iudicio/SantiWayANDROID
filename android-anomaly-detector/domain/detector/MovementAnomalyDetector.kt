package com.yourpackage.anomalydetector.domain.detector

import com.yourpackage.anomalydetector.data.models.AnomalyResult
import com.yourpackage.anomalydetector.data.models.DetectedDevice
import com.yourpackage.anomalydetector.data.repository.DeviceHistoryRepository
import com.yourpackage.anomalydetector.domain.enums.MovementType
import com.yourpackage.anomalydetector.utils.LocationUtils
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class MovementAnomalyDetector(
    private val repo: DeviceHistoryRepository = DeviceHistoryRepository(),
    // Конфигурируемые константы
    private val walkingSpeedThresholdLow: Double = 18.0,
    private val walkingSpeedThresholdHigh: Double = 22.0,
    private val walkingDistanceThreshold: Double = 1_000.0,
    private val drivingDistanceThreshold: Double = 10_000.0,
    private val minDistanceFilter: Double = 10.0,
    private val maxAccuracyMeters: Float = 100f,
    private val idleResetMs: Long = 15 * 60 * 1000L,
    private val maxSpeedKmh: Double = 300.0
) {
    
    // Состояние по MAC для гистерезиса
    private val movementStates = ConcurrentHashMap<String, MovementType>()

    //Основная функция обработки обнаруженного устройства
    fun processDetectedDevice(device: DetectedDevice): AnomalyResult? {
        // Валидация входных данных
        if (!isValidDevice(device)) {
            return null
        }

        val updateResult = repo.updateSession(
            device = device,
            minDistanceMeters = minDistanceFilter,
            idleResetMs = idleResetMs,
            maxSpeedKmh = maxSpeedKmh,
            maxAccuracyMeters = maxAccuracyMeters
        ) ?: return null

        // Определяем тип движения с гистерезисом
        val movementType = determineMovementType(device.macAddress, updateResult.smoothedSpeedKmh)

        // Проверяем аномалию по СУММАРНОЙ дистанции сессии
        val threshold = getDistanceThreshold(movementType)
        val isAnomaly = updateResult.totalDistanceMeters > threshold
        
        if (isAnomaly) {
            val session = repo.getSession(updateResult.mac)
            if (session != null && !session.alerted) {
                session.alerted = true
                return createAnomalyResult(updateResult, movementType)
            }
        }
        
        return null
    }

    //Определение типа движения с гистерезисом

    private fun determineMovementType(macAddress: String, smoothedSpeed: Double): MovementType {
        val currentType = movementStates.getOrPut(macAddress) { MovementType.WALKING }
        
        val newType = when (currentType) {
            MovementType.WALKING -> {
                if (smoothedSpeed > walkingSpeedThresholdHigh) 
                    MovementType.DRIVING 
                else 
                    MovementType.WALKING
            }
            MovementType.DRIVING -> {
                if (smoothedSpeed < walkingSpeedThresholdLow) 
                    MovementType.WALKING 
                else 
                    MovementType.DRIVING
            }
        }
        
        movementStates[macAddress] = newType
        return newType
    }

    // Получение порога дистанции для типа движения

    private fun getDistanceThreshold(movementType: MovementType): Double {
        return when (movementType) {
            MovementType.WALKING -> walkingDistanceThreshold
            MovementType.DRIVING -> drivingDistanceThreshold
        }
    }

    // Создание результата аномалии

    private fun createAnomalyResult(
        updateResult: DeviceHistoryRepository.UpdateResult,
        movementType: MovementType
    ): AnomalyResult {
        return AnomalyResult(
            macAddress = updateResult.mac,
            movementType = movementType,
            distance = updateResult.totalDistanceMeters,
            speed = updateResult.smoothedSpeedKmh,
            isAnomalous = true,
            message = generateAnomalyMessage(
                movementType, 
                updateResult.totalDistanceMeters, 
                updateResult.smoothedSpeedKmh,
                updateResult.sessionDurationMs
            )
        )
    }

    // Генерация сообщения об аномалии
    private fun generateAnomalyMessage(
        movementType: MovementType, 
        totalDistanceMeters: Double, 
        smoothedSpeed: Double,
        sessionDurationMs: Long
    ): String {
        val distanceKm = totalDistanceMeters / 1000.0
        val dist = String.format(Locale.US, "%.2f", distanceKm)
        val spd = String.format(Locale.US, "%.1f", smoothedSpeed)
        val duration = formatDuration(sessionDurationMs)
        
        return when (movementType) {
            MovementType.WALKING -> 
                "АЛЕРТ: Пешеходное перемещение на $dist км за $duration (скорость $spd км/ч)"
            MovementType.DRIVING -> 
                "АЛЕРТ: Автомобильное перемещение на $dist км за $duration (скорость $spd км/ч)"
        }
    }

    // Форматирование длительности

    private fun formatDuration(durationMs: Long): String {
        val minutes = durationMs / (60 * 1000)
        val hours = minutes / 60
        
        return when {
            hours > 0 -> "${hours}ч ${minutes % 60}м"
            minutes > 0 -> "${minutes}м"
            else -> "${durationMs / 1000}с"
        }
    }

    // Валидация устройства

    private fun isValidDevice(device: DetectedDevice): Boolean {
        return device.macAddress.isNotBlank() &&
                LocationUtils.isValidCoordinate(device.latitude, device.longitude) &&
                device.timestamp > 0
    }

    // Получение информации о сессии

    fun getSessionInfo(macAddress: String): SessionInfo? {
        val session = repo.getSession(macAddress) ?: return null
        val movementType = movementStates[macAddress] ?: MovementType.WALKING
        
        return SessionInfo(
            macAddress = macAddress,
            startTime = session.startTimestamp,
            lastActivityTime = session.lastSignificant.timestamp,
            totalDistance = session.totalDistanceMeters,
            currentMovementType = movementType,
            averageSpeed = session.averageSpeed(),
            maxSpeed = session.maxSpeed(),
            alerted = session.alerted
        )
    }

    // Получение всех активных сессий

    fun getAllActiveSessions(): List<SessionInfo> {
        return repo.getAllActiveSessions().mapNotNull { (mac, _) ->
            getSessionInfo(mac)
        }
    }

    // Принудительное завершение сессии

    fun endSession(macAddress: String) {
        repo.removeSession(macAddress)
        movementStates.remove(macAddress)
    }

    // Очистка старых записей

    fun cleanOldRecords(maxAgeMs: Long = 24 * 60 * 60 * 1000L) {
        repo.cleanOldRecords(maxAgeMs)
        
        // Очищаем также состояния движения для удаленных сессий
        val activeMacs = repo.getAllActiveSessions().keys
        movementStates.keys.retainAll(activeMacs)
    }

    // Получение статистики детектора

    fun getDetectorStats(): DetectorStats {
        val sessions = repo.getAllActiveSessions()
        var totalDistance = 0.0
        var walkingSessions = 0
        var drivingSessions = 0
        var alertedSessions = 0
        
        sessions.forEach { (mac, session) ->
            totalDistance += session.totalDistanceMeters
            if (session.alerted) alertedSessions++
            when (movementStates[mac]) {
                MovementType.WALKING -> walkingSessions++
                MovementType.DRIVING -> drivingSessions++
                null -> walkingSessions++ // по умолчанию
            }
        }
        
        return DetectorStats(
            activeSessions = sessions.size,
            totalDistanceTracked = totalDistance,
            walkingSessions = walkingSessions,
            drivingSessions = drivingSessions,
            alertedSessions = alertedSessions
        )
    }

    // Сброс всех данных

    fun reset() {
        repo.clear()
        movementStates.clear()
    }

    data class SessionInfo(
        val macAddress: String,
        val startTime: Long,
        val lastActivityTime: Long,
        val totalDistance: Double,
        val currentMovementType: MovementType,
        val averageSpeed: Double,
        val maxSpeed: Double,
        val alerted: Boolean
    )

    data class DetectorStats(
        val activeSessions: Int,
        val totalDistanceTracked: Double,
        val walkingSessions: Int,
        val drivingSessions: Int,
        val alertedSessions: Int
    )
}
