package com.yourpackage.anomalydetector.utils

import kotlin.math.*

object LocationUtils {
    private const val EARTH_RADIUS_METERS = 6371000.0
    private const val MS_IN_HOUR = 3_600_000.0
    private const val M_IN_KM = 1000.0

    private fun toRadians(degrees: Double): Double = degrees * PI / 180.0

    /**
     * Вычисление расстояния между двумя точками (формула гаверсинуса)
     * Улучшенная версия с защитой от численной погрешности
     * @param lat1 широта первой точки
     * @param lon1 долгота первой точки
     * @param lat2 широта второй точки
     * @param lon2 долгота второй точки
     * @return расстояние в метрах
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // Быстрый возврат для идентичных точек
        if (lat1 == lat2 && lon1 == lon2) return 0.0

        val phi1 = toRadians(lat1)
        val phi2 = toRadians(lat2)
        val dPhi = phi2 - phi1

        // Нормализуем разницу долгот к [-180,180] перед переводом в радианы
        val dLambda = toRadians(normalizeLongitude(lon2 - lon1))

        val sinDphi2 = sin(dPhi / 2)
        val sinDlam2 = sin(dLambda / 2)

        var a = sinDphi2 * sinDphi2 + cos(phi1) * cos(phi2) * sinDlam2 * sinDlam2
        
        // Кламп из-за численной погрешности - защита от NaN
        if (a < 0.0) a = 0.0
        if (a > 1.0) a = 1.0

        // asin чуть стабильнее для малых расстояний чем atan2
        val c = 2 * asin(sqrt(a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Вычисление скорости между двумя точками
     * С защитой от NaN/∞ результатов
     * @param distanceMeters расстояние в метрах
     * @param timeMs разница во времени в миллисекундах
     * @return скорость в км/ч
     */
    fun calculateSpeed(distanceMeters: Double, timeMs: Long): Double {
        // Защита от некорректных входных данных
        if (timeMs <= 0L || distanceMeters.isNaN() || distanceMeters.isInfinite() || distanceMeters < 0.0) {
            return 0.0
        }
        
        val hours = timeMs / MS_IN_HOUR
        if (hours <= 0.0) return 0.0
        
        val speedKmh = (distanceMeters / M_IN_KM) / hours
        
        // Дополнительная защита от NaN/∞ в результате
        return if (speedKmh.isFinite()) speedKmh else 0.0
    }

    // Проверка валидности координат

    fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        return lat in -90.0..90.0 && lon in -180.0..180.0 &&
                lat.isFinite() && lon.isFinite()
    }

    /**
     * Нормализация долготы к диапазону [-180, 180]
     * Используется для корректной работы на антимеридиане
     */
    fun normalizeLongitude(lon: Double): Double {
        if (!lon.isFinite()) return 0.0
        
        var normalized = lon % 360.0
        if (normalized > 180.0) normalized -= 360.0
        if (normalized < -180.0) normalized += 360.0
        return normalized
    }

    /**
     * Расчет подшипника (bearing) между двумя точками
     * С нормализацией долготы для корректной работы
     * @return подшипник в градусах (0-360)
     */
    @Suppress("unused")
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = toRadians(normalizeLongitude(lon2 - lon1))
        val lat1Rad = toRadians(lat1)
        val lat2Rad = toRadians(lat2)
        
        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
        
        val bearing = atan2(y, x) * 180.0 / PI
        return (bearing + 360.0) % 360.0
    }

    /**
     * Быстрая приблизительная оценка расстояния для малых дистанций
     * Использует equirectangular approximation - быстрее гаверсинуса
     * Подходит для расстояний < 1км при высокой частоте вызовов
     */
    @Suppress("unused")
    fun calculateDistanceApprox(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (lat1 == lat2 && lon1 == lon2) return 0.0
        
        val lat1Rad = toRadians(lat1)
        val lat2Rad = toRadians(lat2)
        val dLatRad = lat2Rad - lat1Rad
        val dLonRad = toRadians(normalizeLongitude(lon2 - lon1))
        
        val x = dLonRad * cos((lat1Rad + lat2Rad) / 2)
        val y = dLatRad
        
        return EARTH_RADIUS_METERS * sqrt(x * x + y * y)
    }
}
