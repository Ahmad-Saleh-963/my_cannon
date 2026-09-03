package com.ahmadsaleh.map.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ahmadsaleh.map.R
import com.ahmadsaleh.map.data.db.AppDatabase
import com.ahmadsaleh.map.data.db.entity.CachedRouteEntity
import com.ahmadsaleh.map.domain.calculator.SmartRouteMatcher
import com.ahmadsaleh.map.service.MapDownloadService
import com.ahmadsaleh.map.service.MapboxOfflineRegistry
import com.mapbox.common.TileStore
import com.mapbox.geojson.BoundingBox
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.OfflineManager
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

    private val _offlineSearchQuery = MutableStateFlow("")
    val offlineSearchQuery: StateFlow<String> = _offlineSearchQuery.asStateFlow()

    fun onOfflineSearchQueryChanged(query: String) {
        _offlineSearchQuery.value = query
    }

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
        val resultsList = mutableListOf<SearchResult>()

        // 1. قاعدة البيانات المحلية السورية الفورية
        val syriaDbResults = com.ahmadsaleh.map.data.db.SyriaLocationDatabase.search(query)
        resultsList.addAll(syriaDbResults)

        // تنظيف وتجهيز النص للبحث العربي السوري الدقيق
        val encodedQuery = java.net.URLEncoder.encode(query.trim(), "UTF-8")

        // أ) OpenStreetMap Nominatim Free Geocoding API (مجاني 100% بدون API Key - محدد بالقطر العربي السوري sy)
        try {
            val nominatimUrl = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&countrycodes=sy&format=json&addressdetails=1&accept-language=ar&limit=20"
            val connection = URL(nominatimUrl).openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", "SyriaTacticalMapApp/1.0")
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            if (connection.responseCode == 200) {
                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = org.json.JSONArray(jsonText)

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val lat = item.getDouble("lat")
                    val lon = item.getDouble("lon")
                    val displayName = item.optString("display_name", "")
                    val name = item.optString("name", "")
                    val type = item.optString("type", "")
                    val category = item.optString("class", "")

                    val address = item.optJSONObject("address")
                    val road = address?.optString("road", "") ?: ""
                    val suburb = address?.optString("suburb", "") ?: address?.optString("neighbourhood", "") ?: ""
                    val city = address?.optString("city", "") ?: address?.optString("town", "") ?: address?.optString("village", "") ?: ""
                    val state = address?.optString("state", "") ?: address?.optString("county", "") ?: ""

                    val mainName = when {
                        name.isNotBlank() -> name
                        road.isNotBlank() -> road
                        suburb.isNotBlank() -> suburb
                        else -> displayName.split(",").firstOrNull()?.trim() ?: query
                    }

                    val typeBadge = when {
                        category == "junction" || type == "roundabout" || mainName.contains("دوار") || mainName.contains("ساحة") -> "🔄 "
                        category == "leisure" || type == "park" || mainName.contains("حديقة") || mainName.contains("منتزه") -> "🌳 "
                        category == "highway" || road.isNotBlank() || mainName.contains("شارع") || mainName.contains("طريق") -> "🛣️ "
                        suburb.isNotBlank() || mainName.contains("حي") || mainName.contains("حارة") -> "🏡 "
                        else -> "📍 "
                    }

                    val formattedTitle = typeBadge + mainName
                    val fullAddress = listOf(road, suburb, city, state).filter { it.isNotBlank() }.distinct().joinToString(" • ").ifBlank { displayName }
                    val province = extractSyrianProvince(displayName + " " + state + " " + city)

                    resultsList.add(SearchResult(formattedTitle, fullAddress, province, Point.fromLngLat(lon, lat)))
                }
            }
        } catch (_: Exception) {}

        // ب) Photon Komoot Free OSM API (مجاني 100% بدون API Key - محدد بالنطاق الجغرافي لسوريا)
        try {
            val photonUrl = "https://photon.komoot.io/api/?q=$encodedQuery&lang=ar&bbox=35.6,32.3,42.4,37.3&limit=20"
            val connection = URL(photonUrl).openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", "SyriaTacticalMapApp/1.0")
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            if (connection.responseCode == 200) {
                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(jsonText)
                val features = jsonObj.getJSONArray("features")

                for (i in 0 until features.length()) {
                    val f = features.getJSONObject(i)
                    val geom = f.getJSONObject("geometry")
                    val coords = geom.getJSONArray("coordinates")
                    val lon = coords.getDouble(0)
                    val lat = coords.getDouble(1)

                    val props = f.getJSONObject("properties")
                    val name = props.optString("name", "")
                    val street = props.optString("street", "")
                    val district = props.optString("district", "")
                    val city = props.optString("city", "")
                    val state = props.optString("state", "")

                    val mainName = when {
                        name.isNotBlank() -> name
                        street.isNotBlank() -> street
                        district.isNotBlank() -> district
                        else -> query
                    }

                    val osmValue = props.optString("osm_value", "")
                    val typeBadge = when {
                        osmValue == "roundabout" || mainName.contains("دوار") || mainName.contains("ساحة") -> "🔄 "
                        osmValue == "park" || mainName.contains("حديقة") || mainName.contains("منتزه") -> "🌳 "
                        street.isNotBlank() || mainName.contains("شارع") || mainName.contains("طريق") -> "🛣️ "
                        district.isNotBlank() || mainName.contains("حي") -> "🏡 "
                        else -> "📍 "
                    }

                    val formattedTitle = typeBadge + mainName
                    val fullAddr = listOf(street, district, city, state, "سوريا").filter { it.isNotBlank() }.distinct().joinToString(" • ")
                    val province = extractSyrianProvince(fullAddr)

                    resultsList.add(SearchResult(formattedTitle, fullAddr, province, Point.fromLngLat(lon, lat)))
                }
            }
        } catch (_: Exception) {}

        // جـ) Mapbox Places API (محاولة مكملة إذا توفر المفتاح المجاني)
        try {
            val token = getApplication<Application>().getString(R.string.mapbox_access_token)
            if (token.isNotBlank()) {
                val types = "region,district,place,locality,neighborhood,address,poi"
                var mapboxUrl = "https://api.mapbox.com/geocoding/v5/mapbox.places/$encodedQuery.json?access_token=$token&country=sy&types=$types&limit=15&autocomplete=true&fuzzyMatch=true&language=ar"
                proximity?.let { mapboxUrl += "&proximity=${it.longitude()},${it.latitude()}" }

                val response = URL(mapboxUrl).readText()
                val json = JSONObject(response)
                val features = json.getJSONArray("features")

                for (i in 0 until features.length()) {
                    val f = features.getJSONObject(i)
                    val placeName = f.getString("place_name")
                    val text = if (f.has("text")) f.getString("text") else ""

                    val parts = placeName.split(",")
                    val name = if (text.isNotEmpty()) text else parts[0].trim()
                    val contextText = if (parts.size > 1) parts.drop(1).joinToString(",").trim() else "سوريا"
                    val province = extractSyrianProvince(placeName)

                    val center = f.getJSONArray("center")
                    resultsList.add(SearchResult("📍 $name", contextText, province, Point.fromLngLat(center.getDouble(0), center.getDouble(1))))
                }
            }
        } catch (_: Exception) {}

        // د) Geocode Maps Co Free API (مصدر محلي وعالمي مكمل مجاني بدون مفتاح)
        try {
            val geocodeUrl = "https://geocode.maps.co/search?q=$encodedQuery+سوريا&api_key="
            val connection = URL(geocodeUrl).openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", "SyriaTacticalMapApp/1.0")
            connection.connectTimeout = 2500
            connection.readTimeout = 2500

            if (connection.responseCode == 200) {
                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = org.json.JSONArray(jsonText)

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val lat = item.getDouble("lat")
                    val lon = item.getDouble("lon")
                    val displayName = item.optString("display_name", "")
                    val mainName = displayName.split(",").firstOrNull()?.trim() ?: query
                    val province = extractSyrianProvince(displayName)

                    resultsList.add(SearchResult("📍 $mainName", displayName, province, Point.fromLngLat(lon, lat)))
                }
            }
        } catch (_: Exception) {}

        deduplicateResults(resultsList).take(35)
    }

    private fun cleanArabicName(input: String): String {
        return input.replace(Regex("[🔄🌳🛣️🏡📍]"), "")
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace(Regex("[\\s\\-_]"), "")
            .lowercase(java.util.Locale.ROOT)
            .trim()
    }

    private fun distanceMeters(p1: Point, p2: Point): Double {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            p1.latitude(), p1.longitude(),
            p2.latitude(), p2.longitude(),
            results
        )
        return results[0].toDouble()
    }

    private fun deduplicateResults(rawList: List<SearchResult>): List<SearchResult> {
        val cleanList = mutableListOf<SearchResult>()

        for (item in rawList) {
            val nameClean = cleanArabicName(item.name)
            val isDuplicate = cleanList.any { existing ->
                val existingClean = cleanArabicName(existing.name)
                val dist = distanceMeters(existing.point, item.point)

                // تكرار إذا كانت الإحداثيات قريبة جداً (أقل من 300 متر) والاسم متطابق أو متداخل
                (dist < 300.0 && (nameClean == existingClean || nameClean.contains(existingClean) || existingClean.contains(nameClean))) ||
                (dist < 50.0) // إحداثيات متطابقة تقريباً لنفس النقطة
            }

            if (!isDuplicate) {
                cleanList.add(item)
            }
        }
        return cleanList
    }

    private fun extractSyrianProvince(text: String): String {
        return when {
            text.contains("حلب") -> "محافظة حلب"
            text.contains("إدلب") || text.contains("ادلب") -> "محافظة إدلب"
            text.contains("اللاذقية") || text.contains("لاذقية") -> "محافظة اللاذقية"
            text.contains("طرطوس") -> "محافظة طرطوس"
            text.contains("حماة") || text.contains("حماه") -> "محافظة حماة"
            text.contains("حمص") -> "محافظة حمص"
            text.contains("دمشق") && !text.contains("ريف دمشق") -> "محافظة دمشق"
            text.contains("ريف دمشق") -> "محافظة ريف دمشق"
            text.contains("درعا") -> "محافظة درعا"
            text.contains("السويداء") || text.contains("سويداء") -> "محافظة السويداء"
            text.contains("القنيطرة") || text.contains("قنيطرة") -> "محافظة القنيطرة"
            text.contains("دير الزور") || text.contains("ديرالزور") -> "محافظة دير الزور"
            text.contains("الرقة") || text.contains("رقة") -> "محافظة الرقة"
            text.contains("الحسكة") || text.contains("حسكة") -> "محافظة الحسكة"
            else -> "سوريا"
        }
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
            } catch (_: Exception) {
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
