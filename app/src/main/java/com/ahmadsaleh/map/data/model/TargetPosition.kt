package com.ahmadsaleh.map.data.model

data class TargetPosition(
    val id: String = "target_1",
    val name: String = "الهدف الرئيسي",
    val description: String = "",
    val elevation: Double = 0.0,
    val geoPoint: GeoPoint,
    val utmPoint: UtmPoint
)
