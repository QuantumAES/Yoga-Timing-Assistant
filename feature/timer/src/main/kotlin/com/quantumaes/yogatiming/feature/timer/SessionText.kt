package com.quantumaes.yogatiming.feature.timer

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.quantumaes.yogatiming.core.common.time.TimeFormatter
import com.quantumaes.yogatiming.core.designsystem.theme.TimerPalette
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionSnapshot

/** Последняя минута этапа: кольцо и цифры меняют цвет (ТЗ, Экран 4). */
private const val LAST_MINUTE_MS = 60_000L

internal fun SessionSnapshot?.accent(palette: TimerPalette): Color {
    val snapshot = this ?: return palette.onBackground
    val remaining = snapshot.stageRemainingMs
    return when {
        snapshot.runState == RunState.PAUSED -> palette.paused
        remaining != null && remaining <= LAST_MINUTE_MS -> palette.warning
        else -> palette.onBackground
    }
}

@Composable
internal fun remainingText(snapshot: SessionSnapshot?): String {
    val remaining = snapshot?.stageRemainingMs
    return when {
        snapshot == null -> stringResource(R.string.timer_no_time)

        // У свободного этапа конца нет — счёт идёт вверх (решение B-5).
        remaining == null -> TimeFormatter.clock(snapshot.stageElapsedMs)

        else -> TimeFormatter.clock(remaining, roundUp = true)
    }
}

@Composable
internal fun nextStageText(snapshot: SessionSnapshot): String {
    val next = snapshot.nextStageName ?: return stringResource(R.string.timer_last_stage)
    val duration = snapshot.nextStageDurationMs
    return if (duration == null) {
        stringResource(R.string.timer_next_stage, next)
    } else {
        "${stringResource(R.string.timer_next_stage, next)} · ${TimeFormatter.clock(duration)}"
    }
}

@Composable
internal fun totalText(snapshot: SessionSnapshot?): String {
    if (snapshot == null) return ""
    val clock = TimeFormatter.clock(snapshot.totalRemainingMs)
    val total =
        if (snapshot.totalRemainingIsLowerBound) {
            stringResource(R.string.timer_total_at_least, clock)
        } else {
            clock
        }
    val position = stringResource(R.string.timer_stage_position, snapshot.currentIndex + 1, snapshot.stageCount)
    val remaining = stringResource(R.string.timer_total_remaining, total)
    val adjustment =
        if (snapshot.stageAdjustmentMs == 0L) {
            ""
        } else {
            " · ${TimeFormatter.signedClock(snapshot.stageAdjustmentMs)}"
        }
    return "$position · $remaining$adjustment"
}
