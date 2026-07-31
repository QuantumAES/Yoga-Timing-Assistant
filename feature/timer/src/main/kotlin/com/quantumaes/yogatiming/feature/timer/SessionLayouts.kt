package com.quantumaes.yogatiming.feature.timer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/**
 * Кольцо доходит почти до края экрана, а не до общего поля в 16 dp.
 *
 * Диаметр в портрете упирается в ширину экрана, а не в высоту, — значит
 * каждые отданные полю четыре dp это четыре dp диаметра и, через него, кегля
 * цифр (полевая проверка 2026-07-31, замечание 1). Остальные элементы экрана
 * поле сохраняют: строка текста, прижатая к краю, читается хуже.
 */
private val RING_SIDE_PADDING = 4.dp

/**
 * Высота полосы под кольцом — «Далее: …» и заметка инструктору.
 *
 * Фиксированная, а не по содержимому (полевая проверка 2026-07-31,
 * замечание 5): кольцо занимает всё, что осталось, и этап с заметкой ужимал
 * его на две строки текста относительно этапа без заметки. Диаметр прыгал на
 * каждом переходе, и глаз, привыкший к размеру цифр, каждый раз перенастраивался.
 */
private val FOOTER_HEIGHT = 72.dp

// ─── Геометрия содержимого кольца ────────────────────────────────────────────
//
// Текст внутри круга раскладывается по прямоугольнику, а круг сужается к краям
// — отсюда и наползание названия этапа на дугу (полевая проверка 2026-07-31,
// замечание 1). Ширина строки поэтому задаётся долей диаметра, и доля зависит
// от того, где строка стоит по высоте: половина хорды на удалении y от центра
// равна √(r² − y²).
//
// Полоса содержимого занимает 72% высоты, то есть её край отстоит от центра на
// 0,36 d. Там хорда — 0,59 d, и всё, что уже 0,58 d, гарантированно внутри
// круга. По центру круг шире: цифрам достаётся 0,84 d.

private const val RING_CONTENT_HEIGHT = 0.72f
private const val RING_EDGE_WIDTH = 0.58f
private const val RING_CENTER_WIDTH = 0.84f

/** Скруглённая плашка ручной поправки под цифрами. */
private val ADJUSTMENT_CORNER = 12.dp

/** Насколько плашка поправки светлее фона. Акцент, а не заливка. */
private const val ADJUSTMENT_BACKGROUND_ALPHA = 0.18f

// Раскладки рабочего экрана: портрет, ландшафт и режим фокуса. Отделены от
// TimerScreen намеренно — тот отвечает за состояние и жесты, эти функции
// только раскладывают уже готовые данные.

@Composable
internal fun PortraitContent(
    snapshot: SessionSnapshot?,
    notices: List<TimerRestriction>,
    palette: TimerPalette,
    settingsAvailable: Boolean,
    onOpenSettings: () -> Unit,
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
    // Боковое поле накладывается на элементы по отдельности: кольцу оно почти
    // не положено (см. RING_SIDE_PADDING), остальным — как везде.
    val sides = Modifier.padding(horizontal = Spacing.m)

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SessionTopBar(
            title = snapshot?.profileName.orEmpty(),
            palette = palette,
            settingsAvailable = settingsAvailable,
            onOpenSettings = onOpenSettings,
            onLock = { onModeChange(SessionMode.LOCK) },
            onStop = onStopRequest,
            modifier = sides,
        )

        notices.forEach { restriction ->
            RestrictionNotice(
                restriction = restriction,
                palette = palette,
                onAction = { onNoticeAction(restriction) },
                onDismiss = { onNoticeDismiss(restriction) },
                modifier = sides,
            )
        }

        StageRing(
            snapshot = snapshot,
            palette = palette,
            onEnterFocus = { onModeChange(SessionMode.FOCUS) },
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = RING_SIDE_PADDING, vertical = Spacing.s),
        )

        StageFooter(snapshot = snapshot, palette = palette, modifier = sides)

        SessionControls(
            paused = snapshot?.runState == RunState.PAUSED,
            palette = palette,
            onTogglePause = onTogglePause,
            onNext = onNext,
            onPrevious = onPrevious,
            onAddTime = onAddTime,
            onSubtractTime = onSubtractTime,
            // Блок опущен ниже кольца, а не прижат к нему (полевая проверка
            // 2026-07-31, замечание 5): у кнопок своя зона внизу экрана, и
            // пустая полоса между ней и кольцом — граница между «смотреть»
            // и «нажимать».
            modifier = sides.padding(top = Spacing.l),
        )
    }
}

