package com.example.my_cannon.ui.viewmodel

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

    private val tileStore: TileStore = TileStore.create()
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
                "معرة النعمان" to Point.fromLngLat(36.68, 35.64),
                "جسر الشغور" to Point.fromLngLat(36.32, 35.81),
                "خان شيخون" to Point.fromLngLat(36.65, 35.44),
                "سلقين" to Point.fromLngLat(36.43, 36.13),
                "حارم" to Point.fromLngLat(36.51, 36.20),
                "الدانا" to Point.fromLngLat(36.76, 36.21),
                "سرمدا" to Point.fromLngLat(36.72, 36.18)
            ),
            "Aleppo" to listOf(
                "حلب المدينة" to Point.fromLngLat(37.16, 36.20),
                "الباب" to Point.fromLngLat(37.51, 36.36),
                "منبج" to Point.fromLngLat(37.95, 36.52),
                "عزاز" to Point.fromLngLat(37.04, 36.58),
                "عفرين" to Point.fromLngLat(36.87, 36.51),
                "جرابلس" to Point.fromLngLat(38.01, 36.82),
                "السفيرة" to Point.fromLngLat(37.37, 36.08),
                "تل رفعت" to Point.fromLngLat(37.10, 36.47)
            ),
            "Damascus" to listOf(
                "دمشق المدينة" to Point.fromLngLat(36.27, 33.51),
                "دوما" to Point.fromLngLat(36.40, 33.57),
                "يبرود" to Point.fromLngLat(36.65, 33.97)
            )
        )
    }

    private fun loadProvinces() {
        _provinces.value = listOf(
            ProvinceOfflineState("Damascus", "دمشق", BoundingBox.fromLngLats(36.15, 33.45, 36.40, 33.60)),
            ProvinceOfflineState("Rif Dimashq", "ريف دمشق", BoundingBox.fromLngLats(35.70, 32.70, 39.00, 34.80)),
            ProvinceOfflineState("Aleppo", "حلب وريفها", BoundingBox.fromLngLats(36.00, 35.50, 38.50, 37.00)),
            ProvinceOfflineState("Homs", "حمص وريفها", BoundingBox.fromLngLats(36.30, 33.80, 39.80, 35.60)),
            ProvinceOfflineState("Hama", "حماة وريفها", BoundingBox.fromLngLats(36.00, 34.70, 38.20, 35.80)),
            ProvinceOfflineState("Latakia", "اللاذقية وريفها", BoundingBox.fromLngLats(35.60, 35.00, 36.50, 36.10)),
            ProvinceOfflineState("Tartus", "طرطوس وريفها", BoundingBox.fromLngLats(35.75, 34.50, 36.40, 35.30)),
            ProvinceOfflineState("Idlib", "إدلب وريفها", BoundingBox.fromLngLats(36.10, 35.50, 37.20, 36.50)),
            ProvinceOfflineState("Daraa", "درعا وريفها", BoundingBox.fromLngLats(35.70, 32.40, 36.70, 33.30)),
            ProvinceOfflineState("As-Suwayda", "السويداء وريفها", BoundingBox.fromLngLats(36.30, 32.30, 37.60, 33.20)),
            ProvinceOfflineState("Quneitra", "القنيطرة", BoundingBox.fromLngLats(35.65, 32.85, 36.10, 33.35)),
            ProvinceOfflineState("Deir ez-Zor", "دير الزور وريفها", BoundingBox.fromLngLats(38.80, 33.80, 41.30, 36.20)),
            ProvinceOfflineState("Al-Hasakah", "الحسكة وريفها", BoundingBox.fromLngLats(39.80, 35.60, 42.60, 37.60)),
            ProvinceOfflineState("Raqqa", "الرقة وريفها", BoundingBox.fromLngLats(38.20, 34.80, 40.20, 37.00))
        )
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        executeSearch(query)
    }

    private fun isOnline(): Boolean {
        val connectivityManager = getApplication<Application>().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun executeSearch(query: String) {
        if (query.length < 2) {
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            _isSearching.value = true
            val results = if (isOnline()) {
                searchOnline(query)
            } else {
                searchOffline(query)
            }
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    private suspend fun searchOnline(query: String): List<Pair<String, Point>> = withContext(Dispatchers.IO) {
        try {
            val token = getApplication<Application>().getString(R.string.mapbox_access_token)
            val urlString = "https://api.mapbox.com/geocoding/v5/mapbox.places/${query.replace(" ", "%20")}.json?access_token=$token&country=sy&limit=5"
            val url = URL(urlString)
            val connection = url.openConnection()
            connection.connectTimeout = 5000
            val scanner = Scanner(connection.getInputStream())
            val response = StringBuilder()
            while (scanner.hasNext()) response.append(scanner.next()).append(" ")
            scanner.close()

            val json = JSONObject(response.toString())
            val features = json.getJSONArray("features")
            val list = mutableListOf<Pair<String, Point>>()
            
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val name = feature.getString("place_name")
                val center = feature.getJSONArray("center")
                val point = Point.fromLngLat(center.getDouble(0), center.getDouble(1))
                list.add(name to point)
            }
            list
        } catch (_: Exception) {
            searchOffline(query)
        }
    }

    private fun searchOffline(query: String): List<Pair<String, Point>> {
        val allPois = _poiList.value.values.flatten()
        return allPois.filter { it.first.contains(query, ignoreCase = true) }.take(5)
    }

    fun calculateDrivingRoute(start: Point, destination: Point) {
        viewModelScope.launch {
            if (isOnline()) {
                fetchOnlineRoute(start, destination)
            } else {
                _currentRoute.value = LineString.fromLngLats(listOf(start, destination))
            }
        }
    }

    private suspend fun fetchOnlineRoute(start: Point, destination: Point) = withContext(Dispatchers.IO) {
        try {
            val token = getApplication<Application>().getString(R.string.mapbox_access_token)
            val urlString = "https://api.mapbox.com/directions/v5/mapbox/driving/${start.longitude()},${start.latitude()};${destination.longitude()},${destination.latitude()}?geometries=geojson&overview=full&access_token=$token"
            val url = URL(urlString)
            val connection = url.openConnection()
            connection.connectTimeout = 5000
            val scanner = Scanner(connection.getInputStream())
            val response = StringBuilder()
            while (scanner.hasNext()) response.append(scanner.next()).append(" ")
            scanner.close()

            val json = JSONObject(response.toString())
            val routes = json.getJSONArray("routes")
            if (routes.length() > 0) {
                val route = routes.getJSONObject(0)
                val geometryJson = route.getJSONObject("geometry")
                val geometry = LineString.fromJson(geometryJson.toString())
                _currentRoute.value = geometry
            }
        } catch (_: Exception) {
            _currentRoute.value = LineString.fromLngLats(listOf(start, destination))
        }
    }

    fun clearRoute() {
        _currentRoute.value = null
    }

    private fun refreshDownloadedStates() {
        tileStore.getAllTileRegions { expected ->
            if (expected.isValue) {
                val tileRegions = expected.value!!
                val regionIds = tileRegions.map { it.id }
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

        // تشغيل الخدمة في الخلفية (Foreground Service) لضمان الاستمرارية
        val intent = android.content.Intent(getApplication(), com.example.my_cannon.service.MapDownloadService::class.java).apply {
            putExtra("PROVINCE_NAME", provinceName)
            putExtra("WEST", province.bbox.west())
            putExtra("SOUTH", province.bbox.south())
            putExtra("EAST", province.bbox.east())
            putExtra("NORTH", province.bbox.north())
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }

        // الحفاظ على مراقبة التقدم في واجهة التطبيق أيضاً
        val bbox = province.bbox
        val polygon = Polygon.fromLngLats(
            listOf(
                listOf(
                    Point.fromLngLat(bbox.west(), bbox.south()),
                    Point.fromLngLat(bbox.east(), bbox.south()),
                    Point.fromLngLat(bbox.east(), bbox.north()),
                    Point.fromLngLat(bbox.west(), bbox.north()),
                    Point.fromLngLat(bbox.west(), bbox.south())
                )
            )
        )

        val mapDescriptor = offlineManager.createTilesetDescriptor(
            TilesetDescriptorOptions.Builder()
                .styleURI("mapbox://styles/mapbox/satellite-streets-v12")
                .minZoom(0)
                .maxZoom(14) // زوم 14 لتصغير الحجم بشكل كبير جداً (70% توفير)
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
                val completed = progress.completedResourceCount
                val total = progress.requiredResourceCount
                val p = if (total > 0) completed.toFloat() / total.toFloat() else 0f
                
                val sizeInMb = progress.completedResourceSize / (1024 * 1024)
                val sizeStr = if (sizeInMb > 0) "$sizeInMb MB" else "${progress.completedResourceSize / 1024} KB"

                updateProvinceState(provinceName) { 
                    it.copy(
                        progress = p, 
                        completedResources = completed, 
                        totalResources = total,
                        size = sizeStr,
                        status = "جاري التحميل في الخلفية..."
                    ) 
                }
            },
            { expected ->
                if (expected.isError) {
                    val errorMsg = expected.error?.message ?: "خطأ غير معروف"
                    updateProvinceState(provinceName) { 
                        it.copy(isDownloading = false, status = "فشل: $errorMsg") 
                    }
                } else {
                    updateProvinceState(provinceName) { 
                        it.copy(
                            isDownloading = false, 
                            isDownloaded = true, 
                            progress = 1f,
                            status = "جاهز للعمل الميداني"
                        ) 
                    }
                }
            }
        )
    }

    fun deleteProvince(provinceName: String) {
        tileStore.removeTileRegion(provinceName)
        updateProvinceState(provinceName) { it.copy(isDownloaded = false, progress = 0f) }
    }

    private fun updateProvinceState(name: String, update: (ProvinceOfflineState) -> ProvinceOfflineState) {
        _provinces.value = _provinces.value.map {
            if (it.name == name) update(it) else it
        }
    }
}
