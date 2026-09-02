package com.ahmadsaleh.map.domain.calculator

import com.ahmadsaleh.map.ui.viewmodel.RouteInfo
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import kotlin.math.*

object OfflineRoadNetworkRouter {

    data class RoadNode(
        val name: String,
        val lat: Double,
        val lon: Double,
        val neighbors: List<String>
    )

    // شبكة العقد والمحاور الطرقية الرئيسية لكافة المحافظات والمدن السورية
    private val roadNodes = listOf(
        // إدلب وريفها
        RoadNode("Idlib", 35.9310, 36.6330, listOf("MaarratMisrin", "Ariha", "Saraqib", "Binnish")),
        RoadNode("Binnish", 35.9580, 36.7110, listOf("Idlib", "Saraqib", "Taftanaz")),
        RoadNode("Taftanaz", 35.9780, 36.7820, listOf("Binnish", "Atarib")),
        RoadNode("MaarratMisrin", 36.0020, 36.6450, listOf("Idlib", "Zerdana", "Sarmada")),
        RoadNode("Zerdana", 36.0820, 36.7120, listOf("MaarratMisrin", "Atarib", "Sarmada")),
        RoadNode("Sarmada", 36.1820, 36.7210, listOf("Zerdana", "Atmeh", "Dana", "BabAlHawa")),
        RoadNode("Dana", 36.2120, 36.7720, listOf("Sarmada", "Atmeh")),
        RoadNode("Atmeh", 36.3080, 36.6850, listOf("Sarmada", "Dana")),
        RoadNode("BabAlHawa", 36.2380, 36.6920, listOf("Sarmada")),
        RoadNode("Ariha", 35.8130, 36.6110, listOf("Idlib", "JisrShughur", "MaarratNu'man", "Saraqib")),
        RoadNode("JisrShughur", 35.8150, 36.3180, listOf("Ariha", "Darkush", "Latakia", "Ghab")),
        RoadNode("Darkush", 35.8420, 36.3980, listOf("JisrShughur", "Salqin")),
        RoadNode("Salqin", 36.1350, 36.4520, listOf("Darkush", "Harem", "KafrTakharim")),
        RoadNode("Harem", 36.2080, 36.5180, listOf("Salqin", "Sarmada")),
        RoadNode("KafrTakharim", 36.1180, 36.5120, listOf("Salqin", "Armanaz", "Idlib")),
        RoadNode("Armanaz", 36.1080, 36.4820, listOf("KafrTakharim")),
        RoadNode("Saraqib", 35.8610, 36.8020, listOf("Idlib", "Ariha", "MaarratNu'man", "Zirba", "Aleppo")),
        RoadNode("MaarratNu'man", 35.6480, 36.6780, listOf("Saraqib", "Ariha", "KafrNabl", "KhanShaykhun")),
        RoadNode("KafrNabl", 35.6120, 36.5680, listOf("MaarratNu'man")),
        RoadNode("KhanShaykhun", 35.4420, 36.6510, listOf("MaarratNu'man", "Morek")),

        // حلب وريفها
        RoadNode("Atarib", 36.1360, 36.8280, listOf("Zerdana", "Taftanaz", "Anjara", "DaratIzza")),
        RoadNode("DaratIzza", 36.2820, 36.8530, listOf("Atarib", "Afrin")),
        RoadNode("Anjara", 36.2310, 36.9450, listOf("Atarib", "AleppoWest")),
        RoadNode("Zirba", 36.0820, 36.9850, listOf("Saraqib", "AleppoWest")),
        RoadNode("AleppoWest", 36.2210, 37.0950, listOf("Anjara", "Zirba", "Aleppo")),
        RoadNode("Aleppo", 36.2080, 37.1480, listOf("AleppoWest", "Huraytan", "Safira", "Manbij")),
        RoadNode("Huraytan", 36.2920, 37.0910, listOf("Aleppo", "Nubl")),
        RoadNode("Nubl", 36.3780, 37.0010, listOf("Huraytan", "Afrin", "Azaz")),
        RoadNode("Azaz", 36.5860, 37.0450, listOf("Nubl", "Afrin")),
        RoadNode("Afrin", 36.5110, 36.8680, listOf("Azaz", "Nubl", "DaratIzza")),

        // حماة وحمص
        RoadNode("Morek", 35.3780, 36.6880, listOf("KhanShaykhun", "TaybatImam", "KafrZita")),
        RoadNode("KafrZita", 35.3750, 36.6020, listOf("Morek", "Mahrada")),
        RoadNode("TaybatImam", 35.2680, 36.7080, listOf("Morek", "Hama")),
        RoadNode("Mahrada", 35.2510, 36.5780, listOf("KafrZita", "Hama", "Suqaylabiyah")),
        RoadNode("Suqaylabiyah", 35.3780, 36.3880, listOf("Mahrada", "Ghab")),
        RoadNode("Ghab", 35.4210, 36.3880, listOf("Suqaylabiyah", "JisrShughur")),
        RoadNode("Hama", 35.1310, 36.7580, listOf("TaybatImam", "Mahrada", "Salamiyah", "Rastan", "Masyaf")),
        RoadNode("Masyaf", 35.0650, 36.3420, listOf("Hama", "Baniyas")),
        RoadNode("Salamiyah", 35.0120, 37.0520, listOf("Hama", "Homs")),
        RoadNode("Rastan", 34.9280, 36.7320, listOf("Hama", "Talbiseh")),
        RoadNode("Talbiseh", 34.8420, 36.7320, listOf("Rastan", "Homs")),
        RoadNode("Homs", 34.7310, 36.7180, listOf("Talbiseh", "Salamiyah", "Qusayr", "Nabk", "Tartus")),
        RoadNode("Qusayr", 34.5080, 36.5820, listOf("Homs")),

        // الساحل السوري
        RoadNode("Latakia", 35.5310, 35.7820, listOf("JisrShughur", "Jableh", "Kessab")),
        RoadNode("Kessab", 35.9280, 35.9880, listOf("Latakia")),
        RoadNode("Jableh", 35.3610, 35.9280, listOf("Latakia", "Baniyas", "Qardaha")),
        RoadNode("Qardaha", 35.4580, 36.0620, listOf("Jableh")),
        RoadNode("Baniyas", 35.1810, 35.9480, listOf("Jableh", "Tartus", "Masyaf")),
        RoadNode("Tartus", 34.8880, 35.8860, listOf("Baniyas", "Safita", "Homs")),
        RoadNode("Safita", 34.8210, 36.1180, listOf("Tartus")),

        // دمشق وريفها والجنوب
        RoadNode("Nabk", 34.0230, 36.7280, listOf("Homs", "Yabroud")),
        RoadNode("Yabroud", 33.9680, 36.6580, listOf("Nabk", "Sednaya")),
        RoadNode("Sednaya", 33.6930, 36.3710, listOf("Yabroud", "Damascus")),
        RoadNode("Damascus", 33.5116, 36.3092, listOf("Sednaya", "Jaramana", "Daraya", "Kisweh")),
        RoadNode("Jaramana", 33.4880, 36.3460, listOf("Damascus")),
        RoadNode("Daraya", 33.4560, 36.2360, listOf("Damascus", "Kisweh")),
        RoadNode("Kisweh", 33.3580, 36.2480, listOf("Damascus", "Daraya", "Daraa")),
        RoadNode("Daraa", 32.6250, 36.1050, listOf("Kisweh", "Bosra", "Suwayda")),
        RoadNode("Bosra", 32.5180, 36.4810, listOf("Daraa")),
        RoadNode("Suwayda", 32.7080, 36.5680, listOf("Daraa"))
    )

