package com.quantumaes.yogatiming.feature.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumaes.yogatiming.domain.alert.AlertPlayer
import com.quantumaes.yogatiming.domain.alert.AlertRequest
import com.quantumaes.yogatiming.domain.alert.VoiceStatus
import com.quantumaes.yogatiming.domain.hint.HintStore
import com.quantumaes.yogatiming.domain.model.alert.Alert
import com.quantumaes.yogatiming.domain.model.alert.AlertChannel
import com.quantumaes.yogatiming.domain.model.alert.AlertSound
import com.quantumaes.yogatiming.domain.model.alert.VoicePhrase
import com.quantumaes.yogatiming.domain.settings.AppSettings
import com.quantumaes.yogatiming.domain.settings.SettingsStore
import com.quantumaes.yogatiming.domain.settings.ThemeMode
import com.quantumaes.yogatiming.timer.engine.model.AlertTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** Что произносит проверка голоса — название этапа, самая частая фраза занятия. */
private const val VOICE_TEST_STAGE = "Шавасана"

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsStore: SettingsStore,
        private val hintStore: HintStore,
        private val alertPlayer: AlertPlayer,
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

        /**
         * Состояние голоса: по нему настройки предлагают доустановить языковой
         * пакет (отложено из Фазы 4 — показывать предложение было негде).
         */
        val voiceStatus: StateFlow<VoiceStatus> = alertPlayer.voiceStatus

        init {
            // Движок TTS поднимается при входе в настройки, а не при первой
            // проверке: узнать, что голоса нет, пользователь должен здесь,
            // а не посреди занятия. Ресурсы освобождает `AlertPlayer.stop()`
            // по окончании занятия — общий владелец у них один.
            alertPlayer.prepare()
        }

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

        fun setAlertVolume(percent: Int) {
            viewModelScope.launch { settingsStore.setAlertVolume(percent) }
        }

        fun setDuckMusic(enabled: Boolean) {
            viewModelScope.launch { settingsStore.setDuckMusicOnAlert(enabled) }
        }

        fun setSpeechRate(percent: Int) {
            viewModelScope.launch { settingsStore.setSpeechRate(percent) }
        }

        fun setKeepScreenOn(enabled: Boolean) {
            viewModelScope.launch { settingsStore.setKeepScreenOn(enabled) }
        }

        fun setAutoDim(enabled: Boolean) {
            viewModelScope.launch { settingsStore.setAutoDim(enabled) }
        }

        /**
         * Кнопка настроек на рабочем экране.
         *
         * Занятие от перехода не прерывается — отсчёт держит сервис, а не
         * экран. Выключатель нужен тем, кто кладёт телефон на общий стол:
         * лишняя дверь с рабочего экрана там ни к чему.
         */
        fun setSettingsFromSession(enabled: Boolean) {
            viewModelScope.launch { settingsStore.setSettingsFromSession(enabled) }
        }

        /**
         * Проверка звука и голоса прямо из настроек.
         *
         * Иначе громкость и скорость речи настраиваются вслепую: услышать
         * результат можно только начав занятие, а к тому моменту менять уже
         * поздно. Играет тот же тракт, что и на занятии, — значит, слышно
         * ровно то, что прозвучит.
         */
        fun previewSound() =
            play(
                Alert(
                    channels = setOf(AlertChannel.SOUND),
                    sound = AlertSound.SOFT_GONG,
                ),
            )

        fun previewVoice() =
            play(
                Alert(
                    channels = setOf(AlertChannel.VOICE),
                    sound = AlertSound.NONE,
                    voice = VoicePhrase.STAGE_NAME,
                ),
            )

        private fun play(alert: Alert) {
            alertPlayer.play(
                AlertRequest(
                    alert = alert,
                    trigger = AlertTrigger.START,
                    stageName = VOICE_TEST_STAGE,
                    nextStageName = null,
                ),
            )
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

        /** Пересмотр онбординга: флаг снимается, навигация уводит на первый слайд. */
        fun replayOnboarding() {
            viewModelScope.launch { settingsStore.setOnboardingCompleted(false) }
        }
    }
