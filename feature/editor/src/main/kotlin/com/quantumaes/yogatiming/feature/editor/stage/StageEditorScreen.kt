package com.quantumaes.yogatiming.feature.editor.stage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTheme
import com.quantumaes.yogatiming.domain.model.Stage
import com.quantumaes.yogatiming.domain.model.StageType
import com.quantumaes.yogatiming.feature.editor.R
import com.quantumaes.yogatiming.feature.editor.component.ColorTagPicker
import com.quantumaes.yogatiming.feature.editor.component.DurationPicker
import com.quantumaes.yogatiming.feature.editor.component.EditorScaffold
import com.quantumaes.yogatiming.feature.editor.component.FieldHint
import com.quantumaes.yogatiming.feature.editor.component.SectionTitle
import com.quantumaes.yogatiming.feature.editor.component.SingleChoiceChips
import com.quantumaes.yogatiming.feature.editor.component.SwitchRow
import com.quantumaes.yogatiming.feature.editor.stageTypeLabelRes

private const val NOTE_MAX_LINES = 4

/** Экран 3 «Редактор этапа». */
@Composable
internal fun StageEditorScreen(
    onOpenAlerts: (profileId: Long, stageId: Long) -> Unit,
    onBack: () -> Unit,
    viewModel: StageEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshAlerts() }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is StageEditorEvent.OpenAlerts -> onOpenAlerts(event.profileId, event.stageId)
                StageEditorEvent.Saved -> onBack()
            }
        }
    }

    StageEditorContent(
        uiState = uiState,
        onNameChange = viewModel::setName,
        onTypeChange = viewModel::setType,
        onDeclineRestPreset = viewModel::declineRestPreset,
        onColorChange = viewModel::setColorTag,
        onDurationChange = viewModel::setDuration,
        onNoteChange = viewModel::setNote,
        onOwnAlertsChange = viewModel::setOwnAlerts,
        onOpenAlerts = viewModel::openAlerts,
        onSave = viewModel::save,
        onBack = onBack,
    )
}

@Composable
private fun StageEditorContent(
    uiState: StageEditorUiState,
    onNameChange: (String) -> Unit,
    onTypeChange: (StageType) -> Unit,
    onDeclineRestPreset: () -> Unit,
    onColorChange: (String) -> Unit,
    onDurationChange: (Int) -> Unit,
    onNoteChange: (String) -> Unit,
    onOwnAlertsChange: (Boolean) -> Unit,
    onOpenAlerts: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val title = stringResource(if (uiState.isNew) R.string.editor_stage_new else R.string.editor_stage_title)

    EditorScaffold(title = title, onBack = onBack, onSave = onSave) { modifier ->
        if (uiState.isLoading) {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(
                modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = Spacing.xl),
            ) {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = onNameChange,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.m, vertical = Spacing.s),
                    label = { Text(stringResource(R.string.editor_stage_name)) },
                    singleLine = true,
                    isError = uiState.nameErrorShown,
                )
                if (uiState.nameErrorShown) {
                    FieldHint(stringResource(R.string.editor_stage_name_required), error = true)
                }

                SectionTitle(stringResource(R.string.editor_stage_type))
                SingleChoiceChips(
                    options = StageType.entries,
                    selected = uiState.type,
                    label = { stringResource(it.stageTypeLabelRes()) },
                    onSelect = onTypeChange,
                )
                FieldHint(stringResource(uiState.type.hintRes()), Modifier.padding(top = Spacing.xs))

                if (uiState.restPresetOffered) {
                    RestPresetNotice(onDecline = onDeclineRestPreset)
                }

                if (uiState.hasDuration) {
                    SectionTitle(stringResource(R.string.editor_stage_duration))
                    DurationPicker(durationSec = uiState.durationSec, onDurationChange = onDurationChange)
                    if (uiState.durationErrorShown) {
                        FieldHint(stringResource(R.string.editor_stage_duration_invalid), error = true)
                    }
                }

                SectionTitle(stringResource(R.string.editor_color))
                ColorTagPicker(selected = uiState.colorTag, onSelect = onColorChange)

                SectionTitle(stringResource(R.string.editor_stage_note))
                OutlinedTextField(
                    value = uiState.note,
                    onValueChange = onNoteChange,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.m),
                    label = { Text(stringResource(R.string.editor_stage_note_label)) },
                    maxLines = NOTE_MAX_LINES,
                )
                FieldHint(
                    text = stringResource(R.string.editor_stage_note_hint),
                    modifier = Modifier.padding(top = Spacing.xs),
                )

                SectionTitle(stringResource(R.string.editor_stage_alerts))
                SwitchRow(
                    title = stringResource(R.string.editor_stage_own_alerts),
                    subtitle = stringResource(R.string.editor_stage_own_alerts_hint),
                    checked = uiState.hasOwnAlerts,
                    onCheckedChange = onOwnAlertsChange,
                )
                if (uiState.hasOwnAlerts) {
                    OutlinedButton(
                        onClick = onOpenAlerts,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.m, vertical = Spacing.s),
                    ) {
                        Icon(Icons.Filled.Notifications, contentDescription = null)
                        Text(
                            text = stringResource(R.string.editor_stage_open_alerts),
                            modifier = Modifier.padding(start = Spacing.s),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Предложение тихого пресета для этапа отдыха (решение C-6).
 *
 * Пресет уже применён — сообщение о факте с возможностью отказаться, а не
 * вопрос: диалог «применить тихий пресет?» посреди заполнения формы прерывает
 * работу ради решения, которое почти всегда «да».
 */
@Composable
private fun RestPresetNotice(onDecline: () -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)) {
        Text(
            text = stringResource(R.string.editor_stage_rest_preset),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onDecline) {
            Text(stringResource(R.string.editor_stage_rest_preset_decline))
        }
    }
}

private fun StageType.hintRes(): Int =
    when (this) {
        StageType.NORMAL -> R.string.editor_stage_type_normal_hint
        StageType.TRANSITION -> R.string.editor_stage_type_transition_hint
        StageType.REST -> R.string.editor_stage_type_rest_hint
        StageType.FREE -> R.string.editor_stage_type_free_hint
    }

@Preview
@Composable
private fun StageEditorPreview() {
    YtaTheme(darkTheme = false) {
        StageEditorContent(
            uiState =
                StageEditorUiState(
                    isNew = false,
                    isLoading = false,
                    name = "Асаны стоя",
                    durationSec = Stage.MIN_DURATION_SEC * 216,
                    note = "Вирабхадрасана I–II, триконасана",
                ),
            onNameChange = {},
            onTypeChange = {},
            onDeclineRestPreset = {},
            onColorChange = {},
            onDurationChange = {},
            onNoteChange = {},
            onOwnAlertsChange = {},
            onOpenAlerts = {},
            onSave = {},
            onBack = {},
        )
    }
}
