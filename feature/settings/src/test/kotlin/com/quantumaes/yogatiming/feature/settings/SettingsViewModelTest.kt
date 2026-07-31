package com.quantumaes.yogatiming.feature.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.domain.alert.AlertPlayer
import com.quantumaes.yogatiming.domain.alert.AlertRequest
import com.quantumaes.yogatiming.domain.alert.VoiceStatus
import com.quantumaes.yogatiming.domain.hint.Hint
import com.quantumaes.yogatiming.domain.hint.HintStore
import com.quantumaes.yogatiming.domain.model.alert.AlertChannel
import com.quantumaes.yogatiming.domain.settings.AppSettings
import com.quantumaes.yogatiming.domain.settings.SettingsStore
import com.quantumaes.yogatiming.domain.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Хранилище настроек в памяти: весь контракт — один поток и сеттеры. */
private class FakeSettingsStore(
    initial: AppSettings = AppSettings(),
) : SettingsStore {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = state.value.copy(themeMode = mode)
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        state.value = state.value.copy(dynamicColor = enabled)
    }

    override suspend fun setVoiceEnabled(enabled: Boolean) {
        state.value = state.value.copy(voiceEnabled = enabled)
    }

    override suspend fun setAlertVolume(percent: Int) {
        state.value = state.value.copy(alertVolumePercent = percent)
    }

    override suspend fun setDuckMusicOnAlert(enabled: Boolean) {
        state.value = state.value.copy(duckMusicOnAlert = enabled)
    }

    override suspend fun setSpeechRate(percent: Int) {
        state.value = state.value.copy(speechRatePercent = percent)
    }

    override suspend fun setKeepScreenOn(enabled: Boolean) {
        state.value = state.value.copy(keepScreenOn = enabled)
    }

    override suspend fun setAutoDim(enabled: Boolean) {
        state.value = state.value.copy(autoDimEnabled = enabled)
    }

    override suspend fun setSettingsFromSession(enabled: Boolean) {
        state.value = state.value.copy(settingsFromSession = enabled)
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        state.value = state.value.copy(onboardingCompleted = completed)
    }
}

/** Проигрыватель, который только считает, сколько раз его просили сыграть. */
private class FakeAlertPlayer : AlertPlayer {
    val played = mutableListOf<AlertRequest>()

    override val voiceStatus = MutableStateFlow(VoiceStatus.READY)

    override fun prepare() = Unit

    override fun play(request: AlertRequest) {
        played += request
    }

    override fun stopCustomSound() = Unit

    override fun stop() = Unit
}

private class FakeHintStore : HintStore {
    var resetCalls = 0
        private set

    override suspend fun isDismissed(hint: Hint): Boolean = true

    override suspend fun dismiss(hint: Hint) = Unit

    override suspend fun reset() {
        resetCalls++
    }
}

/** Допуск сравнения долей единицы: проценты делятся на сто без сюрпризов, но float есть float. */
private const val TOLERANCE = 0.0001f

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settingsStore = FakeSettingsStore()
    private val hintStore = FakeHintStore()
    private val alertPlayer = FakeAlertPlayer()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(settingsStore, hintStore, alertPlayer)

    @Test
    fun `по умолчанию голос выключен`() =
        runTest(dispatcher) {
            // Синтезатор читает санскритские названия асан с чужими ударениями,
            // и первое занятие после установки не должно этим огорошивать.
            viewModel().settings.test {
                assertThat(awaitItem().voiceEnabled).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `включение голоса уходит в хранилище`() =
        runTest(dispatcher) {
            val model = viewModel()
            model.settings.test {
                awaitItem()
                model.setVoiceEnabled(true)
                assertThat(awaitItem().voiceEnabled).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * Полевая проверка 2026-07-31, замечание 6: с занятия должен быть путь
     * в настройки — и выключатель для тех, кому он не нужен.
     */
    @Test
    fun `настройки с рабочего экрана разрешены по умолчанию и выключаются`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.settings.test {
                assertThat(awaitItem().settingsFromSession).isTrue()

                viewModel.setSettingsFromSession(false)

                assertThat(awaitItem().settingsFromSession).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `по умолчанию тема системная`() =
        runTest(dispatcher) {
            viewModel().settings.test {
                assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.SYSTEM)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `выбор темы уходит в хранилище и возвращается в состояние`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.settings.test {
                assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.SYSTEM)

                viewModel.setThemeMode(ThemeMode.LIGHT)

                assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.LIGHT)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `динамические цвета переключаются независимо от темы`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.settings.test {
                awaitItem()

                viewModel.setDynamicColor(true)

                val updated = awaitItem()
                assertThat(updated.dynamicColor).isTrue()
                assertThat(updated.themeMode).isEqualTo(ThemeMode.SYSTEM)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `громкость и скорость речи не выходят за границы хранилища`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.settings.test {
                awaitItem()

                viewModel.setAlertVolume(50)
                assertThat(awaitItem().alertVolumePercent).isEqualTo(50)

                viewModel.setSpeechRate(80)
                val updated = awaitItem()
                assertThat(updated.speechRatePercent).isEqualTo(80)
                assertThat(updated.speechRate).isWithin(TOLERANCE).of(0.8f)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `проверка голоса играет через тот же тракт, что и занятие`() =
        runTest(dispatcher) {
            // Настраивать скорость речи вслепую нельзя: услышать результат
            // иначе можно только посреди занятия.
            viewModel().previewVoice()

            val request = alertPlayer.played.single()
            assertThat(request.alert.channels).containsExactly(AlertChannel.VOICE)
            assertThat(request.stageName).isNotEmpty()
        }

    @Test
    fun `пересмотр онбординга снимает флаг`() =
        runTest(dispatcher) {
            // Проверяется хранилище, а не поток состояния: `StateFlow`
            // схлопывает одинаковые значения, и «было false → стало true →
            // снова false» подписчику видно не всегда.
            settingsStore.setOnboardingCompleted(true)

            viewModel().replayOnboarding()
            testScheduler.runCurrent()

            assertThat(settingsStore.settings.first().onboardingCompleted).isFalse()
        }

    @Test
    fun `возврат подсказок сбрасывает хранилище и подтверждается один раз`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.restoreHints()
            testScheduler.runCurrent()

            assertThat(hintStore.resetCalls).isEqualTo(1)
            assertThat(viewModel.hintsRestored.value).isTrue()

            viewModel.acknowledgeHintsRestored()

            assertThat(viewModel.hintsRestored.value).isFalse()
        }
}
