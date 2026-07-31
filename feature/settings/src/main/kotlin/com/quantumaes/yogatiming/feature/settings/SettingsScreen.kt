package com.quantumaes.yogatiming.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTheme
import com.quantumaes.yogatiming.domain.alert.VoiceStatus
import com.quantumaes.yogatiming.domain.settings.AppSettings
import com.quantumaes.yogatiming.domain.settings.ThemeMode

private val VALUE_WIDTH = 56.dp

/**
 * Экран 6 «Настройки» (Фаза 7).
 *
 * Разделы идут в порядке макета ТЗ, но с двумя отличиями, принятыми по ходу:
 *
 * 1. **Экспорта и импорта здесь нет** — они перенесены в v1.1
 *    (docs/01-ROADMAP.md §6), и рисовать пункт, который откроет заглушку,
 *    хуже, чем не рисовать его вовсе.
 * 2. **«Игнорировать Не беспокоить» тоже нет.** Приложение не может обойти
 *    системный режим тишины изнутри — переключатель в настройках приложения
 *    обещал бы невозможное. Вместо него на рабочем экране появляется баннер
 *    с переходом в системные настройки, когда «Полная тишина» действительно
 *    включена (`TimerRestriction.ALARMS_SILENCED_BY_DND`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    onReplayOnboarding: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val voiceStatus by viewModel.voiceStatus.collectAsStateWithLifecycle()
    val hintsRestored by viewModel.hintsRestored.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val hintsRestoredMessage = stringResource(R.string.settings_hints_restored)

    LaunchedEffect(hintsRestored) {
        if (!hintsRestored) return@LaunchedEffect
        snackbarHostState.showSnackbar(hintsRestoredMessage)
        viewModel.acknowledgeHintsRestored()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        SettingsContent(
            settings = settings,
            voiceStatus = voiceStatus,
            dynamicColorAvailable = viewModel.dynamicColorAvailable,
            onThemeModeChange = viewModel::setThemeMode,
            onDynamicColorChange = viewModel::setDynamicColor,
            onVoiceEnabledChange = viewModel::setVoiceEnabled,
            onAlertVolumeChange = viewModel::setAlertVolume,
            onDuckMusicChange = viewModel::setDuckMusic,
            onSpeechRateChange = viewModel::setSpeechRate,
            onKeepScreenOnChange = viewModel::setKeepScreenOn,
            onAutoDimChange = viewModel::setAutoDim,
            onSettingsFromSessionChange = viewModel::setSettingsFromSession,
            onPreviewSound = viewModel::previewSound,
            onPreviewVoice = viewModel::previewVoice,
            onRestoreHints = viewModel::restoreHints,
            onReplayOnboarding = {
                viewModel.replayOnboarding()
                onReplayOnboarding()
            },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun SettingsContent(
    settings: AppSettings,
    voiceStatus: VoiceStatus,
    dynamicColorAvailable: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onVoiceEnabledChange: (Boolean) -> Unit,
    onAlertVolumeChange: (Int) -> Unit,
    onDuckMusicChange: (Boolean) -> Unit,
    onSpeechRateChange: (Int) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onAutoDimChange: (Boolean) -> Unit,
    onSettingsFromSessionChange: (Boolean) -> Unit,
    onPreviewSound: () -> Unit,
    onPreviewVoice: () -> Unit,
    onRestoreHints: () -> Unit,
    onReplayOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xl),
    ) {
        SectionTitle(stringResource(R.string.settings_section_sound))
        PercentRow(
            title = stringResource(R.string.settings_volume),
            percent = settings.alertVolumePercent,
            range = AppSettings.MIN_ALERT_VOLUME..AppSettings.MAX_ALERT_VOLUME,
            step = AppSettings.ALERT_VOLUME_STEP,
            onChange = onAlertVolumeChange,
        )
        HintText(stringResource(R.string.settings_volume_hint))
        PreviewButton(text = stringResource(R.string.settings_test_sound), onClick = onPreviewSound)

        SwitchRow(
            title = stringResource(R.string.settings_duck),
            subtitle = stringResource(R.string.settings_duck_hint),
            checked = settings.duckMusicOnAlert,
            onCheckedChange = onDuckMusicChange,
        )

        HorizontalDivider(Modifier.padding(vertical = Spacing.s))

        SectionTitle(stringResource(R.string.settings_section_voice))
        SwitchRow(
            title = stringResource(R.string.settings_voice),
            subtitle = stringResource(R.string.settings_voice_hint),
            checked = settings.voiceEnabled,
            onCheckedChange = onVoiceEnabledChange,
        )
        // Скорость и проверка показываются только при включённом голосе:
        // настраивать выключённый канал не за чем.
        if (settings.voiceEnabled) {
            PercentRow(
                title = stringResource(R.string.settings_speech_rate),
                percent = settings.speechRatePercent,
                range = AppSettings.MIN_SPEECH_RATE..AppSettings.MAX_SPEECH_RATE,
                step = AppSettings.SPEECH_RATE_STEP,
                onChange = onSpeechRateChange,
            )
            HintText(stringResource(R.string.settings_speech_rate_hint))
            PreviewButton(text = stringResource(R.string.settings_test_voice), onClick = onPreviewVoice)
            VoiceStatusNotice(voiceStatus)
        }

        HorizontalDivider(Modifier.padding(vertical = Spacing.s))

        SectionTitle(stringResource(R.string.settings_section_screen))
        SwitchRow(
            title = stringResource(R.string.settings_keep_screen_on),
            subtitle = stringResource(R.string.settings_keep_screen_on_hint),
            checked = settings.keepScreenOn,
            onCheckedChange = onKeepScreenOnChange,
        )
        SwitchRow(
            title = stringResource(R.string.settings_auto_dim),
            subtitle = stringResource(R.string.settings_auto_dim_hint),
            checked = settings.autoDimEnabled,
            onCheckedChange = onAutoDimChange,
        )
        SwitchRow(
            title = stringResource(R.string.settings_session_settings),
            subtitle = stringResource(R.string.settings_session_settings_hint),
            checked = settings.settingsFromSession,
            onCheckedChange = onSettingsFromSessionChange,
        )

        HorizontalDivider(Modifier.padding(vertical = Spacing.s))

        SectionTitle(stringResource(R.string.settings_section_appearance))
        Column(Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { mode ->
                ThemeOption(
                    mode = mode,
                    selected = mode == settings.themeMode,
                    onSelect = { onThemeModeChange(mode) },
                )
            }
        }
        if (dynamicColorAvailable) {
            SwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                subtitle = stringResource(R.string.settings_dynamic_color_hint),
                checked = settings.dynamicColor,
                onCheckedChange = onDynamicColorChange,
            )
        }

        HorizontalDivider(Modifier.padding(vertical = Spacing.s))

        SectionTitle(stringResource(R.string.settings_section_hints))
        HintText(stringResource(R.string.settings_restore_hints_hint))
        TextButton(onClick = onRestoreHints, modifier = Modifier.padding(horizontal = Spacing.s)) {
            Text(stringResource(R.string.settings_restore_hints))
        }
        TextButton(onClick = onReplayOnboarding, modifier = Modifier.padding(horizontal = Spacing.s)) {
            Text(stringResource(R.string.settings_replay_onboarding))
        }

        HorizontalDivider(Modifier.padding(vertical = Spacing.s))

        SectionTitle(stringResource(R.string.settings_section_about))
        AboutSection()
    }
}

/**
 * Что не так с голосом — и что с этим делать.
 *
 * Предложение доустановить языковой пакет отложили из Фазы 4 именно сюда:
 * показывать его посреди занятия некуда, а до Фазы 7 не было и экрана.
 * Появляется только при `MISSING_DATA` — там, где установка действительно
 * решает дело. Отсутствие голоса вовсе (`UNAVAILABLE`) не чинится ничем,
 * поэтому там честное предупреждение без кнопки.
 */
