package com.quantumaes.yogatiming.timer.engine

import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionState
import com.quantumaes.yogatiming.timer.engine.model.holdElapsedMs
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
        is TimerCommand.Pause -> pause(state, command.mode, now)
        is TimerCommand.SetPauseMode -> setPauseMode(state, command.mode, now)
        TimerCommand.Resume -> resume(state, now)
        TimerCommand.Next -> next(state, now)
        TimerCommand.Previous -> previous(state, now)
        is TimerCommand.Adjust -> adjust(state, command.deltaMs, now)
        TimerCommand.FitToBudget -> fitToBudget(state, now)
        TimerCommand.Stop -> stop(state, now)
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
 * Возврат на этап, покинутый **досрочно**, продолжает его с того места, где он
 * был брошен, а не начинает заново. Это ровно тот случай, ради которого кнопка
 * и существует: «След.» нажали по ошибке — задели телефон на полу, промахнулись
 * мимо паузы, — и исправление промаха обязано вернуть занятие в то состояние,
 * в котором оно было (замечание 5 полевой проверки 2026-08-04). Этап,
 * досмотренный до конца, наоборот, начинается с нуля: возвращаются к нему,
 * чтобы показать позу ещё раз.
 *
 * На первом этапе — no-op: возвращаться некуда.
 */
private fun previous(
    state: SessionState,
    now: Long,
): Reduction {
    if (!state.runState.isActive || state.currentIndex == 0) return Reduction.unchanged(state)

    val target = state.currentIndex - 1
    val left = state.leaveStage(now)
    val spent = left.actualDurationsMs[target] ?: 0L
    val hasDeadline =
        left.plan.stages[target]
            .kind.hasDeadline
    val unfinished = spent > 0L && (!hasDeadline || spent < left.effectiveDurationMs(target))

    if (!unfinished) return enterStage(left, target, now, StageChangeReason.MANUAL_PREVIOUS)

    // Накопленное возвращается из «покинутых» обратно в текущий этап: иначе оно
    // посчиталось бы дважды — и в сумме прошлых этапов, и в текущем.
    val rewound = left.copy(actualDurationsMs = left.actualDurationsMs - target)
    return enterStage(rewound, target, now, StageChangeReason.MANUAL_PREVIOUS, elapsedMs = spent)
}

/**
 * Правка ±30 с (решения B-1, B-2, B-3).
 *
 * Общее время занятия меняется само собой: `totalRemainingMs` считается по
 * эффективным длительностям. Отдельной логики для режима SUM не требуется —
 * это дивиденд от того, что всё выводится, а не хранится.
 *
 * У двусторонней асаны правка первой стороны повторяется на второй: смысл
 * такого этапа в симметрии, и удерживать правую минуту, а левую полторы —
 * не «гибкость», а ошибка (замечание 10 полевой проверки 2026-08-04). Правка
 * второй стороны на первую не переносится: она уже пройдена.
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

    val mirror = state.mirrorIndexOf(index)?.takeIf { it > index }
    val adjustments =
        state.adjustmentsMs + (index to adjustment) + (mirror?.let { mapOf(it to adjustment) }.orEmpty())
    val adjusted = state.copy(adjustmentsMs = adjustments)
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

/**
 * Полный сброс к загруженному плану. Сессия считается брошенной, а не завершённой.
 *
 * Итоги считаются здесь и уходят событием [TimerEvent.SessionStopped]: в
 * состоянии после сброса их уже нет, а тому, кто нажал «Стоп» на сороковой
 * минуте, полагается увидеть эти сорок минут, а не пустой экран.
 */
private fun stop(
    state: SessionState,
    now: Long,
): Reduction {
    if (state.runState == RunState.IDLE) return Reduction.unchanged(state)
    return Reduction(
        SessionState.initial(state.plan),
        listOf(
            TimerEvent.SessionStopped(
                totalElapsedMs = state.totalElapsedMs(now),
                stagesCompleted = state.currentIndex,
                holdMs = state.holdElapsedMs(now),
                adjustmentsMs = state.adjustmentsMs,
            ),
            TimerEvent.RunStateChanged(from = state.runState, to = RunState.IDLE),
        ),
    )
}
