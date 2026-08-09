package com.example.my_cannon.data.model

data class TargetPosition(
    val id: String = "target_1",
    val name: String = "الهدف الرئيسي",
    val geoPoint: GeoPoint,
    val utmPoint: UtmPoint
)
