package com.ahmadsaleh.map.domain.calculator

import com.ahmadsaleh.map.data.db.entity.CachedRouteEntity
import com.ahmadsaleh.map.ui.viewmodel.RouteInfo
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import kotlin.math.roundToInt

object SmartRouteMatcher {

    /**
     * تبحث عن المسارات المخزنة أوفلاين وتستخرج المسارات المباشرة أو الجزئية (Sub-segments)
     * مع حساب المسافة والمدة بدقة متناهية.
     */
    fun findMatchingRoutes(
        start: Point,
        destination: Point,
        cachedEntities: List<CachedRouteEntity>
    ): List<RouteInfo> {
        val matchedRoutes = mutableListOf<RouteInfo>()

        for (entity in cachedEntities) {
            val lineString = try {
                LineString.fromJson(entity.geoJsonGeometry)
            } catch (_: Exception) {
                continue
            }

            val coordinates = lineString.coordinates()
            if (coordinates.size < 2) continue

            // 1. حساب أقرب نقطة على المسار للبداية (Start) والنهاية (Destination)
            var minStartDist = Double.MAX_VALUE
            var startIdx = -1

            var minDestDist = Double.MAX_VALUE
            var destIdx = -1

            val results = FloatArray(1)

            for (i in coordinates.indices) {
                val pt = coordinates[i]
                
                // المسافة لنقطة البداية المطلوبة
                android.location.Location.distanceBetween(
                    start.latitude(), start.longitude(),
                    pt.latitude(), pt.longitude(),
                    results
                )
                val distToStart = results[0].toDouble() / 1000.0
                if (distToStart < minStartDist) {
                    minStartDist = distToStart
                    startIdx = i
                }

                // المسافة لنقطة النهاية المطلوبة
                android.location.Location.distanceBetween(
                    destination.latitude(), destination.longitude(),
                    pt.latitude(), pt.longitude(),
                    results
                )
                val distToDest = results[0].toDouble() / 1000.0
                if (distToDest < minDestDist) {
                    minDestDist = distToDest
                    destIdx = i
                }
            }

            // 2. التحقق من صحة المطابقة:
            // - البداية والنهاية تقعان بالقرب الشديد من الطريق الفعلي المخزن (أقل من 1.5 كم)
            // - ترتيب نقطة البداية يأتي قبل نقطة النهاية اتجاهياً (startIdx < destIdx)
            // - عدد نقاط الطريق يضمن وجود منحنيات وشوارع حقيقية (destIdx - startIdx >= 3)
            if (startIdx != -1 && destIdx != -1 && startIdx < destIdx && (destIdx - startIdx) >= 3 && minStartDist < 1.5 && minDestDist < 1.5) {
                val rawSubList = coordinates.subList(startIdx, destIdx + 1)
                
                val subPoints = mutableListOf<Point>()
                if (minStartDist > 0.05) subPoints.add(start)
                subPoints.addAll(rawSubList)
                if (minDestDist > 0.05) subPoints.add(destination)

                if (subPoints.size < 4) continue

                // حساب المسافة الكلية للقطاع الفرعي المستخرج
                var subDistanceKm = 0.0
                for (k in 0 until subPoints.size - 1) {
                    android.location.Location.distanceBetween(
                        subPoints[k].latitude(), subPoints[k].longitude(),
                        subPoints[k + 1].latitude(), subPoints[k + 1].longitude(),
                        results
                    )
                    subDistanceKm += results[0].toDouble() / 1000.0
                }

                if (subDistanceKm <= 0.05) continue

                val subDurationMin = ((subDistanceKm / 45.0) * 60.0).roundToInt().coerceAtLeast(1)
                val subGeometry = LineString.fromLngLats(subPoints)

                val summaryTag = if (entity.summary.isNotBlank() && !entity.summary.startsWith("طريق بديل")) entity.summary else "مسار موثوق"

                matchedRoutes.add(
                    RouteInfo(
                        geometry = subGeometry,
                        durationMinutes = subDurationMin,
                        distanceKm = subDistanceKm,
                        summary = summaryTag
                    )
                )
            }
        }

        return matchedRoutes.distinctBy { String.format(java.util.Locale.US, "%.1f_%.1f", it.distanceKm, it.durationMinutes.toDouble()) }.take(3)
    }
}
