package com.quantumaes.yogatiming.feature.timer

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumaes.yogatiming.core.common.time.TimeFormatter
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.TimerPalette
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTextStyles
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTheme
import com.quantumaes.yogatiming.core.designsystem.theme.timerPalette
import com.quantumaes.yogatiming.domain.session.SessionOutcome
import com.quantumaes.yogatiming.domain.session.SessionSummary
import java.util.Date

private val ACTION_HEIGHT = 64.dp
private val ACTION_MAX_WIDTH = 360.dp

/** Ширина строки напутствия: длинная строка через весь планшет не читается. */
private val QUOTE_MAX_WIDTH = 420.dp

private val CARD_CORNER = 20.dp

/**
 * Фактическая длительность — второй по величине элемент экрана после заголовка.
 *
 * В sp, а не в dp: в отличие от цифр рабочего экрана, эти читают с руки, и
 * системный размер шрифта им положено уважать.
 */
private val TOTAL_TEXT_SIZE = 56.sp

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
 *
 * Итоги — профиль, факт, план и время — показываются потому, что это первый
 * вопрос после занятия: сколько вышло на самом деле (полевая проверка
 * 2026-07-31, замечание 7). Ниже — напутствие: что отсчёт остановлен, видно
 * и так, а попрощаться с тем, кто провёл полтора часа занятия, есть чем.
 */
@Composable
internal fun SessionFinishedScreen(
    profileId: Long,
    onRepeat: () -> Unit,
    onExit: () -> Unit,
    viewModel: SessionFinishedViewModel = hiltViewModel(),
) {
    val lastSummary by viewModel.summary.collectAsStateWithLifecycle()
    // Итоги чужого занятия — не итоги этого: экран мог открыться по «Назад»
    // после того, как в другом профиле уже началось следующее.
    val summary = lastSummary?.takeIf { it.profileId == profileId }

    SessionFinishedContent(summary = summary, onRepeat = onRepeat, onExit = onExit)
}

@Composable
private fun SessionFinishedContent(
    summary: SessionSummary?,
    onRepeat: () -> Unit,
    onExit: () -> Unit,
) {
    val palette = timerPalette
    val quotes = stringArrayResource(R.array.timer_finished_quotes)
    val authors = stringArrayResource(R.array.timer_finished_quote_authors)

    // `rememberSaveable`, а не `remember`: поворот экрана не должен подменять
    // фразу — читающий её человек решил бы, что промахнулся глазом.
    val quoteIndex = rememberSaveable { if (quotes.isEmpty()) 0 else quotes.indices.random() }

    // Содержимое центрировано, пока помещается, и прокручивается, когда нет:
    // при системном шрифте 200% итоги, напутствие и две кнопки не влезают ни на
    // один телефон, а на обычном шрифте прижатый к верху столбец выглядел бы
    // недоделанным. Отсюда нижняя граница высоты в размер окна: столбец ровно
    // с окно — центрирование работает; больше окна — появляется прокрутка.
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(palette.background)
                .safeDrawingPadding(),
    ) {
        val viewportHeight = maxHeight

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight)
                    .padding(Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(summary.titleRes()),
                style = YtaTextStyles.stageTitle,
                color = palette.onBackground,
                textAlign = TextAlign.Center,
            )

            if (summary != null) {
                SessionTotals(
                    summary = summary,
                    palette = palette,
                    modifier = Modifier.padding(top = Spacing.m),
                )
            }

            Quote(
                text = quotes.getOrElse(quoteIndex) { quotes.firstOrNull().orEmpty() },
                author = authors.getOrNull(quoteIndex).orEmpty(),
                palette = palette,
                modifier = Modifier.padding(top = Spacing.l, bottom = Spacing.xl),
            )

            Button(
                onClick = onRepeat,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = ACTION_MAX_WIDTH)
                        .defaultMinSize(minHeight = ACTION_HEIGHT),
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
                        .defaultMinSize(minHeight = ACTION_HEIGHT)
                        .padding(top = Spacing.s),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.onBackground),
            ) {
                Text(stringResource(R.string.timer_finished_exit))
            }
        }
    }
}

