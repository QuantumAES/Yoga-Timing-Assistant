package com.quantumaes.yogatiming.feature.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumaes.yogatiming.core.common.time.TimeFormatter
import com.quantumaes.yogatiming.core.designsystem.component.KeepScreenOn
import com.quantumaes.yogatiming.core.designsystem.component.PlaceholderAction
import com.quantumaes.yogatiming.core.designsystem.component.PlaceholderScreen
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.TimerPalette
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTextStyles
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTheme
import com.quantumaes.yogatiming.core.designsystem.theme.timerPalette
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionSnapshot
import com.quantumaes.yogatiming.timer.service.restrictions.RestrictionSeverity
import com.quantumaes.yogatiming.timer.service.restrictions.TimerRestriction

/**
 * Экран 4 «Занятие» — временная проверочная версия Фазы 3.
 *
 * Настоящий экран с кольцом прогресса и жестами — Фаза 6 (docs/01-ROADMAP.md).
 * Здесь ровно то, что нужно для вехи M2: увидеть своими глазами, что отсчёт
 * идёт при заблокированном экране, что управление из шторки работает и что
 * сессия переживает убийство процесса.
 *
 * Цвета берутся из [TimerPalette], а не из схемы Material: экран обязан
 * читаться с 2–3 метров, и динамическим цветам здесь места нет
 * (docs/06-MVP-SCOPE.md §4).
 */
@Composable
internal fun TimerScreen(
    profileId: Long,
    onFinish: () -> Unit,
    onExit: () -> Unit,
) {
    val viewModel: TimerViewModel = hiltViewModel()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val notices by viewModel.notices.collectAsStateWithLifecycle()

    // Экран не гаснет и не приглушается, пока идёт занятие: инструктор смотрит
    // на таймер издалека и не может тянуться к телефону, чтобы его разбудить.
    // Процессор при этом держит сервис (SessionWakeLock), а экран — только этот
    // флаг и только пока рабочий экран на переднем плане.
    KeepScreenOn()

    LaunchedEffect(profileId) { viewModel.ensureSession(profileId) }

    // Ограничения перечитываются на каждом возвращении на экран: пользователь
    // уходит их чинить в системные настройки и возвращается сюда — другого
    // сигнала об изменении система не присылает.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshRestrictions() }

    LaunchedEffect(snapshot?.runState) {
        if (snapshot?.runState == RunState.FINISHED) onFinish()
    }

    SessionContent(
        snapshot = snapshot,
        notices = notices,
        onNoticeAction = viewModel::openSettings,
        onNoticeDismiss = viewModel::dismiss,
        onTogglePause = viewModel::togglePause,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onAddTime = viewModel::addTime,
        onSubtractTime = viewModel::subtractTime,
        onStop = {
            viewModel.stop()
            onExit()
        },
    )
}

@Composable
private fun SessionContent(
    snapshot: SessionSnapshot?,
    notices: List<TimerRestriction>,
    onNoticeAction: (TimerRestriction) -> Unit,
    onNoticeDismiss: (TimerRestriction) -> Unit,
    onTogglePause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onAddTime: () -> Unit,
    onSubtractTime: () -> Unit,
    onStop: () -> Unit,
) {
    val palette = timerPalette
    val paused = snapshot?.runState == RunState.PAUSED

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(palette.background)
                .safeDrawingPadding()
                .padding(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        notices.forEach { restriction ->
            RestrictionNotice(
                restriction = restriction,
                onAction = { onNoticeAction(restriction) },
                onDismiss = { onNoticeDismiss(restriction) },
            )
        }

        Text(
            text = snapshot?.currentStageName ?: stringResource(R.string.timer_idle),
            style = YtaTextStyles.stageTitle,
            color = palette.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = remainingText(snapshot),
            style = YtaTextStyles.timerDisplay,
            color = if (paused) palette.paused else palette.onBackground,
        )
        Text(
            text = totalText(snapshot),
            style = MaterialTheme.typography.bodyLarge,
            color = palette.onBackgroundMuted,
            textAlign = TextAlign.Center,
        )

        Row(
            modifier = Modifier.padding(top = Spacing.l),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            SessionButton(stringResource(R.string.timer_previous), onPrevious)
            Button(
                onClick = onTogglePause,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = palette.running,
                        contentColor = palette.background,
                    ),
            ) {
                Text(stringResource(if (paused) R.string.timer_resume else R.string.timer_pause))
            }
            SessionButton(stringResource(R.string.timer_next), onNext)
        }
        Row(
            modifier = Modifier.padding(top = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            SessionButton(stringResource(R.string.timer_subtract_30), onSubtractTime)
            SessionButton(stringResource(R.string.timer_add_30), onAddTime)
            SessionButton(stringResource(R.string.timer_stop), onStop)
        }
    }
}

