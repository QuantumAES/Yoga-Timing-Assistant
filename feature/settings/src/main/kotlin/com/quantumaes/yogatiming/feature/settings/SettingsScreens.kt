package com.quantumaes.yogatiming.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumaes.yogatiming.core.designsystem.component.PlaceholderAction
import com.quantumaes.yogatiming.core.designsystem.component.PlaceholderScreen
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTheme
import com.quantumaes.yogatiming.domain.settings.AppSettings
import com.quantumaes.yogatiming.domain.settings.ThemeMode

/**
 * Экран 6 «Настройки».
 *
 * В v1.0 здесь пока только оформление: выбор темы вытащен из Фазы 7 раньше
 * срока — приложение с одной лишь системной темой невозможно ни показать
 * заказчику в светлом виде, ни проверить на контраст. Громкость сигналов,
 * ducking, параметры TTS, wake lock и автозатемнение приезжают в Фазу 7
 * (docs/01-ROADMAP.md).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
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
            dynamicColorAvailable = viewModel.dynamicColorAvailable,
            onThemeModeChange = viewModel::setThemeMode,
            onDynamicColorChange = viewModel::setDynamicColor,
            onVoiceEnabledChange = viewModel::setVoiceEnabled,
            onRestoreHints = viewModel::restoreHints,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun SettingsContent(
    settings: AppSettings,
    dynamicColorAvailable: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onVoiceEnabledChange: (Boolean) -> Unit,
    onRestoreHints: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xl),
    ) {
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

        SectionTitle(stringResource(R.string.settings_section_alerts))
        SwitchRow(
            title = stringResource(R.string.settings_voice),
            subtitle = stringResource(R.string.settings_voice_hint),
            checked = settings.voiceEnabled,
            onCheckedChange = onVoiceEnabledChange,
        )

        HorizontalDivider(Modifier.padding(vertical = Spacing.s))

        SectionTitle(stringResource(R.string.settings_section_hints))
        Text(
            text = stringResource(R.string.settings_restore_hints_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.m),
        )
        TextButton(
            onClick = onRestoreHints,
            modifier = Modifier.padding(horizontal = Spacing.s),
        ) {
            Text(stringResource(R.string.settings_restore_hints))
        }
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

/**
 * Экран 7 «Онбординг». Заглушка Фазы 1: 2–3 слайда и запрос разрешений — Фаза 7.
 * Battery optimization запрашивается не здесь, а по факту ограничения (P0-8).
 */
@Composable
internal fun OnboardingScreen(onComplete: () -> Unit) {
    PlaceholderScreen(
        title = stringResource(R.string.settings_onboarding_title),
        description = stringResource(R.string.settings_onboarding_description),
        actions = listOf(PlaceholderAction(stringResource(R.string.settings_onboarding_start)) { onComplete() }),
    )
}

@Preview
@Composable
private fun SettingsContentPreview() {
    YtaTheme(darkTheme = false) {
        SettingsContent(
            settings = AppSettings(themeMode = ThemeMode.LIGHT),
            dynamicColorAvailable = true,
            onThemeModeChange = {},
            onDynamicColorChange = {},
            onVoiceEnabledChange = {},
            onRestoreHints = {},
        )
    }
}
