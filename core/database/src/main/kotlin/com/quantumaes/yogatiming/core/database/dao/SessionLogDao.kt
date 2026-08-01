package com.quantumaes.yogatiming.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.quantumaes.yogatiming.core.database.entity.ProfileTotalsProjection
import com.quantumaes.yogatiming.core.database.entity.SessionDayProjection
import com.quantumaes.yogatiming.core.database.entity.SessionLogEntity
import com.quantumaes.yogatiming.core.database.entity.SessionTotalsProjection
import kotlinx.coroutines.flow.Flow

/**
 * Журнал занятий (docs/09-STATISTICS.md §3).
 *
 * Все разрезы — проекциями, как у профилей: агрегаты считает SQLite, а не
 * Kotlin по списку сущностей. Границы периода приходят строками `YYYY-MM-DD`
 * и сравниваются `BETWEEN` — формат ISO упорядочен лексикографически, поэтому
 * ни функций даты, ни арифметики со смещениями в запросах нет.
 */
@Dao
interface SessionLogDao {
    @Insert
    suspend fun insert(entry: SessionLogEntity): Long

    @Query("DELETE FROM session_log WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """
        SELECT * FROM session_log
        WHERE local_date BETWEEN :from AND :to
        ORDER BY started_at_ms DESC
        """,
    )
    fun observeSessions(
        from: String,
        to: String,
    ): Flow<List<SessionLogEntity>>

    @Query(
        """
        SELECT local_date            AS local_date,
               COUNT(*)              AS session_count,
               COALESCE(SUM(duration_ms), 0) AS duration_ms
        FROM session_log
        WHERE local_date BETWEEN :from AND :to
        GROUP BY local_date
        ORDER BY local_date ASC
        """,
    )
    fun observeDays(
        from: String,
        to: String,
    ): Flow<List<SessionDayProjection>>

    /**
     * Сводка одной строкой. `COALESCE` обязателен: у `SUM` по пустой выборке
     * результат `NULL`, и без него пустой период ронял бы маппер, а не
     * показывал нули.
     */
    @Query(
        """
        SELECT COUNT(*)                       AS session_count,
               COALESCE(SUM(duration_ms), 0)  AS duration_ms,
               COUNT(DISTINCT local_date)     AS days_practiced
        FROM session_log
        WHERE local_date BETWEEN :from AND :to
        """,
    )
    fun observeTotals(
        from: String,
        to: String,
    ): Flow<SessionTotalsProjection>

    @Query(
        """
        SELECT profile_name                   AS profile_name,
               COUNT(*)                       AS session_count,
               COALESCE(SUM(duration_ms), 0)  AS duration_ms
        FROM session_log
        WHERE local_date BETWEEN :from AND :to
        GROUP BY profile_name
        ORDER BY duration_ms DESC, profile_name ASC
        """,
    )
    fun observeByProfile(
        from: String,
        to: String,
    ): Flow<List<ProfileTotalsProjection>>
}
