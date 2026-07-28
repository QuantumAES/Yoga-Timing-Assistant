package com.quantumaes.yogatiming.timer.engine.model

/**
 * Состояние сессии (docs/02-TIMER-CORE-DESIGN.md §2).
 *
 * Состояния `TRANSITION` из ТЗ §3.1 здесь нет: как состояние движка оно вредно
 * — либо отсчёт останавливается на время анимации и за восемь этапов
 * накапливает секунды дрейфа, либо не останавливается и ничего не значит.
 * Переход стал одноразовым событием
 * [com.quantumaes.yogatiming.timer.engine.TimerEvent.StageChanged] (решение B-15).
 */
enum class RunState {
    /** План загружен, отсчёт не начат. */
    IDLE,

    RUNNING,
    PAUSED,

    /** Последний этап завершён; выход — `Restart` или `Stop`. */
    FINISHED,
    ;

    /** Идёт ли отсчёт прямо сейчас. */
    val isTicking: Boolean get() = this == RUNNING

    /** Есть ли что показывать на рабочем экране: сессия начата и не сброшена. */
    val isActive: Boolean get() = this == RUNNING || this == PAUSED
}
