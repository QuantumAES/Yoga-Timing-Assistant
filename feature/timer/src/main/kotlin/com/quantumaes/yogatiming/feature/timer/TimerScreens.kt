package com.quantumaes.yogatiming.feature.timer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import com.quantumaes.yogatiming.domain.settings.TimerShape
import com.quantumaes.yogatiming.feature.timer.component.FocusArea
import com.quantumaes.yogatiming.feature.timer.component.LockOverlay
import com.quantumaes.yogatiming.feature.timer.component.ProgressRing
import com.quantumaes.yogatiming.feature.timer.component.RestrictionNotice
import com.quantumaes.yogatiming.feature.timer.component.SessionControls
import com.quantumaes.yogatiming.feature.timer.component.TimerDisplay
import com.quantumaes.yogatiming.feature.timer.component.WindowBrightness
import com.quantumaes.yogatiming.timer.engine.model.PauseMode
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionSnapshot
import com.quantumaes.yogatiming.timer.engine.model.StageKind
import com.quantumaes.yogatiming.timer.service.restrictions.TimerRestriction
import kotlinx.coroutines.delay

/** Через сколько бездействия гаснет экран в режиме фокуса (ТЗ, Экран 4). */
private const val IDLE_DIM_MS = 15_000L

/** До какой яркости приглушать окно. Не в ноль: цифры должны остаться видны. */
private const val DIM_BRIGHTNESS = 0.05f

/**
 * Плотность затемняющей пелены поверх экрана.
 *
 * Яркости окна одной мало: `screenBrightness` — величина абсолютная, и в
 * полутёмном зале, где системная яркость и без того около десяти процентов,
 * приглушение попросту не видно (полевая проверка 2026-07-31, замечание 3).
 * Пелена же затемняет ровно относительно текущего вида и работает на любом
 * устройстве, что бы оболочка ни делала с яркостью окна.
 *
 * Две трети, а не половина (третий круг, замечание 2): при 0,55 белые цифры на
 * почти чёрном фоне гасли до 45% — заметно на снимке экрана и почти незаметно
 * глазу, который к этому моменту уже пятнадцать секунд смотрит на тот же экран.
 */
private const val DIM_SCRIM_ALPHA = 0.66f

/** Насколько плавно гаснет и возвращается экран. Резкая смена бьёт по глазам. */
private const val DIM_FADE_MS = 700

