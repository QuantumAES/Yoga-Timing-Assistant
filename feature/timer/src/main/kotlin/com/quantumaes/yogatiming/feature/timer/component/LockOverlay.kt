package com.quantumaes.yogatiming.feature.timer.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.TimerPalette
import com.quantumaes.yogatiming.feature.timer.R
import kotlinx.coroutines.delay

/** Сколько держать до разблокировки (docs/03-GESTURES.md §4). */
private const val UNLOCK_HOLD_MS = 1_000

/** Сколько висит подсказка после случайного касания. */
private const val HINT_VISIBLE_MS = 2_000L

private val INDICATOR_SIZE = 96.dp
private val INDICATOR_STROKE = 8.dp
private val LOCK_BORDER = 3.dp

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
 * Что экран заблокирован, видно с расстояния по рамке акцентного цвета вокруг
 * всего экрана — не только по значку в верхнем баре.
 */
@Composable
fun LockOverlay(
    palette: TimerPalette,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var holding by remember { mutableStateOf(false) }
    var hintShown by remember { mutableStateOf(false) }
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

    LaunchedEffect(hintShown) {
        if (!hintShown) return@LaunchedEffect
        delay(HINT_VISIBLE_MS)
        hintShown = false
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
                            hintShown = true
                            holding = true
                            tryAwaitRelease()
                            holding = false
                        },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (progress.floatValue > 0f) {
                CircularProgressIndicator(
                    progress = { progress.floatValue },
                    modifier = Modifier.size(INDICATOR_SIZE),
                    color = palette.running,
                    trackColor = palette.ringTrack,
                    strokeWidth = INDICATOR_STROKE,
                )
            }
            AnimatedVisibility(visible = hintShown) {
                Text(
                    text = hint,
                    color = palette.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.xl),
                )
            }
        }
    }
}