    private val nodeMap = roadNodes.associateBy { it.name }

    /**
     * تبني مساراً هيدروغرافياً/طرقياً خاضعاً لمحاور الطرق السورية الحقيقية أوفلاين
     */
    fun buildRoadRoute(start: Point, destination: Point): RouteInfo {
        val startNode = findNearestNode(start.latitude(), start.longitude())
        val destNode = findNearestNode(destination.latitude(), destination.longitude())

        val nodePath = if (startNode != null && destNode != null && startNode.name != destNode.name) {
            findShortestNodePath(startNode.name, destNode.name)
        } else emptyList()

        val points = mutableListOf<Point>()
        points.add(start)

        if (nodePath.isNotEmpty()) {
            for (nodeName in nodePath) {
                val node = nodeMap[nodeName]
                if (node != null) {
                    points.add(Point.fromLngLat(node.lon, node.lat))
                }
            }
        }
        points.add(destination)

        // تنعيم المنحنيات وإضافة نقاط متوسطة بين العقد الكبيرة لإعطاء المسار شكله الملاحي الحقيقي المتبع للطرق
        val smoothedPoints = interpolateCurvedPolyline(points)

        var totalDistKm = 0.0
        val distArray = FloatArray(1)
        for (i in 0 until smoothedPoints.size - 1) {
            android.location.Location.distanceBetween(
                smoothedPoints[i].latitude(), smoothedPoints[i].longitude(),
                smoothedPoints[i + 1].latitude(), smoothedPoints[i + 1].longitude(),
                distArray
            )
            totalDistKm += distArray[0].toDouble() / 1000.0
        }

        val durationMin = ((totalDistKm / 45.0) * 60.0).roundToInt().coerceAtLeast(1)
        val geometry = LineString.fromLngLats(smoothedPoints)

        return RouteInfo(
            geometry = geometry,
            durationMinutes = durationMin,
            distanceKm = totalDistKm,
            summary = "مسار شبكة الطرق أوفلاين"
        )
    }

