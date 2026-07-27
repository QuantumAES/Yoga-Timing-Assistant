package com.quantumaes.yogatiming.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.quantumaes.yogatiming.core.database.entity.StageEntity

/**
 * Операции над этапами.
 *
 * Отдельно от [ProfileDao] намеренно: составную транзакцию «сохранить профиль
 * вместе с этапами» собирает репозиторий через `withTransaction`, а DAO
 * остаются тонкими каталогами запросов.
 */
@Dao
interface StageDao {
    @Upsert
    suspend fun upsertAll(stages: List<StageEntity>)

    /** Удаляет этапы профиля, которых больше нет в редакторе. */
    @Query("DELETE FROM stages WHERE profile_id = :profileId AND id NOT IN (:keepIds)")
    suspend fun deleteNotIn(
        profileId: Long,
        keepIds: List<Long>,
    )

    @Query("DELETE FROM stages WHERE profile_id = :profileId")
    suspend fun deleteAllForProfile(profileId: Long)

    @Query("SELECT COUNT(*) FROM stages")
    suspend fun countAll(): Int
}
