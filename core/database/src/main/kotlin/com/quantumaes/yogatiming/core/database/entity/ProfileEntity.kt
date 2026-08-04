package com.quantumaes.yogatiming.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Профиль занятия.
 *
 * Конфигурация оповещений лежит JSON-строкой в колонке `default_alert_config`,
 * а не в отдельных таблицах, как предлагало ТЗ §2 (ADR-002): нормализация там
 * не окупалась, зато порождала инвертированные каскады и сирот. Сериализация
 * выполняется в мапперах — сущность остаётся честным отражением строки таблицы.
 *
 * `uuid` уникален и служит стабильным ключом для импорта (P1-7).
 *
 * `name_normalized` — имя в нижнем регистре для поиска. Отдельная колонка нужна
 * потому, что `LIKE` и `LOWER()` в SQLite регистронезависимы только для ASCII:
 * без неё поиск «хатха» не нашёл бы профиль «Хатха».
 *
 * `total_duration_mode` и `fixed_total_sec` создаются с первой версии схемы,
 * хотя UI для режима FIXED появится только в v1.1 — чтобы не делать миграцию
 * ради двух колонок (docs/06-MVP-SCOPE.md §1.2).
 *
 * `target_duration_sec` — целевое время занятия (Фаза 11). NULL означает «цели
 * нет», а не «ноль»: занятие без цели длится столько, сколько сумма этапов, и
 * это законный сценарий, а не незаполненное поле.
 */
@Entity(
    tableName = "profiles",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["is_favorite", "sort_order"]),
        Index(value = ["name_normalized"]),
    ],
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    @ColumnInfo(name = "name_normalized") val nameNormalized: String,
    val category: String,
    @ColumnInfo(name = "color_tag") val colorTag: String,
    @ColumnInfo(name = "icon_id") val iconId: String? = null,
    @ColumnInfo(name = "total_duration_mode") val totalDurationMode: String,
    @ColumnInfo(name = "fixed_total_sec") val fixedTotalSec: Int? = null,
    @ColumnInfo(name = "target_duration_sec") val targetDurationSec: Int? = null,
    @ColumnInfo(name = "target_tolerance_sec", defaultValue = "0") val targetToleranceSec: Int = 0,
    @ColumnInfo(name = "wrap_up_offset_sec", defaultValue = "600") val wrapUpOffsetSec: Int = 600,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "default_alert_config") val defaultAlertConfigJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