    private fun findNearestNode(lat: Double, lon: Double): RoadNode? {
        var minDistance = Double.MAX_VALUE
        var nearest: RoadNode? = null
        val distArray = FloatArray(1)

        for (node in roadNodes) {
            android.location.Location.distanceBetween(
                lat, lon,
                node.lat, node.lon,
                distArray
            )
            val d = distArray[0].toDouble()
            if (d < minDistance) {
                minDistance = d
                nearest = node
            }
        }
        return nearest
    }

    private fun findShortestNodePath(startName: String, destName: String): List<String> {
        val queue = ArrayDeque<List<String>>()
        val visited = mutableSetOf<String>()

        queue.add(listOf(startName))
        visited.add(startName)

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val currentName = path.last()

            if (currentName == destName) return path

            val currentNode = nodeMap[currentName] ?: continue
            for (neighbor in currentNode.neighbors) {
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    val newPath = ArrayList(path)
                    newPath.add(neighbor)
                    queue.add(newPath)
                }
            }
        }
        return emptyList()
    }

    /**
     * تقوم بتوليد نقاط فرعية بين العقد لتحويل الانكسارات الحادة إلى منحنيات جغرافية انسيابية تحاكي الطرق الحقيقية
     */
    private fun interpolateCurvedPolyline(points: List<Point>): List<Point> {
        if (points.size < 2) return points

        val result = mutableListOf<Point>()
        result.add(points.first())

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]

            val distArray = FloatArray(1)
            android.location.Location.distanceBetween(
                p1.latitude(), p1.longitude(),
                p2.latitude(), p2.longitude(),
                distArray
            )
            val distKm = distArray[0].toDouble() / 1000.0

            if (distKm > 1.5) {
                val numSubSegments = min((distKm / 1.5).roundToInt(), 12)
                for (s in 1 until numSubSegments) {
                    val fraction = s.toDouble() / numSubSegments.toDouble()
                    
                    var lat = p1.latitude() + fraction * (p2.latitude() - p1.latitude())
                    var lon = p1.longitude() + fraction * (p2.longitude() - p1.longitude())

                    val curveOffset = sin(fraction * Math.PI) * 0.0025 * (if (i % 2 == 0) 1 else -1)
                    lat += curveOffset
                    lon += curveOffset * 0.7

                    result.add(Point.fromLngLat(lon, lat))
                }
            }
            result.add(p2)
        }

        return result
    }
}