/**
 * Экран 4 «Занятие» (Фаза 6 дорожной карты).
 *
 * Два вида экрана из карты жестов (docs/03-GESTURES.md): обычный с кнопками и
 * фокус без них, — плюс блокировка поверх любого из них. Управление ранжировано
 * по частоте использования на реальном занятии, а не по порядку макета: «Пауза»
 * доминирует, «Пред. этап» уходит в текстовую кнопку (§5.1 анализа).
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
    val ended by viewModel.ended.collectAsStateWithLifecycle()
    val started by viewModel.started.collectAsStateWithLifecycle()
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

    // Любой конец занятия — свой, чужой, из шторки — ведёт на экран итогов.
    // Только собственное занятие этого экрана: состояние предыдущего
    // отсеивается моделью (см. `TimerViewModel.ended`).
    LaunchedEffect(ended) {
        if (ended) onFinish()
    }

    var mode by rememberSaveable { mutableStateOf(SessionMode.NORMAL) }
    // Блокировка — слой поверх режима, а не режим: запертый фокус остаётся
    // фокусом и после разблокировки (замечание 5 полевой проверки 2026-08-05).
    var locked by rememberSaveable { mutableStateOf(false) }
    var stopRequested by rememberSaveable { mutableStateOf(false) }

    // Плашка отсечки живёт до тех пор, пока её не закрыли или пока план не
    // уложился в бюджет. Закрытие помнится экраном, а не движком: это решение
    // «я видел и не буду ничего менять», а не часть состояния занятия.
    var wrapUpDismissed by rememberSaveable { mutableStateOf(false) }
    val wrapUpVisible = snapshot.let { it?.wrapUpPassed == true && it.budgetOverrun } && !wrapUpDismissed

    SessionScreen(
        snapshot = snapshot,
        notices = notices,
        autoDimEnabled = settings.autoDimEnabled,
        shape = settings.timerShape,
        settingsAvailable = settings.settingsFromSession,
        wrapUpVisible = wrapUpVisible,
        onOpenSettings = onOpenSettings,
        mode = mode,
        onModeChange = { mode = it },
        locked = locked,
        onLockChange = { locked = it },
        onNoticeAction = viewModel::openSettings,
        onNoticeDismiss = viewModel::dismiss,
        onTogglePause = viewModel::togglePause,
        onSwitchPauseMode = viewModel::switchPauseMode,
        onFitToBudget = {
            viewModel.fitToBudget()
            wrapUpDismissed = true
        },
        onDismissWrapUp = { wrapUpDismissed = true },
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onAddTime = viewModel::addTime,
        onSubtractTime = viewModel::subtractTime,
        onStopRequest = { stopRequested = true },
    )

    // Системная «Назад» в обычном режиме спрашивает — занятие идёт, и выйти
    // из него случайным свайпом от края нельзя. В фокусе она выходит из фокуса,
    // под блокировкой игнорируется (docs/03-GESTURES.md §3). Блокировка
    // проверяется первой: она забирает всё управление, каким бы ни был режим.
    BackHandler(enabled = true) {
        when {
            locked -> Unit
            mode.isFocus -> mode = SessionMode.NORMAL
            else -> stopRequested = true
        }
    }

    if (stopRequested) {
        StopConfirmation(
            onConfirm = {
                stopRequested = false
                viewModel.stop()
                // Занятие, которое успело начаться, уходит на экран итогов по
                // `ended` — с профилем и длительностью. Не начавшееся показывать
                // нечем: профиль без этапов или упавший запуск сервиса.
                if (!started) onExit()
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
    shape: TimerShape,
    settingsAvailable: Boolean,
    wrapUpVisible: Boolean,
    onOpenSettings: () -> Unit,
    mode: SessionMode,
    onModeChange: (SessionMode) -> Unit,
    locked: Boolean,
    onLockChange: (Boolean) -> Unit,
    onNoticeAction: (TimerRestriction) -> Unit,
    onNoticeDismiss: (TimerRestriction) -> Unit,
    onTogglePause: () -> Unit,
    onSwitchPauseMode: (PauseMode) -> Unit,
    onFitToBudget: () -> Unit,
    onDismissWrapUp: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onAddTime: () -> Unit,
    onSubtractTime: () -> Unit,
    onStopRequest: () -> Unit,
) {
    val palette = timerPalette

    // Ландшафт определяется по размеру окна, а не по ориентации устройства:
    // в многооконном режиме телефон стоит вертикально, а окно шире, чем выше.
    val window = LocalWindowInfo.current.containerSize
    val landscape = window.width > window.height

    // Счётчик поводов светить полной яркостью: касание экрана и смена этапа.
    // Один счётчик на оба повода — см. `isDimmed`.
    var wakeUps by remember { mutableIntStateOf(0) }
    val stageIndex = snapshot?.currentIndex
    LaunchedEffect(stageIndex) { wakeUps++ }

    val dimmed = isDimmed(focus = mode.isFocus, enabled = autoDimEnabled, wakeUps = wakeUps)
    WindowBrightness(level = if (dimmed) DIM_BRIGHTNESS else null)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(palette.background),
    ) {
        // Боковое поле здесь не задаётся: его накладывают сами раскладки, по
        // элементам. Кольцо и цифры доходят почти до края экрана — там каждый
        // dp виден с трёх метров, — а строки текста поле сохраняют.
        val content =
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                // Вертикальное поле — половинное: системные отступы уже учтены
                // `safeDrawingPadding`, а каждые восемь dp сверху и снизу это
                // шестнадцать dp диаметра кольца в портрете.
                .padding(vertical = Spacing.s)

        when {
            mode.isFocus -> {
                FocusArea(
                    onExit = { onModeChange(SessionMode.NORMAL) },
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onTogglePause = onTogglePause,
                    onInteraction = { wakeUps++ },
                    modifier = content,
                    // Страница, которая приедет на место текущей: пока палец
                    // не отпущен, она въезжает с противоположной стороны и
                    // говорит, чего ждать (замечание 3 полевой проверки
                    // 2026-08-05).
                    peek = { target ->
                        FocusPeekPage(
                            target = target,
                            snapshot = snapshot,
                            palette = palette,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                ) {
                    FocusContent(
                        snapshot = snapshot,
                        palette = palette,
                        settingsAvailable = settingsAvailable,
                        onOpenSettings = onOpenSettings,
                        onLock = { onLockChange(true) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            landscape -> {
                LandscapeContent(
                    snapshot = snapshot,
                    notices = notices,
                    palette = palette,
                    shape = shape,
                    settingsAvailable = settingsAvailable,
                    wrapUpVisible = wrapUpVisible,
                    onOpenSettings = onOpenSettings,
                    onModeChange = onModeChange,
                    onLock = { onLockChange(true) },
                    onNoticeAction = onNoticeAction,
                    onNoticeDismiss = onNoticeDismiss,
                    onTogglePause = onTogglePause,
                    onSwitchPauseMode = onSwitchPauseMode,
                    onFitToBudget = onFitToBudget,
                    onDismissWrapUp = onDismissWrapUp,
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
                    shape = shape,
                    settingsAvailable = settingsAvailable,
                    wrapUpVisible = wrapUpVisible,
                    onOpenSettings = onOpenSettings,
                    onModeChange = onModeChange,
                    onLock = { onLockChange(true) },
                    onNoticeAction = onNoticeAction,
                    onNoticeDismiss = onNoticeDismiss,
                    onTogglePause = onTogglePause,
                    onSwitchPauseMode = onSwitchPauseMode,
                    onFitToBudget = onFitToBudget,
                    onDismissWrapUp = onDismissWrapUp,
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

        // Разблокировка возвращает тот экран, который заперли: тот, кто запер
        // фокус, ждёт увидеть под пеленой те же цифры во весь экран.
        if (locked) {
            LockOverlay(palette = palette, onUnlock = { onLockChange(false) })
        }
    }
}

/**
 * Пора ли гасить экран в режиме фокуса (ТЗ, Экран 4).
 *
 * Один таймер и один флаг: пятнадцать секунд без повода светить — гасим, любой
 * повод — зажигаем и отсчитываем заново. Поводов два, касание и смена этапа, и
 * оба приходят одним счётчиком [wakeUps].
 *
 * Раньше поводов было два и флага тоже два: `idle` от бездействия и `stageFlash`
 * на две секунды от смены этапа, а решение принималось как `idle && !stageFlash`.
 * Схема разваливалась молча: эффект подсветки перезапускался по индексу этапа, и
 * если индекс успевал стать `null` (снимок занятия между сессиями), корутина
 * снималась с уже поднятым `stageFlash` и опустить его было некому. Экран после
 * этого не гас никогда, и понять почему, глядя на него, нельзя было (полевая
 * проверка 2026-07-31, третий круг, замечание 2). Одно состояние, которое
 * некуда рассинхронизировать, стоит дешевле двухсекундной подсветки: смена
 * этапа теперь просто заводит те же пятнадцать секунд заново.
 */
