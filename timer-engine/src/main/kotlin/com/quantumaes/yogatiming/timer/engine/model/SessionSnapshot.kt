package com.quantumaes.yogatiming.timer.engine.model

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
 */
data class SessionSnapshot(
    val profileId: Long,
    val profileName: String,
    val runState: RunState,
    val currentIndex: Int,
    val stageCount: Int,
    val currentStageName: String,
    val currentStageColor: String,
    val currentStageKind: StageKind,
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
    val nextStageName: String?,
    val nextStageDurationMs: Long?,
    val isLastStage: Boolean,
)

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
        currentIndex = currentIndex,
        stageCount = plan.stages.size,
        currentStageName = currentStage.name,
        currentStageColor = currentStage.colorTag,
        currentStageKind = currentStage.kind,
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
        nextStageName = nextStage?.name,
        nextStageDurationMs = nextStageDurationMs(),
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
