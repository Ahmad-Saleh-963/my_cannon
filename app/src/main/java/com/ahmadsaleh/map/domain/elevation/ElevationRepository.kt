package com.ahmadsaleh.map.domain.elevation

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
//  واجهة المستودع
// ─────────────────────────────────────────────────────────────────────────────
interface ElevationRepository {
    /**
     * جلب ارتفاع نقطة جغرافية عن سطح البحر (بالمتر).
     * يُعيد null في حال فشل الجلب من جميع المصادر.
     */
    suspend fun getElevation(latitude: Double, longitude: Double): Double?
}

// ─────────────────────────────────────────────────────────────────────────────
//  التنفيذ: Mapbox Terrain-RGB أولاً ← Open-Meteo احتياطياً
// ─────────────────────────────────────────────────────────────────────────────
class ElevationRepositoryImpl(
    private val mapboxToken: String
) : ElevationRepository {

    /**
     * كاش بسيط في الذاكرة يحتفظ بآخر 300 نقطة.
     * المفتاح: "lat_lon" مقرَّب إلى 4 خانات عشرية (~11م دقة).
     */
    private val cache = object : LinkedHashMap<String, Double>(300, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Double>) = size > 300
    }

    // ─── واجهة عامة ──────────────────────────────────────────────────────────
    override suspend fun getElevation(latitude: Double, longitude: Double): Double? {
        val key = buildCacheKey(latitude, longitude)
        cache[key]?.let { return it }                       // من الكاش مباشرة

        return withContext(Dispatchers.IO) {
            val elevation =
                fetchFromTerrainRGB(latitude, longitude)    // المصدر الأول  (0.1م دقة)
                    ?: fetchFromOpenMeteo(latitude, longitude)  // احتياطي (مجاني)

            if (elevation != null) cache[key] = elevation
            elevation
        }
    }

    // ─── المصدر الأول: Mapbox Terrain-RGB ────────────────────────────────────
    /**
     * يُحمِّل تايل PNG من خريطة Mapbox Terrain-RGB ويفك تشفير RGB → ارتفاع.
     *
     * المعادلة الرسمية من Mapbox:
     *   elevation = -10000 + ((R * 65536 + G * 256 + B) * 0.1)
     *
     * Zoom=14 → دقة بكسل ≈ 2.4م على خط الاستواء (دقة عالية جداً).
     */
    private fun fetchFromTerrainRGB(lat: Double, lon: Double): Double? {
        return try {
            val zoom = 14
            val (tileX, tileY) = latLonToTileXY(lat, lon, zoom)
            val (pixelX, pixelY) = latLonToPixelInTile(lat, lon, zoom, tileX, tileY)

            val urlStr = "https://api.mapbox.com/v4/mapbox.terrain-rgb/" +
                    "$zoom/$tileX/$tileY.pngraw?access_token=$mapboxToken"

            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout    = 8_000
                requestMethod  = "GET"
                setRequestProperty("Accept", "image/png")
            }

            if (conn.responseCode != 200) { conn.disconnect(); return null }

            val bitmap = conn.inputStream.use { BitmapFactory.decodeStream(it) }
            conn.disconnect()

            if (bitmap == null) return null

            val safeX = pixelX.coerceIn(0, bitmap.width - 1)
            val safeY = pixelY.coerceIn(0, bitmap.height - 1)
            val pixel = bitmap.getPixel(safeX, safeY)
            bitmap.recycle()

            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr  8) and 0xFF
            val b =  pixel         and 0xFF

            val elevation = -10000.0 + ((r * 65536 + g * 256 + b) * 0.1)

            // تصفية القيم خارج النطاق المنطقي (أعمق نقطة -11,000م، أعلى قمة 8,849م)
            if (elevation < -11_000 || elevation > 9_000) null else elevation

        } catch (_: Exception) { null }
    }

    // ─── المصدر الاحتياطي: Open-Meteo ────────────────────────────────────────
    /**
     * API مجاني لا يحتاج مفتاح، يستخدم SRTM بدقة ~90م أفقياً.
     * Response JSON: {"elevation":[123.4]}
     */
    private fun fetchFromOpenMeteo(lat: Double, lon: Double): Double? {
        return try {
            val urlStr = "https://api.open-meteo.com/v1/elevation" +
                    "?latitude=$lat&longitude=$lon"

            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout    = 8_000
                requestMethod  = "GET"
            }

            if (conn.responseCode != 200) { conn.disconnect(); return null }

            val json = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            // استخراج الرقم من: {"elevation":[XXX.X]}
            Regex(""""elevation"\s*:\s*\[\s*(-?[\d.]+)""")
                .find(json)?.groupValues?.get(1)?.toDoubleOrNull()

        } catch (_: Exception) { null }
    }

    // ─── دوال مساعدة: حسابات التايل ──────────────────────────────────────────

    /** lat/lon → رقم التايل (x, y) عند مستوى zoom المعطى */
    private fun latLonToTileXY(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
        val n = 2.0.pow(zoom.toDouble())
        val x = ((lon + 180.0) / 360.0 * n).toInt()
        val latRad = Math.toRadians(lat)
        val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
        return Pair(x.coerceIn(0, (n - 1).toInt()), y.coerceIn(0, (n - 1).toInt()))
    }

    /** lat/lon → موضع البكسل (px, py) داخل التايل (0–255) */
    private fun latLonToPixelInTile(
        lat: Double, lon: Double,
        zoom: Int, tileX: Int, tileY: Int
    ): Pair<Int, Int> {
        val n = 2.0.pow(zoom.toDouble())
        val latRad = Math.toRadians(lat)
        val globalX = (lon + 180.0) / 360.0 * n * 256.0
        val globalY = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n * 256.0
        val px = (globalX - tileX * 256.0).toInt().coerceIn(0, 255)
        val py = (globalY - tileY * 256.0).toInt().coerceIn(0, 255)
        return Pair(px, py)
    }

    /** مفتاح الكاش: lat,lon مقرَّب لـ 4 خانات عشرية (~11م) */
    private fun buildCacheKey(lat: Double, lon: Double): String {
        val rLat = (lat * 10_000).roundToLong() / 10_000.0
        val rLon = (lon * 10_000).roundToLong() / 10_000.0
        return "${rLat}_${rLon}"
    }
}
