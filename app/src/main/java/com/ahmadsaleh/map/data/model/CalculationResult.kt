package com.ahmadsaleh.map.data.model

data class CalculationResult(
    val deltaX: Double,
    val deltaY: Double,
    val theta: Double,    // بالدرجات
    val distance: Double, // بالأمتار
    val azimuth: Double,  // بالدرجات
    val quadrant: Quadrant,
    val isXIncreasing: Boolean,
    val isYIncreasing: Boolean,
    val x1: Double = 0.0,
    val y1: Double = 0.0,
    val x2: Double = 0.0,
    val y2: Double = 0.0
) {
    // السمت بالميليم العسكري (6000)
    val azimuthMils6000: Double get() = (azimuth % 360).let { if (it < 0) it + 360 else it } * (6000.0 / 360.0)
    
    // السمت بالميليم الناتو (6400)
    val azimuthMils6400: Double get() = (azimuth % 360).let { if (it < 0) it + 360 else it } * (6400.0 / 360.0)

    val normalizedAzimuth: Double get() = (azimuth % 360).let { if (it < 0) it + 360 else it }
}

data class ReadingResult(
    val refName: String,
    val targetAzMil: Double,
    val refAzMil: Double,
    val baseValue: Double, // AzT ± 3000
    val operation: String, // "+" or "-"
    val result: Double,    // (AzT ± 3000) - AzR
    val finalReading: Double // Normalized to [0, 6000]
)
