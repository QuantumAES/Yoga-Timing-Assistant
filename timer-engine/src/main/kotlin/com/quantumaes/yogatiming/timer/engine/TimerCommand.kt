package com.quantumaes.yogatiming.timer.engine

import com.quantumaes.yogatiming.timer.engine.model.PauseMode
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

    /**
     * Пауза.
     *
     * @param mode останавливать ли вместе с этапом и часы занятия
     *   (см. [PauseMode]). Значение по умолчанию сохраняет поведение до
     *   Фазы 11: пауза останавливает всё.
     */
    data class Pause(
        val mode: PauseMode = PauseMode.DEFAULT,
    ) : TimerCommand

    /**
     * Переключение режима уже идущей паузы.
     *
     * Отдельная команда, а не «снять паузу и поставить заново»: снятие паузы
     * возобновило бы этап, а инструктор всего лишь уточняет, считать ли эти
     * минуты временем занятия. Вне паузы команда ничего не делает — режим
     * задаётся в момент нажатия «Пауза».
     */
    data class SetPauseMode(
        val mode: PauseMode,
    ) : TimerCommand

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

    /**
     * Ужать оставшийся план под остаток бюджета (замечание 11 полевой проверки
     * 2026-08-04).
     *
     * Дефицит распределяется по будущим этапам пропорционально их длительности.
     * Текущий этап не трогается: он уже идёт, и менять его конец под ногами у
     * инструктора — сюрприз, а не помощь.
     */
    data object FitToBudget : TimerCommand

    data object Stop : TimerCommand

    data object Restart : TimerCommand
}
