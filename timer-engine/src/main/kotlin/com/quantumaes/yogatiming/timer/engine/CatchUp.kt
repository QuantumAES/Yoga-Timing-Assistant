package com.quantumaes.yogatiming.timer.engine

import com.quantumaes.yogatiming.timer.engine.model.AlertTrigger
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionState
import com.quantumaes.yogatiming.timer.engine.schedule.ScheduledEvent
import com.quantumaes.yogatiming.timer.engine.schedule.dueEvents

/**
 * Отработка наступивших событий, в том числе далеко просроченных
 * (docs/02-TIMER-CORE-DESIGN.md §7).
 *
 * Процесс мог быть заморожен Doze, убит OEM-надстройкой и воскрешён
 * watchdog-алармом или просто не получить процессорного времени. При возврате
 * [now] оказывается за несколькими дедлайнами сразу, и движок обязан пройти их
 * все — иначе состояние навсегда останется в прошлом.
 *
 * Это же единственный путь автоматического перехода между этапами: и штатное
 * пробуждение по дедлайну, и воскрешение после заморозки идут одной веткой кода.
 */
fun catchUp(
    state: SessionState,
    now: Long,
): Reduction {
    if (state.runState != RunState.RUNNING) return Reduction.unchanged(state)
    val earliestDue = state.dueEvents(now).minOfOrNull { it.atElapsedMs } ?: return Reduction.unchanged(state)

    val processed = drainDueEvents(state, now)
    val delivery = filterOutdatedAlerts(processed.events, processed.state, now)

    val driftMs = now - earliestDue
    val events =
        if (driftMs > TimerLimits.DRIFT_REPORT_THRESHOLD_MS) {
            delivery.delivered + TimerEvent.DriftDetected(driftMs, delivery.dropped)
        } else {
            delivery.delivered
        }
    return Reduction(processed.state, events)
}

/**
 * Проход по всем наступившим событиям.
 *
 * Расписание перечитывается на каждом шаге: завершение этапа меняет и текущий
 * индекс, и весь список дедлайнов, поэтому итерация по разово вычисленному
 * списку работала бы с устаревшими данными.
 *
 * Смена этапа датируется **плановой границей**, а не моментом пробуждения.
 * Это и есть механика догона: следующий этап начинает отсчёт с того момента,
 * когда он должен был начаться, поэтому после заморозки на десять минут движок
 * за несколько итераций доходит до этапа, идущего прямо сейчас. Датируй мы
 * переход текущим временем — движок навсегда отстал бы ровно на длительность
 * заморозки.
 */
private fun drainDueEvents(
    state: SessionState,
    now: Long,
): Reduction {
    var current = state
    val events = mutableListOf<TimerEvent>()

    while (current.runState == RunState.RUNNING) {
        val due = current.dueEvents(now).firstOrNull() ?: break
        when (val kind = due.kind) {
            is ScheduledEvent.Kind.Alert -> {
                events +=
                    TimerEvent.PlayAlert(
                        alert = kind.alert,
                        stageIndex = current.currentIndex,
                        scheduledAtMs = due.atElapsedMs,
                    )
                current = current.copy(firedAlertIds = current.firedAlertIds + due.id)
            }

            ScheduledEvent.Kind.StageEnd -> {
                val completion = completeStage(current, due.atElapsedMs, StageChangeReason.AUTO)
                current = completion.state
                events += completion.events
            }
        }
    }
    return Reduction(current, events)
}

private class AlertDelivery(
    val delivered: List<TimerEvent>,
    val dropped: Int,
)

/**
 * Отсев безнадёжно просроченных оповещений.
 *
 * Проиграть «осталось 2 минуты», когда этап закончился три минуты назад, хуже,
 * чем промолчать: молчание инструктор спишет на настройки, а ложный сигнал
 * собьёт с плана.
 *
 * Исключение — START того этапа, на котором мы фактически оказались. Оно
 * проигрывается независимо от опоздания: без него инструктор не поймёт, где
 * находится занятие после того, как система вернула приложению управление.
 */
private fun filterOutdatedAlerts(
    events: List<TimerEvent>,
    finalState: SessionState,
    now: Long,
): AlertDelivery {
    val landedStart =
        events
            .filterIsInstance<TimerEvent.PlayAlert>()
            .lastOrNull { it.stageIndex == finalState.currentIndex && it.alert.trigger == AlertTrigger.START }
            .takeIf { finalState.runState == RunState.RUNNING }

    val delivered = mutableListOf<TimerEvent>()
    var dropped = 0
    for (event in events) {
        val outdated =
            event is TimerEvent.PlayAlert &&
                now - event.scheduledAtMs > TimerLimits.LATE_TOLERANCE_MS &&
                event !== landedStart
        if (outdated) dropped++ else delivered += event
    }
    return AlertDelivery(delivered, dropped)
}
