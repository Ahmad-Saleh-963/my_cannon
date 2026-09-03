package com.ahmadsaleh.map.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ahmadsaleh.map.data.db.AppDatabase
import com.ahmadsaleh.map.data.db.entity.DriveSessionEntity
import com.ahmadsaleh.map.data.io.ImportResult
import com.ahmadsaleh.map.data.model.*
import com.ahmadsaleh.map.data.repository.PointsRepository
import com.ahmadsaleh.map.domain.calculator.ArtilleryCalculator
import com.ahmadsaleh.map.domain.calculator.UtmConverter
import com.ahmadsaleh.map.domain.elevation.ElevationRepository
import com.ahmadsaleh.map.domain.elevation.ElevationRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class CannonViewModel(application: Application) : AndroidViewModel(application) {

    // ─── SharedPreferences (للموقع الأخير فقط) ───────────────────────────────
    private val sharedPrefs: SharedPreferences =
        application.getSharedPreferences("cannon_prefs", Context.MODE_PRIVATE)

    // ─── Repository ───────────────────────────────────────────────────────────
    private val db = AppDatabase.getInstance(application)
    val pointsRepository = PointsRepository(db)

    // ─── مستودع الارتفاع ──────────────────────────────────────────────────────
    private val elevationRepository: ElevationRepository by lazy {
        ElevationRepositoryImpl(
            mapboxToken = application.getString(
                application.resources.getIdentifier("mapbox_access_token", "string", application.packageName)
            )
        )
    }

    /** IDs النقاط التي يجري الآن جلب ارتفاعها */
    val elevationLoadingIds = mutableStateListOf<String>()

    // ─── حالة الـ UI ──────────────────────────────────────────────────────────
    var cannonPos by mutableStateOf<CannonPosition?>(null)
        private set

    val targets = mutableStateListOf<TargetPosition>()
    val referencePoints = mutableStateListOf<ReferencePoint>()

    var ballisticParams by mutableStateOf(BallisticParams())
    var selectedPointType by mutableStateOf(PointType.NONE)
    var showEditDialog by mutableStateOf(false)
    var pointToEdit by mutableStateOf<Any?>(null)
    var manualAddType by mutableStateOf<PointType?>(null)

    var mainResult by mutableStateOf<CalculationResult?>(null)
        private set

    // ─── حالة الاستيراد ───────────────────────────────────────────────────────
    /** نتيجة الاستيراد الأخيرة — تُستخدم لعرض حوار التأكيد في الـ UI */
    var pendingImport by mutableStateOf<ImportResult.Success?>(null)

    // ─── أحداث التنقل ────────────────────────────────────────────────────────
    private val _cameraMoveEvent = MutableSharedFlow<GeoPoint>()
    val cameraMoveEvent = _cameraMoveEvent.asSharedFlow()

    fun moveToLocation(geoPoint: GeoPoint) {
        viewModelScope.launch { _cameraMoveEvent.emit(geoPoint) }
    }

    // ─── تهيئة ───────────────────────────────────────────────────────────────
    init {
        collectFromDatabase()
    }

    /**
     * يستمع لتغييرات قاعدة البيانات ويُحدِّث الـ UI تلقائياً.
     * يقوم بمزامنة أولية واحدة من SharedPrefs إلى Room إذا وُجد مربض قديم.
     */
    private fun collectFromDatabase() {
        viewModelScope.launch {
            pointsRepository.observeCannon().collect { dbCannon ->
                if (dbCannon == null) {
                    // محاولة استعادة المربط من SharedPrefs (للتوافق مع النسخ القديمة)
                    migrateCannonFromPrefs()
                } else {
                    cannonPos = dbCannon
                    calculateAll()
                }
            }
        }
        viewModelScope.launch {
            pointsRepository.observeTargets().collect { list ->
                targets.clear()
                targets.addAll(list)
                calculateAll()
            }
        }
        viewModelScope.launch {
            pointsRepository.observeReferencePoints().collect { list ->
                referencePoints.clear()
                referencePoints.addAll(list)
            }
        }
    }

    /** يُهاجر بيانات المربط القديمة من SharedPrefs إلى Room مرة واحدة */
    private fun migrateCannonFromPrefs() {
        val lat = sharedPrefs.getFloat("cannon_lat", -1f)
        val lon = sharedPrefs.getFloat("cannon_lon", -1f)
        if (lat != -1f && lon != -1f) {
            val geo = GeoPoint(lat.toDouble(), lon.toDouble())
            val utm = UtmConverter.fromGeoToUtm(geo)
            val cannon = CannonPosition(geoPoint = geo, utmPoint = utm)
            viewModelScope.launch { pointsRepository.saveCannon(cannon) }
        }
    }

    // ─── الموقع ───────────────────────────────────────────────────────────────
    // ─── الموقع والسرعة اللحظية المباشرة والمعدل الذكي ───────────────────────
    var currentSpeedKmh by mutableFloatStateOf(0f)
        private set
    var currentSpeedDisplay by mutableIntStateOf(0)
        private set
    var averageMovingSpeedKmh by mutableFloatStateOf(40f)
        private set
    var topSpeedKmh by mutableFloatStateOf(0f)
        private set
    var isMoving by mutableStateOf(false)
        private set
    var lastGpsFixTime by mutableStateOf(0L)
        private set

    private var previousLocation: Location? = null
    private var smoothedSpeedKmh = 0f
    private val speedHistory = ArrayDeque<Float>()

    /**
     * لمعالجة تحديثات الموقع وحساب السرعة اللحظية بدقة عالية وبطريقة احترافية فائقة الاستجابة
     * تعتمد على Doppler hardware speed أولاً ثم الدلتا المكانية والزمنية دقيقة جداً
     * مع مرشح تكيفي ذكي وحساب معدل حركة السائق بدقة متناهية
     */
    fun processLocationUpdate(location: Location) {
        lastGpsFixTime = System.currentTimeMillis()

        // تجاهل القراءات ذات الدقة المنخفضة جداً (أكبر من 30 متر) لمنع القفزات الخاطئة
        if (location.hasAccuracy() && location.accuracy > 30f) {
            return
        }

        var rawSpeedMs = -1f

        // 1. القراءة المباشرة من مستشعر سرعة GPS العتادي (Doppler effect)
        if (location.hasSpeed() && location.speed >= 0f) {
            rawSpeedMs = location.speed
        } else {
            // 2. الحساب البديل: المسافة المقطوعة / الفارق الزمني (Δd / Δt)
            previousLocation?.let { prev ->
                val timeDiffSec = (location.time - prev.time) / 1000.0f
                if (timeDiffSec in 0.15f..5.0f) {
                    val distanceMeters = prev.distanceTo(location)
                    rawSpeedMs = distanceMeters / timeDiffSec
                }
            }
        }

        previousLocation = location

        // حفظ آخر موقع في الملاحظات والكاش
        saveLastLocation(location.latitude, location.longitude)

        if (rawSpeedMs < 0f) return

        var rawSpeedKmh = rawSpeedMs * 3.6f

        // 3. عتبة السكون (Deadband noise gate): إذا كانت السرعة أقل من 0.8 كم/س نعتبرها 0
        if (rawSpeedKmh < 0.8f) {
            rawSpeedKmh = 0f
        }

        // 4. تتبع معدل السرعة أثناء الحركة (Sliding window)
        if (rawSpeedKmh >= 5.0f) {
            speedHistory.addLast(rawSpeedKmh)
            if (speedHistory.size > 30) speedHistory.removeFirst()
            averageMovingSpeedKmh = speedHistory.average().toFloat()
        }

        // 5. مرشح تكيفي زمني ذكي جداً واستجابة لحظية فورية
        val speedDiff = kotlin.math.abs(rawSpeedKmh - smoothedSpeedKmh)
        val alpha = when {
            rawSpeedKmh == 0f -> 0.85f // توقف فوري حاد
            speedDiff > 8f -> 0.70f // تسارع أو تباطؤ مفاجئ -> استجابة لحظية عالية جداً
            else -> 0.45f // سرعة منتظمة لتنعيم القراءة
        }

        smoothedSpeedKmh = (alpha * rawSpeedKmh) + ((1f - alpha) * smoothedSpeedKmh)

        if (smoothedSpeedKmh < 0.5f) {
            smoothedSpeedKmh = 0f
        }

        currentSpeedKmh = smoothedSpeedKmh
        currentSpeedDisplay = smoothedSpeedKmh.roundToInt()
        isMoving = currentSpeedKmh > 1.0f

        if (currentSpeedKmh > topSpeedKmh) {
            topSpeedKmh = currentSpeedKmh
        }

        if (isDrivingSessionActive) {
            if (activeSessionLastLat != null && activeSessionLastLon != null) {
                val distArray = FloatArray(1)
                Location.distanceBetween(
                    activeSessionLastLat!!, activeSessionLastLon!!,
                    location.latitude, location.longitude,
                    distArray
                )
                val deltaMeters = distArray[0].toDouble()
                if (deltaMeters in 3.0..500.0 && (isMoving || currentSpeedKmh >= 1.5f)) {
                    activeSessionDistanceMeters += deltaMeters
                    activeSessionPathPoints.add(com.mapbox.geojson.Point.fromLngLat(location.longitude, location.latitude))
                    activeSessionLastLat = location.latitude
                    activeSessionLastLon = location.longitude
                } else if (deltaMeters > 500.0) {
                    activeSessionLastLat = location.latitude
                    activeSessionLastLon = location.longitude
                }
            } else {
                activeSessionLastLat = location.latitude
                activeSessionLastLon = location.longitude
                activeSessionStartLat = location.latitude
                activeSessionStartLon = location.longitude
                activeSessionStartPlace = com.ahmadsaleh.map.data.db.SyriaLocationDatabase.findNearestPlace(location.latitude, location.longitude)
                activeSessionPathPoints.clear()
                activeSessionPathPoints.add(com.mapbox.geojson.Point.fromLngLat(location.longitude, location.latitude))
            }
            if (currentSpeedKmh > activeSessionTopSpeed) {
                activeSessionTopSpeed = currentSpeedKmh.toDouble()
            }
        }
    }

    private val driveSessionDao by lazy {
        AppDatabase.getInstance(getApplication()).driveSessionDao()
    }

    private var activeSessionStartTime: Long = 0L
    private var activeSessionStartLat: Double = 0.0
    private var activeSessionStartLon: Double = 0.0
    private var activeSessionStartPlace: String = ""
    private var activeSessionTopSpeed: Double = 0.0
    private var activeSessionDistanceMeters: Double = 0.0
    private var activeSessionPathPoints = mutableListOf<com.mapbox.geojson.Point>()

    val activeSessionDistanceKm: Double
        get() = activeSessionDistanceMeters / 1000.0

    val activeSessionElapsedTimeSeconds: Long
        get() = if (isDrivingSessionActive && activeSessionStartTime > 0L) {
            ((System.currentTimeMillis() - activeSessionStartTime) / 1000L).coerceAtLeast(0L)
        } else {
            0L
        }
    private var activeSessionLastLat: Double? = null
    private var activeSessionLastLon: Double? = null
    var isDrivingSessionActive by mutableStateOf(false)
        private set

    fun onDrivingModeToggled(enabled: Boolean) {
        if (enabled) {
            startDrivingSession()
        } else {
            stopDrivingSession()
        }
    }

    private fun startDrivingSession() {
        val lastLoc = getLastLocation()
        val sLat = lastLoc?.first ?: 33.5138
        val sLon = lastLoc?.second ?: 36.2765

        activeSessionStartTime = System.currentTimeMillis()
        activeSessionStartLat = sLat
        activeSessionStartLon = sLon
        activeSessionStartPlace = com.ahmadsaleh.map.data.db.SyriaLocationDatabase.findNearestPlace(sLat, sLon)
        activeSessionTopSpeed = 0.0
        activeSessionDistanceMeters = 0.0
        activeSessionLastLat = null
        activeSessionLastLon = null

        speedHistory.clear()
        averageMovingSpeedKmh = 0f
        topSpeedKmh = 0f

        activeSessionPathPoints.clear()

        isDrivingSessionActive = true
    }

    private fun stopDrivingSession() {
        if (!isDrivingSessionActive) return

        val lastLoc = getLastLocation()
        val eLat = lastLoc?.first ?: activeSessionLastLat ?: activeSessionStartLat
        val eLon = lastLoc?.second ?: activeSessionLastLon ?: activeSessionStartLon
        val endTime = System.currentTimeMillis()
        val durationSeconds = ((endTime - activeSessionStartTime) / 1000L).coerceAtLeast(1)

        val endPlaceName = com.ahmadsaleh.map.data.db.SyriaLocationDatabase.findNearestPlace(eLat, eLon)
        val distanceKm = activeSessionDistanceMeters / 1000.0
        val avgSpeedKmh = if (durationSeconds > 0) ((distanceKm / (durationSeconds / 3600.0))).coerceAtMost(180.0) else 0.0

        if (activeSessionPathPoints.isEmpty() || activeSessionPathPoints.last().latitude() != eLat) {
            activeSessionPathPoints.add(com.mapbox.geojson.Point.fromLngLat(eLon, eLat))
        }

        val routeGeometry = com.mapbox.geojson.LineString.fromLngLats(activeSessionPathPoints)

        val session = DriveSessionEntity(
            startTime = activeSessionStartTime,
            endTime = endTime,
            durationSeconds = durationSeconds,
            startLat = activeSessionStartLat,
            startLon = activeSessionStartLon,
            startPlaceName = activeSessionStartPlace,
            endLat = eLat,
            endLon = eLon,
            endPlaceName = endPlaceName,
            topSpeedKmh = activeSessionTopSpeed,
            averageSpeedKmh = if (avgSpeedKmh > 0.1) avgSpeedKmh else averageMovingSpeedKmh.toDouble(),
            distanceKm = distanceKm,
            geoJsonGeometry = routeGeometry.toJson()
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                driveSessionDao.insertDriveSession(session)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        isDrivingSessionActive = false
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

    // ─── إضافة نقطة من الخريطة ───────────────────────────────────────────────
    fun updatePointFromMap(lat: Double, lon: Double) {
        if (selectedPointType == PointType.NONE) return

        val geo = GeoPoint(lat, lon)
        val utm = UtmConverter.fromGeoToUtm(geo)

        when (selectedPointType) {
            PointType.CANNON -> {
                val point = CannonPosition(geoPoint = geo, utmPoint = utm)
                cannonPos = point
                viewModelScope.launch { pointsRepository.saveCannon(point) }
                // حفظ SharedPrefs للتوافق
                sharedPrefs.edit().putFloat("cannon_lat", lat.toFloat()).putFloat("cannon_lon", lon.toFloat()).apply()
                calculateAll()
                fetchAndApplyElevation(point.id, lat, lon, PointType.CANNON)
            }
            PointType.TARGET -> {
                val index = targets.size + 1
                val newTarget = TargetPosition(
                    id = String.format(Locale.US, "target_%d_%d", index, System.currentTimeMillis()),
                    name = String.format(Locale.US, "الهدف %d", index),
                    geoPoint = geo, utmPoint = utm
                )
                viewModelScope.launch { pointsRepository.saveTarget(newTarget) }
                fetchAndApplyElevation(newTarget.id, lat, lon, PointType.TARGET)
            }
            PointType.REFERENCE -> {
                val index = referencePoints.size + 1
                val newRef = ReferencePoint(
                    id = String.format(Locale.US, "ref_%d_%d", index, System.currentTimeMillis()),
                    name = String.format(Locale.US, "نقطة علام %d", index),
                    geoPoint = geo, utmPoint = utm
                )
                viewModelScope.launch { pointsRepository.saveReferencePoint(newRef) }
                fetchAndApplyElevation(newRef.id, lat, lon, PointType.REFERENCE)
            }
            else -> {}
        }
    }

    // ─── جلب الارتفاع تلقائياً ────────────────────────────────────────────────
    private fun fetchAndApplyElevation(id: String, lat: Double, lon: Double, type: PointType) {
        elevationLoadingIds.add(id)
        viewModelScope.launch {
            val elevation = try { elevationRepository.getElevation(lat, lon) } catch (_: Exception) { null }

            elevation?.let { elev ->
                when (type) {
                    PointType.CANNON -> {
                        val updated = cannonPos?.copy(
                            elevation = elev,
                            geoPoint = cannonPos!!.geoPoint.copy(altitude = elev)
                        )
                        if (updated != null) {
                            cannonPos = updated
                            pointsRepository.saveCannon(updated)
                        }
                    }
                    PointType.TARGET -> {
                        val idx = targets.indexOfFirst { it.id == id }
                        if (idx != -1) {
                            val updated = targets[idx].copy(
                                elevation = elev,
                                geoPoint = targets[idx].geoPoint.copy(altitude = elev)
                            )
                            pointsRepository.saveTarget(updated)
                            // سيُحدَّث الـ UI تلقائياً عبر Flow
                        }
                    }
                    PointType.REFERENCE -> {
                        val idx = referencePoints.indexOfFirst { it.id == id }
                        if (idx != -1) {
                            val updated = referencePoints[idx].copy(
                                elevation = elev,
                                geoPoint = referencePoints[idx].geoPoint.copy(altitude = elev)
                            )
                            pointsRepository.saveReferencePoint(updated)
                        }
                    }
                    else -> {}
                }
            }
            elevationLoadingIds.remove(id)
        }
    }

    // ─── الحسابات ─────────────────────────────────────────────────────────────
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
        var finalReading = rawResult
        while (finalReading < 0) finalReading += 6000.0
        while (finalReading >= 6000) finalReading -= 6000.0
        return ReadingResult(
            refName = ref.name, targetAzMil = azT, refAzMil = azR,
            baseValue = baseValue, operation = operation,
            result = rawResult, finalReading = finalReading
        )
    }

    // ─── مسح الكل ─────────────────────────────────────────────────────────────
    fun clearPoints() {
        viewModelScope.launch {
            pointsRepository.deleteAll()
            mainResult = null
            sharedPrefs.edit().remove("cannon_lat").remove("cannon_lon").apply()
        }
    }

    // ─── تحديث/إضافة نقطة كاملة ──────────────────────────────────────────────
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
            when (point) {
                is CannonPosition -> {
                    val updated = point.copy(
                        name = name, description = description, elevation = elevation,
                        geoPoint = geo.copy(altitude = elevation), utmPoint = utm
                    )
                    cannonPos = updated
                    viewModelScope.launch { pointsRepository.saveCannon(updated) }
                    sharedPrefs.edit().putFloat("cannon_lat", geo.latitude.toFloat()).putFloat("cannon_lon", geo.longitude.toFloat()).apply()
                    calculateAll()
                    if (elevation == 0.0) fetchAndApplyElevation(updated.id, geo.latitude, geo.longitude, PointType.CANNON)
                }
                is TargetPosition -> {
                    val updated = point.copy(
                        name = name, description = description, elevation = elevation,
                        geoPoint = geo.copy(altitude = elevation), utmPoint = utm
                    )
                    viewModelScope.launch { pointsRepository.saveTarget(updated) }
                    if (elevation == 0.0) fetchAndApplyElevation(updated.id, geo.latitude, geo.longitude, PointType.TARGET)
                }
                is ReferencePoint -> {
                    val updated = point.copy(
                        name = name, description = description, elevation = elevation,
                        geoPoint = geo.copy(altitude = elevation), utmPoint = utm
                    )
                    viewModelScope.launch { pointsRepository.saveReferencePoint(updated) }
                    if (elevation == 0.0) fetchAndApplyElevation(updated.id, geo.latitude, geo.longitude, PointType.REFERENCE)
                }
            }
        } else if (type != null) {
            when (type) {
                PointType.CANNON -> {
                    val newPoint = CannonPosition(
                        name = name, description = description, elevation = elevation,
                        geoPoint = geo.copy(altitude = elevation), utmPoint = utm
                    )
                    cannonPos = newPoint
                    viewModelScope.launch { pointsRepository.saveCannon(newPoint) }
                    sharedPrefs.edit().putFloat("cannon_lat", geo.latitude.toFloat()).putFloat("cannon_lon", geo.longitude.toFloat()).apply()
                    calculateAll()
                    if (elevation == 0.0) fetchAndApplyElevation(newPoint.id, geo.latitude, geo.longitude, PointType.CANNON)
                }
                PointType.TARGET -> {
                    val newPoint = TargetPosition(
                        id = "target_manual_${targets.size + 1}_${System.currentTimeMillis()}",
                        name = name, description = description, elevation = elevation,
                        geoPoint = geo.copy(altitude = elevation), utmPoint = utm
                    )
                    viewModelScope.launch { pointsRepository.saveTarget(newPoint) }
                    if (elevation == 0.0) fetchAndApplyElevation(newPoint.id, geo.latitude, geo.longitude, PointType.TARGET)
                }
                PointType.REFERENCE -> {
                    val newPoint = ReferencePoint(
                        id = "ref_manual_${referencePoints.size + 1}_${System.currentTimeMillis()}",
                        name = name, description = description, elevation = elevation,
                        geoPoint = geo.copy(altitude = elevation), utmPoint = utm
                    )
                    viewModelScope.launch { pointsRepository.saveReferencePoint(newPoint) }
                    if (elevation == 0.0) fetchAndApplyElevation(newPoint.id, geo.latitude, geo.longitude, PointType.REFERENCE)
                }
                else -> {}
            }
        }
        showEditDialog = false
        pointToEdit = null
        manualAddType = null
    }

    // ─── حذف نقطة ─────────────────────────────────────────────────────────────
    fun deletePoint(point: Any) {
        when (point) {
            is CannonPosition -> {
                viewModelScope.launch {
                    pointsRepository.deleteCannon()
                    mainResult = null
                    sharedPrefs.edit().remove("cannon_lat").remove("cannon_lon").apply()
                }
            }
            is TargetPosition -> {
                viewModelScope.launch { pointsRepository.deleteTarget(point.id) }
            }
            is ReferencePoint -> {
                viewModelScope.launch { pointsRepository.deleteReferencePoint(point.id) }
            }
        }
        showEditDialog = false
        pointToEdit = null
    }

    // ─── الاستيراد ────────────────────────────────────────────────────────────
    /**
     * يُطبِّق جلسة مُستوردة على قاعدة البيانات.
     * [replaceAll] = true → يمسح الموجود ويستبدله
     * [replaceAll] = false → يُضيف فوق الموجود
     */
    fun applyImport(result: ImportResult.Success, replaceAll: Boolean) {
        viewModelScope.launch {
            if (replaceAll) pointsRepository.deleteAll()

            result.cannon?.let { pointsRepository.saveCannon(it) }

            val baseTime = System.currentTimeMillis()
            result.targets.forEachIndexed { i, t ->
                pointsRepository.saveTarget(t, baseTime + i)
            }
            result.referencePoints.forEachIndexed { i, r ->
                pointsRepository.saveReferencePoint(r, baseTime + 10_000 + i)
            }
            pendingImport = null
        }
    }

    fun cancelImport() { pendingImport = null }

    // ─── حوارات التعديل ───────────────────────────────────────────────────────
    fun openEditDialog(point: Any) {
        pointToEdit = point; manualAddType = null; showEditDialog = true
    }

    fun openManualAddDialog(type: PointType) {
        manualAddType = type; pointToEdit = null; showEditDialog = true
    }

    // ─── للتوافق مع fetchAndSaveLocation في MainActivity ─────────────────────
    fun saveCannonPosition(lat: Double, lon: Double) {
        sharedPrefs.edit().putFloat("cannon_lat", lat.toFloat()).putFloat("cannon_lon", lon.toFloat()).apply()
    }
}
