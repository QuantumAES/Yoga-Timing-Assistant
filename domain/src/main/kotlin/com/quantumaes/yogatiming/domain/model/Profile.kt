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
 * @param targetDurationSec целевое время занятия — сколько времени есть на
 *   самом деле: аренда зала, оплаченный час, окно между группами. `null` —
 *   занятие длится столько, сколько сумма его этапов (поведение до Фазы 11).
 *   Это **не** [fixedTotalSec]: тот распределяет время по этапам при
 *   планировании, а это — граница, о которой приложение напоминает на занятии.
 * @param targetToleranceSec допустимый перерасход. Ноль — «ровно столько»
 *   (индивидуальное занятие); десять минут — «арендодатель не возражает»
 *   (групповое). Влияет на то, с какого момента расхождение показывается
 *   тревожно, а не на сам отсчёт.
 * @param wrapUpOffsetSec за сколько до целевого конца звучит предупреждение
 *   о завершении. Ноль — предупреждения нет.
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
    val targetDurationSec: Int? = null,
    val targetToleranceSec: Int = 0,
    val wrapUpOffsetSec: Int = DEFAULT_WRAP_UP_SEC,
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
    val totalDurationSec: Int get() = stages.filter { it.hasPlannedDuration }.sumOf { it.plannedDurationSec }

    val hasFreeStages: Boolean get() = stages.any { !it.hasPlannedDuration }

    /** Профиль без этапов запустить нельзя (решение B-6). */
    val isRunnable: Boolean get() = stages.isNotEmpty()

    /** Задано ли целевое время занятия. */
    val hasTarget: Boolean get() = (targetDurationSec ?: 0) > 0

    /**
     * Сколько времени целевого бюджета ещё не распределено по этапам.
     *
     * Положительное — осталось разложить, отрицательное — план длиннее, чем
     * занятие может себе позволить. `null` — цели нет и сравнивать не с чем
     * (замечание 8 полевой проверки 2026-08-04).
     */
    val unallocatedSec: Int? get() = targetDurationSec?.takeIf { it > 0 }?.minus(totalDurationSec)

    companion object {
        /**
         * Отсечка за десять минут до конца — по умолчанию.
         *
         * Десять минут это шавасана плюс выход из неё: столько нужно, чтобы
         * завершить занятие спокойно, а не оборвать его. Инструктор, у которого
         * финал длиннее или короче, поправит число под свой сценарий.
         */
        const val DEFAULT_WRAP_UP_SEC = 600

        /** Границы целевого времени: 5 минут … 4 часа. */
        const val MIN_TARGET_SEC = 5 * 60
        const val MAX_TARGET_SEC = 4 * 60 * 60
    }
}
