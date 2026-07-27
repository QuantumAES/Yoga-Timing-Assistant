package com.quantumaes.yogatiming.domain.model

import com.quantumaes.yogatiming.domain.model.alert.AlertConfig
import com.quantumaes.yogatiming.domain.model.alert.AlertPresets

/** Идентификатор ещё не сохранённой сущности. */
const val NEW_ID: Long = 0L

/** Цвет по умолчанию для профилей и этапов (ТЗ §2). */
const val DEFAULT_COLOR_TAG: String = "#4CAF50"

/**
 * Профиль занятия со всеми этапами.
 *
 * @param uuid стабильный идентификатор для экспорта и импорта (решение P1-7).
 *   Создаётся вместе с профилем и не меняется при копировании базы.
 * @param defaultAlertConfig конфиг, который наследуют этапы без собственного.
 * @param fixedTotalSec заполняется только в режиме [TotalDurationMode.FIXED] (v1.1).
 */
data class Profile(
    val id: Long = NEW_ID,
    val uuid: String,
    val name: String,
    val category: ProfileCategory = ProfileCategory.DEFAULT,
    val colorTag: String = DEFAULT_COLOR_TAG,
    val iconId: String? = null,
    val totalDurationMode: TotalDurationMode = TotalDurationMode.DEFAULT,
    val fixedTotalSec: Int? = null,
    val isFavorite: Boolean = false,
    val sortOrder: Int = 0,
    val defaultAlertConfig: AlertConfig = AlertPresets.standard(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val stages: List<Stage> = emptyList(),
) {
    /**
     * Суммарная плановая длительность.
     *
     * FREE-этапы исключены: их длительность неизвестна заранее, поэтому сумма —
     * нижняя граница, о чём UI сообщает отдельно (решение B-4, [hasFreeStages]).
     */
    val totalDurationSec: Int get() = stages.filter { it.hasPlannedDuration }.sumOf { it.durationSec }

    val hasFreeStages: Boolean get() = stages.any { !it.hasPlannedDuration }

    /** Профиль без этапов запустить нельзя (решение B-6). */
    val isRunnable: Boolean get() = stages.isNotEmpty()
}
