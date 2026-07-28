package com.quantumaes.yogatiming.timer.engine

import com.quantumaes.yogatiming.timer.engine.model.SessionPlan

/**
 * Всё, что может произойти с сессией по воле пользователя
 * (docs/02-TIMER-CORE-DESIGN.md §4).
 *
 * Команды — единственный вход в движок. Экран занятия, действия уведомления и
 * восстановление после kill идут одним и тем же путём, поэтому поведение
 * «нажал Далее в шторке» невозможно рассогласовать с «нажал Далее на экране».
 */
sealed interface TimerCommand {
    /** Загрузить план и перейти в IDLE. Предыдущая сессия отбрасывается. */
    data class Load(
        val plan: SessionPlan,
    ) : TimerCommand

    data object Start : TimerCommand

    data object Pause : TimerCommand

    data object Resume : TimerCommand

    data object Next : TimerCommand

    data object Previous : TimerCommand

    /**
     * Правка длительности текущего этапа (решения B-1, B-2).
     *
     * @param deltaMs обычно ±[TimerLimits.ADJUST_STEP_MS].
     */
    data class Adjust(
        val deltaMs: Long,
    ) : TimerCommand

    data object Stop : TimerCommand

    data object Restart : TimerCommand
}