@Composable
private fun isDimmed(
    focus: Boolean,
    enabled: Boolean,
    wakeUps: Int,
): Boolean {
    var dimmed by remember { mutableStateOf(false) }

    LaunchedEffect(focus, enabled, wakeUps) {
        dimmed = false
        if (!focus || !enabled) return@LaunchedEffect
        delay(IDLE_DIM_MS)
        dimmed = true
    }

    return dimmed
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
    SessionScreenPreview(TimerShape.RING)
}

/**
 * Панель на маленьком экране — то, ради чего вторая форма и появилась.
 *
 * Размер окна задан явно: на превью по умолчанию (телефон целиком) разница
 * между круг и панелью скромная, а видна она именно там, где кольцу не хватает
 * высоты (полевая проверка 2026-08-03, замечание 2).
 */
@Preview(widthDp = 360, heightDp = 592)
@Composable
private fun SessionScreenPanelPreview() {
    SessionScreenPreview(TimerShape.PANEL)
}

@Composable
private fun SessionScreenPreview(shape: TimerShape) {
    YtaTheme(darkTheme = true) {
        SessionScreen(
            snapshot = previewSnapshot(),
            notices = emptyList(),
            autoDimEnabled = true,
            shape = shape,
            settingsAvailable = true,
            wrapUpVisible = false,
            onOpenSettings = {},
            mode = SessionMode.NORMAL,
            onModeChange = {},
            locked = false,
            onLockChange = {},
            onNoticeAction = {},
            onNoticeDismiss = {},
            onTogglePause = {},
            onSwitchPauseMode = {},
            onFitToBudget = {},
            onDismissWrapUp = {},
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
        pauseMode = PauseMode.SESSION,
        currentIndex = 2,
        stageCount = 6,
        currentStageName = "Асаны стоя",
        currentStageColor = "#FFB300",
        currentStageKind = StageKind.NORMAL,
        currentStageSide = null,
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
        sessionElapsedMs = 1_100_000,
        holdMs = 0,
        budgetRemainingMs = 2_500_000,
        budgetDeficitMs = 0,
        budgetToleranceMs = 300_000,
        wrapUpPassed = false,
        nextStageName = "Балансы",
        nextStageDurationMs = 720_000,
        isLastStage = false,
    )