/** Кнопка рабочего экрана: контур и подпись — цветами палитры, а не схемы. */
@Composable
private fun SessionButton(
    label: String,
    onClick: () -> Unit,
) {
    val palette = timerPalette
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.onBackground),
    ) {
        Text(label)
    }
}

/**
 * Сообщение об ограничении системы.
 *
 * Красным — только то, из-за чего занятие пройдёт молча или без управления.
 * Совет про энергосбережение красным не бывает: он описывает риск, а не
 * поломку, закрывается навсегда и не имеет права мозолить глаза каждый раз
 * (docs/05-PLAY-DECLARATIONS.md §5). Полноценные баннеры рабочего экрана —
 * Фаза 6.
 */
@Composable
private fun RestrictionNotice(
    restriction: TimerRestriction,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = timerPalette
    val warning = restriction.severity == RestrictionSeverity.WARNING
    val accent = if (warning) palette.danger else palette.running

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.m),
        colors = CardDefaults.cardColors(containerColor = palette.ringTrack),
    ) {
        Column(modifier = Modifier.padding(Spacing.m)) {
            Text(
                text = stringResource(restriction.messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackground,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text =
                            stringResource(
                                if (warning) R.string.timer_notice_dismiss else R.string.timer_notice_understood,
                            ),
                        color = palette.onBackgroundMuted,
                    )
                }
                TextButton(onClick = onAction) {
                    Text(stringResource(restriction.actionRes), color = accent)
                }
            }
        }
    }
}

/**
 * Формулировки честные до цифр: пользователю важно знать не то, что «что-то
 * может пойти не так», а что именно и насколько это плохо.
 */
private val TimerRestriction.messageRes: Int
    get() =
        when (this) {
            TimerRestriction.NOTIFICATIONS_DISABLED -> R.string.timer_notice_notifications
            TimerRestriction.ALARMS_SILENCED_BY_DND -> R.string.timer_notice_dnd
            TimerRestriction.BATTERY_OPTIMIZED -> R.string.timer_notice_battery
            TimerRestriction.EXACT_ALARMS_UNAVAILABLE -> R.string.timer_notice_exact_alarms
        }

private val TimerRestriction.actionRes: Int
    get() =
        when (this) {
            TimerRestriction.NOTIFICATIONS_DISABLED -> R.string.timer_notice_action_notifications
            TimerRestriction.ALARMS_SILENCED_BY_DND -> R.string.timer_notice_action_dnd
            TimerRestriction.BATTERY_OPTIMIZED -> R.string.timer_notice_action_battery
            TimerRestriction.EXACT_ALARMS_UNAVAILABLE -> R.string.timer_notice_action_exact_alarms
        }

@Composable
private fun remainingText(snapshot: SessionSnapshot?): String {
    val remaining = snapshot?.stageRemainingMs
    return when {
        snapshot == null -> stringResource(R.string.timer_no_time)

        // У свободного этапа конца нет — счёт идёт вверх (решение B-5).
        remaining == null -> TimeFormatter.clock(snapshot.stageElapsedMs)

        else -> TimeFormatter.clock(remaining, roundUp = true)
    }
}

@Composable
private fun totalText(snapshot: SessionSnapshot?): String {
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

/** Экран после завершения занятия: «В начало» / «Повторить». */
@Composable
internal fun SessionFinishedScreen(
    onRepeat: () -> Unit,
    onExit: () -> Unit,
) {
    PlaceholderScreen(
        title = stringResource(R.string.timer_finished_title),
        description = "",
        actions =
            listOf(
                PlaceholderAction(stringResource(R.string.timer_finished_repeat)) { onRepeat() },
                PlaceholderAction(stringResource(R.string.timer_finished_exit)) { onExit() },
            ),
    )
}

@Preview
@Composable
private fun SessionContentPreview() {
    YtaTheme(darkTheme = false) {
        SessionContent(
            snapshot = null,
            notices = emptyList(),
            onNoticeAction = {},
            onNoticeDismiss = {},
            onTogglePause = {},
            onNext = {},
            onPrevious = {},
            onAddTime = {},
            onSubtractTime = {},
            onStop = {},
        )
    }
}
