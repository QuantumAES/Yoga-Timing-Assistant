package com.quantumaes.yogatiming.core.database.entity

import androidx.room.ColumnInfo

/**
 * Результат агрегирующего запроса для экрана списка (решение P1-5).
 *
 * Количество этапов и суммарное время считает SQL, а не Kotlin: иначе каждое
 * обновление Flow тянуло бы все этапы и все конфиги оповещений всех профилей.
 */
data class ProfileSummaryProjection(
    val id: Long,
    val uuid: String,
    val name: String,
    val category: String,
    @ColumnInfo(name = "color_tag") val colorTag: String,
    @ColumnInfo(name = "icon_id") val iconId: String?,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "stage_count") val stageCount: Int,
    @ColumnInfo(name = "total_duration_sec") val totalDurationSec: Int,
    @ColumnInfo(name = "free_stage_count") val freeStageCount: Int,
)