@Composable
private fun VoiceStatusNotice(status: VoiceStatus) {
    val context = LocalContext.current
    when (status) {
        VoiceStatus.MISSING_DATA -> {
            Text(
                text = stringResource(R.string.settings_voice_missing_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.m),
            )
            TextButton(
                onClick = { context.installVoiceData() },
                modifier = Modifier.padding(horizontal = Spacing.s),
            ) {
                Text(stringResource(R.string.settings_voice_install))
            }
        }

        VoiceStatus.UNAVAILABLE -> {
            Text(
                text = stringResource(R.string.settings_voice_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.m),
            )
        }

        VoiceStatus.UNKNOWN, VoiceStatus.READY -> {
            Unit
        }
    }
}

/**
 * Установка голосовых данных — дело системного экрана TTS.
 *
 * `ACTION_INSTALL_TTS_DATA` обрабатывает движок, а движка может не быть вовсе:
 * тогда ставить нечего и сообщать не о чем — рядом уже висит объяснение.
 */
private fun Context.installVoiceData() {
    try {
        startActivity(
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        // Движка TTS на устройстве нет — открывать нечего.
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.m, bottom = Spacing.s),
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.m),
    )
}

/**
 * Ползунок процентов с подписью текущего значения.
 *
 * Значение показано числом, а не только положением ручки: «сколько именно
 * процентов» — это то, что пользователь сравнивает между запусками, а на глаз
 * по ползунку 70% от 80% не отличить.
 */
@Composable
private fun PercentRow(
    title: String,
    percent: Int,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = Spacing.m),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Slider(
                value = percent.toFloat(),
                onValueChange = { onChange(it.toInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first) / step - 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.settings_percent_value, percent),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = VALUE_WIDTH),
            )
        }
    }
}

@Composable
private fun PreviewButton(
    text: String,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null)
        Text(text = text, modifier = Modifier.padding(start = Spacing.s))
    }
}

/**
 * Вариант темы.
 *
 * `selectable` на всей строке, а не только на кнопке: попасть пальцем в
 * радиокнопку с коврика тяжело, а TalkBack получает одну цель вместо двух.
 */
@Composable
private fun ThemeOption(
    mode: ThemeMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = stringResource(mode.labelRes),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private val ThemeMode.labelRes: Int
    get() =
        when (this) {
            ThemeMode.SYSTEM -> R.string.settings_theme_system
            ThemeMode.LIGHT -> R.string.settings_theme_light
            ThemeMode.DARK -> R.string.settings_theme_dark
        }

@Preview
@Composable
private fun SettingsContentPreview() {
    YtaTheme(darkTheme = false) {
        SettingsContent(
            settings = AppSettings(themeMode = ThemeMode.LIGHT, voiceEnabled = true),
            voiceStatus = VoiceStatus.READY,
            dynamicColorAvailable = true,
            onThemeModeChange = {},
            onDynamicColorChange = {},
            onVoiceEnabledChange = {},
            onAlertVolumeChange = {},
            onDuckMusicChange = {},
            onSpeechRateChange = {},
            onKeepScreenOnChange = {},
            onAutoDimChange = {},
            onSettingsFromSessionChange = {},
            onPreviewSound = {},
            onPreviewVoice = {},
            onRestoreHints = {},
            onReplayOnboarding = {},
        )
    }
}
