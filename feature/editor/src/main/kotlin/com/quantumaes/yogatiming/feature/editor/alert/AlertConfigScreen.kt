package com.quantumaes.yogatiming.feature.editor.alert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumaes.yogatiming.core.common.time.TimeFormatter
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTheme
import com.quantumaes.yogatiming.domain.model.alert.Alert
import com.quantumaes.yogatiming.domain.model.alert.AlertChannel
import com.quantumaes.yogatiming.domain.model.alert.AlertPreset
import com.quantumaes.yogatiming.domain.model.alert.AlertPresets
import com.quantumaes.yogatiming.domain.model.alert.AlertSound
import com.quantumaes.yogatiming.domain.model.alert.VibrationPattern
import com.quantumaes.yogatiming.domain.model.alert.VoicePhrase
import com.quantumaes.yogatiming.feature.editor.R
import com.quantumaes.yogatiming.feature.editor.channelLabelRes
import com.quantumaes.yogatiming.feature.editor.component.CustomSoundPicker
import com.quantumaes.yogatiming.feature.editor.component.EditorScaffold
import com.quantumaes.yogatiming.feature.editor.component.FieldHint
import com.quantumaes.yogatiming.feature.editor.component.SectionTitle
import com.quantumaes.yogatiming.feature.editor.component.SingleChoiceChips
import com.quantumaes.yogatiming.feature.editor.component.SwitchRow
import com.quantumaes.yogatiming.feature.editor.presetLabelRes
import com.quantumaes.yogatiming.feature.editor.soundLabelRes
import com.quantumaes.yogatiming.feature.editor.vibrationLabelRes
import com.quantumaes.yogatiming.feature.editor.voiceLabelRes
import com.quantumaes.yogatiming.timer.engine.model.AlertTrigger

private const val MS_IN_SECOND = 1_000L
private const val PERCENT_MAX = 100f
private const val SECONDS_IN_MINUTE = 60

/** Смещения предупреждений, из которых их и собирают: 5, 3, 2, 1 минута и 30/10 секунд. */
private val WARNING_OFFSETS_SEC = listOf(300, 180, 120, 60, 30, 10)

/**
 * Сколько секунд файла пользователя играть.
 *
 * Готовые значения, а не ползунок: разница между 12 и 13 секундами на слух
 * неразличима, а попасть пальцем в ползунок точнее пяти секунд всё равно
 * нельзя. Границы — `Alert.MIN_CUSTOM_SOUND_SEC`…`MAX_CUSTOM_SOUND_SEC`.
 */
private val CUSTOM_SOUND_SECONDS = listOf(3, 5, 10, 15, 30, 60)

/**
 * Экран 5 «Редактор оповещений».
 *
 * Структура повторяет модель: START, список предупреждений, END. Плоского
 * списка с полем «тип триггера» нет ни в данных, ни здесь — так невозможно
 * собрать два START или END со смещением (ADR-002).
 */
@Composable
internal fun AlertConfigScreen(
    onBack: () -> Unit,
    viewModel: AlertConfigViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                AlertConfigEvent.Saved -> onBack()
            }
        }
    }

    AlertConfigContent(
        uiState = uiState,
        onPreset = viewModel::applyPreset,
        onVolume = viewModel::setMasterVolume,
        onTriggerEnabled = viewModel::setTriggerEnabled,
        onUpdateStart = viewModel::updateStart,
        onUpdateEnd = viewModel::updateEnd,
        onUpdateWarning = viewModel::updateWarning,
        onWarningOffset = viewModel::setWarningOffset,
        onAddWarning = viewModel::addWarning,
        onRemoveWarning = viewModel::removeWarning,
        onPreview = viewModel::preview,
        onSave = viewModel::save,
        onBack = onBack,
    )
}

