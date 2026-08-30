package com.example.my_cannon.data.io

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.my_cannon.data.model.CannonPosition
import com.example.my_cannon.data.model.ReferencePoint
import com.example.my_cannon.data.model.TargetPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * يُصدِّر جلسة العمل الكاملة إلى ملف JSON قابل للمشاركة.
 */
object MissionExporter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * يُنشئ ملف التصدير ويُعيد [Intent] جاهزاً للمشاركة.
     * يجب استدعاؤه من coroutine.
     *
     * @return Intent للمشاركة، أو null في حال الفشل.
     */
    suspend fun exportAndShare(
        context: Context,
        cannon: CannonPosition?,
        targets: List<TargetPosition>,
        referencePoints: List<ReferencePoint>
    ): Intent? = withContext(Dispatchers.IO) {
        try {
            val snapshot = MissionSnapshot(
                exportedAt = Instant.now().toString(),
                cannon = cannon?.toSnapshot(),
                targets = targets.map { it.toSnapshot() },
                referencePoints = referencePoints.map { it.toSnapshot() }
            )

            val jsonStr = json.encodeToString(snapshot)

            // حفظ في مجلد مؤقت مُخصص للمشاركة
            val exportDir = File(context.cacheDir, "mission_exports")
            exportDir.mkdirs()
            val fileName = "mission_${System.currentTimeMillis()}.${MissionSnapshot.FILE_EXTENSION}"
            val file = File(exportDir, fileName)
            file.writeText(jsonStr, Charsets.UTF_8)

            // إنشاء URI آمن عبر FileProvider
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            Intent(Intent.ACTION_SEND).apply {
                type = MissionSnapshot.MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "جلسة مربط مدفعي")
                putExtra(Intent.EXTRA_TEXT, "ملف جلسة المدفعية — قابل للاستيراد في تطبيق my_cannon")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ─── دوال تحويل النماذج ──────────────────────────────────────────────────

    private fun CannonPosition.toSnapshot() = SnapshotPoint(
        id = id, name = name, description = description, elevation = elevation,
        latitude = geoPoint.latitude, longitude = geoPoint.longitude, altitude = geoPoint.altitude,
        utmEasting = utmPoint.easting, utmNorthing = utmPoint.northing,
        utmZoneNumber = utmPoint.zoneNumber, utmZoneLetter = utmPoint.zoneLetter.toString()
    )

    private fun TargetPosition.toSnapshot() = SnapshotPoint(
        id = id, name = name, description = description, elevation = elevation,
        latitude = geoPoint.latitude, longitude = geoPoint.longitude, altitude = geoPoint.altitude,
        utmEasting = utmPoint.easting, utmNorthing = utmPoint.northing,
        utmZoneNumber = utmPoint.zoneNumber, utmZoneLetter = utmPoint.zoneLetter.toString()
    )

    private fun ReferencePoint.toSnapshot() = SnapshotPoint(
        id = id, name = name, description = description, elevation = elevation,
        latitude = geoPoint.latitude, longitude = geoPoint.longitude, altitude = geoPoint.altitude,
        utmEasting = utmPoint.easting, utmNorthing = utmPoint.northing,
        utmZoneNumber = utmPoint.zoneNumber, utmZoneLetter = utmPoint.zoneLetter.toString()
    )
}
