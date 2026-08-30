package com.ahmadsaleh.map.data.repository

import com.ahmadsaleh.map.data.db.AppDatabase
import com.ahmadsaleh.map.data.db.entity.CannonEntity
import com.ahmadsaleh.map.data.db.entity.ReferencePointEntity
import com.ahmadsaleh.map.data.db.entity.TargetEntity
import com.ahmadsaleh.map.data.model.CannonPosition
import com.ahmadsaleh.map.data.model.ReferencePoint
import com.ahmadsaleh.map.data.model.TargetPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository موحد لكل عمليات قاعدة البيانات.
 * يُقدم واجهة نظيفة للـ ViewModel دون كشف تفاصيل Room.
 */
class PointsRepository(db: AppDatabase) {

    private val cannonDao = db.cannonDao()
    private val targetDao = db.targetDao()
    private val refDao    = db.referencePointDao()

    // ─── المربط ──────────────────────────────────────────────────────────────

    /** Flow يُصدر المربط عند كل تغيير في قاعدة البيانات */
    fun observeCannon(): Flow<CannonPosition?> =
        cannonDao.observeCannon().map { it?.toModel() }

    suspend fun saveCannon(cannon: CannonPosition) =
        cannonDao.upsert(CannonEntity.fromModel(cannon))

    suspend fun deleteCannon() =
        cannonDao.deleteAll()

    // ─── الأهداف ─────────────────────────────────────────────────────────────

    /** Flow يُصدر قائمة الأهداف مُرتَّبة حسب وقت الإضافة */
    fun observeTargets(): Flow<List<TargetPosition>> =
        targetDao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun saveTarget(target: TargetPosition, order: Long = System.currentTimeMillis()) =
        targetDao.upsert(TargetEntity.fromModel(target, order))

    suspend fun deleteTarget(id: String) =
        targetDao.deleteById(id)

    suspend fun deleteAllTargets() =
        targetDao.deleteAll()

    // ─── نقاط العلام ────────────────────────────────────────────────────────

    /** Flow يُصدر نقاط العلام مُرتَّبة حسب وقت الإضافة */
    fun observeReferencePoints(): Flow<List<ReferencePoint>> =
        refDao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun saveReferencePoint(ref: ReferencePoint, order: Long = System.currentTimeMillis()) =
        refDao.upsert(ReferencePointEntity.fromModel(ref, order))

    suspend fun deleteReferencePoint(id: String) =
        refDao.deleteById(id)

    suspend fun deleteAllReferencePoints() =
        refDao.deleteAll()

    // ─── مسح شامل ───────────────────────────────────────────────────────────

    suspend fun deleteAll() {
        cannonDao.deleteAll()
        targetDao.deleteAll()
        refDao.deleteAll()
    }

    // ─── قراءة فورية (للتصدير) ──────────────────────────────────────────────

    suspend fun snapshot(): Triple<CannonPosition?, List<TargetPosition>, List<ReferencePoint>> =
        Triple(
            cannonDao.getCannon()?.toModel(),
            targetDao.getAll().map { it.toModel() },
            refDao.getAll().map { it.toModel() }
        )
}