@Composable
private fun AlertConfigContent(
    uiState: AlertConfigUiState,
    onPreset: (AlertPreset) -> Unit,
    onVolume: (Int) -> Unit,
    onTriggerEnabled: (AlertTrigger, Boolean) -> Unit,
    onUpdateStart: ((Alert) -> Alert) -> Unit,
    onUpdateEnd: ((Alert) -> Alert) -> Unit,
    onUpdateWarning: (Int, (Alert) -> Alert) -> Unit,
    onWarningOffset: (Int, Int) -> Unit,
    onAddWarning: () -> Unit,
    onRemoveWarning: (Int) -> Unit,
    onPreview: (Alert, AlertTrigger) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val title =
        stringResource(
            if (uiState.isStageScope) R.string.editor_alerts_stage_title else R.string.editor_alerts_profile_title,
        )

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
                FieldHint(stringResource(R.string.editor_alerts_owner, uiState.ownerName))

                SectionTitle(stringResource(R.string.editor_alerts_preset))
                SingleChoiceChips(
                    options = AlertPreset.entries,
                    selected = uiState.config.preset,
                    label = { stringResource(it.presetLabelRes()) },
                    onSelect = onPreset,
                )

                SectionTitle(stringResource(R.string.editor_alerts_volume))
                VolumeSlider(percent = uiState.config.masterVolumePercent, onVolume = onVolume)

                if (uiState.isFreeStage) {
                    FieldHint(stringResource(R.string.editor_alerts_free_stage))
                }

                SectionTitle(stringResource(R.string.editor_alerts_start))
                TriggerSection(
                    alert = uiState.config.start,
                    trigger = AlertTrigger.START,
                    voiceOptions = START_VOICES,
                    voiceEnabled = uiState.voiceEnabled,
                    onEnabled = { onTriggerEnabled(AlertTrigger.START, it) },
                    onUpdate = onUpdateStart,
                    onPreview = onPreview,
                )

                SectionTitle(stringResource(R.string.editor_alerts_warnings))
                uiState.warnings.forEach { warning ->
                    WarningSection(
                        alert = warning,
                        unreachable = uiState.isWarningUnreachable(warning),
                        voiceEnabled = uiState.voiceEnabled,
                        onOffsetChange = { onWarningOffset(warning.offsetSec, it) },
                        onUpdate = { update -> onUpdateWarning(warning.offsetSec, update) },
                        onRemove = { onRemoveWarning(warning.offsetSec) },
                        onPreview = onPreview,
                    )
                }
                OutlinedButton(
                    onClick = onAddWarning,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.m, vertical = Spacing.s),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(
                        text = stringResource(R.string.editor_alerts_add_warning),
                        modifier = Modifier.padding(start = Spacing.s),
                    )
                }

                SectionTitle(stringResource(R.string.editor_alerts_end))
                TriggerSection(
                    alert = uiState.config.end,
                    trigger = AlertTrigger.END,
                    voiceOptions = END_VOICES,
                    voiceEnabled = uiState.voiceEnabled,
                    onEnabled = { onTriggerEnabled(AlertTrigger.END, it) },
                    onUpdate = onUpdateEnd,
                    onPreview = onPreview,
                )
            }
        }
    }
}

/** Фразы, осмысленные в момент старта этапа. */
private val START_VOICES = listOf(VoicePhrase.NONE, VoicePhrase.STAGE_NAME, VoicePhrase.CUSTOM)

/** В конце этапа объявляют следующий этап или конец занятия. */
private val END_VOICES =
    listOf(VoicePhrase.NONE, VoicePhrase.NEXT_STAGE, VoicePhrase.SESSION_FINISHED, VoicePhrase.CUSTOM)

/** У предупреждения смысл имеет только остаток времени или своя фраза. */
private val WARNING_VOICES = listOf(VoicePhrase.NONE, VoicePhrase.TIME_REMAINING, VoicePhrase.CUSTOM)

@Composable
private fun VolumeSlider(
    percent: Int,
    onVolume: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Slider(
            value = percent.toFloat(),
            onValueChange = { onVolume(it.toInt()) },
            valueRange = 0f..PERCENT_MAX,
            steps = PERCENT_MAX.toInt() / VOLUME_STEP_PERCENT - 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.editor_alerts_volume_value, percent),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    FieldHint(stringResource(R.string.editor_alerts_volume_hint))
}

/** Секция START или END: включение, каналы и параметры каждого канала. */
@Composable
private fun TriggerSection(
    alert: Alert?,
    trigger: AlertTrigger,
    voiceOptions: List<VoicePhrase>,
    voiceEnabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    onUpdate: ((Alert) -> Alert) -> Unit,
    onPreview: (Alert, AlertTrigger) -> Unit,
) {
    SwitchRow(
        title = stringResource(R.string.editor_alerts_enabled),
        checked = alert?.enabled == true,
        onCheckedChange = onEnabled,
    )
    if (alert == null || !alert.enabled) return

    AlertBody(
        alert = alert,
        trigger = trigger,
        voiceOptions = voiceOptions,
        voiceEnabled = voiceEnabled,
        onUpdate = onUpdate,
        onPreview = onPreview,
    )
}

@Composable
private fun WarningSection(
    alert: Alert,
    unreachable: Boolean,
    voiceEnabled: Boolean,
    onOffsetChange: (Int) -> Unit,
    onUpdate: ((Alert) -> Alert) -> Unit,
    onRemove: () -> Unit,
    onPreview: (Alert, AlertTrigger) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, top = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.editor_alerts_warning_at, alert.offsetLabel()),
                style = MaterialTheme.typography.titleSmall,
            )
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.editor_alerts_remove_warning),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        SingleChoiceChips(
            options = WARNING_OFFSETS_SEC,
            selected = alert.offsetSec,
            label = { offsetLabel(it) },
            onSelect = onOffsetChange,
        )

        // Решение B-7: такое предупреждение движок пропустит молча, и узнать
        // об этом лучше здесь, чем во время занятия.
        if (unreachable) {
            FieldHint(stringResource(R.string.editor_alerts_warning_unreachable), error = true)
        }

        AlertBody(
            alert = alert,
            trigger = AlertTrigger.WARNING,
            voiceOptions = WARNING_VOICES,
            voiceEnabled = voiceEnabled,
            onUpdate = onUpdate,
            onPreview = onPreview,
        )
    }
}

