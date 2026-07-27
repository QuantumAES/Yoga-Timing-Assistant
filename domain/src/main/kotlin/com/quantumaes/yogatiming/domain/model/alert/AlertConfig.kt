package com.quantumaes.yogatiming.domain.model.alert

import kotlinx.serialization.Serializable

/** Готовый набор настроек оповещений (ТЗ, Экран 5). */
@Serializable
enum class AlertPreset {
    /** Дефолтная схема ТЗ §5.2. */
    STANDARD,

    /** Тихий — для шавасаны и медитации: мягкая чаша, без предупреждений. */
    SILENT,

    /** Только вибрация — для залов, где звук неуместен. */
    VIBRO_ONLY,

    /** Максимум — все каналы, включая отсчёт последних секунд. */
    MAX,

    /** Пользователь отредактировал набор вручную. */
    CUSTOM,
}

/**
 * Конфигурация оповещений профиля или этапа.
 *
 * Хранится сериализованным JSON в колонке владельца (ADR-002): отдельных
 * таблиц `alert_configs` и `alerts` нет, поэтому сироты невозможны by design.
 * Тот же класс используется форматом экспорта — формат БД и формат обмена совпадают.
 *
 * @param schemaVersion версия структуры JSON. Меняется при несовместимом
 *   изменении полей и позволяет мигрировать данные без SQL-миграции.
 */
@Serializable
data class AlertConfig(
    val schemaVersion: Int = SCHEMA_VERSION,
    val preset: AlertPreset = AlertPreset.STANDARD,
    val masterVolumePercent: Int = DEFAULT_MASTER_VOLUME,
    val start: Alert? = null,
    val warnings: List<Alert> = emptyList(),
    val end: Alert? = null,
) {
    /** Предупреждения от самого раннего к самому позднему. */
    val warningsByTime: List<Alert> get() = warnings.sortedByDescending { it.offsetSec }

    /** Ни один триггер ничего не проиграет. */
    val isFullySilent: Boolean
        get() =
            (start?.isSilent ?: true) &&
                (end?.isSilent ?: true) &&
                warnings.all { it.isSilent }

    companion object {
        const val SCHEMA_VERSION = 1
        const val DEFAULT_MASTER_VOLUME = 70
        const val MIN_VOLUME = 0
        const val MAX_VOLUME = 100
    }
}
