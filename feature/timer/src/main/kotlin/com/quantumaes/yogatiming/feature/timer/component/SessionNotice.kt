package com.quantumaes.yogatiming.feature.timer.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quantumaes.yogatiming.core.common.time.TimeFormatter
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.TimerPalette
import com.quantumaes.yogatiming.feature.timer.R
import com.quantumaes.yogatiming.timer.engine.model.PauseMode
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionSnapshot

private val NOTICE_CORNER = 16.dp

/** Насколько плашка светлее фона. Акцент, а не заливка: рядом живут цифры. */
private const val NOTICE_BACKGROUND_ALPHA = 0.16f

/**
 * Плашка состояния над индикатором отсчёта.
 *
 * Занимает одно и то же место для обоих сообщений — паузы и отсечки, — потому
 * что показывать их разом незачем: занятие на паузе к концу не приближается.
 * Одна полоса вместо двух оставляет экран пустым в те девяносто процентов
 * времени, когда сообщать нечего.
 */
@Composable
internal fun SessionNotice(
    snapshot: SessionSnapshot?,
    palette: TimerPalette,
    wrapUpVisible: Boolean,
    onSwitchPauseMode: (PauseMode) -> Unit,
    onFitToBudget: () -> Unit,
    onDismissWrapUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (snapshot == null) return
    when {
        snapshot.runState == RunState.PAUSED -> {
            PauseNotice(snapshot = snapshot, palette = palette, onSwitch = onSwitchPauseMode, modifier = modifier)
        }

        wrapUpVisible -> {
            WrapUpNotice(
                snapshot = snapshot,
                palette = palette,
                onFit = onFitToBudget,
                onDismiss = onDismissWrapUp,
                modifier = modifier,
            )
        }

        else -> {
            Unit
        }
    }
}

/**
 * Что именно остановлено — и как это поменять.
 *
 * Режим паузы виден словами, а не догадкой: разница между «время идёт» и
 * «время стоит» на экране никак не проявляется, а стоит она десяти минут
 * аренды. Плашка сама и есть переключатель — отдельная кнопка рядом с текстом,
 * который её объясняет, была бы вторым элементом там, где хватает одного.
 */
@Composable
private fun PauseNotice(
    snapshot: SessionSnapshot,
    palette: TimerPalette,
    onSwitch: (PauseMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stageHeld = snapshot.pauseMode == PauseMode.STAGE
    val title =
        stringResource(
            if (stageHeld) R.string.timer_pause_stage_title else R.string.timer_pause_session_title,
        )
    val text =
        if (stageHeld) {
            stringResource(R.string.timer_pause_stage_text, TimeFormatter.clock(snapshot.holdMs))
        } else {
            stringResource(R.string.timer_pause_session_text)
        }

    NoticeSurface(
        accent = palette.paused,
        modifier =
            modifier.clickable {
                onSwitch(if (stageHeld) PauseMode.SESSION else PauseMode.STAGE)
            },
    ) {
        NoticeText(title = title, text = text, accent = palette.paused, palette = palette)
    }
}

/**
 * Отсечка: сколько осталось по часам и сколько ещё в плане.
 *
 * Разница между этими двумя числами и есть ответ на вопрос «успеваю ли я»,
 * а считать её в уме посреди занятия — ровно то, от чего приложение обязано
 * избавить. Действие одно: ужать оставшиеся этапы под остаток времени.
 */
@Composable
private fun WrapUpNotice(
    snapshot: SessionSnapshot,
    palette: TimerPalette,
    onFit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remaining = TimeFormatter.clock((snapshot.budgetRemainingMs ?: 0L).coerceAtLeast(0L), roundUp = true)
    val planned = TimeFormatter.clock(snapshot.totalRemainingMs)

    NoticeSurface(accent = palette.danger, modifier = modifier) {
        NoticeText(
            title = stringResource(R.string.timer_wrap_up_title),
            text = stringResource(R.string.timer_wrap_up_text, remaining, planned),
            accent = palette.danger,
            palette = palette,
        )
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.timer_wrap_up_hide), color = palette.onBackgroundMuted)
        }
        TextButton(onClick = onFit) {
            Text(stringResource(R.string.timer_wrap_up_fit), color = palette.danger)
        }
    }
}

@Composable
private fun NoticeSurface(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NOTICE_CORNER))
                .background(accent.copy(alpha = NOTICE_BACKGROUND_ALPHA))
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        content = content,
    )
}

@Composable
private fun RowScope.NoticeText(
    title: String,
    text: String,
    accent: Color,
    palette: TimerPalette,
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onBackgroundMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
