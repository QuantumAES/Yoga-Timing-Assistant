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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumaes.yogatiming.core.common.time.TimeFormatter
import com.quantumaes.yogatiming.core.designsystem.component.PlaceholderAction
import com.quantumaes.yogatiming.core.designsystem.component.PlaceholderScreen
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionSnapshot
import com.quantumaes.yogatiming.timer.service.restrictions.TimerRestrictions

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
    val restrictions by viewModel.restrictions.collectAsStateWithLifecycle()

    LaunchedEffect(profileId) {
        viewModel.ensureSession(profileId)
        viewModel.refreshRestrictions()
    }

    LaunchedEffect(snapshot?.runState) {
        if (snapshot?.runState == RunState.FINISHED) onFinish()
    }

    SessionContent(
        snapshot = snapshot,
        restrictions = restrictions,
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
    restrictions: TimerRestrictions,
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
        if (restrictions.hasAny) {
            RestrictionsBanner(restrictions)
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

@Composable
private fun RestrictionsBanner(restrictions: TimerRestrictions) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.m),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            text = restrictionsText(restrictions),
            modifier = Modifier.padding(Spacing.m),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/**
 * Критерий T-4 требует не победы над ограничениями, а честного о них
 * сообщения. Полноценные баннеры с переходом в системные настройки — Фаза 6.
 */
private fun restrictionsText(restrictions: TimerRestrictions): String =
    buildList {
        if (restrictions.batteryOptimized) add("включена оптимизация батареи")
        if (restrictions.exactAlarmsUnavailable) add("запрещены точные будильники")
        if (restrictions.notificationsDisabled) add("выключены уведомления")
        if (restrictions.alarmsSilencedByDnd) add("режим «Полная тишина» глушит сигналы")
    }.joinToString(prefix = "Оповещения могут запаздывать: ", separator = ", ")

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
