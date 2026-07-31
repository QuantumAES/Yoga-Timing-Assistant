package com.quantumaes.yogatiming.feature.timer.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.TimerPalette
import com.quantumaes.yogatiming.feature.timer.R

/** Ранг 1 карты жестов: «Пауза» должна попадаться вслепую (docs/03-GESTURES.md §2). */
private val PRIMARY_HEIGHT = 88.dp

/** Ранг 2–3: «След.» и «±30 с». */
private val SECONDARY_HEIGHT = 64.dp

/** Ранг 4: «Пред. этап». Ниже остальных ровно настолько, чтобы это было видно. */
private val TERTIARY_HEIGHT = 52.dp

private val PRIMARY_TEXT_SIZE = 22.sp

/** Одно скругление на все кнопки блока: разнобой углов читается как небрежность. */
private val CONTROL_CORNER = 20.dp

/** Насколько заливка «След.» темнее «Паузы»: тот же цвет, другой вес. */
private const val SECONDARY_FILL_ALPHA = 0.16f

/**
 * Кнопки рабочего экрана в порядке частоты использования, а не макета
 * (docs/03-GESTURES.md §2).
 *
 * ```
 * ┌──────────┬──────────┐   ранг 3: правка текущего этапа
 * │  −30 с   │  +30 с   │
 * ├──────────┴───┬──────┤   ранг 1 и 2: чем чаще, тем крупнее
 * │    Пауза     │ След.│
 * ├──────────────┴──────┤   ранг 4: возврат при ошибке
 * │   ‹ Пред. этап      │
 * └─────────────────────┘
 * ```
 *
 * Все четыре — кнопки одной семьи: общее скругление, общая ширина, разный вес
 * заливки. Раньше «Пред. этап» был текстовой ссылкой и выпадал из блока
 * (полевая проверка 2026-07-31, замечание 5) — при том, что ранг у него ниже,
 * а не «другой»: разницу в важности показывают вес и высота, а не
 * принадлежность к другому виду элементов.
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
    val shape = RoundedCornerShape(CONTROL_CORNER)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            OutlinedControl(
                label = stringResource(R.string.timer_subtract_30),
                palette = palette,
                shape = shape,
                onClick = onSubtractTime,
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = SECONDARY_HEIGHT),
            )
            OutlinedControl(
                label = stringResource(R.string.timer_add_30),
                palette = palette,
                shape = shape,
                onClick = onAddTime,
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = SECONDARY_HEIGHT),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Button(
                onClick = onTogglePause,
                shape = shape,
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
            // «След.» — заливка того же цвета, что «Пауза», но прозрачная:
            // соседство читается как «то же семейство, рангом ниже», а не как
            // вторая по важности кнопка того же веса.
            Button(
                onClick = onNext,
                shape = shape,
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = PRIMARY_HEIGHT),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = palette.running.copy(alpha = SECONDARY_FILL_ALPHA),
                        contentColor = palette.onBackground,
                    ),
            ) {
                Text(text = stringResource(R.string.timer_next))
            }
        }

        // Ранг 4: к предыдущему этапу возвращаются при ошибке, а не по ходу
        // занятия. Кнопка во всю ширину, но самая низкая и без заливки —
        // спорить с «Паузой» ей нечем.
        OutlinedControl(
            label = stringResource(R.string.timer_previous_stage),
            palette = palette,
            shape = shape,
            onClick = onPrevious,
            contentColor = palette.onBackgroundMuted,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = TERTIARY_HEIGHT),
        )
    }
}

@Composable
private fun OutlinedControl(
    label: String,
    palette: TimerPalette,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = palette.onBackground,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(text = label, modifier = Modifier.padding(vertical = Spacing.xs))
    }
}
