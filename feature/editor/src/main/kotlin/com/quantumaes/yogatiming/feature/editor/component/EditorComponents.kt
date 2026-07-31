package com.quantumaes.yogatiming.feature.editor.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.feature.editor.R

/**
 * Каркас экрана редактора: заголовок, «Назад» и кнопка сохранения.
 *
 * Сохранение — текстовая кнопка в верхней панели, а не FAB: FAB на экране
 * с длинным списком этапов закрывает последнюю строку, а редактор — это форма,
 * а не список действий.
 *
 * @param hasUnsavedChanges есть ли правки, которых нет в базе. Уход с такими
 *   правками спрашивает подтверждение — и «Назад» в панели, и системная
 *   «Назад» (полевая проверка 2026-07-31, замечание 8): форма редактора
 *   заполняется минутами, а теряется одним движением от края экрана.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScaffold(
    title: String,
    onBack: () -> Unit,
    onSave: (() -> Unit)? = null,
    saveEnabled: Boolean = true,
    snackbarHostState: SnackbarHostState? = null,
    hasUnsavedChanges: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    var confirmExit by remember { mutableStateOf(false) }
    val requestExit = { if (hasUnsavedChanges) confirmExit = true else onBack() }

    BackHandler(enabled = hasUnsavedChanges) { confirmExit = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = requestExit) {
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

    if (confirmExit) {
        UnsavedChangesDialog(
            // «Сохранить» закрывает экран сама: сохранение уводит с него по
            // своему событию — второй раз звать `onBack` нельзя.
            onSave =
                onSave?.let { save ->
                    {
                        confirmExit = false
                        save()
                    }
                },
            onDiscard = {
                confirmExit = false
                onBack()
            },
            onCancel = { confirmExit = false },
        )
    }
}

/** Вопрос при уходе с несохранёнными правками. */
@Composable
private fun UnsavedChangesDialog(
    onSave: (() -> Unit)?,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.editor_unsaved_title)) },
        text = { Text(stringResource(R.string.editor_unsaved_message)) },
        confirmButton = {
            onSave?.let {
                TextButton(onClick = it) { Text(stringResource(R.string.editor_unsaved_save)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text(
                    text = stringResource(R.string.editor_unsaved_discard),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
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

/**
 * Раздел формы карточкой: заголовок, необязательный переключатель и содержимое.
 *
 * Карточка, а не заголовок с отступом (полевая проверка 2026-07-31,
 * замечание 1): на экране оповещений подряд идут четыре однотипных набора
 * чипов, и без видимых границ они читались как одна россыпь кнопок. Граница
 * карточки отвечает на вопрос «к чему относится этот переключатель» раньше,
 * чем его успевают задать.
 *
 * @param checked состояние переключателя в шапке. `null` — раздел без него.
 */
@Composable
fun EditorCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.m, end = Spacing.m, top = Spacing.m, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (checked != null && onCheckedChange != null) {
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
        }
        Column(Modifier.padding(bottom = Spacing.s), content = content)
    }
}

/** Тонкий разделитель внутри карточки: границы между параметрами одного набора. */
@Composable
fun CardDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Подпись над рядом вариантов: без неё чипы не говорят, чем именно управляют. */
@Composable
fun FieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.xs),
    )
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
