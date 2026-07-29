package com.quantumaes.yogatiming.feature.editor.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.quantumaes.yogatiming.core.designsystem.theme.ColorTags
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.feature.editor.R

private val SWATCH_SIZE = 40.dp
private val SWATCH_BORDER = 2.dp

/**
 * Каркас экрана редактора: заголовок, «Назад» и кнопка сохранения.
 *
 * Сохранение — текстовая кнопка в верхней панели, а не FAB: FAB на экране
 * с длинным списком этапов закрывает последнюю строку, а редактор — это форма,
 * а не список действий.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScaffold(
    title: String,
    onBack: () -> Unit,
    onSave: (() -> Unit)? = null,
    saveEnabled: Boolean = true,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back),
                        )
                    }
                },
                actions = {
                    onSave?.let {
                        TextButton(onClick = it, enabled = saveEnabled) {
                            Text(stringResource(R.string.editor_save))
                        }
                    }
                },
            )
        },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}

/** Заголовок раздела формы. */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.m, bottom = Spacing.s),
    )
}

/** Пояснение под полем: правило, а не украшение. */
@Composable
fun FieldHint(
    text: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = Spacing.m),
    )
}

/** Горизонтальный ряд чипов выбора одного значения. */
@Composable
fun <T> SingleChoiceChips(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
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
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
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

/** Строка-переключатель с подписью и пояснением. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
