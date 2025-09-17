package com.yourpackage.anomalydetector.data.models

import com.yourpackage.anomalydetector.domain.enums.MovementType

data class AnomalyResult(
    val macAddress: String,
    val movementType: MovementType,
    val distance: Double,
    val speed: Double,
    val isAnomalous: Boolean,
    val message: String
)
