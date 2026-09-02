package com.ahmadsaleh.map.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ahmadsaleh.map.R
import com.ahmadsaleh.map.data.db.AppDatabase
import com.ahmadsaleh.map.data.db.entity.CachedRouteEntity
import com.ahmadsaleh.map.domain.calculator.OfflineRoadNetworkRouter
import com.ahmadsaleh.map.domain.calculator.SmartRouteMatcher
import com.ahmadsaleh.map.service.MapDownloadService
import com.ahmadsaleh.map.service.MapboxOfflineRegistry
import com.google.android.gms.location.LocationServices
import com.mapbox.common.TileStore
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.geojson.BoundingBox
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.TilesetDescriptorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.Locale
import androidx.core.content.edit
import kotlin.time.Duration.Companion.milliseconds

data class ProvinceOfflineState(
    val name: String,
    val nameAr: String,
    val bbox: BoundingBox,
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val isDownloaded: Boolean = false,
    val size: String = "",
    val speed: String = "",
    val completedResources: Long = 0,
    val totalResources: Long = 0,
    val status: String = "",
    val targetZoom: Int = 16
)

data class SearchResult(
    val name: String,
    val fullAddress: String,
    val province: String,
    val point: Point
)

data class RouteInfo(
    val geometry: LineString,
    val durationMinutes: Int,
    val distanceKm: Double,
    val summary: String = ""
)

class MapOfflineViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("map_download_prefs", Context.MODE_PRIVATE)

    private val mapRootPath: String by lazy {
        // استخدام مسار داخلي آمن لضمان عمل التحميل 100% على كافة الإصدارات
        val folder = File(application.filesDir, "mymap")
        if (!folder.exists()) folder.mkdirs()
        folder.absolutePath
    }

    private val tileStore: TileStore by lazy {
        MapboxOfflineRegistry.getTileStore(application)
    }
    private val offlineManager: OfflineManager by lazy {
        MapboxOfflineRegistry.getOfflineManager()
    }

    private val _provinces = MutableStateFlow<List<ProvinceOfflineState>>(emptyList())
    val provinces: StateFlow<List<ProvinceOfflineState>> = _provinces.asStateFlow()

    private val _autoDownloadEnabled = MutableStateFlow(prefs.getBoolean("auto_download", false))

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _poiList = MutableStateFlow<Map<String, List<Pair<String, Point>>>>(emptyMap())

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _currentRoutes = MutableStateFlow<List<RouteInfo>>(emptyList())
    val currentRoutes: StateFlow<List<RouteInfo>> = _currentRoutes.asStateFlow()

    private val _selectedRouteIndex = MutableStateFlow(0)
    val selectedRouteIndex: StateFlow<Int> = _selectedRouteIndex.asStateFlow()

    private val _proResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val proResults: StateFlow<List<SearchResult>> = _proResults.asStateFlow()

    private val _downloadedRegionIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        loadProvinces()
        loadTacticalPois()
        syncTileStoreRegions()
        checkAndResumeDownloads()
        startLiveProgressObserver()
    }

    fun syncTileStoreRegions() {
        tileStore.getAllTileRegions { expected ->
            if (expected.isValue) {
                _downloadedRegionIds.value = expected.value!!.map { it.id }.toSet()
                refreshDownloadedStates()
            }
        }
    }

    fun refreshDownloadedStates() {
        val regionIds = _downloadedRegionIds.value

        _provinces.value = _provinces.value.map { province ->
            val isInterrupted = prefs.getBoolean("downloading_${province.name}", false)
            val storedP = prefs.getFloat("progress_${province.name}", 0f)
            val speed = prefs.getString("speed_${province.name}", "0 KB/s") ?: "0 KB/s"
            val sizeMB = prefs.getString("size_${province.name}", "") ?: ""
            val completed = prefs.getLong("completed_${province.name}", 0L)
            val total = prefs.getLong("total_${province.name}", 0L)
            val savedStatus = prefs.getString("status_${province.name}", null)

            val isDownloaded = regionIds.contains(province.name)
            val isDownloading = isInterrupted && !isDownloaded
            val effectiveP = if (isDownloaded) 1f else if (storedP > 0f) maxOf(province.progress, storedP) else province.progress

            val currentStatus = when {
                isDownloaded -> "مكتمل"
                isDownloading -> if (total > 0) "جاري التحميل بالخلفية..." else "جاري تجهيز المصادر..."
                savedStatus != null -> savedStatus
                else -> province.status
            }

            province.copy(
                isDownloaded = isDownloaded,
                isDownloading = isDownloading,
                progress = effectiveP,
                speed = speed,
                size = sizeMB,
                completedResources = completed,
                totalResources = total,
                status = currentStatus
            )
        }
    }

    private fun loadTacticalPois() {
        _poiList.value = mapOf(
            "Idlib" to listOf(
                "إدلب المدينة" to Point.fromLngLat(36.63, 35.93),
                "سراقب" to Point.fromLngLat(36.80, 35.86),
                "أريحا" to Point.fromLngLat(36.61, 35.81),
                "معرة النعمان" to Point.fromLngLat(36.68, 35.64)
            ),
            "Aleppo" to listOf(
                "حلب المدينة" to Point.fromLngLat(37.16, 36.20),
                "عزاز" to Point.fromLngLat(37.04, 36.58)
            )
        )
    }

    private fun loadProvinces() {
        _provinces.value = listOf(
            ProvinceOfflineState("Damascus", "دمشق المدينة", BoundingBox.fromLngLats(36.15, 33.42, 36.42, 33.60)),
            ProvinceOfflineState("RuralDamascus", "ريف دمشق", BoundingBox.fromLngLats(35.80, 33.10, 37.20, 34.30)),
            ProvinceOfflineState("Aleppo", "حلب وريفها", BoundingBox.fromLngLats(36.00, 35.50, 38.20, 37.10)),
            ProvinceOfflineState("Idlib", "إدلب وريفها", BoundingBox.fromLngLats(36.10, 35.40, 37.10, 36.50)),
            ProvinceOfflineState("Tartus", "طرطوس وريفها", BoundingBox.fromLngLats(35.70, 34.60, 36.30, 35.30)),
            ProvinceOfflineState("Latakia", "اللاذقية وريفها", BoundingBox.fromLngLats(35.60, 35.20, 36.30, 36.00)),
            ProvinceOfflineState("Hama", "حماة وريفها", BoundingBox.fromLngLats(36.00, 34.80, 38.20, 35.70)),
            ProvinceOfflineState("Homs", "حمص وريفها", BoundingBox.fromLngLats(36.20, 33.80, 39.50, 35.40)),
            ProvinceOfflineState("Daraa", "درعا وريفها", BoundingBox.fromLngLats(35.80, 32.30, 36.60, 33.20)),
            ProvinceOfflineState("Suwayda", "السويداء وريفها", BoundingBox.fromLngLats(36.30, 32.30, 37.30, 33.20)),
            ProvinceOfflineState("Quneitra", "القنيطرة والجولان", BoundingBox.fromLngLats(35.60, 32.80, 36.10, 33.40)),
            ProvinceOfflineState("DeirEzZor", "دير الزور وريفها", BoundingBox.fromLngLats(39.20, 34.30, 41.10, 36.20)),
            ProvinceOfflineState("Raqqa", "الرقة وريفها", BoundingBox.fromLngLats(38.00, 35.20, 39.80, 36.80)),
            ProvinceOfflineState("Hasakah", "الحسكة وريفها", BoundingBox.fromLngLats(39.80, 36.00, 42.40, 37.30))
        )
    }

    fun downloadAllProvinces(globalZoom: Int = 16) {
        _provinces.value.forEach { province ->
            if (!province.isDownloaded && !province.isDownloading) {
                downloadProvince(province.name, globalZoom)
            }
        }
    }

    private fun checkAndResumeDownloads() {
        val downloadingProvinces = _provinces.value.filter { prefs.getBoolean("downloading_${it.name}", false) && !it.isDownloaded }
        if (downloadingProvinces.isNotEmpty()) {
            for (p in downloadingProvinces) {
                val z = prefs.getInt("zoom_${p.name}", 16)
                val intent = Intent(getApplication(), MapDownloadService::class.java).apply {
                    action = "ADD_TASK"
                    putExtra("PROVINCE_NAME", p.name)
                    putExtra("PROVINCE_NAME_AR", p.nameAr)
                    putExtra("WEST", p.bbox.west())
                    putExtra("SOUTH", p.bbox.south())
                    putExtra("EAST", p.bbox.east())
                    putExtra("NORTH", p.bbox.north())
                    putExtra("MAX_ZOOM", z)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    getApplication<Application>().startForegroundService(intent)
                } else {
                    getApplication<Application>().startService(intent)
                }
            }
            startLiveProgressObserver()
        }
    }

    fun downloadProvince(provinceName: String, customZoom: Int? = null) {
        val province = _provinces.value.find { it.name == provinceName } ?: return
        val zoomToUse = customZoom ?: prefs.getInt("zoom_$provinceName", province.targetZoom)
        
        prefs.edit { 
            putBoolean("downloading_$provinceName", true)
            putInt("zoom_$provinceName", zoomToUse)
        }
        updateProvinceState(provinceName) { 
            it.copy(isDownloading = true, targetZoom = zoomToUse, status = "جاري التحميل بالخلفية...") 
        }

        val intent = Intent(getApplication(), MapDownloadService::class.java).apply {
            action = "ADD_TASK"
            putExtra("PROVINCE_NAME", province.name)
            putExtra("PROVINCE_NAME_AR", province.nameAr)
            putExtra("WEST", province.bbox.west())
            putExtra("SOUTH", province.bbox.south())
            putExtra("EAST", province.bbox.east())
            putExtra("NORTH", province.bbox.north())
            putExtra("MAX_ZOOM", zoomToUse)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }

        startLiveProgressObserver()
    }

    private fun startLiveProgressObserver() {
        viewModelScope.launch {
            while (true) {
                refreshDownloadedStates()
                delay(500.milliseconds)
            }
        }
    }

    fun deleteProvince(provinceName: String) {
        tileStore.removeTileRegion(provinceName)
        prefs.edit { remove("downloading_$provinceName") }
        viewModelScope.launch {
            delay(500.milliseconds)
            refreshDownloadedStates()
            updateProvinceState(provinceName) { it.copy(isDownloaded = false, isDownloading = false, progress = 0f, status = "تم الحذف") }
        }
    }

    fun importMapFromFolder(folderPath: String) {
        viewModelScope.launch {
            try {
                val sourceDir = File(folderPath)
                val targetDir = File(mapRootPath)
                if (sourceDir.exists() && sourceDir.isDirectory) {
                    sourceDir.copyRecursively(targetDir, overwrite = true)
                    refreshDownloadedStates()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onSearchQueryChanged(query: String, proximity: Point? = null) {
        _searchQuery.value = query
        executeSearch(query, proximity)
    }

    private fun isOnline(): Boolean {
        val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun executeSearch(query: String, proximity: Point? = null) {
        if (query.length < 2) {
            _proResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            val results = if (isOnline()) searchOnlinePro(query, proximity) else searchOfflinePro(query)
            _proResults.value = results
            _isSearching.value = false
        }
    }

    private suspend fun searchOnlinePro(query: String, proximity: Point?): List<SearchResult> = withContext(Dispatchers.IO) {
        val syriaDbResults = com.ahmadsaleh.map.data.db.SyriaLocationDatabase.search(query)
        val onlineList = mutableListOf<SearchResult>()
        try {
            val token = getApplication<Application>().getString(R.string.mapbox_access_token)
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val types = "region,district,place,locality,neighborhood,address,poi"
            var urlString = "https://api.mapbox.com/geocoding/v5/mapbox.places/$encodedQuery.json?access_token=$token&country=sy&types=$types&limit=15&autocomplete=true&fuzzyMatch=true&language=ar"
            proximity?.let { urlString += "&proximity=${it.longitude()},${it.latitude()}" }

            val response = URL(urlString).readText()
            val json = JSONObject(response)
            val features = json.getJSONArray("features")

            for (i in 0 until features.length()) {
                val f = features.getJSONObject(i)
                val placeName = f.getString("place_name")
                val text = if (f.has("text")) f.getString("text") else ""

                val parts = placeName.split(",")
                val name = if (text.isNotEmpty()) text else parts[0].trim()
                val contextText = if (parts.size > 1) parts.drop(1).joinToString(",").trim() else "سوريا"

                val province = when {
                    placeName.contains("حلب") -> "محافظة حلب"
                    placeName.contains("إدلب") -> "محافظة إدلب"
                    placeName.contains("اللاذقية") -> "محافظة اللاذقية"
                    placeName.contains("طرطوس") -> "محافظة طرطوس"
                    placeName.contains("حماة") -> "محافظة حماة"
                    placeName.contains("حمص") -> "محافظة حمص"
                    placeName.contains("دمشق") -> "محافظة دمشق"
                    placeName.contains("درعا") -> "محافظة درعا"
                    placeName.contains("السويداء") -> "محافظة السويداء"
                    placeName.contains("القنيطرة") -> "محافظة القنيطرة"
                    placeName.contains("دير الزور") -> "محافظة دير الزور"
                    placeName.contains("الرقة") -> "محافظة الرقة"
                    placeName.contains("الحسكة") -> "محافظة الحسكة"
                    else -> "سوريا"
                }

                val center = f.getJSONArray("center")
                onlineList.add(SearchResult(name, contextText, province, Point.fromLngLat(center.getDouble(0), center.getDouble(1))))
            }
        } catch (_: Exception) {}

        (syriaDbResults + onlineList).distinctBy { Pair(it.name, it.province) }.take(25)
    }

    private fun searchOfflinePro(query: String): List<SearchResult> {
        val syriaDbResults = com.ahmadsaleh.map.data.db.SyriaLocationDatabase.search(query)
        val poiResults = _poiList.value.values.flatten()
            .filter { it.first.contains(query, ignoreCase = true) }
            .map { SearchResult(it.first, "نقطة تكتيكية مخزنة", "سوريا", it.second) }

        return (syriaDbResults + poiResults).distinctBy { Pair(it.name, it.province) }
    }

    private val cachedRouteDao by lazy {
        AppDatabase.getInstance(getApplication()).cachedRouteDao()
    }

    fun calculateDrivingRoute(start: Point, destination: Point) {
        viewModelScope.launch {
            if (isOnline()) {
                fetchOnlineRoutes(start, destination)
            } else {
                fetchOfflineSmartRoutes(start, destination)
            }
        }
    }

    private suspend fun fetchOfflineSmartRoutes(start: Point, destination: Point) = withContext(Dispatchers.IO) {
        try {
            val allCached = cachedRouteDao.getAllCachedRoutes()
            val matchedRoutes = SmartRouteMatcher.findMatchingRoutes(start, destination, allCached)

            if (matchedRoutes.isNotEmpty()) {
                _currentRoutes.value = matchedRoutes
                _selectedRouteIndex.value = 0
                return@withContext
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // في حال عدم توفر اتصال بالإنترنت أو عدم توفر بيانات مسار مسبقة، لا يتم رسم أي خط تجريدي نهائياً
        _currentRoutes.value = emptyList()
        _selectedRouteIndex.value = 0

        withContext(Dispatchers.Main) {
            android.widget.Toast.makeText(
                getApplication(),
                "لا يتوفر اتصال بالإنترنت أو بيانات مسبقة عن هذا المسار لعرضه أوفلاين",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    fun selectRoute(index: Int) {
        if (index in 0 until _currentRoutes.value.size) {
            _selectedRouteIndex.value = index
        }
    }

    private suspend fun fetchOnlineRoutes(start: Point, destination: Point) = withContext(Dispatchers.IO) {
        try {
            val token = getApplication<Application>().getString(R.string.mapbox_access_token)
            // طلب كافة المسارات المتاحة والبديلة مع الازدحام المروري
            val profile = "mapbox/driving-traffic"
            val url = "https://api.mapbox.com/directions/v5/$profile/${start.longitude()},${start.latitude()};${destination.longitude()},${destination.latitude()}?geometries=geojson&overview=full&alternatives=true&continue_straight=true&access_token=$token"
            
            val response = try {
                URL(url).readText()
            } catch (e: Exception) {
                // المحاولة البديلة باستخدام التوجيه الافتراضي للسيارات إذا لم تتوفر بيانات حركة المرور
                val fallbackUrl = "https://api.mapbox.com/directions/v5/mapbox/driving/${start.longitude()},${start.latitude()};${destination.longitude()},${destination.latitude()}?geometries=geojson&overview=full&alternatives=true&access_token=$token"
                URL(fallbackUrl).readText()
            }

            val json = JSONObject(response)
            val routesJson = json.getJSONArray("routes")
            
            val routesList = mutableListOf<RouteInfo>()
            for (i in 0 until routesJson.length()) {
                val route = routesJson.getJSONObject(i)
                val geometryJson = route.getJSONObject("geometry")
                val geometry = LineString.fromJson(geometryJson.toString())
                val duration = (route.getDouble("duration") / 60.0).toInt()
                val distance = route.getDouble("distance") / 1000.0
                val summary = if (route.has("summary") && route.getString("summary").isNotBlank()) {
                    route.getString("summary")
                } else if (i == 0) {
                    "المسار الرئيسي"
                } else {
                    "طريق بديل $i"
                }
                
                routesList.add(RouteInfo(geometry, duration, distance, summary))
            }

            _currentRoutes.value = routesList
            _selectedRouteIndex.value = 0

            // حفظ وتحديث جميع المسارات المحسوبة أونلاين تلقائياً في قاعدة البيانات المحلية
            for (r in routesList) {
                try {
                    cachedRouteDao.deleteExactPair(start.latitude(), start.longitude(), destination.latitude(), destination.longitude())
                    cachedRouteDao.insertRoute(
                        CachedRouteEntity(
                            startLat = start.latitude(),
                            startLon = start.longitude(),
                            destLat = destination.latitude(),
                            destLon = destination.longitude(),
                            geoJsonGeometry = r.geometry.toJson(),
                            durationMinutes = r.durationMinutes,
                            distanceKm = r.distanceKm,
                            summary = r.summary
                        )
                    )
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            fetchOfflineSmartRoutes(start, destination)
        }
    }

    fun clearRoute() {
        _currentRoutes.value = emptyList()
        _selectedRouteIndex.value = 0
    }

    private fun updateProvinceState(name: String, update: (ProvinceOfflineState) -> ProvinceOfflineState) {
        _provinces.value = _provinces.value.map { if (it.name == name) update(it) else it }
    }
}
