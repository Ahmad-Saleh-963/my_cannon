package com.example.my_cannon.data.model

data class BallisticParams(
    val longWindActive: Double = 0.0,
    val longWindInactive: Double = 0.0,
    val crossWindActive: Double = 0.0,
    val crossWindInactive: Double = 0.0,
    val airTempDelta: Double = 0.0,
    val airPressureDelta: Double = 0.0,
    val powderTemp: Double = 0.0,
    val muzzleVelocityDelta: Double = 0.0,
    val crossWindFromLeft: Boolean = true // true: Left to Right, false: Right to Left
)
