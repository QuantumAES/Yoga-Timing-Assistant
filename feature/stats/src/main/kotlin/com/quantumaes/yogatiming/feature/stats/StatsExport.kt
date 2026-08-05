package com.quantumaes.yogatiming.feature.stats

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.quantumaes.yogatiming.domain.stats.SessionCsvLabels
import kotlinx.coroutines.flow.Flow

/**
 * Подписи колонок выгрузки (фаза S7).
 *
 * Заголовки в файле — на языке интерфейса: отчёт открывает тот же человек,
 * который смотрел статистику. Домен их не сочиняет, поэтому собираются они
 * здесь и уезжают в `SessionCsv.render` одним значением.
 */
@Composable
internal fun csvLabels(): SessionCsvLabels =
    SessionCsvLabels(
        date = stringResource(R.string.stats_csv_date),
        start = stringResource(R.string.stats_csv_start),
        finish = stringResource(R.string.stats_csv_finish),
        duration = stringResource(R.string.stats_csv_duration),
        planned = stringResource(R.string.stats_csv_planned),
        profile = stringResource(R.string.stats_csv_profile),
        stages = stringResource(R.string.stats_csv_stages),
        outcome = stringResource(R.string.stats_csv_outcome),
        completed = stringResource(R.string.stats_csv_completed),
        stopped = stringResource(R.string.stats_csv_stopped),
    )

/**
 * Ответ на экспорт — снекбаром, а не диалогом: сохранение прошло, и вопросов к
 * пользователю нет.
 *
 * Собран отдельным composable по той же причине, что и снекбары списка
 * профилей: `showSnackbar` приостанавливается до закрытия снекбара, и внутри
 * тела экрана он висел бы поперёк перекомпозиций.
 */
@Composable
internal fun StatsSnackbars(
    events: Flow<StatsEvent>,
    snackbarHostState: SnackbarHostState,
) {
    val exported = stringResource(R.string.stats_export_done)
    val failed = stringResource(R.string.stats_export_failed)

    LaunchedEffect(events, snackbarHostState) {
        events.collect { event ->
            val message =
                when (event) {
                    StatsEvent.Exported -> exported
                    StatsEvent.ExportFailed -> failed
                }
            snackbarHostState.showSnackbar(message = message, withDismissAction = true)
        }
    }
}
