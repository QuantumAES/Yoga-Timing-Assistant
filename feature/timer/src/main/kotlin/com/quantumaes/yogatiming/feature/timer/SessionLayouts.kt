package com.quantumaes.yogatiming.feature.timer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quantumaes.yogatiming.core.designsystem.theme.Dimens
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.TimerPalette
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTextStyles
import com.quantumaes.yogatiming.feature.timer.component.ProgressRing
import com.quantumaes.yogatiming.feature.timer.component.RestrictionNotice
import com.quantumaes.yogatiming.feature.timer.component.SessionControls
import com.quantumaes.yogatiming.feature.timer.component.TimerDisplay
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionSnapshot
import com.quantumaes.yogatiming.timer.service.restrictions.TimerRestriction
import kotlinx.coroutines.delay

/** Сколько висит подсказка о свайпах при входе в фокус. */
private const val FOCUS_HINT_MS = 3_000L

private val LANDSCAPE_RING_PADDING = 8.dp

// Раскладки рабочего экрана: портрет, ландшафт и режим фокуса. Отделены от
// TimerScreen намеренно — тот отвечает за состояние и жесты, эти функции
// только раскладывают уже готовые данные.

@Composable
internal fun PortraitContent(
    snapshot: SessionSnapshot?,
    notices: List<TimerRestriction>,
    palette: TimerPalette,
    onModeChange: (SessionMode) -> Unit,
    onNoticeAction: (TimerRestriction) -> Unit,
    onNoticeDismiss: (TimerRestriction) -> Unit,
    onTogglePause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onAddTime: () -> Unit,
    onSubtractTime: () -> Unit,
    onStopRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SessionTopBar(
            title = snapshot?.profileName.orEmpty(),
            palette = palette,
            onLock = { onModeChange(SessionMode.LOCK) },
            onStop = onStopRequest,
        )

        notices.forEach { restriction ->
            RestrictionNotice(
                restriction = restriction,
                palette = palette,
                onAction = { onNoticeAction(restriction) },
                onDismiss = { onNoticeDismiss(restriction) },
            )
        }

        StageRing(
            snapshot = snapshot,
            palette = palette,
            onEnterFocus = { onModeChange(SessionMode.FOCUS) },
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = Spacing.s),
        )

        StageFooter(snapshot = snapshot, palette = palette)

        SessionControls(
            paused = snapshot?.runState == RunState.PAUSED,
            palette = palette,
            onTogglePause = onTogglePause,
            onNext = onNext,
            onPrevious = onPrevious,
            onAddTime = onAddTime,
            onSubtractTime = onSubtractTime,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}

/** Ландшафт: кольцо слева, управление справа — обе половины достижимы большим пальцем. */
@Composable
internal fun LandscapeContent(
    snapshot: SessionSnapshot?,
    notices: List<TimerRestriction>,
    palette: TimerPalette,
    onModeChange: (SessionMode) -> Unit,
    onNoticeAction: (TimerRestriction) -> Unit,
    onNoticeDismiss: (TimerRestriction) -> Unit,
    onTogglePause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onAddTime: () -> Unit,
    onSubtractTime: () -> Unit,
    onStopRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        StageRing(
            snapshot = snapshot,
            palette = palette,
            onEnterFocus = { onModeChange(SessionMode.FOCUS) },
            modifier = Modifier.weight(1f).fillMaxSize().padding(LANDSCAPE_RING_PADDING),
        )

        Column(
            modifier = Modifier.weight(1f).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SessionTopBar(
                title = snapshot?.profileName.orEmpty(),
                palette = palette,
                onLock = { onModeChange(SessionMode.LOCK) },
                onStop = onStopRequest,
            )

            // В ландшафте показывается только самое серьёзное сообщение:
            // места под столбец баннеров нет, а прятать их вовсе нельзя.
            notices.firstOrNull()?.let { restriction ->
                RestrictionNotice(
                    restriction = restriction,
                    palette = palette,
                    onAction = { onNoticeAction(restriction) },
                    onDismiss = { onNoticeDismiss(restriction) },
                )
            }

            StageFooter(snapshot = snapshot, palette = palette)

            SessionControls(
                paused = snapshot?.runState == RunState.PAUSED,
                palette = palette,
                onTogglePause = onTogglePause,
                onNext = onNext,
                onPrevious = onPrevious,
                onAddTime = onAddTime,
                onSubtractTime = onSubtractTime,
            )
        }
    }
}

