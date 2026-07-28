package com.quantumaes.yogatiming.feature.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumaes.yogatiming.core.common.time.TimeFormatter
import com.quantumaes.yogatiming.core.designsystem.component.KeepScreenOn
import com.quantumaes.yogatiming.core.designsystem.component.PlaceholderAction
import com.quantumaes.yogatiming.core.designsystem.component.PlaceholderScreen
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionSnapshot
import com.quantumaes.yogatiming.timer.service.restrictions.RestrictionSeverity
import com.quantumaes.yogatiming.timer.service.restrictions.TimerRestriction

/**
 * Экран 4 «Занятие» — временная проверочная версия Фазы 3.
 *
 * Настоящий экран с кольцом прогресса, жестами и фиксированной палитрой —
 * Фаза 6 (docs/01-ROADMAP.md). Здесь ровно то, что нужно для вехи M2: увидеть
 * своими глазами, что отсчёт идёт при заблокированном экране, что управление
 * из шторки работает и что сессия переживает убийство процесса.
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
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.m),
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
            text = snapshot?.currentStageName ?: "Занятие не запущено",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = remainingText(snapshot),
            style = MaterialTheme.typography.displayLarge,
        )
        Text(
            text = totalText(snapshot),
            style = MaterialTheme.typography.bodyLarge,
        )

        Row(
            modifier = Modifier.padding(top = Spacing.l),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            OutlinedButton(onClick = onPrevious) { Text("Пред.") }
            Button(onClick = onTogglePause) {
                Text(if (snapshot?.runState == RunState.PAUSED) "Продолжить" else "Пауза")
            }
            OutlinedButton(onClick = onNext) { Text("След.") }
        }
        Row(
            modifier = Modifier.padding(top = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            OutlinedButton(onClick = onSubtractTime) { Text("−30 с") }
            OutlinedButton(onClick = onAddTime) { Text("+30 с") }
            OutlinedButton(onClick = onStop) { Text("Стоп") }
        }
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
    val warning = restriction.severity == RestrictionSeverity.WARNING
    val container =
        if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    val content =
        if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val accent = if (warning) content else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.m),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.padding(Spacing.m)) {
            Text(
                text = restrictionText(restriction),
                style = MaterialTheme.typography.bodyMedium,
                color = content,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(if (warning) "Скрыть" else "Понятно", color = content)
                }
                TextButton(onClick = onAction) {
                    Text(restriction.actionLabel, color = accent)
                }
            }
        }
    }
}

/**
 * Формулировки честные до цифр: пользователю важно знать не то, что «что-то
 * может пойти не так», а что именно и насколько это плохо.
 */
private fun restrictionText(restriction: TimerRestriction): String =
    when (restriction) {
        TimerRestriction.NOTIFICATIONS_DISABLED -> {
            "Уведомления выключены: управлять занятием из шторки нельзя, " +
                "а система охотнее останавливает сервис без видимого уведомления."
        }

        TimerRestriction.ALARMS_SILENCED_BY_DND -> {
            "Режим «Полная тишина» глушит канал будильника — сигналов занятия слышно не будет."
        }

        TimerRestriction.BATTERY_OPTIMIZED -> {
            "Пока приложение живо, отсчёт идёт по монотонным часам и сигналы звучат вовремя. " +
                "Но система вправе выгрузить его из памяти, и тогда возврат к занятию " +
                "займёт до нескольких минут. Список «без ограничений» это снимает — " +
                "фирменные энергосберегайки оболочки на него не влияют."
        }

        TimerRestriction.EXACT_ALARMS_UNAVAILABLE -> {
            "Точные будильники запрещены. Отсчёт это не затрагивает, но если система выгрузит " +
                "приложение, страховочное пробуждение сработает с задержкой."
        }
    }

private val TimerRestriction.actionLabel: String
    get() =
        when (this) {
            TimerRestriction.NOTIFICATIONS_DISABLED -> "Включить"
            TimerRestriction.ALARMS_SILENCED_BY_DND -> "Настроить"
            TimerRestriction.BATTERY_OPTIMIZED -> "Снять ограничения"
            TimerRestriction.EXACT_ALARMS_UNAVAILABLE -> "Разрешить"
        }

private fun remainingText(snapshot: SessionSnapshot?): String {
    val remaining = snapshot?.stageRemainingMs
    return when {
        snapshot == null -> "--:--"

        // У свободного этапа конца нет — счёт идёт вверх (решение B-5).
        remaining == null -> TimeFormatter.clock(snapshot.stageElapsedMs)

        else -> TimeFormatter.clock(remaining, roundUp = true)
    }
}

private fun totalText(snapshot: SessionSnapshot?): String {
    if (snapshot == null) return ""
    val prefix = if (snapshot.totalRemainingIsLowerBound) "≥ " else ""
    val position = "Этап ${snapshot.currentIndex + 1}/${snapshot.stageCount}"
    val adjustment =
        if (snapshot.stageAdjustmentMs == 0L) "" else " · ${TimeFormatter.signedClock(snapshot.stageAdjustmentMs)}"
    return "$position · осталось $prefix${TimeFormatter.clock(snapshot.totalRemainingMs)}$adjustment"
}

/** Экран после завершения занятия: «В начало» / «Повторить». */
@Composable
internal fun SessionFinishedScreen(
    onRepeat: () -> Unit,
    onExit: () -> Unit,
) {
    PlaceholderScreen(
        title = "Занятие завершено",
        description = "Заглушка Фазы 1.",
        actions =
            listOf(
                PlaceholderAction("Повторить") { onRepeat() },
                PlaceholderAction("К списку профилей") { onExit() },
            ),
    )
}
