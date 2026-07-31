package com.quantumaes.yogatiming.feature.timer.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import kotlinx.coroutines.delay

/** Сколько держать до разблокировки (docs/03-GESTURES.md §4). */
private const val UNLOCK_HOLD_MS = 1_000

/** Сколько подсказка висит после того, как палец убрали. */
private const val HINT_LINGER_MS = 2_500L

/** Как плавно проявляется и гаснет подсказка. */
private const val HINT_FADE_MS = 250

/** Насколько блокировка притемняет экран. Цифры остаются читаемыми. */
private const val LOCK_SCRIM_ALPHA = 0.28f

/** Пастельная вуаль поверх притемнения: цвет говорит «заблокировано» без слов. */
private const val LOCK_TINT_ALPHA = 0.10f

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
 * Что экран заблокирован, видно по нему самому: лёгкое притемнение и пастельная
 * вуаль поверх (полевая проверка 2026-07-31, замечание 4). Это состояние, а не
 * сообщение, — его показывают тоном, а не текстом, который придётся читать
 * каждый раз, когда взгляд упал на таймер. Цифры сквозь вуаль читаются:
 * притемнение мягкое, а вуаль почти прозрачна.
 *
 * Подсказка «Удерживайте, чтобы разблокировать» появляется по касанию и гаснет
 * через пару секунд после того, как палец убрали: она нужна ровно тому, кто уже
 * тянется к экрану. Разблокировка — удержание секунду, а не двойной тап из
 * ТЗ §6.3: двойной тап в этих условиях повторяется случайно, удержание — нет.
 * Отпускание раньше срока откатывает индикатор, то есть жест видно и его можно
 * передумать.
 */
@Composable
fun LockOverlay(
    palette: TimerPalette,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var holding by remember { mutableStateOf(false) }
    var hintVisible by remember { mutableStateOf(false) }
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

    // Подсказка гаснет сама, но только когда палец уже убран: держать пальцем
    // и смотреть, как исчезает объяснение происходящего, незачем.
    LaunchedEffect(holding, hintVisible) {
        if (holding || !hintVisible) return@LaunchedEffect
        delay(HINT_LINGER_MS)
        hintVisible = false
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.lockScrim.copy(alpha = LOCK_SCRIM_ALPHA))
                .semantics { contentDescription = hint }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            hintVisible = true
                            holding = true
                            tryAwaitRelease()
                            holding = false
                        },
                    )
                },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Вуаль отдельным слоем поверх притемнения: смешивать цвет с чёрным в
        // одну краску значит подбирать её заново для каждой темы.
        Box(Modifier.fillMaxSize().background(palette.lockTint.copy(alpha = LOCK_TINT_ALPHA)))

        AnimatedVisibility(
            visible = hintVisible,
            enter = fadeIn(tween(HINT_FADE_MS)),
            exit = fadeOut(tween(HINT_FADE_MS)),
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
                .background(palette.background.copy(alpha = PILL_BACKGROUND_ALPHA)),
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