/**
 * Кольцо с названием этапа и цифрами.
 *
 * Вся зона — одна большая цель для тапа (docs/03-GESTURES.md §2): попасть в
 * неё с коврика можно не глядя, в отличие от кнопки «фокус» где-нибудь в углу.
 */
@Composable
private fun StageRing(
    snapshot: SessionSnapshot?,
    palette: TimerPalette,
    onEnterFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusLabel = stringResource(R.string.timer_focus_enter)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        ProgressRing(
            progress = snapshot?.stageProgress,
            color = snapshot.accent(palette),
            trackColor = palette.ringTrack,
            pulsing = snapshot?.runState == RunState.PAUSED,
            strokeWidth = Dimens.progressRingWidth,
            modifier =
                Modifier
                    .aspectRatio(1f)
                    .tapTarget(label = focusLabel, onTap = onEnterFocus),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = snapshot?.currentStageName ?: stringResource(R.string.timer_idle),
                    style = YtaTextStyles.stageTitle,
                    color = palette.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TimerDisplay(
                    text = remainingText(snapshot),
                    color = snapshot.accent(palette),
                    modifier = Modifier.fillMaxWidth(),
                )
                // Две строки, а не одна: «Этап 2/6» и остаток занятия читаются
                // с трёх метров по отдельности и не ужимаются под ширину кольца.
                Text(
                    text = stagePositionText(snapshot),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.onBackgroundMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Text(
                    text = totalRemainingText(snapshot),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.onBackgroundMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** «Далее: Балансы · 12:00» и заметка инструктору. */
@Composable
private fun StageFooter(
    snapshot: SessionSnapshot?,
    palette: TimerPalette,
    modifier: Modifier = Modifier,
) {
    if (snapshot == null) return
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = nextStageText(snapshot),
            style = YtaTextStyles.stageNext,
            color = palette.onBackgroundMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        snapshot.currentNote?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundMuted,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun SessionTopBar(
    title: String,
    palette: TimerPalette,
    onLock: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = palette.onBackgroundMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onLock, modifier = Modifier.size(Dimens.minTouchTarget)) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = stringResource(R.string.timer_lock),
                tint = palette.onBackgroundMuted,
            )
        }
        IconButton(onClick = onStop, modifier = Modifier.size(Dimens.minTouchTarget)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.timer_stop),
                tint = palette.onBackgroundMuted,
            )
        }
    }
}

/**
 * Режим фокуса (docs/03-GESTURES.md §5): только цифры.
 *
 * Кнопок нет — и именно поэтому свайп здесь однозначен: спорить ему не с чем.
 */
@Composable
internal fun FocusContent(
    snapshot: SessionSnapshot?,
    palette: TimerPalette,
    modifier: Modifier = Modifier,
) {
    var hintVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(FOCUS_HINT_MS)
        hintVisible = false
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = snapshot?.currentStageName ?: stringResource(R.string.timer_idle),
            style = YtaTextStyles.stageTitle,
            color = palette.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        TimerDisplay(
            text = remainingText(snapshot),
            color = snapshot.accent(palette),
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        snapshot?.let {
            Text(
                text = nextStageText(it),
                style = YtaTextStyles.stageNext,
                color = palette.onBackgroundMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AnimatedVisibility(visible = hintVisible) {
            Text(
                text = stringResource(R.string.timer_focus_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.m),
            )
        }
    }
}
