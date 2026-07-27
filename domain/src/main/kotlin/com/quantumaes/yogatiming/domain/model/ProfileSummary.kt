package com.quantumaes.yogatiming.domain.model

/**
 * Лёгкая проекция профиля для списка (решение P1-5).
 *
 * Экран списка показывает название, категорию, количество этапов и общее время.
 * Тянуть ради этого все этапы и все конфиги оповещений каждого профиля —
 * лишняя работа на каждом обновлении Flow, поэтому агрегаты считает SQL.
 */
data class ProfileSummary(
    val id: Long,
    val uuid: String,
    val name: String,
    val category: ProfileCategory,
    val colorTag: String,
    val iconId: String?,
    val isFavorite: Boolean,
    val stageCount: Int,
    val totalDurationSec: Int,
    val hasFreeStages: Boolean,
) {
    /** Профиль без этапов запустить нельзя (решение B-6). */
    val isRunnable: Boolean get() = stageCount > 0
}
