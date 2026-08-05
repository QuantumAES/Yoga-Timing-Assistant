package com.quantumaes.yogatiming.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.style.TextOverflow
import com.quantumaes.yogatiming.core.designsystem.theme.Dimens
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.domain.session.SessionOutcome
import com.quantumaes.yogatiming.domain.stats.SessionLogEntry

/**
 * Строка журнала: «3 ноя · Хатха 60 · 18:05 → 19:03 · 58 мин» (фаза S5).
 *
 * Каждая строка — отдельный элемент `LazyColumn`, а не кусок одной длинной
 * карточки: журнал за «всё время» это тысячи строк, и складывать их в один
 * `Column` значит собирать всю тысячу на каждый кадр (R-S4).
 *
 * Свайпом строка не удаляется сразу, а спрашивает: занятие в журнале — это
 * запись о прошлом, и случайный жест на телефоне, лежащем в сумке, не имеет
 * права стирать историю. Карточка при этом возвращается на место немедленно —
 * из списка её уберёт поток данных, когда удаление дойдёт до базы.
 *
 * Для TalkBack строка — один узел с полной фразой и действием «Удалить строку»
 * в меню действий (фаза S6, проверка A-1): свайп по экрану там перехвачен
 * самим TalkBack, и удаление, доступное только жестом, для незрячего
 * пользователя не существует вовсе.
 */
@Composable
internal fun JournalRow(
    entry: SessionLogEntry,
    onRequestDelete: () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    val description = entryDescription(entry)
    val deleteLabel = stringResource(R.string.stats_journal_delete)

    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.Settled) return@LaunchedEffect
        onRequestDelete()
        state.reset()
    }

    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(Dimens.cardCornerRadius),
                        ).padding(horizontal = Spacing.l),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.stats_journal_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Card(
            Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = description
                customActions =
                    listOf(
                        CustomAccessibilityAction(deleteLabel) {
                            onRequestDelete()
                            true
                        },
                    )
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text =
                            stringResource(
                                R.string.stats_journal_row_title,
                                dayShort(entry.localDate),
                                entry.profileName,
                            ),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text(
                            text =
                                stringResource(
                                    R.string.stats_day_time_range,
                                    wallClock(entry.startedAtMs),
                                    wallClock(entry.finishedAtMs),
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (entry.outcome == SessionOutcome.STOPPED) {
                            Text(
                                text = stringResource(R.string.stats_day_stopped),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Text(
                    text = durationText(entry.durationMs),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/**
 * Подтверждение удаления строки.
 *
 * Занятие названо в вопросе целиком — днём и профилем: список под диалогом
 * не виден, и «удалить занятие?» без уточнения предлагает согласиться вслепую.
 */
@Composable
internal fun JournalDeleteDialog(
    entry: SessionLogEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stats_journal_delete_title)) },
        text = {
            Text(
                stringResource(
                    R.string.stats_journal_delete_message,
                    dayTitle(entry.localDate),
                    entry.profileName,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.stats_journal_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.stats_journal_delete_cancel)) }
        },
    )
}
