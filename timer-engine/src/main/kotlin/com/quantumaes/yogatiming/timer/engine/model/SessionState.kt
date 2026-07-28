package com.quantumaes.yogatiming.timer.engine.model

import com.quantumaes.yogatiming.timer.engine.TimerLimits

/**
 * Единственный источник истины о сессии (docs/02-TIMER-CORE-DESIGN.md §3.2).
 *
 * Принцип П-1: состояние не меняется по таймеру — только командами и
 * наступлением запланированных событий. Хранятся **метки**, а не счётчики,
 * поэтому заморозка процесса на десять минут не ломает состояние, а лишь
 * откладывает вычисление.
 *
 * Семь полей несут состояние, всё остальное выводится.
 *
 * @param stageElapsedAtResumeMs сколько текущего этапа пройдено к моменту
 *   последнего Start/Resume/входа в этап.
 * @param resumedAtMs монотонная метка последнего Start/Resume/входа в этап.
 *   Значима только в [RunState.RUNNING].
 * @param adjustmentsMs накопленные правки ±30 с по индексу этапа.
 * @param actualDurationsMs фактическое время, проведённое в **покинутых**
 *   этапах. Вклад текущего этапа всегда считается через [stageElapsedMs] —
 *   без этого инварианта время либо теряется, либо считается дважды.
 * @param firedAlertIds уже отработавшие в текущем этапе оповещения — защита
 *   от повторов при catch-up.
 */
data class SessionState(
    val plan: SessionPlan,
    val runState: RunState = RunState.IDLE,
    val currentIndex: Int = 0,
    val stageElapsedAtResumeMs: Long = 0L,
    val resumedAtMs: Long = 0L,
    val adjustmentsMs: Map<Int, Long> = emptyMap(),
    val actualDurationsMs: Map<Int, Long> = emptyMap(),
    val firedAlertIds: Set<String> = emptySet(),
) {
    val currentStage: PlannedStage get() = plan.stages[currentIndex]

    val nextStage: PlannedStage? get() = plan.stages.getOrNull(currentIndex + 1)

    val isLastStage: Boolean get() = currentIndex == plan.lastIndex

    /**
     * Есть ли в остатке этапы без плановой длительности.
     *
     * UI показывает «≥ 42 мин» вместо «42 мин» (решение B-4).
     */
    val totalRemainingIsLowerBound: Boolean
        get() =
            runState != RunState.FINISHED &&
                (currentIndex..plan.lastIndex).any { !plan.stages[it].kind.hasDeadline }

    /**
     * Длительность этапа с учётом накопленных правок ±30 с.
     *
     * Клампится в границы 5 с … 4 ч (решение B-3). Для FREE-этапа — 0:
     * плановой длительности у него нет, и подставлять нижнюю границу было бы
     * ложью, на которой строился бы весь остальной расчёт.
     */
    fun effectiveDurationMs(index: Int): Long {
        val stage = plan.stages[index]
        if (!stage.kind.hasDeadline) return 0L
        val adjusted = stage.plannedDurationMs + (adjustmentsMs[index] ?: 0L)
        return adjusted.coerceIn(TimerLimits.MIN_STAGE_MS, TimerLimits.MAX_STAGE_MS)
    }

    /** Сколько текущего этапа пройдено к моменту [now]. */
    fun stageElapsedMs(now: Long): Long =
        if (runState.isTicking) {
            stageElapsedAtResumeMs + (now - resumedAtMs).coerceAtLeast(0L)
        } else {
            stageElapsedAtResumeMs
        }

    /** Остаток текущего этапа; `null` для FREE — у него нет конца (решение B-5). */
    fun stageRemainingMs(now: Long): Long? =
        when {
            !currentStage.kind.hasDeadline -> null
            runState == RunState.FINISHED -> 0L
            else -> (effectiveDurationMs(currentIndex) - stageElapsedMs(now)).coerceAtLeast(0L)
        }

    /**
     * Момент, в который этап «начался» с точки зрения расчётов.
     *
     * Считается от [now] назад, а не от [resumedAtMs] вперёд, — тогда формула
     * одинаково верна и в RUNNING, и в PAUSED, где [resumedAtMs] уже неактуален.
     */
    fun virtualStageStartMs(now: Long): Long = now - stageElapsedMs(now)

    /** Монотонная метка конца текущего этапа; `null` для FREE. */
    fun stageEndAtMs(now: Long): Long? =
        if (!currentStage.kind.hasDeadline) {
            null
        } else {
            virtualStageStartMs(now) + effectiveDurationMs(currentIndex)
        }

    /**
     * Сколько прошло с начала занятия.
     *
     * Считается по фактическим длительностям, а не по плановым: инструктор мог
     * нажать «След.» на середине этапа, и прогресс-полоса обязана отражать
     * реальность.
     */
    fun totalElapsedMs(now: Long): Long = actualDurationsMs.values.sum() + stageElapsedMs(now)

    /** Сколько занятия осталось. FREE-этапы в сумму не входят (решение B-4). */
    fun totalRemainingMs(now: Long): Long {
        if (runState == RunState.FINISHED) return 0L
        val future =
            (currentIndex + 1..plan.lastIndex)
                .filter { plan.stages[it].kind.hasDeadline }
                .sumOf { effectiveDurationMs(it) }
        return future + (stageRemainingMs(now) ?: 0L)
    }

    /**
     * Уход с текущего этапа: время, проведённое в нём, переносится в
     * [actualDurationsMs] и накапливается.
     *
     * Накапливается, а не перезаписывается: инструктор мог вернуться на этап
     * кнопкой «Пред.» и пройти его дважды — оба захода реально заняли время,
     * и общий прогресс обязан остаться монотонным.
     */
    fun leaveStage(now: Long): SessionState =
        copy(
            actualDurationsMs =
                actualDurationsMs + (currentIndex to (actualDurationsMs[currentIndex] ?: 0L) + stageElapsedMs(now)),
            stageElapsedAtResumeMs = 0L,
            resumedAtMs = now,
        )

    companion object {
        /** Загруженный, но не начатый план. */
        fun initial(plan: SessionPlan): SessionState = SessionState(plan = plan)
    }
}
