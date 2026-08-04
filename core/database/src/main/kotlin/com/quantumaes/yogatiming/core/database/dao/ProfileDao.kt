package com.quantumaes.yogatiming.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.quantumaes.yogatiming.core.database.entity.ProfileEntity
import com.quantumaes.yogatiming.core.database.entity.ProfileSummaryProjection
import com.quantumaes.yogatiming.core.database.entity.ProfileWithStages
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    /**
     * Список профилей: агрегаты считает SQL, этапы не читаются вовсе (P1-5).
     *
     * Поиск идёт по нормализованному имени, потому что `LIKE` в SQLite
     * регистронезависим только для ASCII — по `name` запрос «хатха» не нашёл бы
     * «Хатха 60 мин».
     *
     * `ESCAPE '\'` нужен, чтобы `%` и `_`, введённые пользователем, искались
     * как обычные символы, а не как шаблон.
     */
    @Query(
        """
        SELECT p.id            AS id,
               p.uuid          AS uuid,
               p.name          AS name,
               p.category      AS category,
               p.color_tag     AS color_tag,
               p.icon_id       AS icon_id,
               p.is_favorite   AS is_favorite,
               COUNT(s.id)     AS stage_count,
               COALESCE(SUM(CASE WHEN s.type <> 'FREE'
                                 THEN s.duration_sec * (CASE WHEN s.bilateral = 1 THEN 2 ELSE 1 END)
                                 ELSE 0 END), 0)
                               AS total_duration_sec,
               COALESCE(SUM(CASE WHEN s.type =  'FREE' THEN 1 ELSE 0 END), 0)
                               AS free_stage_count
        FROM profiles p
        LEFT JOIN stages s ON s.profile_id = p.id
        WHERE (:query = '' OR p.name_normalized LIKE '%' || :query || '%' ESCAPE '\')
          AND (:category IS NULL OR p.category = :category)
          AND (:favoritesOnly = 0 OR p.is_favorite = 1)
        GROUP BY p.id
        ORDER BY p.is_favorite DESC, p.sort_order ASC, p.name_normalized ASC
        """,
    )
    fun observeSummaries(
        query: String,
        category: String?,
        favoritesOnly: Boolean,
    ): Flow<List<ProfileSummaryProjection>>

    @Transaction
    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observeProfileWithStages(id: Long): Flow<ProfileWithStages?>

    @Transaction
    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileWithStages(id: Long): ProfileWithStages?

    /** Поиск по стабильному идентификатору — понадобится импорту (P1-7). */
    @Transaction
    @Query("SELECT * FROM profiles WHERE uuid = :uuid")
    suspend fun getProfileWithStagesByUuid(uuid: String): ProfileWithStages?

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun countProfiles(): Int

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE profiles SET is_favorite = :isFavorite, updated_at = :updatedAt WHERE id = :id")
    suspend fun setFavorite(
        id: Long,
        isFavorite: Boolean,
        updatedAt: Long,
    )
}