/**
 * Итоги: название профиля, факт крупно и четыре строки подробностей.
 *
 * Факт вынесен из карточки и набран крупно: это ответ на единственный вопрос,
 * который задают сразу после занятия. Остальное — справка, и читается уже
 * тогда, когда за ней потянулись глазами.
 */
@Composable
private fun SessionTotals(
    summary: SessionSummary,
    palette: TimerPalette,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().widthIn(max = ACTION_MAX_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = summary.profileName,
            style = MaterialTheme.typography.titleMedium,
            color = palette.onBackgroundMuted,
            textAlign = TextAlign.Center,
        )
        Text(
            text = TimeFormatter.clock(summary.actualDurationMs),
            style = YtaTextStyles.timerDisplay.copy(fontSize = TOTAL_TEXT_SIZE, lineHeight = TOTAL_TEXT_SIZE),
            color = palette.onBackground,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(
            text = stringResource(R.string.timer_summary_actual),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onBackgroundMuted,
        )

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.m)
                    .clip(RoundedCornerShape(CARD_CORNER))
                    .background(palette.ringTrack)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            TotalsRow(
                label = stringResource(R.string.timer_summary_stages),
                value =
                    stringResource(
                        R.string.timer_summary_stages_value,
                        summary.stagesCompleted,
                        summary.stageCount,
                    ),
                palette = palette,
            )
            TotalsRow(
                label = stringResource(R.string.timer_summary_planned),
                value = TimeFormatter.clock(summary.plannedDurationMs),
                palette = palette,
            )
            // Отклонение показывается только когда оно есть: ноль сообщает
            // ровно то же, что и отсутствие строки, но занимает место.
            summary.deviationMs.takeIf { it != 0L }?.let { deviation ->
                TotalsRow(
                    label = stringResource(R.string.timer_summary_deviation),
                    value = TimeFormatter.signedClock(deviation),
                    palette = palette,
                )
            }
            TotalsRow(
                label = stringResource(R.string.timer_summary_time),
                value =
                    stringResource(
                        R.string.timer_summary_time_value,
                        wallClock(summary.startedAtWallMs),
                        wallClock(summary.finishedAtWallMs),
                    ),
                palette = palette,
            )
        }
    }
}

@Composable
private fun TotalsRow(
    label: String,
    value: String,
    palette: TimerPalette,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = palette.onBackgroundMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = palette.onBackground,
        )
    }
}

@Composable
private fun Quote(
    text: String,
    author: String,
    palette: TimerPalette,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(max = QUOTE_MAX_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            color = palette.onBackgroundMuted,
            textAlign = TextAlign.Center,
        )
        // Автор подписывается только у авторских фраз: у остальных строка
        // пуста, и приписывать их некому (полевая проверка 2026-07-31,
        // замечание 3).
        if (author.isNotBlank()) {
            Text(
                text = stringResource(R.string.timer_finished_quote_author, author),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

/**
 * Заголовок по-честному: остановленное занятие завершённым не называется.
 *
 * Без итогов — общий заголовок: приложение перезапустили, и чем именно
 * кончилось занятие, уже не известно.
 */
private fun SessionSummary?.titleRes(): Int =
    when (this?.outcome) {
        SessionOutcome.STOPPED -> R.string.timer_stopped_title
        else -> R.string.timer_finished_title
    }

/** Время в формате устройства: 12- или 24-часовой выбирает система, а не мы. */
@Composable
private fun wallClock(millis: Long): String {
    val context: Context = LocalContext.current
    val format = remember(context) { DateFormat.getTimeFormat(context) }
    return remember(format, millis) { format.format(Date(millis)) }
}

@Preview
@Composable
private fun SessionFinishedPreview() {
    YtaTheme(darkTheme = true) {
        SessionFinishedContent(
            summary =
                SessionSummary(
                    profileId = 1,
                    profileName = "Хатха 60 мин",
                    outcome = SessionOutcome.COMPLETED,
                    startedAtWallMs = 1_800_000_000_000,
                    finishedAtWallMs = 1_800_003_522_000,
                    plannedDurationMs = 3_600_000,
                    actualDurationMs = 3_522_000,
                    stagesCompleted = 6,
                    stageCount = 6,
                ),
            onRepeat = {},
            onExit = {},
        )
    }
}
