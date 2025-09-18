package com.yourpackage.anomalydetector.utils

import java.util.Locale

// Форматирование Double с фиксированным количеством знаков (без локализации)

fun Double.toFixed(decimals: Int): String = 
    String.format(Locale.US, "%.${decimals}f", this)

// Проверка на конечное число

fun Double.isFiniteAndPositive(): Boolean = isFinite() && this > 0.0

// Безопасное деление с проверкой на ноль

fun Double.safeDivide(divisor: Double): Double = if (divisor != 0.0) this / divisor else 0.0

// Конвертация миллисекунд в читаемый формат

fun Long.toReadableTime(): String {
    val seconds = this / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "${days}д ${hours % 24}ч ${minutes % 60}м"
        hours > 0 -> "${hours}ч ${minutes % 60}м"
        minutes > 0 -> "${minutes}м ${seconds % 60}с"
        else -> "${seconds}с"
    }
}

// Конвертация метров в читаемый формат

fun Double.toReadableDistance(): String {
    return when {
        this >= 1000 -> "${(this / 1000.0).toFixed(2)} км"
        else -> "${this.toFixed(0)} м"
    }
}

// Конвертация скорости в читаемый формат

fun Double.toReadableSpeed(): String {
    return "${this.toFixed(1)} км/ч"
}
