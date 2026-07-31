package com.quantumaes.yogatiming.feature.timer.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.TimerPalette
import com.quantumaes.yogatiming.feature.timer.R

/** Сколько держать до разблокировки (docs/03-GESTURES.md §4). */
private const val UNLOCK_HOLD_MS = 1_000

private val LOCK_BORDER = 3.dp

/** Высота плашки разблокировки: та же цель для пальца, что и у кнопок экрана. */
private val PILL_HEIGHT = 64.dp
private val PILL_MAX_WIDTH = 420.dp
private val PILL_CORNER = 32.dp
private val ICON_SIZE = 24.dp

/** Насколько плашка перекрывает то, что под ней. Не до конца: экран продолжает жить. */
private const val PILL_BACKGROUND_ALPHA = 0.92f

/** Заливка-индикатор удержания: акцент под текстом, а не поверх него. */
private const val PILL_FILL_ALPHA = 0.35f

/**
 * Режим блокировки (docs/03-GESTURES.md §4).
 *
 * Слой поверх всего экрана, который съедает касания: телефон лежит на коврике
 * между занимающимися, и любое касание одеждой не должно ни ставить занятие на
 * паузу, ни промотать этап.
 *
 * Разблокировка — удержание секунду, а не двойной тап из ТЗ §6.3: двойной тап
 * в этих условиях повторяется случайно, удержание — нет. Отпускание раньше
 * срока откатывает индикатор, то есть жест видно и его можно передумать.
 *
 * Подсказка живёт в плашке внизу экрана и видна всегда (полевая проверка
 * 2026-07-31, замечание 10). Раньше она возникала по касанию в центре экрана —
 * поверх названия этапа и цифр — и то появлялась, то исчезала: текст прыгал
 * и сливался с тем, что под ним. Теперь у неё постоянное место, непрозрачная
 * подложка и собственный индикатор удержания, а цифры таймера остаются видны.
 */
@Composable
fun LockOverlay(
    palette: TimerPalette,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var holding by remember { mutableStateOf(false) }
    val progress = remember { mutableFloatStateOf(0f) }
    val hint = stringResource(R.string.timer_unlock_hint)

    // Прогресс живёт снаружи жеста: отпускание должно откатить его назад, а не
    // оборвать вместе с корутиной жеста.
    LaunchedEffect(holding) {
        if (holding) {
            animate(
                initialValue = progress.floatValue,
                targetValue = 1f,
                animationSpec = tween(UNLOCK_HOLD_MS, easing = LinearEasing),
            ) { value, _ -> progress.floatValue = value }
            onUnlock()
        } else {
            animate(
                initialValue = progress.floatValue,
                targetValue = 0f,
                animationSpec = tween(UNLOCK_HOLD_MS / 2, easing = LinearEasing),
            ) { value, _ -> progress.floatValue = value }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .border(LOCK_BORDER, palette.running)
                .semantics { contentDescription = hint }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            holding = true
                            tryAwaitRelease()
                            holding = false
                        },
                    )
                },
        contentAlignment = Alignment.BottomCenter,
    ) {
        UnlockPill(
            hint = hint,
            progress = progress.floatValue,
            palette = palette,
            modifier =
                Modifier
                    .safeDrawingPadding()
                    .padding(horizontal = Spacing.l, vertical = Spacing.xl),
        )
    }
}

/**
 * Плашка «Удерживайте, чтобы разблокировать».
 *
 * Индикатор — заливка самой плашки слева направо, а не отдельное кольцо:
 * жест идёт пальцем по экрану, и подтверждение должно быть там же, где текст,
 * а не в другом его конце.
 */
@Composable
private fun UnlockPill(
    hint: String,
    progress: Float,
    palette: TimerPalette,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(PILL_CORNER)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .widthIn(max = PILL_MAX_WIDTH)
                .heightIn(min = PILL_HEIGHT)
                .clip(shape)
                .background(palette.background.copy(alpha = PILL_BACKGROUND_ALPHA))
                .border(width = 1.dp, color = palette.running, shape = shape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(palette.running.copy(alpha = PILL_FILL_ALPHA)),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = palette.running,
                modifier = Modifier.size(ICON_SIZE),
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.titleMedium,
                color = palette.onBackground,
                // Две строки, а не одна: при системном шрифте 200% подсказка
                // в одну строку обрывалась многоточием ровно на слове «чтобы».
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
