package com.quantumaes.yogatiming.timer.engine

import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionState
import com.quantumaes.yogatiming.timer.engine.model.holdElapsedMs
import com.quantumaes.yogatiming.timer.engine.schedule.endAlertId
import com.quantumaes.yogatiming.timer.engine.schedule.keptAlertIds
import com.quantumaes.yogatiming.timer.engine.schedule.passedEventIds

/**
 * Вход в этап (docs/02-TIMER-CORE-DESIGN.md §4.2).
 *
 * Отметки о сработавших оповещениях сбрасываются: у нового этапа собственное
 * расписание, и защита от повторов начинается с чистого листа. Уцелевают
 * только события занятия — отсечка бюджета проходится один раз, а не по разу
 * на этап (`keptAlertIds`).
 *
 * START-оповещение отрабатывает немедленно — оно и есть сигнал «этап начался».
 *
 * @param elapsedMs с какого места этапа продолжать. Ненулевое значение бывает
 *   ровно в одном случае — возврат кнопкой «Пред.» на этап, покинутый досрочно
 *   (см. `previous`). Такой вход не объявляет этап заново: инструктор
 *   исправляет промах, а не начинает новый отрезок занятия, — и не переигрывает
 *   предупреждения, которые в этом этапе уже звучали.
 */
internal fun enterStage(
    state: SessionState,
    index: Int,
    now: Long,
    reason: StageChangeReason,
    elapsedMs: Long = 0L,
): Reduction {
    require(index in state.plan.stages.indices) { "Этап $index вне плана" }

    val entered =
        state.copy(
            currentIndex = index,
            stageElapsedAtResumeMs = elapsedMs,
            resumedAtMs = now,
            firedAlertIds = state.keptAlertIds(),
        )
    val resumed = if (elapsedMs > 0L) entered.copy(firedAlertIds = entered.passedEventIds(now)) else entered
    val events =
        buildList {
            add(TimerEvent.StageChanged(from = state.currentIndex, to = index, reason = reason))
            if (elapsedMs == 0L) {
                resumed.currentStage.alerts.start?.let {
                    add(TimerEvent.PlayAlert(alert = it, stageIndex = index, scheduledAtMs = now))
                }
            }
        }
    return Reduction(resumed, events)
}

/**
 * Завершение текущего этапа: END-оповещение и переход дальше.
 *
 * END не дублируется: при автоматическом завершении оно уже отработало как
 * обычное событие расписания и отмечено в `firedAlertIds`, а при ручном
 * переходе — не отмечено и проигрывается здесь.
 */
internal fun completeStage(
    state: SessionState,
    now: Long,
    reason: StageChangeReason,
): Reduction {
    val endEvents = endAlertEvents(state, now)
    if (state.isLastStage) return finish(state, now, endEvents)

    val left = state.leaveStage(now)
    val entered = enterStage(left, state.currentIndex + 1, now, reason)
    return Reduction(entered.state, endEvents + entered.events)
}

/**
 * Переход в FINISHED.
 *
 * Время последнего этапа не переносится в `actualDurationsMs`, а замораживается
 * в `stageElapsedAtResumeMs`: инвариант «в actualDurationsMs только покинутые
 * этапы» сохраняется, общее время не удваивается, а рабочий экран продолжает
 * показывать, сколько занял последний этап.
 */
internal fun finish(
    state: SessionState,
    now: Long,
    preceding: List<TimerEvent>,
): Reduction {
    val finished =
        state.copy(
            runState = RunState.FINISHED,
            stageElapsedAtResumeMs = state.stageElapsedMs(now),
            resumedAtMs = now,
            firedAlertIds = state.firedAlertIds + endAlertId(state.currentIndex),
        )
    return Reduction(
        finished,
        preceding +
            TimerEvent.SessionFinished(
                totalElapsedMs = finished.totalElapsedMs(now),
                holdMs = finished.holdElapsedMs(now),
                adjustmentsMs = finished.adjustmentsMs,
            ),
    )
}

/**
 * END-оповещение этапа, если оно есть и ещё не отработало.
 *
 * `scheduledAtMs = now`: при ручном переходе конец этапа наступает именно
 * сейчас, а не в плановый момент, — опоздания нет и отбрасывать нечего.
 */
private fun endAlertEvents(
    state: SessionState,
    now: Long,
): List<TimerEvent> {
    val end = state.currentStage.alerts.end ?: return emptyList()
    if (endAlertId(state.currentIndex) in state.firedAlertIds) return emptyList()
    return listOf(TimerEvent.PlayAlert(alert = end, stageIndex = state.currentIndex, scheduledAtMs = now))
}
