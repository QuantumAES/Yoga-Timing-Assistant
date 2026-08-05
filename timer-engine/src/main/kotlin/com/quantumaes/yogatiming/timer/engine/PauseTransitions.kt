package com.quantumaes.yogatiming.timer.engine

import com.quantumaes.yogatiming.timer.engine.model.PauseMode
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionState
import com.quantumaes.yogatiming.timer.engine.model.holdElapsedMs

// Пауза и её режимы (замечание 6 полевой проверки 2026-08-04).
//
// Отдельно от остального редьюсера: у паузы свои три метки — `pausedAtMs`,
// `holdSinceMs` и `holdMs`, — и все три перехода, которые их трогают, стоят
// рядом. Разложенные по общему файлу, они выглядели бы тремя независимыми
// правилами, хотя на самом деле это одно: время удержания копится, пока пауза
// держит этап.
//
// Меток о начале паузы две, и это не дублирование. `pausedAtMs` отвечает на
// вопрос «сколько эта пауза длится» — его задаёт человек, глядя на экран.
// `holdSinceMs` отвечает на вопрос «сколько времени занятия съедено» — его
// задаёт бюджет. Ответы расходятся ровно тогда, когда режим паузы переключили
// на её середине: удержание с этого момента считается заново, а пауза идёт та
// же самая, и обнулять её счётчик под носом у пользователя нельзя (замечание 3
// полевой проверки 2026-08-05).

/**
 * Пауза в одном из двух режимов (замечание 6 полевой проверки 2026-08-04).
 *
 * Этап останавливается в обоих: разница только в том, продолжают ли идти часы
 * занятия. Отсюда и метка [SessionState.holdSinceMs] — по ней растёт время
 * удержания, которое бюджет обязан учесть, а практика — нет.
 */
internal fun pause(
    state: SessionState,
    mode: PauseMode,
    now: Long,
): Reduction {
    if (state.runState != RunState.RUNNING) return Reduction.unchanged(state)
    val paused =
        state.copy(
            runState = RunState.PAUSED,
            stageElapsedAtResumeMs = state.stageElapsedMs(now),
            pauseMode = mode,
            pausedAtMs = now,
            holdSinceMs = now,
        )
    return Reduction(paused, listOf(TimerEvent.RunStateChanged(RunState.RUNNING, RunState.PAUSED)))
}

/**
 * Переключение режима идущей паузы.
 *
 * Накопленное в прежнем режиме время фиксируется, отсчёт нового начинается с
 * этого момента: инструктор передумал на середине паузы, а не задним числом.
 * Начало самой паузы при этом не трогается — она не начинается заново оттого,
 * что её переназвали.
 */
internal fun setPauseMode(
    state: SessionState,
    mode: PauseMode,
    now: Long,
): Reduction {
    if (state.runState != RunState.PAUSED || state.pauseMode == mode) return Reduction.unchanged(state)
    val switched = state.copy(holdMs = state.holdElapsedMs(now), pauseMode = mode, holdSinceMs = now)
    return Reduction(switched, listOf(TimerEvent.PlanChanged))
}

internal fun resume(
    state: SessionState,
    now: Long,
): Reduction {
    if (state.runState != RunState.PAUSED) return Reduction.unchanged(state)
    val resumed =
        state.copy(
            runState = RunState.RUNNING,
            resumedAtMs = now,
            // Время удержания фиксируется здесь и больше не растёт: пауза кончилась.
            holdMs = state.holdElapsedMs(now),
            pausedAtMs = 0L,
            holdSinceMs = 0L,
        )
    return Reduction(resumed, listOf(TimerEvent.RunStateChanged(RunState.PAUSED, RunState.RUNNING)))
}
