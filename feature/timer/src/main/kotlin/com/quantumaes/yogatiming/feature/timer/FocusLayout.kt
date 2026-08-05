package com.quantumaes.yogatiming.feature.timer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.quantumaes.yogatiming.core.common.time.TimeFormatter
import com.quantumaes.yogatiming.core.designsystem.theme.Dimens
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.TimerPalette
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTextStyles
import com.quantumaes.yogatiming.feature.timer.component.FocusPeek
import com.quantumaes.yogatiming.feature.timer.component.TimerDisplay
import com.quantumaes.yogatiming.timer.engine.model.PauseMode
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionSnapshot
import kotlinx.coroutines.delay

// Режим фокуса целиком: сам экран, два его значка, страница-подсказка свайпа и
// строка паузы. Отделён от `SessionLayouts.kt` не по размеру файла, а по сути —
// это другой экран с другим набором правил: без кнопок, с жестами и с текстом,
// рассчитанным на чтение с другого конца зала.

/** Сколько висит подсказка о свайпах при входе в фокус. */
private const val FOCUS_HINT_MS = 3_000L

/** Мигание строки паузы в фокусе: период полуволны и нижняя прозрачность. */
private const val PAUSE_BLINK_MS = 900
private const val PAUSE_BLINK_MIN_ALPHA = 0.35f

/**
 * Режим фокуса (docs/03-GESTURES.md §5): цифры во весь экран.
 *
 * Управления по-прежнему нет — свайп здесь однозначен ровно потому, что
 * спорить ему не с чем, — но два значка в углу появились (замечание 5 полевой
 * проверки 2026-08-05). Оба ведут туда, куда из фокуса приходилось выходить и
 * возвращаться обратно: замок запирает экран перед тем, как положить телефон
 * на коврик, — а это и есть та самая минута, ради которой фокус включали, —
 * настройки правят громкость сигнала, который только что прозвучал не так.
 * Промахнуться мимо них жестом нельзя: обе цели по 48 dp в углу, а нажатие по
 * значку не доходит до области жестов — она видит его уже разобранным.
 */
@Composable
internal fun FocusContent(
    snapshot: SessionSnapshot?,
    palette: TimerPalette,
    settingsAvailable: Boolean,
    onOpenSettings: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hintVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(FOCUS_HINT_MS)
        hintVisible = false
    }
    val paused = snapshot?.takeIf { it.runState == RunState.PAUSED }

    // Цифры идут во всю ширину экрана, текст — с полем: в фокусе кроме цифр
    // смотреть не на что, и каждый отданный им dp виден с другого конца зала.
    val sides = Modifier.padding(horizontal = Spacing.m)

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FocusActions(
            palette = palette,
            settingsAvailable = settingsAvailable,
            onOpenSettings = onOpenSettings,
            onLock = onLock,
            modifier = sides,
        )
        FocusBody(
            snapshot = snapshot,
            paused = paused,
            palette = palette,
            hintVisible = hintVisible,
            sides = sides,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

/**
 * Замок и настройки в углу фокуса.
 *
 * Значки приглушены до цвета второстепенного текста: они нужны раз за занятие,
 * а смотрят в фокусе на цифры.
 */
@Composable
private fun FocusActions(
    palette: TimerPalette,
    settingsAvailable: Boolean,
    onOpenSettings: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
    }
}

/** Собственно фокус: название этапа, цифры, «что дальше» и заметка. */
@Composable
private fun FocusBody(
    snapshot: SessionSnapshot?,
    paused: SessionSnapshot?,
    palette: TimerPalette,
    hintVisible: Boolean,
    sides: Modifier,
    modifier: Modifier = Modifier,
) {
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
            maxLines = STAGE_TITLE_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = sides,
        )
        // Пауза в фокусе видна словом и растущими часами (замечание 3 полевой
        // проверки 2026-08-05). Цвета цифр для этого мало: он меняется и в
        // последнюю минуту этапа, а замершие цифры сами по себе неотличимы от
        // подвисшего приложения. Плашки рабочего экрана здесь нет — в фокусе
        // нет ничего, кроме этой строки, поэтому она и мигает.
        paused?.let { FocusPauseNotice(snapshot = it, palette = palette, modifier = sides) }
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
        // Заметка инструктора есть и в фокусе (замечание 3 полевой проверки
        // 2026-08-04): именно в фокусе на телефон смотрят издалека, и именно
        // там «Вирабхадрасана I–II, триконасана» нужнее всего. Раньше её тут
        // не было вовсе — режим считался «только цифры», но цифры сами по себе
        // не напоминают, что показывать.
        snapshot?.currentNote?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                text = note,
                style = YtaTextStyles.stageNote,
                color = palette.onBackgroundMuted,
                textAlign = TextAlign.Center,
                maxLines = STAGE_NOTE_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = sides.padding(top = Spacing.s),
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

