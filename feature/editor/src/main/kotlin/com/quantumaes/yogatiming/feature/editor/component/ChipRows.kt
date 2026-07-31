package com.quantumaes.yogatiming.feature.editor.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.quantumaes.yogatiming.core.designsystem.theme.ColorTags
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.feature.editor.R

private val SWATCH_SIZE = 40.dp
private val SWATCH_BORDER = 2.dp

/** Ширина растворяющегося края у прокручиваемого ряда. */
private val FADE_WIDTH = 28.dp

/**
 * Горизонтальный ряд чипов выбора одного значения.
 *
 * @param label подпись ряда. Пусто — ряд идёт под собственным заголовком.
 */
@Composable
fun <T> SingleChoiceChips(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    Column(modifier) {
        title?.let { FieldLabel(it) }
        ScrollableRow {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

/**
 * Выбор цветовой метки.
 *
 * Выбранный образец помечается галочкой, а не только рамкой: рамка вокруг
 * тёмного кружка на тёмном фоне почти не видна, а метку выбирают в том числе
 * при плохом освещении.
 */
@Composable
fun ColorTagPicker(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentDescription = stringResource(R.string.editor_color)
    ScrollableRow(modifier) {
        ColorTags.palette.forEach { tag ->
            val isSelected = tag.equals(selected, ignoreCase = true)
            Box(
                modifier =
                    Modifier
                        .size(SWATCH_SIZE)
                        .background(ColorTags.toColor(tag), CircleShape)
                        .border(
                            width = if (isSelected) SWATCH_BORDER else 0.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape,
                        ).selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(tag) },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = contentDescription,
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * Ряд, который прокручивается вбок, — и по нему это видно.
 *
 * Список вариантов шире экрана выглядел как обрезанный по краю: узнать, что
 * справа есть ещё, можно было только случайным движением пальца (полевая
 * проверка 2026-07-31, замечание 1). Теперь край растворяется, а поверх него
 * стоит стрелка — оба признака появляются ровно тогда, когда прокручивать
 * действительно есть куда, и исчезают, когда ряд домотан до конца.
 */
@Composable
internal fun ScrollableRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val state = rememberScrollState()

    Box(modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fadingEdges(state)
                    .horizontalScroll(state)
                    .padding(horizontal = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        ScrollArrow(
            visible = state.canScrollBackward,
            forward = false,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        ScrollArrow(visible = state.canScrollForward, forward = true, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun ScrollArrow(
    visible: Boolean,
    forward: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Icon(
        imageVector =
            if (forward) {
                Icons.AutoMirrored.Filled.KeyboardArrowRight
            } else {
                Icons.AutoMirrored.Filled.KeyboardArrowLeft
            },
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = Spacing.xxs),
    )
}

/**
 * Растворяющиеся края прокручиваемого ряда.
 *
 * Рисуется поверх содержимого маской прозрачности (`DstIn`), поэтому слой
 * приходится компоновать отдельно от общего холста. Ставится **до**
 * `horizontalScroll`: там размер узла — это видимое окно, а не вся лента.
 */
private fun Modifier.fadingEdges(state: ScrollState): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = FADE_WIDTH.toPx().coerceAtMost(size.width / 2)
            if (state.canScrollBackward) {
                drawRect(
                    brush =
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            startX = 0f,
                            endX = fade,
                        ),
                    size = Size(fade, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (state.canScrollForward) {
                drawRect(
                    brush =
                        Brush.horizontalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startX = size.width - fade,
                            endX = size.width,
                        ),
                    topLeft = Offset(size.width - fade, 0f),
                    size = Size(fade, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
