package com.example.my_cannon.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.my_cannon.R
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
    val status: String = ""
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

    private val tileStore: TileStore = TileStore.create(mapRootPath)
    private val offlineManager: OfflineManager = OfflineManager()

    private val _provinces = MutableStateFlow<List<ProvinceOfflineState>>(emptyList())
    val provinces: StateFlow<List<ProvinceOfflineState>> = _provinces.asStateFlow()

    private val _autoDownloadEnabled = MutableStateFlow(prefs.getBoolean("auto_download", false))
    val autoDownloadEnabled: StateFlow<Boolean> = _autoDownloadEnabled.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _poiList = MutableStateFlow<Map<String, List<Pair<String, Point>>>>(emptyMap())
    val poiList: StateFlow<Map<String, List<Pair<String, Point>>>> = _poiList.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _currentRoutes = MutableStateFlow<List<RouteInfo>>(emptyList())
    val currentRoutes: StateFlow<List<RouteInfo>> = _currentRoutes.asStateFlow()

    private val _selectedRouteIndex = MutableStateFlow(0)
    val selectedRouteIndex: StateFlow<Int> = _selectedRouteIndex.asStateFlow()

    private val _proResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val proResults: StateFlow<List<SearchResult>> = _proResults.asStateFlow()

    init {
        loadProvinces()
        loadTacticalPois()
        refreshDownloadedStates()
        checkAndResumeDownloads()
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
            ProvinceOfflineState("Damascus", "دمشق", BoundingBox.fromLngLats(36.15, 33.45, 36.40, 33.60)),
            ProvinceOfflineState("Aleppo", "حلب وريفها", BoundingBox.fromLngLats(36.00, 35.50, 38.50, 37.00)),
            ProvinceOfflineState("Idlib", "إدلب وريفها", BoundingBox.fromLngLats(36.10, 35.50, 37.20, 36.50)),
            ProvinceOfflineState("Hama", "حماة وريفها", BoundingBox.fromLngLats(36.00, 34.70, 38.20, 35.80)),
            ProvinceOfflineState("Homs", "حمص وريفها", BoundingBox.fromLngLats(36.30, 33.80, 39.80, 35.60))
        )
    }

    fun toggleAutoDownload(enabled: Boolean) {
        _autoDownloadEnabled.value = enabled
        prefs.edit().putBoolean("auto_download", enabled).apply()
        if (enabled) {
            startSmartAutoDownload()
        }
    }

    private fun startSmartAutoDownload() {
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplication<Application>())
        
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val targetLoc = location ?: android.location.Location("").apply { 
                    latitude = 33.5138; longitude = 36.2765 // دمشق كخيار احتياطي
                }
                
                val intent = android.content.Intent(getApplication(), com.example.my_cannon.service.MapDownloadService::class.java).apply {
                    putExtra("MODE", "SMART_AUTO")
                    putExtra("LAT", targetLoc.latitude)
                    putExtra("LON", targetLoc.longitude)
                    putExtra("ROOT_PATH", mapRootPath)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    getApplication<Application>().startForegroundService(intent)
                } else {
                    getApplication<Application>().startService(intent)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun refreshDownloadedStates() {
        tileStore.getAllTileRegions { expected ->
            if (expected.isValue) {
                val regionIds = expected.value!!.map { it.id }
                _provinces.value = _provinces.value.map { province ->
                    val isInterrupted = prefs.getBoolean("downloading_${province.name}", false)
                    province.copy(
                        isDownloaded = regionIds.contains(province.name),
                        isDownloading = isInterrupted && !regionIds.contains(province.name)
                    )
                }
            }
        }
    }

    private fun checkAndResumeDownloads() {
        _provinces.value.forEach { province ->
            if (province.isDownloading) resumeDownload(province.name)
        }
        if (_autoDownloadEnabled.value) startSmartAutoDownload()
    }

    private var lastResourceSizeMap = mutableMapOf<String, Long>()
    private var lastUpdateTimeMap = mutableMapOf<String, Long>()

    fun downloadProvince(provinceName: String) {
        prefs.edit().putBoolean("downloading_$provinceName", true).apply()
        startLoading(provinceName)
    }

    private fun resumeDownload(provinceName: String) {
        startLoading(provinceName)
    }

    private fun startLoading(provinceName: String) {
        val province = _provinces.value.find { it.name == provinceName } ?: return
        updateProvinceState(provinceName) { it.copy(isDownloading = true, progress = 0f, status = "متابعة التحميل...") }

        lastResourceSizeMap[provinceName] = 0L
        lastUpdateTimeMap[provinceName] = System.currentTimeMillis()

        val polygon = Polygon.fromLngLats(listOf(listOf(
            Point.fromLngLat(province.bbox.west(), province.bbox.south()),
            Point.fromLngLat(province.bbox.east(), province.bbox.south()),
            Point.fromLngLat(province.bbox.east(), province.bbox.north()),
            Point.fromLngLat(province.bbox.west(), province.bbox.north()),
            Point.fromLngLat(province.bbox.west(), province.bbox.south())
        )))

        val mapDescriptor = offlineManager.createTilesetDescriptor(
            TilesetDescriptorOptions.Builder()
                .styleURI("mapbox://styles/mapbox/satellite-streets-v12")
                .minZoom(0)
                .maxZoom(14)
                .build()
        )

        tileStore.loadTileRegion(
            provinceName,
            TileRegionLoadOptions.Builder()
                .geometry(polygon)
                .descriptors(listOf(mapDescriptor))
                .acceptExpired(true)
                .build(),
            { progress ->
                val currentTime = System.currentTimeMillis()
                val lastTime = lastUpdateTimeMap[provinceName] ?: currentTime
                val lastSize = lastResourceSizeMap[provinceName] ?: 0L
                val timeDiff = (currentTime - lastTime) / 1000.0
                val sizeDiff = progress.completedResourceSize - lastSize
                
                val speedStr = if (timeDiff > 0.8) {
                    val speedKB = (sizeDiff / 1024.0) / timeDiff
                    lastUpdateTimeMap[provinceName] = currentTime
                    lastResourceSizeMap[provinceName] = progress.completedResourceSize
                    if (speedKB > 1024) String.format(Locale.US, "%.1f MB/s", speedKB / 1024.0) 
                    else String.format(Locale.US, "%.1f KB/s", speedKB)
                } else {
                    _provinces.value.find { it.name == provinceName }?.speed ?: "0 KB/s"
                }

                val p = if (progress.requiredResourceCount > 0) progress.completedResourceCount.toFloat() / progress.requiredResourceCount.toFloat() else 0f
                val sizeMB = String.format(Locale.US, "%.1f MB", progress.completedResourceSize / (1024.0 * 1024.0))

                updateProvinceState(provinceName) { 
                    it.copy(progress = p, completedResources = progress.completedResourceCount, totalResources = progress.requiredResourceCount, size = sizeMB, speed = speedStr, status = "جاري المزامنة...") 
                }
            },
            { expected ->
                if (expected.isValue) {
                    prefs.edit().remove("downloading_$provinceName").apply()
                    updateProvinceState(provinceName) { it.copy(isDownloading = false, isDownloaded = true, progress = 1f, status = "مكتمل", speed = "") }
                } else {
                    updateProvinceState(provinceName) { it.copy(isDownloading = false, status = "متوقف مؤقتاً") }
                }
            }
        )
    }

    fun deleteProvince(provinceName: String) {
        tileStore.removeTileRegion(provinceName)
        prefs.edit().remove("downloading_$provinceName").apply()
        viewModelScope.launch {
            delay(500)
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
        try {
            val token = getApplication<Application>().getString(R.string.mapbox_access_token)
            val types = "region,district,place,locality,neighborhood,address,poi"
            var urlString = "https://api.mapbox.com/geocoding/v5/mapbox.places/${query.replace(" ", "%20")}.json?access_token=$token&country=sy&types=$types&limit=12&language=ar"
            proximity?.let { urlString += "&proximity=${it.longitude()},${it.latitude()}" }
            val response = URL(urlString).readText()
            val json = JSONObject(response)
            val features = json.getJSONArray("features")
            val list = mutableListOf<SearchResult>()
            for (i in 0 until features.length()) {
                val f = features.getJSONObject(i)
                val placeName = f.getString("place_name")
                val parts = placeName.split(",")
                val name = parts[0].trim()
                val context = if (parts.size > 1) parts.drop(1).joinToString(",").trim() else "سوريا"
                val province = when {
                    placeName.contains("حلب") -> "محافظة حلب"
                    placeName.contains("إدلب") -> "محافظة إدلب"
                    placeName.contains("اللاذقية") -> "محافظة اللاذقية"
                    placeName.contains("طرطوس") -> "محافظة طرطوس"
                    placeName.contains("حماة") -> "محافظة حماة"
                    placeName.contains("حمص") -> "محافظة حمص"
                    placeName.contains("دمشق") -> "محافظة دمشق"
                    else -> "سوريا"
                }
                list.add(SearchResult(name, context, province, Point.fromLngLat(f.getJSONArray("center").getDouble(0), f.getJSONArray("center").getDouble(1))))
            }
            list
        } catch (e: Exception) {
            searchOfflinePro(query)
        }
    }

    private fun searchOfflinePro(query: String): List<SearchResult> {
        return _poiList.value.values.flatten().filter { it.first.contains(query, ignoreCase = true) }.take(5).map { SearchResult(it.first, "منطقة مخزنة - أوفلاين", "سوريا", it.second) }
    }

    fun calculateDrivingRoute(start: Point, destination: Point) {
        viewModelScope.launch {
            if (isOnline()) {
                fetchOnlineRoutes(start, destination)
            } else {
                val direct = LineString.fromLngLats(listOf(start, destination))
                _currentRoutes.value = listOf(RouteInfo(direct, 0, 0.0, "مسار مباشر (أوفلاين)"))
                _selectedRouteIndex.value = 0
            }
        }
    }

    private suspend fun fetchOnlineRoutes(start: Point, destination: Point) = withContext(Dispatchers.IO) {
        try {
            val token = getApplication<Application>().getString(R.string.mapbox_access_token)
            // طلب 3 مسارات بديلة
            val url = "https://api.mapbox.com/directions/v5/mapbox/driving/${start.longitude()},${start.latitude()};${destination.longitude()},${destination.latitude()}?geometries=geojson&overview=full&alternatives=true&access_token=$token"
            val response = URL(url).readText()
            val json = JSONObject(response)
            val routesJson = json.getJSONArray("routes")
            
            val routesList = mutableListOf<RouteInfo>()
            for (i in 0 until routesJson.length()) {
                val route = routesJson.getJSONObject(i)
                val geometryJson = route.getJSONObject("geometry")
                val geometry = LineString.fromJson(geometryJson.toString())
                val duration = (route.getDouble("duration") / 60.0).toInt()
                val distance = route.getDouble("distance") / 1000.0
                val summary = if (route.has("summary")) route.getString("summary") else "مسار ${i+1}"
                
                routesList.add(RouteInfo(geometry, duration, distance, summary))
            }
            _currentRoutes.value = routesList
            _selectedRouteIndex.value = 0
        } catch (e: Exception) {
            val direct = LineString.fromLngLats(listOf(start, destination))
            _currentRoutes.value = listOf(RouteInfo(direct, 0, 0.0, "خطأ في جلب المسارات"))
            _selectedRouteIndex.value = 0
        }
    }

    fun selectRoute(index: Int) {
        if (index in _currentRoutes.value.indices) {
            _selectedRouteIndex.value = index
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
