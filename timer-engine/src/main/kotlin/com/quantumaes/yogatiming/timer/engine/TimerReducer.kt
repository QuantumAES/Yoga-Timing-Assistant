package com.quantumaes.yogatiming.timer.engine

import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionState
import com.quantumaes.yogatiming.timer.engine.schedule.passedEventIds

/**
 * Ядро движка: чистая функция перехода (docs/02-TIMER-CORE-DESIGN.md §4).
 *
 * Ничего не проигрывает, не сохраняет и не показывает — возвращает новое
 * состояние и список побочных эффектов как данные. Отсюда следует, что вся
 * таблица переходов §4.1 проверяется юнит-тестами без единого мока, а
 * `:timer-service` остаётся тонким исполнителем.
 *
 * Команда, неприменимая в текущем состоянии, — не ошибка: кнопка могла быть
 * нажата в шторке за мгновение до автоперехода. Такая команда молча ничего
 * не меняет.
 *
 * @param now монотонные миллисекунды ([TimeSource.elapsed]).
 */
fun reduce(
    state: SessionState,
    command: TimerCommand,
    now: Long,
): Reduction =
    when (command) {
        is TimerCommand.Load -> load(command)
        TimerCommand.Start -> start(state, now)
        TimerCommand.Restart -> restart(state, now)
        TimerCommand.Pause -> pause(state, now)
        TimerCommand.Resume -> resume(state, now)
        TimerCommand.Next -> next(state, now)
        TimerCommand.Previous -> previous(state, now)
        is TimerCommand.Adjust -> adjust(state, command.deltaMs, now)
        TimerCommand.Stop -> stop(state)
    }

/**
 * Загрузка плана: предыдущая сессия отбрасывается молча.
 *
 * Событий здесь нет намеренно. `RunStateChanged(→ IDLE)` означает «занятия
 * больше нет», и сервис по нему гасит себя; в штатной последовательности
 * «Load → Start» такое событие погасило бы сервис ровно в момент запуска
 * следующего занятия. Убрать прошлую сессию из хранилища — задача того, кто
 * подаёт `Load`, и она решается сохранением новой на первом же `Start`.
 */
private fun load(command: TimerCommand.Load): Reduction = Reduction(SessionState.initial(command.plan))

private fun start(
    state: SessionState,
    now: Long,
): Reduction = if (state.runState == RunState.IDLE) startFresh(state, now) else Reduction.unchanged(state)

/** Повтор занятия из FINISHED: тот же путь, что и обычный старт (§4.1). */
private fun restart(
    state: SessionState,
    now: Long,
): Reduction = if (state.runState == RunState.FINISHED) startFresh(state, now) else Reduction.unchanged(state)

private fun startFresh(
    state: SessionState,
    now: Long,
): Reduction {
    val fresh = SessionState.initial(state.plan).copy(runState = RunState.RUNNING, resumedAtMs = now)
    val events =
        buildList {
            add(TimerEvent.RunStateChanged(from = state.runState, to = RunState.RUNNING))
            fresh.currentStage.alerts.start?.let {
                add(TimerEvent.PlayAlert(alert = it, stageIndex = 0, scheduledAtMs = now))
            }
        }
    return Reduction(fresh, events)
}

private fun pause(
    state: SessionState,
    now: Long,
): Reduction {
    if (state.runState != RunState.RUNNING) return Reduction.unchanged(state)
    val paused = state.copy(runState = RunState.PAUSED, stageElapsedAtResumeMs = state.stageElapsedMs(now))
    return Reduction(paused, listOf(TimerEvent.RunStateChanged(RunState.RUNNING, RunState.PAUSED)))
}

private fun resume(
    state: SessionState,
    now: Long,
): Reduction {
    if (state.runState != RunState.PAUSED) return Reduction.unchanged(state)
    val resumed = state.copy(runState = RunState.RUNNING, resumedAtMs = now)
    return Reduction(resumed, listOf(TimerEvent.RunStateChanged(RunState.PAUSED, RunState.RUNNING)))
}

/**
 * «След.»: этап завершается досрочно вместе с END-оповещением.
 *
 * В PAUSED разрешено и отсчёт не возобновляет (ТЗ §3): инструктор может
 * перебрать план на паузе и продолжить с нужного места.
 */
private fun next(
    state: SessionState,
    now: Long,
): Reduction =
    if (state.runState.isActive) {
        completeStage(state, now, StageChangeReason.MANUAL_NEXT)
    } else {
        Reduction.unchanged(state)
    }

/**
 * «Пред.»: вход в предыдущий этап без END-оповещения покидаемого (решение B-10).
 *
 * На первом этапе — no-op: возвращаться некуда.
 */
private fun previous(
    state: SessionState,
    now: Long,
): Reduction {
    if (!state.runState.isActive || state.currentIndex == 0) return Reduction.unchanged(state)
    return enterStage(state.leaveStage(now), state.currentIndex - 1, now, StageChangeReason.MANUAL_PREVIOUS)
}

/**
 * Правка ±30 с (решения B-1, B-2, B-3).
 *
 * Общее время занятия меняется само собой: `totalRemainingMs` считается по
 * эффективным длительностям. Отдельной логики для режима SUM не требуется —
 * это дивиденд от того, что всё выводится, а не хранится.
 */
private fun adjust(
    state: SessionState,
    deltaMs: Long,
    now: Long,
): Reduction {
    if (!state.runState.isActive || !state.currentStage.kind.hasDeadline || deltaMs == 0L) {
        return Reduction.unchanged(state)
    }

    val index = state.currentIndex
    val previousAdjustment = state.adjustmentsMs[index] ?: 0L
    val planned = state.currentStage.plannedDurationMs
    // Клампим саму правку, а не результат: иначе после десяти нажатий «−30 с»
    // на пятиминутном этапе первое «+30 с» ничего бы не изменило, и показанная
    // на экране накопленная правка разошлась бы с фактической длительностью.
    val adjustment =
        (planned + previousAdjustment + deltaMs)
            .coerceIn(TimerLimits.MIN_STAGE_MS, TimerLimits.MAX_STAGE_MS) - planned
    if (adjustment == previousAdjustment) return Reduction.unchanged(state)

    val adjusted = state.copy(adjustmentsMs = state.adjustmentsMs + (index to adjustment))
    // Сдвиг конца этапа сдвигает и WARNING, и END. Всё, что правка увела
    // в будущее, обязано сработать заново.
    val rescheduled = adjusted.copy(firedAlertIds = adjusted.firedAlertIds intersect adjusted.passedEventIds(now))

    val remaining = rescheduled.stageRemainingMs(now)
    return if (remaining != null && remaining <= 0L) {
        // Остаток ушёл в ноль — этап завершается немедленно (решение B-2).
        // Опоздания здесь нет: конец наступил в момент нажатия, а не в прошлом,
        // поэтому END-оповещение отрабатывает, а не отбрасывается как просроченное.
        val completion = completeStage(rescheduled, now, StageChangeReason.AUTO)
        Reduction(completion.state, listOf(TimerEvent.PlanChanged) + completion.events)
    } else {
        Reduction(rescheduled, listOf(TimerEvent.PlanChanged))
    }
}

/** Полный сброс к загруженному плану. Сессия считается брошенной, а не завершённой. */
private fun stop(state: SessionState): Reduction {
    if (state.runState == RunState.IDLE) return Reduction.unchanged(state)
    return Reduction(
        SessionState.initial(state.plan),
        listOf(TimerEvent.RunStateChanged(from = state.runState, to = RunState.IDLE)),
    )
}
