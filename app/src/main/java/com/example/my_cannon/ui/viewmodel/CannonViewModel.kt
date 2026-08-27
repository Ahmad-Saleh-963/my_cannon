package com.example.my_cannon.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import android.app.Application
import com.example.my_cannon.data.model.*
import com.example.my_cannon.domain.calculator.ArtilleryCalculator
import com.example.my_cannon.domain.calculator.UtmConverter
import com.example.my_cannon.domain.elevation.ElevationRepository
import com.example.my_cannon.domain.elevation.ElevationRepositoryImpl
import java.util.Locale
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class CannonViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPrefs: SharedPreferences = application.getSharedPreferences("cannon_prefs", Context.MODE_PRIVATE)

    // مستودع الارتفاع — يُهيَّأ بمجرد توفر التوكن من الموارد
    private val elevationRepository: ElevationRepository by lazy {
        ElevationRepositoryImpl(
            mapboxToken = application.getString(
                application.resources.getIdentifier("mapbox_access_token", "string", application.packageName)
            )
        )
    }

    /**
     * IDs النقاط التي يجري الآن جلب ارتفاعها.
     * يُستخدم لعرض مؤشر التحميل في الـ UI.
     */
    val elevationLoadingIds = mutableStateListOf<String>()

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
    var showEditDialog by mutableStateOf(value = false)
    var pointToEdit by mutableStateOf<Any?>(null) // CannonPosition, TargetPosition, or ReferencePoint
    var manualAddType by mutableStateOf<PointType?>(null)

    // نتائج الحسابات (للهدف الأول للتوافق أو إزالتها إذا استبدلت بالكامل)
    var mainResult by mutableStateOf<CalculationResult?>(null)
        private set

    private val _cameraMoveEvent = MutableSharedFlow<GeoPoint>()
    val cameraMoveEvent = _cameraMoveEvent.asSharedFlow()

    fun moveToLocation(geoPoint: GeoPoint) {
        viewModelScope.launch {
            _cameraMoveEvent.emit(geoPoint)
        }
    }

    init {
        loadCannonPosition()
    }

    private fun loadCannonPosition() {
        val lat = sharedPrefs.getFloat("cannon_lat", -1f)
        val lon = sharedPrefs.getFloat("cannon_lon", -1f)
        if (lat != -1f && lon != -1f) {
            val geo = GeoPoint(lat.toDouble(), lon.toDouble())
            val utm = UtmConverter.fromGeoToUtm(geo)
            cannonPos = CannonPosition(geoPoint = geo, utmPoint = utm)
            calculateAll()
        }
    }

    fun saveCannonPosition(lat: Double, lon: Double) {
        sharedPrefs.edit().apply {
            putFloat("cannon_lat", lat.toFloat())
            putFloat("cannon_lon", lon.toFloat())
            apply()
        }
    }

    fun saveLastLocation(lat: Double, lon: Double) {
        sharedPrefs.edit().apply {
            putFloat("last_lat", lat.toFloat())
            putFloat("last_lon", lon.toFloat())
            apply()
        }
    }

    fun getLastLocation(): Pair<Double, Double>? {
        val lat = sharedPrefs.getFloat("last_lat", -1f)
        val lon = sharedPrefs.getFloat("last_lon", -1f)
        if (lat == -1f || lon == -1f) return null
        return Pair(lat.toDouble(), lon.toDouble())
    }

    fun updatePointFromMap(lat: Double, lon: Double) {
        if (selectedPointType == PointType.NONE) return

        val geo = GeoPoint(lat, lon)
        val utm = UtmConverter.fromGeoToUtm(geo)

        when (selectedPointType) {
            PointType.CANNON -> {
                val point = CannonPosition(geoPoint = geo, utmPoint = utm)
                cannonPos = point
                saveCannonPosition(lat, lon)
                calculateAll()
                fetchAndApplyElevation(point.id, lat, lon, PointType.CANNON)
            }
            PointType.TARGET -> {
                val index = targets.size + 1
                val newTarget = TargetPosition(
                    id = String.format(Locale.US, "target_%d_%d", index, System.currentTimeMillis()),
                    name = String.format(Locale.US, "الهدف %d", index),
                    geoPoint = geo,
                    utmPoint = utm
                )
                targets.add(newTarget)
                calculateAll()
                fetchAndApplyElevation(newTarget.id, lat, lon, PointType.TARGET)
            }
            PointType.REFERENCE -> {
                val index = referencePoints.size + 1
                val newRef = ReferencePoint(
                    id = String.format(Locale.US, "ref_%d_%d", index, System.currentTimeMillis()),
                    name = String.format(Locale.US, "نقطة علام %d", index),
                    geoPoint = geo,
                    utmPoint = utm
                )
                referencePoints.add(newRef)
                fetchAndApplyElevation(newRef.id, lat, lon, PointType.REFERENCE)
            }
            else -> {}
        }
    }

    /**
     * يجلب الارتفاع في الخلفية ويُحدِّث النقطة المعنية تلقائياً.
     * يُضيف ID النقطة إلى [elevationLoadingIds] أثناء الجلب،
     * ويُزيله فور الانتهاء (سواء بنجاح أو فشل).
     */
    private fun fetchAndApplyElevation(id: String, lat: Double, lon: Double, type: PointType) {
        elevationLoadingIds.add(id)
        viewModelScope.launch {
            val elevation = try {
                elevationRepository.getElevation(lat, lon)
            } catch (_: Exception) { null }

            elevation?.let { elev ->
                when (type) {
                    PointType.CANNON -> {
                        cannonPos = cannonPos?.copy(
                            elevation = elev,
                            geoPoint = cannonPos!!.geoPoint.copy(altitude = elev)
                        )
                    }
                    PointType.TARGET -> {
                        val idx = targets.indexOfFirst { it.id == id }
                        if (idx != -1) {
                            targets[idx] = targets[idx].copy(
                                elevation = elev,
                                geoPoint = targets[idx].geoPoint.copy(altitude = elev)
                            )
                        }
                    }
                    PointType.REFERENCE -> {
                        val idx = referencePoints.indexOfFirst { it.id == id }
                        if (idx != -1) {
                            referencePoints[idx] = referencePoints[idx].copy(
                                elevation = elev,
                                geoPoint = referencePoints[idx].geoPoint.copy(altitude = elev)
                            )
                        }
                    }
                    else -> {}
                }
            }
            elevationLoadingIds.remove(id)
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
        sharedPrefs.edit()?.apply {
            remove("cannon_lat")
            remove("cannon_lon")
            apply()
        }
    }

    fun updatePointFull(
        point: Any?,
        type: PointType?,
        name: String,
        description: String,
        elevation: Double,
        geo: GeoPoint,
        utm: UtmPoint
    ) {
        if (point != null) {
            // تعديل نقطة موجودة
            when (point) {
                is CannonPosition -> {
                    val updated = point.copy(
                        name = name, description = description,
                        elevation = elevation,
                        geoPoint = geo.copy(altitude = elevation),
                        utmPoint = utm
                    )
                    cannonPos = updated
                    saveCannonPosition(geo.latitude, geo.longitude)
                    calculateAll()
                    // إذا لم يُدخل المستخدم ارتفاعاً → اجلبه تلقائياً
                    if (elevation == 0.0) fetchAndApplyElevation(updated.id, geo.latitude, geo.longitude, PointType.CANNON)
                }
                is TargetPosition -> {
                    val index = targets.indexOfFirst { it.id == point.id }
                    if (index != -1) {
                        val updated = point.copy(
                            name = name, description = description,
                            elevation = elevation,
                            geoPoint = geo.copy(altitude = elevation),
                            utmPoint = utm
                        )
                        targets[index] = updated
                        calculateAll()
                        if (elevation == 0.0) fetchAndApplyElevation(updated.id, geo.latitude, geo.longitude, PointType.TARGET)
                    }
                }
                is ReferencePoint -> {
                    val index = referencePoints.indexOfFirst { it.id == point.id }
                    if (index != -1) {
                        val updated = point.copy(
                            name = name, description = description,
                            elevation = elevation,
                            geoPoint = geo.copy(altitude = elevation),
                            utmPoint = utm
                        )
                        referencePoints[index] = updated
                        if (elevation == 0.0) fetchAndApplyElevation(updated.id, geo.latitude, geo.longitude, PointType.REFERENCE)
                    }
                }
            }
        } else if (type != null) {
            // إضافة نقطة يدوياً
            when (type) {
                PointType.CANNON -> {
                    val newPoint = CannonPosition(
                        name = name, description = description,
                        elevation = elevation,
                        geoPoint = geo.copy(altitude = elevation),
                        utmPoint = utm
                    )
                    cannonPos = newPoint
                    saveCannonPosition(geo.latitude, geo.longitude)
                    calculateAll()
                    if (elevation == 0.0) fetchAndApplyElevation(newPoint.id, geo.latitude, geo.longitude, PointType.CANNON)
                }
                PointType.TARGET -> {
                    val index = targets.size + 1
                    val newPoint = TargetPosition(
                        id = "target_manual_${index}_${System.currentTimeMillis()}",
                        name = name, description = description,
                        elevation = elevation,
                        geoPoint = geo.copy(altitude = elevation),
                        utmPoint = utm
                    )
                    targets.add(newPoint)
                    calculateAll()
                    if (elevation == 0.0) fetchAndApplyElevation(newPoint.id, geo.latitude, geo.longitude, PointType.TARGET)
                }
                PointType.REFERENCE -> {
                    val index = referencePoints.size + 1
                    val newPoint = ReferencePoint(
                        id = "ref_manual_${index}_${System.currentTimeMillis()}",
                        name = name, description = description,
                        elevation = elevation,
                        geoPoint = geo.copy(altitude = elevation),
                        utmPoint = utm
                    )
                    referencePoints.add(newPoint)
                    if (elevation == 0.0) fetchAndApplyElevation(newPoint.id, geo.latitude, geo.longitude, PointType.REFERENCE)
                }
                else -> {}
            }
        }

        showEditDialog = false
        pointToEdit = null
        manualAddType = null
    }

    fun deletePoint(point: Any) {
        when (point) {
            is CannonPosition -> {
                cannonPos = null
                sharedPrefs.edit()?.apply {
                    remove("cannon_lat")
                    remove("cannon_lon")
                    apply()
                }
                mainResult = null
            }
            is TargetPosition -> {
                targets.removeIf { it.id == point.id }
                calculateAll()
            }
            is ReferencePoint -> {
                referencePoints.removeIf { it.id == point.id }
            }
        }
        showEditDialog = false
        pointToEdit = null
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
