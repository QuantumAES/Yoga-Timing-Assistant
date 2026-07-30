package com.quantumaes.yogatiming.feature.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTextStyles
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTheme
import com.quantumaes.yogatiming.core.designsystem.theme.timerPalette

private val ACTION_HEIGHT = 64.dp
private val ACTION_MAX_WIDTH = 360.dp

/**
 * Экран после завершения занятия (ТЗ, Экран 4 → Finished).
 *
 * Отдельный экран, а не состояние рабочего: на нём нет ни кольца, ни цифр,
 * ни жестов, а «Назад» с него ведёт к списку профилей, а не в уже завершённую
 * сессию (см. `YtaNavHost`).
 *
 * Палитра та же, что у рабочего экрана: занятие только что закончилось,
 * телефон всё ещё лежит на коврике, и белая вспышка после тёмного таймера
 * бьёт по глазам.
 */
@Composable
internal fun SessionFinishedScreen(
    onRepeat: () -> Unit,
    onExit: () -> Unit,
) {
    val palette = timerPalette

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(palette.background)
                .safeDrawingPadding()
                .padding(Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.timer_finished_title),
            style = YtaTextStyles.stageTitle,
            color = palette.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.timer_finished_hint),
            color = palette.onBackgroundMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.xl),
        )

        Button(
            onClick = onRepeat,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = ACTION_MAX_WIDTH)
                    .height(ACTION_HEIGHT),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = palette.running,
                    contentColor = palette.background,
                ),
        ) {
            Text(stringResource(R.string.timer_finished_repeat))
        }
        OutlinedButton(
            onClick = onExit,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = ACTION_MAX_WIDTH)
                    .height(ACTION_HEIGHT)
                    .padding(top = Spacing.s),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.onBackground),
        ) {
            Text(stringResource(R.string.timer_finished_exit))
        }
    }
}

@Preview
@Composable
private fun SessionFinishedPreview() {
    YtaTheme(darkTheme = true) {
        SessionFinishedScreen(onRepeat = {}, onExit = {})
    }
}