/**
 * Страница, которая приедет на место текущей, — то, что видно за уезжающим
 * содержимым во время свайпа (замечание 3 полевой проверки 2026-08-05).
 *
 * Пишется тем же почерком, что и сам фокус: подпись направления, крупное имя
 * того, что приедет, и уточнение под ним. Читать её приходится за полсекунды,
 * не отпуская пальца, поэтому строк ровно три и все они об одном.
 *
 * Края плана названы прямо, а не умолчанием. Свайп влево на последнем этапе
 * **заканчивает занятие** — это не «следующий этап», и узнать об этом после
 * отпускания поздно. Свайп вправо на первом не делает ничего, и страница об
 * этом говорит: жест, который ничем не ответит, иначе выглядит как незамеченный.
 */
@Composable
internal fun FocusPeekPage(
    target: FocusPeek,
    snapshot: SessionSnapshot?,
    palette: TimerPalette,
    modifier: Modifier = Modifier,
) {
    val page = peekPage(target, snapshot, palette)

    Column(
        modifier = modifier.padding(horizontal = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = page.caption,
            style = YtaTextStyles.stageNext,
            color = palette.onBackgroundMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = page.title,
            style = YtaTextStyles.stageTitle,
            color = page.titleColor,
            textAlign = TextAlign.Center,
            maxLines = STAGE_TITLE_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.s),
        )
        page.detail?.let { detail ->
            Text(
                text = detail,
                style = YtaTextStyles.stageNext,
                color = palette.onBackgroundMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

/**
 * Три строки страницы-подсказки.
 *
 * Цвет заголовка — часть сообщения, а не оформление: конец занятия набран
 * тревожным, потому что он необратим, а «дальше некуда» — приглушённым, потому
 * что от этого жеста не случится ничего.
 */
private data class PeekPage(
    val caption: String,
    val title: String,
    val detail: String?,
    val titleColor: Color,
)

@Composable
private fun peekPage(
    target: FocusPeek,
    snapshot: SessionSnapshot?,
    palette: TimerPalette,
): PeekPage =
    when (target) {
        FocusPeek.NEXT -> {
            val next = snapshot?.nextStageName
            PeekPage(
                caption = stringResource(R.string.timer_peek_next),
                title = next ?: stringResource(R.string.timer_peek_finish),
                detail = snapshot?.nextStageDurationMs?.let { TimeFormatter.clock(it) },
                titleColor = if (next == null) palette.danger else palette.onBackground,
            )
        }

        FocusPeek.PREVIOUS -> {
            val previous = snapshot?.previousStageName
            PeekPage(
                caption = stringResource(R.string.timer_peek_previous),
                title = previous ?: stringResource(R.string.timer_peek_first),
                detail = null,
                titleColor = if (previous == null) palette.onBackgroundMuted else palette.onBackground,
            )
        }

        FocusPeek.EXIT ->
            PeekPage(
                caption = stringResource(R.string.timer_peek_exit),
                title = stringResource(R.string.timer_peek_normal),
                detail = stringResource(R.string.timer_peek_normal_detail),
                titleColor = palette.onBackground,
            )
    }

/**
 * «Пауза 1:20» в режиме фокуса — единственное, что там движется на паузе.
 *
 * Мигает, а не просто окрашено: на паузу смотрят с другого конца зала, а
 * различить оттенок цифр с трёх метров нельзя — движение различимо всегда.
 * Период неспешный: тревожная мигалка на йоге неуместна, задача — быть
 * замеченной, а не подгонять.
 *
 * У паузы этапа своя строка: там продолжают идти часы занятия, и молчать об
 * этом нельзя — именно из этой разницы состоит смысл двух режимов.
 */
@Composable
private fun FocusPauseNotice(
    snapshot: SessionSnapshot,
    palette: TimerPalette,
    modifier: Modifier = Modifier,
) {
    val blink = rememberInfiniteTransition(label = "focus-pause")
    val alpha by blink.animateFloat(
        initialValue = PAUSE_BLINK_MIN_ALPHA,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(PAUSE_BLINK_MS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "focus-pause-alpha",
    )
    val clock = TimeFormatter.clock(snapshot.pauseElapsedMs)
    val text =
        if (snapshot.pauseMode == PauseMode.STAGE) {
            stringResource(R.string.timer_focus_paused_stage, clock)
        } else {
            stringResource(R.string.timer_focus_paused, clock)
        }

    Text(
        text = text,
        style = YtaTextStyles.stageNext,
        color = palette.paused.copy(alpha = alpha),
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(top = Spacing.xs),
    )
}
