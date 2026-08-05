package com.quantumaes.yogatiming.timer.engine.model

import com.quantumaes.yogatiming.timer.engine.schedule.WRAP_UP_ID

private const val NO_PROGRESS = 0f
private const val FULL_PROGRESS = 1f

/**
 * Всё, что нужно интерфейсу и уведомлению, одним объектом
 * (docs/02-TIMER-CORE-DESIGN.md §3.4).
 *
 * Снапшот **вычисляется по запросу**, а не хранится: единственный источник
 * истины — [SessionState], и второй копии состояния, которая может с ним
 * разойтись, в системе не существует.
 *
 * @param stageRemainingMs `null` для FREE-этапа — у него нет конца.
 * @param stageElapsedMs для FREE это основной показатель: счёт идёт вверх.
 * @param totalRemainingIsLowerBound `true`, если в остатке есть FREE-этапы;
 *   UI показывает «≥ 42 мин» вместо «42 мин» (решение B-4).
 * @param sessionElapsedMs сколько занятие идёт по часам: практика плюс паузы
 *   этапа. Именно это число сравнивается с бюджетом.
 * @param holdMs сколько занятие простояло на паузе этапа — «дополнительное
 *   время» в терминах замечания 6.
 * @param pauseElapsedMs сколько длится **идущая** пауза; вне паузы ноль. На
 *   паузе это единственное растущее число на экране: остаток этапа замер, а
 *   без часов паузы непонятно, стоишь ты десять секунд или четыре минуты.
 * @param budgetRemainingMs остаток до целевого конца занятия; `null` — цели
 *   нет. Отрицательное значение — перерасход, и его надо показывать.
 * @param budgetDeficitMs на сколько остаток плана не помещается в остаток
 *   бюджета; `null` — цели нет. Отрицательное — запас.
 * @param budgetToleranceMs допустимый перерасход: до него расхождение
 *   показывается спокойно, после — тревожно.
 * @param wrapUpPassed отсечка уже прозвучала.
 * @param previousStageName куда вернёт «Пред.»; `null` на первом этапе — там
 *   команда ничего не делает. Длительности у него нет намеренно: возврат на
 *   брошенный досрочно этап продолжает его с того места, где он остановился
 *   (см. `previous` в редьюсере), и плановое число соврало бы.
 *
 * У полей бюджета и паузы есть значения по умолчанию — «цели нет, пауза
 * обычная». Не ради краткости: снапшот собирается ровно в одном месте
 * ([snapshot]), и там заполнены все поля, а умолчания нужны тем, кто строит
 * снимок вручную — превью и тестам. Занятие без целевого времени описывается
 * ими в точности.
 */
data class SessionSnapshot(
    val profileId: Long,
    val profileName: String,
    val runState: RunState,
    val pauseMode: PauseMode = PauseMode.DEFAULT,
    val currentIndex: Int,
    val stageCount: Int,
    val currentStageName: String,
    val currentStageColor: String,
    val currentStageKind: StageKind,
    val currentStageSide: StageSide? = null,
    val currentNote: String?,
    val stageRemainingMs: Long?,
    val stageElapsedMs: Long,
    val stageDurationMs: Long,
    val stageProgress: Float?,
    val stageAdjustmentMs: Long,
    val totalElapsedMs: Long,
    val totalRemainingMs: Long,
    val totalRemainingIsLowerBound: Boolean,
    val totalProgress: Float,
    val sessionElapsedMs: Long = 0L,
    val holdMs: Long = 0L,
    val pauseElapsedMs: Long = 0L,
    val budgetRemainingMs: Long? = null,
    val budgetDeficitMs: Long? = null,
    val budgetToleranceMs: Long = 0L,
    val wrapUpPassed: Boolean = false,
    val nextStageName: String?,
    val nextStageDurationMs: Long?,
    val previousStageName: String? = null,
    val isLastStage: Boolean,
) {
    /** Есть ли у занятия целевое время вообще. */
    val hasBudget: Boolean get() = budgetRemainingMs != null

    /**
     * Пора ли предлагать ужать план.
     *
     * Не «дефицит есть», а «дефицит уже вышел за допуск»: групповое занятие с
     * эластичным окном в десять минут не должно тревожить инструктора из-за
     * полутора минут (замечание 12 полевой проверки 2026-08-04).
     */
    val budgetOverrun: Boolean get() = (budgetDeficitMs ?: 0L) > budgetToleranceMs
}

/** Проекция состояния на момент [now]. */
fun SessionState.snapshot(now: Long): SessionSnapshot {
    val elapsed = stageElapsedMs(now)
    val duration = effectiveDurationMs(currentIndex)
    val totalElapsed = totalElapsedMs(now)
    val totalRemaining = totalRemainingMs(now)

    return SessionSnapshot(
        profileId = plan.profileId,
        profileName = plan.profileName,
        runState = runState,
        pauseMode = pauseMode,
        currentIndex = currentIndex,
        stageCount = plan.stages.size,
        currentStageName = currentStage.name,
        currentStageColor = currentStage.colorTag,
        currentStageKind = currentStage.kind,
        currentStageSide = currentStage.side,
        currentNote = currentStage.note,
        stageRemainingMs = stageRemainingMs(now),
        stageElapsedMs = elapsed,
        stageDurationMs = duration,
        stageProgress = stageProgress(elapsed, duration),
        stageAdjustmentMs = adjustmentsMs[currentIndex] ?: 0L,
        totalElapsedMs = totalElapsed,
        totalRemainingMs = totalRemaining,
        totalRemainingIsLowerBound = totalRemainingIsLowerBound,
        totalProgress = ratio(totalElapsed, totalElapsed + totalRemaining),
        sessionElapsedMs = sessionElapsedMs(now),
        holdMs = holdElapsedMs(now),
        pauseElapsedMs = pauseElapsedMs(now),
        budgetRemainingMs = budgetRemainingMs(now),
        budgetDeficitMs = budgetDeficitMs(now),
        budgetToleranceMs = plan.budget?.toleranceMs ?: 0L,
        wrapUpPassed = WRAP_UP_ID in firedAlertIds,
        nextStageName = nextStage?.name,
        nextStageDurationMs = nextStageDurationMs(),
        previousStageName = previousStage?.name,
        isLastStage = isLastStage,
    )
}

/** Длительность следующего этапа; `null`, если его нет или он свободный. */
private fun SessionState.nextStageDurationMs(): Long? =
    nextStage?.takeIf { it.kind.hasDeadline }?.let { effectiveDurationMs(currentIndex + 1) }

/** Прогресс этапа: `null` для FREE — делить не на что. */
private fun SessionState.stageProgress(
    elapsed: Long,
    duration: Long,
): Float? =
    when {
        !currentStage.kind.hasDeadline -> null
        runState == RunState.FINISHED -> FULL_PROGRESS
        else -> ratio(elapsed, duration)
    }

private fun ratio(
    part: Long,
    whole: Long,
): Float = if (whole <= 0L) NO_PROGRESS else (part.toFloat() / whole).coerceIn(NO_PROGRESS, FULL_PROGRESS)
