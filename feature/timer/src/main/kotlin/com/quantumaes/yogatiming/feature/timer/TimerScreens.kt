package com.quantumaes.yogatiming.feature.timer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumaes.yogatiming.core.common.time.TimeFormatter
import com.quantumaes.yogatiming.core.designsystem.component.KeepScreenOn
import com.quantumaes.yogatiming.core.designsystem.theme.Dimens
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.TimerPalette
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTextStyles
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTheme
import com.quantumaes.yogatiming.core.designsystem.theme.timerPalette
import com.quantumaes.yogatiming.feature.timer.component.LockOverlay
import com.quantumaes.yogatiming.feature.timer.component.ProgressRing
import com.quantumaes.yogatiming.feature.timer.component.RestrictionNotice
import com.quantumaes.yogatiming.feature.timer.component.SessionControls
import com.quantumaes.yogatiming.feature.timer.component.TimerDisplay
import com.quantumaes.yogatiming.feature.timer.component.WindowBrightness
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionSnapshot
import com.quantumaes.yogatiming.timer.engine.model.StageKind
import com.quantumaes.yogatiming.timer.service.restrictions.TimerRestriction
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Через сколько бездействия гаснет экран в режиме фокуса (ТЗ, Экран 4). */
private const val IDLE_DIM_MS = 15_000L

/** Смена этапа подсвечивает экран независимо от таймера бездействия. */
private const val STAGE_FLASH_MS = 2_000L

/** До какой яркости приглушать окно. Не в ноль: цифры должны остаться видны. */
private const val DIM_BRIGHTNESS = 0.1f

/**
 * Плотность затемняющей пелены поверх экрана.
 *
 * Яркости окна одной мало: `screenBrightness` — величина абсолютная, и в
 * полутёмном зале, где системная яркость и без того около десяти процентов,
 * приглушение попросту не видно (полевая проверка 2026-07-31, замечание 3).
 * Пелена же затемняет ровно относительно текущего вида и работает на любом
 * устройстве, что бы оболочка ни делала с яркостью окна.
 */
private const val DIM_SCRIM_ALPHA = 0.55f

/** Насколько плавно гаснет и возвращается экран. Резкая смена бьёт по глазам. */
private const val DIM_FADE_MS = 700

/** Порог свайпа в фокусе, px. Ниже — случайное смещение пальца при тапе. */
private const val SWIPE_THRESHOLD_PX = 120f

/**
 * Экран 4 «Занятие» (Фаза 6 дорожной карты).
 *
 * Три режима из карты жестов (docs/03-GESTURES.md): обычный с кнопками, фокус
 * без них и блокировка. Управление ранжировано по частоте использования на
 * реальном занятии, а не по порядку макета: «Пауза» доминирует, «Пред. этап»
 * уходит в текстовую кнопку (§5.1 анализа).
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
    onOpenSettings: () -> Unit,
) {
    val viewModel: TimerViewModel = hiltViewModel()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val notices by viewModel.notices.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // Экран не гаснет и не приглушается системой, пока идёт занятие: инструктор
    // смотрит на таймер издалека и не может тянуться к телефону, чтобы его
    // разбудить. Процессор при этом держит сервис (SessionWakeLock), а экран —
    // только этот флаг и только пока рабочий экран на переднем плане.
    KeepScreenOn(enabled = settings.keepScreenOn)

    LaunchedEffect(profileId) { viewModel.ensureSession(profileId) }

    // Ограничения перечитываются на каждом возвращении на экран: пользователь
    // уходит их чинить в системные настройки и возвращается сюда — другого
    // сигнала об изменении система не присылает.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshRestrictions() }

    // Только собственное занятие этого экрана: `FINISHED` от предыдущего
    // отсеивается моделью (см. `TimerViewModel.finished`).
    LaunchedEffect(finished) {
        if (finished) onFinish()
    }

    var mode by rememberSaveable { mutableStateOf(SessionMode.NORMAL) }
    var stopRequested by rememberSaveable { mutableStateOf(false) }

    SessionScreen(
        snapshot = snapshot,
        notices = notices,
        autoDimEnabled = settings.autoDimEnabled,
        settingsAvailable = settings.settingsFromSession,
        onOpenSettings = onOpenSettings,
        mode = mode,
        onModeChange = { mode = it },
        onNoticeAction = viewModel::openSettings,
        onNoticeDismiss = viewModel::dismiss,
        onTogglePause = viewModel::togglePause,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onAddTime = viewModel::addTime,
        onSubtractTime = viewModel::subtractTime,
        onStopRequest = { stopRequested = true },
    )

    // Системная «Назад» в обычном режиме спрашивает — занятие идёт, и выйти
    // из него случайным свайпом от края нельзя. В фокусе она выходит из фокуса,
    // под блокировкой игнорируется (docs/03-GESTURES.md §3).
    BackHandler(enabled = true) {
        when (mode) {
            SessionMode.NORMAL -> stopRequested = true
            SessionMode.FOCUS -> mode = SessionMode.NORMAL
            SessionMode.LOCK -> Unit
        }
    }

    if (stopRequested) {
        StopConfirmation(
            onConfirm = {
                stopRequested = false
                viewModel.stop()
                onExit()
            },
            onDismiss = { stopRequested = false },
        )
    }
}

/**
 * Разметка экрана.
 *
 * Ландшафт — отдельная раскладка, а не повёрнутый портрет (§5.4 анализа):
 * в повёрнутом портрете кольцо съедает всю высоту и кнопки уезжают за экран.
 */