/** Общая часть любого оповещения: каналы и их параметры. */
@Composable
private fun AlertBody(
    alert: Alert,
    trigger: AlertTrigger,
    voiceOptions: List<VoicePhrase>,
    voiceEnabled: Boolean,
    onUpdate: ((Alert) -> Alert) -> Unit,
    onPreview: (Alert, AlertTrigger) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        AlertChannel.entries.forEach { channel ->
            FilterChip(
                selected = channel in alert.channels,
                onClick = { onUpdate { it.toggleChannel(channel) } },
                label = { Text(stringResource(channel.channelLabelRes())) },
            )
        }
    }

    if (AlertChannel.SOUND in alert.channels) {
        SingleChoiceChips(
            options = AlertSound.entries,
            selected = alert.sound,
            label = { stringResource(it.soundLabelRes()) },
            onSelect = { sound -> onUpdate { it.copy(sound = sound) } },
        )
        if (alert.sound == AlertSound.CUSTOM) {
            CustomSoundPicker(
                uri = alert.customSoundUri,
                onPick = { uri -> onUpdate { it.copy(customSoundUri = uri) } },
            )
            if (alert.customSoundUri != null) {
                SectionTitle(stringResource(R.string.editor_sound_custom_duration))
                SingleChoiceChips(
                    options = CUSTOM_SOUND_SECONDS,
                    selected = alert.customSoundDurationSec,
                    label = { stringResource(R.string.editor_sound_custom_duration_value, it) },
                    onSelect = { seconds -> onUpdate { it.copy(customSoundDurationSec = seconds) } },
                )
                FieldHint(stringResource(R.string.editor_sound_custom_duration_hint))
            }
        }
    }

    if (AlertChannel.VOICE in alert.channels) {
        // Канал включён, а голос выключен целиком: оповещение промолчит, и
        // сказать об этом надо здесь, а не оставить выяснять на занятии.
        if (!voiceEnabled) {
            FieldHint(stringResource(R.string.editor_alerts_voice_disabled), error = true)
        }
        SingleChoiceChips(
            options = voiceOptions,
            selected = alert.voice,
            label = { stringResource(it.voiceLabelRes()) },
            onSelect = { voice -> onUpdate { it.copy(voice = voice) } },
            modifier = Modifier.padding(top = Spacing.s),
        )
        if (alert.voice == VoicePhrase.CUSTOM) {
            OutlinedTextField(
                value = alert.customVoiceText.orEmpty(),
                onValueChange = { text -> onUpdate { it.copy(customVoiceText = text) } },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.m, vertical = Spacing.s),
                label = { Text(stringResource(R.string.editor_alerts_custom_text)) },
                singleLine = true,
            )
            FieldHint(stringResource(R.string.editor_alerts_custom_text_hint))
        }
    }

    if (AlertChannel.VIBRATION in alert.channels) {
        SingleChoiceChips(
            options = VibrationPattern.entries,
            selected = alert.vibration,
            label = { stringResource(it.vibrationLabelRes()) },
            onSelect = { pattern -> onUpdate { it.copy(vibration = pattern) } },
            modifier = Modifier.padding(top = Spacing.s),
        )
    }

    OutlinedButton(
        onClick = { onPreview(alert, trigger) },
        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null)
        Text(
            text = stringResource(R.string.editor_alerts_preview),
            modifier = Modifier.padding(start = Spacing.s),
        )
    }
}

@Composable
private fun Alert.offsetLabel(): String = offsetLabel(offsetSec)

@Composable
private fun offsetLabel(offsetSec: Int): String =
    if (offsetSec % SECONDS_IN_MINUTE == 0) {
        stringResource(R.string.editor_alerts_offset_minutes, offsetSec / SECONDS_IN_MINUTE)
    } else {
        TimeFormatter.clock(offsetSec * MS_IN_SECOND)
    }

@Preview
@Composable
private fun AlertConfigPreview() {
    YtaTheme(darkTheme = false) {
        AlertConfigContent(
            uiState =
                AlertConfigUiState(
                    isLoading = false,
                    ownerName = "Хатха 60 мин",
                    config = AlertPresets.standard(),
                ),
            onPreset = {},
            onVolume = {},
            onTriggerEnabled = { _, _ -> },
            onUpdateStart = {},
            onUpdateEnd = {},
            onUpdateWarning = { _, _ -> },
            onWarningOffset = { _, _ -> },
            onAddWarning = {},
            onRemoveWarning = {},
            onPreview = { _, _ -> },
            onSave = {},
            onBack = {},
        )
    }
}
