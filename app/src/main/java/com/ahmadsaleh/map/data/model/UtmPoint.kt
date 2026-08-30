package com.ahmadsaleh.map.data.model

data class UtmPoint(
    val easting: Double,   // X (أفقياً)
    val northing: Double,  // Y (شاقولياً)
    val zoneNumber: Int = 36,
    val zoneLetter: Char = 'N'
)
