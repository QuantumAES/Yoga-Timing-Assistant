package com.quantumaes.yogatiming.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Этап профиля.
 *
 * Единственный внешний ключ идёт от этапа к профилю с каскадом — направление
 * совпадает с владением, поэтому удаление профиля забирает этапы, а обратного
 * эффекта не существует (ADR-002, снятие блокера P0-7).
 *
 * `alert_config` = NULL означает наследование конфига профиля, а не «тишину».
 * `voice_name` = NULL означает «произносить как написано», а не «молчать».
 * `bilateral` = 1 означает, что `duration_sec` — длительность **одной стороны**:
 * на занятии такой этап проходится дважды (Фаза 11).
 */
@Entity(
    tableName = "stages",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["profile_id", "sort_order"])],
)
data class StageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    val name: String,
    val type: String,
    @ColumnInfo(name = "color_tag") val colorTag: String,
    @ColumnInfo(name = "duration_sec") val durationSec: Int,
    val note: String? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "alert_config") val alertConfigJson: String? = null,
    @ColumnInfo(name = "voice_name") val voiceName: String? = null,
    @ColumnInfo(name = "bilateral", defaultValue = "0") val bilateral: Boolean = false,
)
