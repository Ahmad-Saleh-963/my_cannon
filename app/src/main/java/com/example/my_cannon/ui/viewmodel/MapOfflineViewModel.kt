package com.example.my_cannon.ui.viewmodel

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.my_cannon.R
import com.mapbox.common.TileStore
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.geojson.BoundingBox
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.TilesetDescriptorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.Scanner

data class ProvinceOfflineState(
    val name: String,
    val nameAr: String,
    val bbox: BoundingBox,
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val isDownloaded: Boolean = false,
    val size: String = "",
    val completedResources: Long = 0,
    val totalResources: Long = 0,
    val status: String = ""
)

class MapOfflineViewModel(application: Application) : AndroidViewModel(application) {

    // تحديد مسار التخزين في مجلد التحميلات العام
    private val mapRootPath: String by lazy {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "mymap")
        if (!folder.exists()) folder.mkdirs()
        folder.absolutePath
    }

    private val tileStore: TileStore = TileStore.create(mapRootPath)
    private val offlineManager: OfflineManager = OfflineManager()

    private val _provinces = MutableStateFlow<List<ProvinceOfflineState>>(emptyList())
    val provinces: StateFlow<List<ProvinceOfflineState>> = _provinces.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _poiList = MutableStateFlow<Map<String, List<Pair<String, Point>>>>(emptyMap())
    val poiList: StateFlow<Map<String, List<Pair<String, Point>>>> = _poiList.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Pair<String, Point>>>(emptyList())
    val searchResults: StateFlow<List<Pair<String, Point>>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _currentRoute = MutableStateFlow<LineString?>(null)
    val currentRoute: StateFlow<LineString?> = _currentRoute.asStateFlow()

    init {
        loadProvinces()
        loadTacticalPois()
        refreshDownloadedStates()
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

    fun refreshDownloadedStates() {
        tileStore.getAllTileRegions { expected ->
            if (expected.isValue) {
                val regionIds = expected.value!!.map { it.id }
                _provinces.value = _provinces.value.map { province ->
                    province.copy(isDownloaded = regionIds.contains(province.name))
                }
            }
        }
    }

    fun downloadProvince(provinceName: String) {
        val province = _provinces.value.find { it.name == provinceName } ?: return
        
        updateProvinceState(provinceName) { 
            it.copy(isDownloading = true, progress = 0f, status = "بدء التجهيز...") 
        }

        val intent = android.content.Intent(getApplication(), com.example.my_cannon.service.MapDownloadService::class.java).apply {
            putExtra("PROVINCE_NAME", provinceName)
            putExtra("WEST", province.bbox.west())
            putExtra("SOUTH", province.bbox.south())
            putExtra("EAST", province.bbox.east())
            putExtra("NORTH", province.bbox.north())
            putExtra("ROOT_PATH", mapRootPath) // تمرير المسار للخدمة
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }

        // مراقبة التقدم
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
                val p = if (progress.requiredResourceCount > 0) progress.completedResourceCount.toFloat() / progress.requiredResourceCount.toFloat() else 0f
                updateProvinceState(provinceName) { it.copy(progress = p, status = "جاري التحميل...") }
            },
            { expected ->
                if (expected.isValue) {
                    updateProvinceState(provinceName) { it.copy(isDownloading = false, isDownloaded = true, progress = 1f, status = "مكتمل") }
                } else {
                    updateProvinceState(provinceName) { it.copy(isDownloading = false, status = "فشل") }
                }
            }
        )
    }

    // وظيفة استيراد خريطة من مجلد
    fun importMapFromFolder(folderPath: String) {
        viewModelScope.launch {
            try {
                // منطق الاستيراد: نقوم بنسخ الملفات إلى مجلد mymap ثم عمل Refresh
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

    // هيكل بيانات جديد لنتائج البحث الأكثر احترافية
    data class SearchResult(
        val name: String,
        val fullAddress: String,
        val province: String,
        val point: Point
    )

    private val _proResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val proResults: StateFlow<List<SearchResult>> = _proResults.asStateFlow()

    fun onSearchQueryChanged(query: String, proximity: Point? = null) {
        _searchQuery.value = query
        executeSearch(query, proximity)
    }

    private fun isOnline(): Boolean {
        val connectivityManager = getApplication<Application>().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
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
            var urlString = "https://api.mapbox.com/geocoding/v5/mapbox.places/${query.replace(" ", "%20")}.json" +
                    "?access_token=$token&country=sy&types=$types&limit=12&language=ar"
            
            // ميزة التحيز المكاني (تقوية النتائج القريبة من المستخدم)
            proximity?.let {
                urlString += "&proximity=${it.longitude()},${it.latitude()}"
            }
            
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
                
                // استخراج المحافظة بشكل ذكي
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

                val center = f.getJSONArray("center")
                list.add(SearchResult(
                    name = name,
                    fullAddress = context,
                    province = province,
                    point = Point.fromLngLat(center.getDouble(0), center.getDouble(1))
                ))
            }
            list
        } catch (e: Exception) {
            searchOfflinePro(query)
        }
    }

    private fun searchOfflinePro(query: String): List<SearchResult> {
        return _poiList.value.values.flatten()
            .filter { it.first.contains(query, ignoreCase = true) }
            .take(5)
            .map { 
                SearchResult(it.first, "منطقة مخزنة - أوفلاين", "سوريا", it.second)
            }
    }

    fun calculateDrivingRoute(start: Point, destination: Point) {
        viewModelScope.launch {
            if (isOnline()) fetchOnlineRoute(start, destination)
            else _currentRoute.value = LineString.fromLngLats(listOf(start, destination))
        }
    }

    private suspend fun fetchOnlineRoute(start: Point, destination: Point) = withContext(Dispatchers.IO) {
        try {
            val token = getApplication<Application>().getString(R.string.mapbox_access_token)
            val url = "https://api.mapbox.com/directions/v5/mapbox/driving/${start.longitude()},${start.latitude()};${destination.longitude()},${destination.latitude()}?geometries=geojson&overview=full&access_token=$token"
            val response = URL(url).readText()
            val json = JSONObject(response)
            val routes = json.getJSONArray("routes")
            if (routes.length() > 0) {
                _currentRoute.value = LineString.fromJson(routes.getJSONObject(0).getJSONObject("geometry").toString())
            }
        } catch (e: Exception) {
            _currentRoute.value = LineString.fromLngLats(listOf(start, destination))
        }
    }

    fun clearRoute() { _currentRoute.value = null }

    fun deleteProvince(provinceName: String) {
        tileStore.removeTileRegion(provinceName)
        updateProvinceState(provinceName) { it.copy(isDownloaded = false, progress = 0f) }
    }

    private fun updateProvinceState(name: String, update: (ProvinceOfflineState) -> ProvinceOfflineState) {
        _provinces.value = _provinces.value.map { if (it.name == name) update(it) else it }
    }
}