@Composable
private fun SessionScreen(
    snapshot: SessionSnapshot?,
    notices: List<TimerRestriction>,
    autoDimEnabled: Boolean,
    settingsAvailable: Boolean,
    onOpenSettings: () -> Unit,
    mode: SessionMode,
    onModeChange: (SessionMode) -> Unit,
    onNoticeAction: (TimerRestriction) -> Unit,
    onNoticeDismiss: (TimerRestriction) -> Unit,
    onTogglePause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onAddTime: () -> Unit,
    onSubtractTime: () -> Unit,
    onStopRequest: () -> Unit,
) {
    val palette = timerPalette
    var interactions by remember { mutableIntStateOf(0) }

    // Ландшафт определяется по размеру окна, а не по ориентации устройства:
    // в многооконном режиме телефон стоит вертикально, а окно шире, чем выше.
    val window = LocalWindowInfo.current.containerSize
    val landscape = window.width > window.height

    val dimmed =
        isDimmed(
            mode = mode,
            enabled = autoDimEnabled,
            interactions = interactions,
            stageIndex = snapshot?.currentIndex,
        )
    WindowBrightness(level = if (dimmed) DIM_BRIGHTNESS else null)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(palette.background),
    ) {
        val content =
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(Spacing.m)

        when {
            mode.isFocus -> {
                FocusContent(
                    snapshot = snapshot,
                    palette = palette,
                    modifier =
                        content.focusGestures(
                            onExit = { onModeChange(SessionMode.NORMAL) },
                            onNext = onNext,
                            onPrevious = onPrevious,
                            onInteraction = { interactions++ },
                        ),
                )
            }

            landscape -> {
                LandscapeContent(
                    snapshot = snapshot,
                    notices = notices,
                    palette = palette,
                    settingsAvailable = settingsAvailable,
                    onOpenSettings = onOpenSettings,
                    onModeChange = onModeChange,
                    onNoticeAction = onNoticeAction,
                    onNoticeDismiss = onNoticeDismiss,
                    onTogglePause = onTogglePause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onAddTime = onAddTime,
                    onSubtractTime = onSubtractTime,
                    onStopRequest = onStopRequest,
                    modifier = content,
                )
            }

            else -> {
                PortraitContent(
                    snapshot = snapshot,
                    notices = notices,
                    palette = palette,
                    settingsAvailable = settingsAvailable,
                    onOpenSettings = onOpenSettings,
                    onModeChange = onModeChange,
                    onNoticeAction = onNoticeAction,
                    onNoticeDismiss = onNoticeDismiss,
                    onTogglePause = onTogglePause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onAddTime = onAddTime,
                    onSubtractTime = onSubtractTime,
                    onStopRequest = onStopRequest,
                    modifier = content,
                )
            }
        }

        // Пелена без собственных обработчиков касаний: жесты режима фокуса
        // проходят сквозь неё и сами же её снимают.
        val scrim by animateFloatAsState(
            targetValue = if (dimmed) DIM_SCRIM_ALPHA else 0f,
            animationSpec = tween(DIM_FADE_MS),
            label = "focus-dim",
        )
        if (scrim > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))
        }

        if (mode.isLocked) {
            LockOverlay(palette = palette, onUnlock = { onModeChange(SessionMode.NORMAL) })
        }
    }
}

