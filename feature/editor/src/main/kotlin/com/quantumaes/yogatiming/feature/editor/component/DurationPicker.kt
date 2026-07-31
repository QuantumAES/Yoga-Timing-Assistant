package com.quantumaes.yogatiming.feature.editor.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quantumaes.yogatiming.core.common.time.TimeFormatter
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.domain.model.Stage
import com.quantumaes.yogatiming.feature.editor.R

private const val SECONDS_IN_MINUTE = 60
private val FIELD_WIDTH = 104.dp

/** Готовые длительности в минутах: то, из чего реально собирают занятие. */
private val PRESET_MINUTES = listOf(1, 3, 5, 8, 10, 15, 20, 30, 45, 60)

private const val MS_IN_SECOND = 1_000L

/**
 * Упрощённый пикер длительности (решение P1 §5.4).
 *
 * Колёсный пикер часы-минуты-секунды заменён двумя полями и рядом готовых
 * значений: длительности этапов в йоге — круглые числа минут, и выкручивать
 * барабан ради «10 минут» пользователь не должен. Секунды остаются для
 * коротких переходов.
 *
 * @param durationSec 0 означает «не задано»; ограничения 5 с … 4 ч
 *   (решение B-3) применяются при сохранении, а не при вводе — иначе нельзя
 *   стереть поле, чтобы напечатать заново.
 */
@Composable
fun DurationPicker(
    durationSec: Int,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val minutes = durationSec / SECONDS_IN_MINUTE
    val seconds = durationSec % SECONDS_IN_MINUTE

    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberField(
                value = minutes,
                label = stringResource(R.string.editor_duration_minutes),
                onValueChange = { onDurationChange(it * SECONDS_IN_MINUTE + seconds) },
                max = Stage.MAX_DURATION_SEC / SECONDS_IN_MINUTE,
            )
            NumberField(
                value = seconds,
                label = stringResource(R.string.editor_duration_seconds),
                onValueChange = { onDurationChange(minutes * SECONDS_IN_MINUTE + it) },
                max = SECONDS_IN_MINUTE - 1,
            )
            Text(
                text = TimeFormatter.clock(durationSec * MS_IN_SECOND),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        // Готовых значений десять, и в ширину экрана они не помещаются:
        // ряд прокручивается, и по растворяющемуся краю это видно.
        ScrollableRow(Modifier.padding(vertical = Spacing.s)) {
            PRESET_MINUTES.forEach { preset ->
                AssistChip(
                    onClick = { onDurationChange(preset * SECONDS_IN_MINUTE) },
                    label = { Text(stringResource(R.string.editor_duration_preset, preset)) },
                )
            }
        }
    }
}

/**
 * Числовое поле без «умного» поведения: пустая строка читается как ноль,
 * нецифровые символы отбрасываются, значение выше потолка не принимается.
 * Всё остальное — забота валидации при сохранении.
 */
@Composable
private fun NumberField(
    value: Int,
    label: String,
    onValueChange: (Int) -> Unit,
    max: Int,
) {
    OutlinedTextField(
        value = if (value == 0) "" else value.toString(),
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(MAX_DIGITS)
            val parsed = digits.toIntOrNull() ?: 0
            if (parsed <= max) onValueChange(parsed)
        },
        modifier = Modifier.width(FIELD_WIDTH),
        label = { Text(label) },
        singleLine = true,
        placeholder = { Text("0") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

private const val MAX_DIGITS = 3