/** Ландшафт: кольцо слева, управление справа — обе половины достижимы большим пальцем. */
@Composable
internal fun LandscapeContent(
    snapshot: SessionSnapshot?,
    notices: List<TimerRestriction>,
    palette: TimerPalette,
    settingsAvailable: Boolean,
    onOpenSettings: () -> Unit,
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
            modifier = Modifier.weight(1f).fillMaxSize().padding(end = Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SessionTopBar(
                title = snapshot?.profileName.orEmpty(),
                palette = palette,
                settingsAvailable = settingsAvailable,
                onOpenSettings = onOpenSettings,
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
 *
 * Содержимое вписано в круг по хорде, а не по квадрату кольца, и ширина каждой
 * строки задана долей диаметра (см. «Геометрия содержимого кольца» выше).
 * Цифры получают `weight`, поэтому строки под ними не могут быть вытеснены за
 * пределы круга даже системным шрифтом в 200%.
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
            Column(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(RING_CONTENT_HEIGHT),
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
                    modifier = Modifier.fillMaxWidth(RING_EDGE_WIDTH),
                )
                // `fill = false`: цифры берут из полосы столько, сколько им нужно
                // по ширине, и не растягивают столбец. Строки под ними при этом
                // остаются внутри круга — вытеснить их цифрам больше нечем.
                TimerDisplay(
                    text = remainingText(snapshot),
                    color = snapshot.accent(palette),
                    modifier = Modifier.fillMaxWidth(RING_CENTER_WIDTH).weight(1f, fill = false),
                )
                // Ручная поправка — плашкой под цифрами, а не строкой в общем
                // столбце: «+0:30» относится к текущему этапу, читается одним
                // взглядом и не спорит по ширине с остатком занятия.
                StageAdjustment(snapshot = snapshot, palette = palette)
                // Две строки, а не одна: «Этап 2/6» и остаток занятия читаются
                // с трёх метров по отдельности и не ужимаются под ширину кольца.
                Text(
                    text = stagePositionText(snapshot),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.onBackgroundMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(RING_EDGE_WIDTH),
                )
                Text(
                    text = totalRemainingText(snapshot),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.onBackgroundMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(RING_EDGE_WIDTH),
                )
            }
        }
    }
}

/** «+0:30» — накопленная правка ±30 с текущего этапа. */
@Composable
private fun StageAdjustment(
    snapshot: SessionSnapshot?,
    palette: TimerPalette,
) {
    val adjustment = snapshot?.stageAdjustmentMs?.takeIf { it != 0L } ?: return
    val accent = snapshot.accent(palette)
    val description = stageAdjustmentText(adjustment)

    Text(
        text = signedClock(adjustment),
        style = MaterialTheme.typography.titleMedium,
        color = accent,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier =
            Modifier
                .padding(vertical = Spacing.xxs)
                .clip(RoundedCornerShape(ADJUSTMENT_CORNER))
                .background(accent.copy(alpha = ADJUSTMENT_BACKGROUND_ALPHA))
                .padding(horizontal = Spacing.s, vertical = Spacing.xxs)
                // Вслух — целиком: «+0:30» само по себе не сообщает, к чему оно.
                .semantics { contentDescription = description },
    )
}

/**
 * «Далее: Балансы · 12:00» и заметка инструктору.
 *
 * Высота полосы постоянна и не зависит от того, есть ли у этапа заметка:
 * иначе кольцо над ней меняло бы диаметр на каждом переходе (см. [FOOTER_HEIGHT]).
 */
@Composable
private fun StageFooter(
    snapshot: SessionSnapshot?,
    palette: TimerPalette,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().height(FOOTER_HEIGHT),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (snapshot == null) return@Column
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
    settingsAvailable: Boolean,
    onOpenSettings: () -> Unit,
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
        // Настройки прямо с занятия (полевая проверка 2026-07-31, замечание 6):
        // громкость и скорость речи правят тогда, когда сигнал уже прозвучал
        // не так, — то есть посреди занятия. Отсчёт от ухода не страдает:
        // его ведёт сервис, а не этот экран. Кнопку можно убрать настройкой
        // «Настройки во время занятия».
        if (settingsAvailable) {
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(Dimens.minTouchTarget)) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.timer_settings),
                    tint = palette.onBackgroundMuted,
                )
            }
        }
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

    // Цифры идут во всю ширину экрана, текст — с полем: в фокусе кроме цифр
    // смотреть не на что, и каждый отданный им dp виден с другого конца зала.
    val sides = Modifier.padding(horizontal = Spacing.m)

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
            modifier = sides,
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
                modifier = sides,
            )
        }
        AnimatedVisibility(visible = hintVisible) {
            Text(
                text = stringResource(R.string.timer_focus_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundMuted,
                textAlign = TextAlign.Center,
                modifier = sides.padding(top = Spacing.m),
            )
        }
    }
}
