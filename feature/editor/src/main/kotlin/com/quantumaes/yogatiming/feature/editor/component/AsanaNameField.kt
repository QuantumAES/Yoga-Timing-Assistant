package com.quantumaes.yogatiming.feature.editor.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.feature.editor.R

/** Сколько подсказок показывать разом: длинный список перекрывает форму. */
private const val MAX_SUGGESTIONS = 8

/** Потолок высоты списка подсказок — чтобы он не закрывал поле ввода. */
private val SUGGESTIONS_MAX_HEIGHT = 280.dp

/**
 * Название этапа с подсказками из справочника асан (замечание 2 полевой
 * проверки 2026-08-04).
 *
 * Поле остаётся обычным полем ввода: справочник **подсказывает**, а не
 * ограничивает. «Асаны стоя», «Разминка» и «Блок для новичков» — законные
 * названия этапов, которых ни в одном справочнике нет и быть не должно.
 * Поэтому список фильтруется по мере набора и исчезает, когда совпадений нет,
 * — вместо выпадающего списка, из которого обязательно надо выбрать.
 *
 * Выбор из справочника заодно заполняет произношение: ударение в
 * «Паривритта Паршваконасана» пользователь не обязан знать, а синтезатор без
 * него читает название неузнаваемо (см. `Pronunciation`).
 *
 * Поиск идёт по вхождению подстроки, а не по началу слова: инструктор помнит
 * «конасана» чаще, чем «упавиштха».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsanaNameField(
    value: String,
    onValueChange: (String) -> Unit,
    onPick: (name: String, voiceName: String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val names = stringArrayResource(R.array.asana_directory)
    val voices = stringArrayResource(R.array.asana_directory_voice)

    var expanded by remember { mutableStateOf(false) }

    val suggestions =
        remember(value, names) {
            val query = value.trim()
            if (query.isEmpty()) {
                emptyList()
            } else {
                names
                    .withIndex()
                    .filter { (_, name) -> name.contains(query, ignoreCase = true) && !name.equals(query, true) }
                    .take(MAX_SUGGESTIONS)
            }
        }

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            modifier =
                Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
            label = { Text(stringResource(R.string.editor_stage_name)) },
            singleLine = true,
            isError = isError,
        )

        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = SUGGESTIONS_MAX_HEIGHT),
        ) {
            suggestions.forEach { (index, name) ->
                DropdownMenuItem(
                    text = { Text(name, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onPick(name, voices.getOrElse(index) { "" })
                        expanded = false
                    },
                )
            }
        }
    }
}
