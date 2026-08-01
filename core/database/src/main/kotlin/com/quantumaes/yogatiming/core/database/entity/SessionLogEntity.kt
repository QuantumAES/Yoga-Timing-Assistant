package com.quantumaes.yogatiming.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Строка журнала занятий (docs/09-STATISTICS.md, схема §3).
 *
 * Ссылка на профиль — `ON DELETE SET NULL`, а имя профиля продублировано
 * колонкой (D-S6): удаление профиля не стирает проведённые по нему занятия,
 * а переименование не переписывает историю. Индексы стоят по всем трём
 * колонкам, по которым журнал спрашивают: день (календарь и сводка), метка
 * начала (сортировка журнала), профиль (разрез «по профилям»).
 *
 * `local_date` — строка `YYYY-MM-DD`, а не вычисление из `started_at_ms` при
 * чтении: см. D-S4. Формат ISO выбран не случайно — лексикографический порядок
 * строк совпадает с хронологическим, поэтому `BETWEEN` по нему работает без
 * преобразований.
 */
@Entity(
    tableName = "session_log",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["local_date"]),
        Index(value = ["started_at_ms"]),
        Index(value = ["profile_id"]),
    ],
)
data class SessionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id") val profileId: Long?,
    @ColumnInfo(name = "profile_name") val profileName: String,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "started_at_ms") val startedAtMs: Long,
    @ColumnInfo(name = "finished_at_ms") val finishedAtMs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "planned_ms") val plannedMs: Long,
    @ColumnInfo(name = "stages_completed") val stagesCompleted: Int,
    @ColumnInfo(name = "stage_count") val stageCount: Int,
    val outcome: String,
)

/** День календаря: сколько занятий и сколько времени (запрос `observeDays`). */
data class SessionDayProjection(
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "session_count") val sessionCount: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
)

/** Сводка за период одной строкой — считает SQLite (R-S4). */
data class SessionTotalsProjection(
    @ColumnInfo(name = "session_count") val sessionCount: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "days_practiced") val daysPracticed: Int,
)

/** Разрез «по профилям». */
data class ProfileTotalsProjection(
    @ColumnInfo(name = "profile_name") val profileName: String,
    @ColumnInfo(name = "session_count") val sessionCount: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
)
