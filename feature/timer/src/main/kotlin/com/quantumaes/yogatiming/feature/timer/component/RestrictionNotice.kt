package com.quantumaes.yogatiming.feature.timer.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.TimerPalette
import com.quantumaes.yogatiming.feature.timer.R
import com.quantumaes.yogatiming.timer.service.restrictions.RestrictionSeverity
import com.quantumaes.yogatiming.timer.service.restrictions.TimerRestriction

/**
 * Сообщение об ограничении системы (детект Фазы 3, баннеры Фазы 6).
 *
 * Красным — только то, из-за чего занятие пройдёт молча или без управления.
 * Совет про энергосбережение красным не бывает: он описывает риск, а не
 * поломку, закрывается навсегда и не имеет права мозолить глаза каждый раз
 * (docs/05-PLAY-DECLARATIONS.md §5).
 */
@Composable
fun RestrictionNotice(
    restriction: TimerRestriction,
    palette: TimerPalette,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val warning = restriction.severity == RestrictionSeverity.WARNING
    val accent = if (warning) palette.danger else palette.running

    Card(
        modifier = modifier.fillMaxWidth().padding(bottom = Spacing.s),
        colors = CardDefaults.cardColors(containerColor = palette.ringTrack),
    ) {
        Column(modifier = Modifier.padding(Spacing.m)) {
            Text(
                text = stringResource(restriction.messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackground,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text =
                            stringResource(
                                if (warning) R.string.timer_notice_dismiss else R.string.timer_notice_understood,
                            ),
                        color = palette.onBackgroundMuted,
                    )
                }
                TextButton(onClick = onAction) {
                    Text(stringResource(restriction.actionRes), color = accent)
                }
            }
        }
    }
}

/**
 * Формулировки честные до цифр: пользователю важно знать не то, что «что-то
 * может пойти не так», а что именно и насколько это плохо.
 */
private val TimerRestriction.messageRes: Int
    get() =
        when (this) {
            TimerRestriction.NOTIFICATIONS_DISABLED -> R.string.timer_notice_notifications
            TimerRestriction.ALARMS_SILENCED_BY_DND -> R.string.timer_notice_dnd
            TimerRestriction.BATTERY_OPTIMIZED -> R.string.timer_notice_battery
            TimerRestriction.EXACT_ALARMS_UNAVAILABLE -> R.string.timer_notice_exact_alarms
        }

private val TimerRestriction.actionRes: Int
    get() =
        when (this) {
            TimerRestriction.NOTIFICATIONS_DISABLED -> R.string.timer_notice_action_notifications
            TimerRestriction.ALARMS_SILENCED_BY_DND -> R.string.timer_notice_action_dnd
            TimerRestriction.BATTERY_OPTIMIZED -> R.string.timer_notice_action_battery
            TimerRestriction.EXACT_ALARMS_UNAVAILABLE -> R.string.timer_notice_action_exact_alarms
        }
