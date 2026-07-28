package com.quantumaes.yogatiming.timer.engine.schedule

import com.quantumaes.yogatiming.timer.engine.model.PlannedAlert
import com.quantumaes.yogatiming.timer.engine.model.SessionState

/**
 * Запланированный момент внутри текущего этапа (docs/02-TIMER-CORE-DESIGN.md §5).
 *
 * @param id стабильный идентификатор вида `stage3:warn:120000`. Стабильность
 *   важнее краткости: по нему [SessionState.firedAlertIds] защищает от повторов
 *   при догоне и переживает сохранение и восстановление сессии.
 * @param atElapsedMs монотонный дедлайн.
 */
data class ScheduledEvent(
    val id: String,
    val atElapsedMs: Long,
    val kind: Kind,
) {
    sealed interface Kind {
        data class Alert(
            val alert: PlannedAlert,
        ) : Kind

        /** Плановый конец этапа: переход к следующему. */
        data object StageEnd : Kind
    }
}
