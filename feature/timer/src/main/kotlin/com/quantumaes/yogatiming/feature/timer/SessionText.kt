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

/**
 * «Этап 2/6» — первая из двух строк под таймером.
 *
 * Строки разделены, а не собраны в одну через разделитель (полевая проверка
 * 2026-07-30, замечание 5): в одну строку «Этап 2/6 · осталось 45:45» уходит на
 * ширину экрана целиком, ужимается до нечитаемого кегля на узких экранах и
 * теряет хвост с поправкой ±30 с. Две короткие строки читаются с трёх метров
 * обе.
 */
@Composable
internal fun stagePositionText(snapshot: SessionSnapshot?): String {
    if (snapshot == null) return ""
    return stringResource(R.string.timer_stage_position, snapshot.currentIndex + 1, snapshot.stageCount)
}

/** «Осталось 45:45» — вторая строка: остаток всего занятия. */
@Composable
internal fun totalRemainingText(snapshot: SessionSnapshot?): String {
    if (snapshot == null) return ""
    val clock = TimeFormatter.clock(snapshot.totalRemainingMs)
    val total =
        if (snapshot.totalRemainingIsLowerBound) {
            stringResource(R.string.timer_total_at_least, clock)
        } else {
            clock
        }
    return stringResource(R.string.timer_total_remaining, total)
}

/**
 * «+0:30 к этапу» — ручная поправка ±30 с отдельной строкой.
 *
 * Раньше она дописывалась хвостом к остатку занятия и на узком экране
 * наезжала на дугу кольца (полевая проверка 2026-07-31, замечание 4).
 * `null` — поправки не было: пустой строки в разметке кольца тоже не место.
 */
@Composable
internal fun stageAdjustmentText(snapshot: SessionSnapshot?): String? {
    val adjustment = snapshot?.stageAdjustmentMs?.takeIf { it != 0L } ?: return null
    return stringResource(R.string.timer_stage_adjustment, TimeFormatter.signedClock(adjustment))
}