/**
 * Пора ли гасить экран в режиме фокуса (ТЗ, Экран 4).
 *
 * Два независимых повода светить полной яркостью: касание и смена этапа.
 * Касание перезапускает пятнадцатисекундный отсчёт бездействия, смена этапа
 * подсвечивает экран на две секунды — за них инструктор успевает поднять
 * глаза и увидеть, что началось.
 */
@Composable
private fun isDimmed(
    mode: SessionMode,
    enabled: Boolean,
    interactions: Int,
    stageIndex: Int?,
): Boolean {
    var idle by remember { mutableStateOf(false) }
    var stageFlash by remember { mutableStateOf(false) }

    LaunchedEffect(mode, enabled, interactions) {
        idle = false
        if (!mode.isFocus || !enabled) return@LaunchedEffect
        delay(IDLE_DIM_MS)
        idle = true
    }

    LaunchedEffect(stageIndex) {
        if (stageIndex == null) return@LaunchedEffect
        stageFlash = true
        delay(STAGE_FLASH_MS)
        stageFlash = false
    }

    return idle && !stageFlash
}

/**
 * Тап без ряби и без прямоугольника подсветки: экран рисуется фиксированной
 * палитрой, и ripple из схемы Material смотрелся бы на нём чужим. Роль и
 * подпись действия остаются — TalkBack объявляет цель как кнопку.
 */
internal fun Modifier.tapTarget(
    label: String,
    onTap: () -> Unit,
): Modifier =
    this
        .pointerInput(onTap) { detectTapGestures { onTap() } }
        .semantics {
            role = Role.Button
            onClick(label = label) {
                onTap()
                true
            }
        }

/**
 * Свайпы режима фокуса.
 *
 * Направление определяется по преобладающей оси в конце жеста, а не в его
 * начале: палец на коврике идёт по дуге, и решение по первым пикселям
 * ошибается. Любое касание считается активностью и отменяет автозатемнение.
 */
private fun Modifier.focusGestures(
    onExit: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onInteraction: () -> Unit,
): Modifier =
    this
        .pointerInput(onExit, onNext, onPrevious) {
            var total = Offset.Zero
            detectDragGestures(
                onDragStart = {
                    total = Offset.Zero
                    onInteraction()
                },
                onDragEnd = {
                    when {
                        abs(total.x) > abs(total.y) && abs(total.x) > SWIPE_THRESHOLD_PX -> {
                            if (total.x < 0) onNext() else onPrevious()
                        }

                        total.y > SWIPE_THRESHOLD_PX -> {
                            onExit()
                        }
                    }
                },
                onDrag = { _, delta -> total += delta },
            )
        }.pointerInput(onExit) {
            detectTapGestures {
                onInteraction()
                onExit()
            }
        }

/** Подтверждение выхода: занятие идёт, и обрывать его случайным жестом нельзя. */
@Composable
private fun StopConfirmation(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timer_stop_title)) },
        text = { Text(stringResource(R.string.timer_stop_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.timer_stop_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.timer_stop_cancel)) }
        },
    )
}

@Preview
@Composable
private fun SessionScreenPreview() {
    YtaTheme(darkTheme = true) {
        SessionScreen(
            snapshot = previewSnapshot(),
            notices = emptyList(),
            autoDimEnabled = true,
            settingsAvailable = true,
            onOpenSettings = {},
            mode = SessionMode.NORMAL,
            onModeChange = {},
            onNoticeAction = {},
            onNoticeDismiss = {},
            onTogglePause = {},
            onNext = {},
            onPrevious = {},
            onAddTime = {},
            onSubtractTime = {},
            onStopRequest = {},
        )
    }
}

private fun previewSnapshot() =
    SessionSnapshot(
        profileId = 1,
        profileName = "Хатха 60 мин",
        runState = RunState.RUNNING,
        currentIndex = 2,
        stageCount = 6,
        currentStageName = "Асаны стоя",
        currentStageColor = "#FFB300",
        currentStageKind = StageKind.NORMAL,
        currentNote = "Держим позиции по 5 циклов дыхания",
        stageRemainingMs = 754_000,
        stageElapsedMs = 326_000,
        stageDurationMs = 1_080_000,
        stageProgress = 0.3f,
        stageAdjustmentMs = 0,
        totalElapsedMs = 1_100_000,
        totalRemainingMs = 2_500_000,
        totalRemainingIsLowerBound = false,
        totalProgress = 0.3f,
        nextStageName = "Балансы",
        nextStageDurationMs = 720_000,
        isLastStage = false,
    )
