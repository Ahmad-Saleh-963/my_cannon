package com.ahmadsaleh.map.data.io

import kotlinx.serialization.Serializable

/**
 * النموذج الكامل للملف المُصدَّر/المُستورَد.
 * صيغة JSON احترافية مستقلة عن بنية قاعدة البيانات.
 */
@Serializable
data class MissionSnapshot(
    val version: Int = CURRENT_VERSION,
    val exportedAt: String,
    val exportedBy: String = "my_cannon",
    val cannon: SnapshotPoint? = null,
    val targets: List<SnapshotPoint> = emptyList(),
    val referencePoints: List<SnapshotPoint> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val FILE_EXTENSION  = "cannon"
        const val MIME_TYPE       = "application/json"
    }
}

@Serializable
data class SnapshotPoint(
    val id: String,
    val name: String,
    val description: String,
    val elevation: Double,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val utmEasting: Double,
    val utmNorthing: Double,
    val utmZoneNumber: Int,
    val utmZoneLetter: String
)
