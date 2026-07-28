package com.quantumaes.yogatiming.timer.engine

import com.quantumaes.yogatiming.timer.engine.model.SessionState

/**
 * Результат чистого перехода: новое состояние и список того, что следует
 * сделать снаружи (docs/02-TIMER-CORE-DESIGN.md §4).
 */
data class Reduction(
    val state: SessionState,
    val events: List<TimerEvent> = emptyList(),
) {
    companion object {
        /** Команда неприменима в текущем состоянии — молча ничего не делаем. */
        fun unchanged(state: SessionState): Reduction = Reduction(state)
    }
}
