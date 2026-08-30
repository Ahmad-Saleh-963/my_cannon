package com.ahmadsaleh.map.domain.calculator

import com.ahmadsaleh.map.data.model.GeoPoint
import com.ahmadsaleh.map.data.model.UtmPoint
import kotlin.math.*

/**
 * محول إحداثيات الخريطة (WGS84) إلى نظام UTM (Easting/Northing) بالأمتار
 * لضمان دقة حسابات المسافة والسمت.
 */
object UtmConverter {
    private const val RADIUS = 6378137.0 // نصف قطر الأرض A (WGS84)
    private const val ECCENTRICITY_SQUARED = 0.00669438 // اللامركزية المربعة

    fun fromGeoToUtm(geo: GeoPoint): UtmPoint {
        val lat = geo.latitude
        val lon = geo.longitude

        val lon0 = (floor((lon + 180) / 6) * 6 - 180 + 3).toDouble()
        val zoneNumber = ((lon + 180) / 6).toInt() + 1
        
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        val lon0Rad = Math.toRadians(lon0)

        val k0 = 0.9996
        val n = RADIUS / sqrt(1 - ECCENTRICITY_SQUARED * sin(latRad).pow(2))
        val t = tan(latRad).pow(2)
        val c = ECCENTRICITY_SQUARED * cos(latRad).pow(2) / (1 - ECCENTRICITY_SQUARED)
        val a = cos(latRad) * (lonRad - lon0Rad)

        val m = RADIUS * (
            (1 - ECCENTRICITY_SQUARED / 4 - 3 * ECCENTRICITY_SQUARED.pow(2) / 64 - 5 * ECCENTRICITY_SQUARED.pow(3) / 256) * latRad -
            (3 * ECCENTRICITY_SQUARED / 8 + 3 * ECCENTRICITY_SQUARED.pow(2) / 32 + 45 * ECCENTRICITY_SQUARED.pow(3) / 1024) * sin(2 * latRad) +
            (15 * ECCENTRICITY_SQUARED.pow(2) / 256 + 45 * ECCENTRICITY_SQUARED.pow(3) / 1024) * sin(4 * latRad) -
            (35 * ECCENTRICITY_SQUARED.pow(3) / 3072) * sin(6 * latRad)
        )

        val easting = k0 * n * (a + (1 - t + c) * a.pow(3) / 6 + (5 - 18 * t + t.pow(2) + 72 * c - 58 * ECCENTRICITY_SQUARED) * a.pow(5) / 120) + 500000.0
        var northing = k0 * (m + n * tan(latRad) * (a.pow(2) / 2 + (5 - t + 9 * c + 4 * c.pow(2)) * a.pow(4) / 24 + (61 - 58 * t + t.pow(2) + 600 * c - 330 * ECCENTRICITY_SQUARED) * a.pow(6) / 720))
        
        if (lat < 0) {
            northing += 10000000.0
        }

        return UtmPoint(easting, northing, zoneNumber)
    }

    fun fromUtmToGeo(utm: UtmPoint): GeoPoint {
        val k0 = 0.9996
        val a = RADIUS
        val eSq = ECCENTRICITY_SQUARED
        val e1 = (1 - sqrt(1 - eSq)) / (1 + sqrt(1 - eSq))

        val x = utm.easting - 500000.0
        val y = if (utm.zoneLetter < 'N') utm.northing - 10000000.0 else utm.northing

        val m = y / k0
        val mu = m / (a * (1 - eSq / 4 - 3 * eSq.pow(2) / 64 - 5 * eSq.pow(3) / 256))

        val phi1Rad = mu + (3 * e1 / 2 - 27 * e1.pow(3) / 32) * sin(2 * mu) +
                (21 * e1.pow(2) / 16 - 55 * e1.pow(4) / 32) * sin(4 * mu) +
                (151 * e1.pow(3) / 96) * sin(6 * mu)

        val n1 = a / sqrt(1 - eSq * sin(phi1Rad).pow(2))
        val t1 = tan(phi1Rad).pow(2)
        val c1 = eSq * cos(phi1Rad).pow(2) / (1 - eSq)
        val r1 = a * (1 - eSq) / (1 - eSq * sin(phi1Rad).pow(2)).pow(1.5)
        val d = x / (n1 * k0)

        val latRad = phi1Rad - (n1 * tan(phi1Rad) / r1) * (d.pow(2) / 2 - (5 + 3 * t1 + 10 * c1 - 4 * c1.pow(2) - 9 * eSq) * d.pow(4) / 24 +
                (61 + 90 * t1 + 298 * c1 + 45 * t1.pow(2) - 252 * eSq - 3 * c1.pow(2)) * d.pow(6) / 720)
        
        val lon0 = ((utm.zoneNumber - 1) * 6 - 180 + 3).toDouble()
        val lonRad = (d - (1 + 2 * t1 + c1) * d.pow(3) / 6 + (5 - 2 * c1 + 28 * t1 - 3 * c1.pow(2) + 8 * eSq + 24 * t1.pow(2)) * d.pow(5) / 120) / cos(phi1Rad)
        
        return GeoPoint(Math.toDegrees(latRad), lon0 + Math.toDegrees(lonRad))
    }
}
