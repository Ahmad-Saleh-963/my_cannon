package com.example.my_cannon.data.io

import android.content.Context
import android.net.Uri
import com.example.my_cannon.data.model.CannonPosition
import com.example.my_cannon.data.model.GeoPoint
import com.example.my_cannon.data.model.ReferencePoint
import com.example.my_cannon.data.model.TargetPosition
import com.example.my_cannon.data.model.UtmPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** نتيجة عملية الاستيراد */
sealed class ImportResult {
    data class Success(
        val cannon: CannonPosition?,
        val targets: List<TargetPosition>,
        val referencePoints: List<ReferencePoint>,
        val stats: String
    ) : ImportResult()

    data class Error(val message: String) : ImportResult()
}

/**
 * يستورد جلسة عمل من ملف JSON (Uri مُختار من منتقي الملفات).
 */
object MissionImporter {

    private val json = Json {
        ignoreUnknownKeys = true   // متسامح مع إصدارات مستقبلية
        isLenient = true
    }

    suspend fun import(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return@withContext ImportResult.Error("تعذّر فتح الملف")

            val snapshot = json.decodeFromString<MissionSnapshot>(text)

            // التحقق من الإصدار
            if (snapshot.version > MissionSnapshot.CURRENT_VERSION) {
                return@withContext ImportResult.Error(
                    "الملف مُنشأ بإصدار أحدث من التطبيق (v${snapshot.version}). يرجى تحديث التطبيق."
                )
            }

            val cannon = snapshot.cannon?.toCannonPosition()
            val targets = snapshot.targets.map { it.toTargetPosition() }
            val refs    = snapshot.referencePoints.map { it.toReferencePoint() }

            val stats = buildString {
                if (cannon != null) append("✅ مربض\n")
                if (targets.isNotEmpty()) append("✅ ${targets.size} هدف\n")
                if (refs.isNotEmpty()) append("✅ ${refs.size} نقطة علام")
            }.trim()

            ImportResult.Success(cannon, targets, refs, stats)
        } catch (e: Exception) {
            ImportResult.Error("الملف تالف أو غير صالح: ${e.message}")
        }
    }

    // ─── دوال تحويل ──────────────────────────────────────────────────────────

    private fun SnapshotPoint.toCannonPosition() = CannonPosition(
        id = id, name = name, description = description, elevation = elevation,
        geoPoint = GeoPoint(latitude, longitude, altitude),
        utmPoint = UtmPoint(utmEasting, utmNorthing, utmZoneNumber, utmZoneLetter.firstOrNull() ?: 'N')
    )

    private fun SnapshotPoint.toTargetPosition() = TargetPosition(
        id = id, name = name, description = description, elevation = elevation,
        geoPoint = GeoPoint(latitude, longitude, altitude),
        utmPoint = UtmPoint(utmEasting, utmNorthing, utmZoneNumber, utmZoneLetter.firstOrNull() ?: 'N')
    )

    private fun SnapshotPoint.toReferencePoint() = ReferencePoint(
        id = id, name = name, description = description, elevation = elevation,
        geoPoint = GeoPoint(latitude, longitude, altitude),
        utmPoint = UtmPoint(utmEasting, utmNorthing, utmZoneNumber, utmZoneLetter.firstOrNull() ?: 'N')
    )
}
