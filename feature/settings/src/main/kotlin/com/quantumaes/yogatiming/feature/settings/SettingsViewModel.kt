package com.quantumaes.yogatiming.feature.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumaes.yogatiming.domain.hint.HintStore
import com.quantumaes.yogatiming.domain.settings.AppSettings
import com.quantumaes.yogatiming.domain.settings.SettingsStore
import com.quantumaes.yogatiming.domain.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsStore: SettingsStore,
        private val hintStore: HintStore,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings> =
            settingsStore.settings.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = AppSettings(),
            )

        /**
         * Material You требует API 31. Показывать переключатель, который ничего
         * не делает, нечестно — на старых устройствах его просто нет.
         */
        val dynamicColorAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        /** Разовое подтверждение возврата подсказок — исчезает при следующем действии. */
        private val _hintsRestored = MutableStateFlow(false)
        val hintsRestored: StateFlow<Boolean> = _hintsRestored.asStateFlow()

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch { settingsStore.setThemeMode(mode) }
        }

        fun setDynamicColor(enabled: Boolean) {
            viewModelScope.launch { settingsStore.setDynamicColor(enabled) }
        }

        /**
         * Голос выключен по умолчанию: синтезатор читает санскритские названия
         * асан с чужими ударениями, и первое же занятие после установки не
         * должно этим огорошивать. Включается одним движением — когда
         * произношение этапов настроено (поле «Произношение» в редакторе этапа).
         */
        fun setVoiceEnabled(enabled: Boolean) {
            viewModelScope.launch { settingsStore.setVoiceEnabled(enabled) }
        }

        /**
         * «Показывать подсказки снова»: единственный способ вернуть совет об
         * энергосбережении, закрытый навсегда (docs/05-PLAY-DECLARATIONS.md §5).
         */
        fun restoreHints() {
            viewModelScope.launch {
                hintStore.reset()
                _hintsRestored.value = true
            }
        }

        fun acknowledgeHintsRestored() {
            _hintsRestored.value = false
        }
    }
