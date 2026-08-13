package com.example.my_cannon.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.my_cannon.data.model.*
import com.example.my_cannon.domain.calculator.ArtilleryCalculator
import com.example.my_cannon.domain.calculator.UtmConverter
import java.util.Locale
import android.content.Context
import android.content.SharedPreferences

class CannonViewModel : ViewModel() {
    private var sharedPrefs: SharedPreferences? = null

    fun initPrefs(context: Context) {
        sharedPrefs = context.getSharedPreferences("cannon_prefs", Context.MODE_PRIVATE)
    }

    fun saveLastLocation(lat: Double, lon: Double) {
        sharedPrefs?.edit()?.apply {
            putFloat("last_lat", lat.toFloat())
            putFloat("last_lon", lon.toFloat())
            apply()
        }
    }

    fun getLastLocation(): Pair<Double, Double>? {
        val lat = sharedPrefs?.getFloat("last_lat", -1f) ?: -1f
        val lon = sharedPrefs?.getFloat("last_lon", -1f) ?: -1f
        if (lat == -1f || lon == -1f) return null
        return Pair(lat.toDouble(), lon.toDouble())
    }

    // حالة المربط
    var cannonPos by mutableStateOf<CannonPosition?>(null)
        private set

    // قائمة الأهداف
    val targets = mutableStateListOf<TargetPosition>()

    // نقاط العلام
    val referencePoints = mutableStateListOf<ReferencePoint>()

    // معطيات الرمي (الباليستية)
    var ballisticParams by mutableStateOf(BallisticParams())

    // نوع النقطة التي يتم اختيارها حالياً من الخريطة
    var selectedPointType by mutableStateOf(PointType.NONE)

    // حالة حوار الإدخال اليدوي
    var showEditDialog by mutableStateOf(false)
    var pointToEdit by mutableStateOf<Any?>(null) // CannonPosition, TargetPosition, or ReferencePoint
    var manualAddType by mutableStateOf<PointType?>(null)

    // نتائج الحسابات (للهدف الأول للتوافق أو إزالتها إذا استبدلت بالكامل)
    var mainResult by mutableStateOf<CalculationResult?>(null)
        private set

    fun updatePointFromMap(lat: Double, lon: Double) {
        if (selectedPointType == PointType.NONE) return

        val geo = GeoPoint(lat, lon)
        val utm = UtmConverter.fromGeoToUtm(geo)

        when (selectedPointType) {
            PointType.CANNON -> {
                cannonPos = CannonPosition(geoPoint = geo, utmPoint = utm)
                calculateAll()
            }
            PointType.TARGET -> {
                val index = targets.size + 1
                val newTarget = TargetPosition(
                    id = String.format(Locale.US, "target_%d", index),
                    name = String.format(Locale.US, "الهدف %d", index),
                    geoPoint = geo,
                    utmPoint = utm
                )
                targets.add(newTarget)
                calculateAll()
            }
            PointType.REFERENCE -> {
                val index = referencePoints.size + 1
                val newRef = ReferencePoint(
                    id = String.format(Locale.US, "ref_%d", index),
                    name = String.format(Locale.US, "نقطة علام %d", index),
                    geoPoint = geo,
                    utmPoint = utm
                )
                referencePoints.add(newRef)
            }
            else -> {}
        }
    }

    private fun calculateAll() {
        val c = cannonPos ?: return
        if (targets.isNotEmpty()) {
            mainResult = ArtilleryCalculator.calculate(c, targets.first())
        }
    }

    fun getTargetResult(target: TargetPosition): CalculationResult? {
        val c = cannonPos ?: return null
        return ArtilleryCalculator.calculateBetweenPoints(c.utmPoint, target.utmPoint)
    }

    fun getRefResult(ref: ReferencePoint): CalculationResult? {
        val c = cannonPos ?: return null
        return ArtilleryCalculator.calculateBetweenPoints(c.utmPoint, ref.utmPoint)
    }

    fun calculateReading(targetRes: CalculationResult, ref: ReferencePoint): ReadingResult? {
        val refRes = getRefResult(ref) ?: return null
        
        val azT = targetRes.azimuthMils6000
        val azR = refRes.azimuthMils6000
        
        val isPlus = azT < 3000.0
        val operation = if (isPlus) "+" else "-"
        val baseValue = if (isPlus) azT + 3000.0 else azT - 3000.0
        
        val rawResult = baseValue - azR
        
        // Normalize to [0, 6000]
        var finalReading = rawResult
        while (finalReading < 0) finalReading += 6000.0
        while (finalReading >= 6000) finalReading -= 6000.0
        
        return ReadingResult(
            refName = ref.name,
            targetAzMil = azT,
            refAzMil = azR,
            baseValue = baseValue,
            operation = operation,
            result = rawResult,
            finalReading = finalReading
        )
    }

    fun clearPoints() {
        cannonPos = null
        targets.clear()
        referencePoints.clear()
        mainResult = null
    }

    fun removeTarget(target: TargetPosition) {
        targets.remove(target)
        calculateAll()
    }

    fun removeReferencePoint(point: ReferencePoint) {
        referencePoints.remove(point)
    }

    fun updatePointManually(newGeo: GeoPoint? = null, newUtm: UtmPoint? = null) {
        val geo = newGeo ?: newUtm?.let { UtmConverter.fromUtmToGeo(it) } ?: return
        val utm = newUtm ?: UtmConverter.fromGeoToUtm(geo)

        val editingPoint = pointToEdit
        if (editingPoint != null) {
            // Case 1: Editing existing point
            when (editingPoint) {
                is CannonPosition -> {
                    cannonPos = CannonPosition(id = editingPoint.id, name = editingPoint.name, geoPoint = geo, utmPoint = utm)
                    calculateAll()
                }
                is TargetPosition -> {
                    val index = targets.indexOf(editingPoint)
                    if (index != -1) {
                        targets[index] = TargetPosition(id = editingPoint.id, name = editingPoint.name, geoPoint = geo, utmPoint = utm)
                    }
                    calculateAll()
                }
                is ReferencePoint -> {
                    val index = referencePoints.indexOf(editingPoint)
                    if (index != -1) {
                        referencePoints[index] = ReferencePoint(id = editingPoint.id, name = editingPoint.name, geoPoint = geo, utmPoint = utm)
                    }
                }
            }
        } else {
            // Case 2: Manual add new point
            when (manualAddType) {
                PointType.CANNON -> {
                    cannonPos = CannonPosition(geoPoint = geo, utmPoint = utm)
                    calculateAll()
                }
                PointType.TARGET -> {
                    val index = targets.size + 1
                    targets.add(TargetPosition(
                        id = "target_$index",
                        name = "الهدف $index",
                        geoPoint = geo,
                        utmPoint = utm
                    ))
                    calculateAll()
                }
                PointType.REFERENCE -> {
                    val index = referencePoints.size + 1
                    referencePoints.add(ReferencePoint(
                        id = "ref_$index",
                        name = "نقطة علام $index",
                        geoPoint = geo,
                        utmPoint = utm
                    ))
                }
                else -> {}
            }
        }
        
        showEditDialog = false
        pointToEdit = null
        manualAddType = null
    }

    fun openEditDialog(point: Any) {
        pointToEdit = point
        manualAddType = null
        showEditDialog = true
    }

    fun openManualAddDialog(type: PointType) {
        manualAddType = type
        pointToEdit = null
        showEditDialog = true
    }
}
