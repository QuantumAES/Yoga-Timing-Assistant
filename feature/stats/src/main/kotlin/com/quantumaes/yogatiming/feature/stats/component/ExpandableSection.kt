package com.quantumaes.yogatiming.feature.stats.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.quantumaes.yogatiming.core.designsystem.theme.Dimens
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.feature.stats.R

private const val COLLAPSED_ROTATION = -90f
private const val EXPANDED_ROTATION = 0f

/**
 * Сворачиваемый раздел статистики (замечание 16 полевой проверки 2026-08-04).
 *
 * До него экран выкладывал всё сразу: плитки, график, календарь, разбивку по
 * профилям и журнал целиком. За месяц активной практики это метра три
 * прокрутки, и чтобы посмотреть журнал, приходилось пролистать всё остальное —
 * при том, что за один заход обычно нужен ровно один раздел.
 *
 * Заголовок остаётся информативным и в свёрнутом виде: рядом с названием
 * стоит [subtitle] — то, ради чего в раздел чаще всего и заходят. «Журнал ·
 * 12 занятий» отвечает на вопрос, не раскрываясь; раскрывают, чтобы посмотреть
 * подробности.
 *
 * Состояние держит вызывающий: раздел не должен схлопываться при листании
 * периода, а знать про период он не может.
 */
@Composable
internal fun ExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) EXPANDED_ROTATION else COLLAPSED_ROTATION,
        label = "section-chevron",
    )
    val action =
        stringResource(if (expanded) R.string.stats_section_collapse else R.string.stats_section_expand)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget)
                    // Подпись действия на всю строку: TalkBack прочитает
                    // «Журнал, 12 занятий, развернуть» одной фразой.
                    .clickable(onClickLabel = action, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                // Строка уже нажимается целиком и объявляется TalkBack; значок
                // здесь — украшение состояния, а не вторая цель.
                contentDescription = null,
                modifier = Modifier.rotate(rotation),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = Spacing.s)) { content() }
        }
    }
}
