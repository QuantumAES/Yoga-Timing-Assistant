package com.quantumaes.yogatiming.feature.timer.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.TimerPalette
import com.quantumaes.yogatiming.feature.timer.R

/** Ранг 1 карты жестов: «Пауза» должна попадаться вслепую (docs/03-GESTURES.md §2). */
private val PRIMARY_HEIGHT = 88.dp

/** Ранг 2–4: «След.», «±30 с», «Пред.». */
private val SECONDARY_HEIGHT = 64.dp

private val PRIMARY_TEXT_SIZE = 22.sp

/**
 * Кнопки рабочего экрана в порядке частоты использования, а не макета
 * (docs/03-GESTURES.md §2).
 *
 * Подписи словами, а не значками: слово «Пауза» читается с трёх метров
 * однозначно, а два вертикальных прямоугольника с той же дистанции сливаются
 * с фоном. Заодно TalkBack получает готовое имя без `contentDescription`.
 */
@Composable
fun SessionControls(
    paused: Boolean,
    palette: TimerPalette,
    onTogglePause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onAddTime: () -> Unit,
    onSubtractTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            SecondaryControl(
                label = stringResource(R.string.timer_subtract_30),
                palette = palette,
                onClick = onSubtractTime,
                modifier = Modifier.weight(1f),
            )
            SecondaryControl(
                label = stringResource(R.string.timer_add_30),
                palette = palette,
                onClick = onAddTime,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Button(
                onClick = onTogglePause,
                modifier =
                    Modifier
                        .weight(2f)
                        .defaultMinSize(minHeight = PRIMARY_HEIGHT),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = if (paused) palette.paused else palette.running,
                        contentColor = palette.background,
                    ),
            ) {
                Text(
                    text = stringResource(if (paused) R.string.timer_resume else R.string.timer_pause),
                    fontSize = PRIMARY_TEXT_SIZE,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            SecondaryControl(
                label = stringResource(R.string.timer_next),
                palette = palette,
                onClick = onNext,
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = PRIMARY_HEIGHT),
            )
        }

        // Ранг 4: к предыдущему этапу возвращаются при ошибке, а не по ходу
        // занятия. Текстовая кнопка — чтобы не соперничать с «Паузой».
        TextButton(onClick = onPrevious, modifier = Modifier.defaultMinSize(minHeight = SECONDARY_HEIGHT)) {
            Text(
                text = stringResource(R.string.timer_previous_stage),
                color = palette.onBackgroundMuted,
            )
        }
    }
}

@Composable
private fun SecondaryControl(
    label: String,
    palette: TimerPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = SECONDARY_HEIGHT),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.onBackground),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(text = label, modifier = Modifier.padding(vertical = Spacing.xs))
    }
}
