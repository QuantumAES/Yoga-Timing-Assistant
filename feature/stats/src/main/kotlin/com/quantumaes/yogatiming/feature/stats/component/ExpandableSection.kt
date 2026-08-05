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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    // Состояние — отдельно от действия: «свернуть» говорит, что случится по
    // нажатию, но не говорит, как сейчас. Незрячему пользователю нужно и то и
    // другое, и первым — текущее состояние (фаза S6, проверка A-1).
    val state =
        stringResource(if (expanded) R.string.stats_section_expanded else R.string.stats_section_collapsed)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget)
                    // Подпись действия на всю строку: TalkBack прочитает
                    // «Журнал, 12 занятий, свёрнуто, развернуть» одной фразой.
                    .clickable(onClickLabel = action, onClick = onToggle)
                    // Заголовок раздела: по заголовкам TalkBack умеет прыгать,
                    // и на экране из пяти разделов это единственный способ
                    // добраться до журнала, не прослушав всё, что над ним.
                    .semantics {
                        heading()
                        stateDescription = state
                    },
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
