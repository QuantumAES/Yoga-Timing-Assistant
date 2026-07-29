package com.quantumaes.yogatiming.feature.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.domain.hint.Hint
import com.quantumaes.yogatiming.domain.hint.HintStore
import com.quantumaes.yogatiming.domain.settings.AppSettings
import com.quantumaes.yogatiming.domain.settings.SettingsStore
import com.quantumaes.yogatiming.domain.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Хранилище настроек в памяти: весь контракт — один поток и два сеттера. */
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settingsStore = FakeSettingsStore()
    private val hintStore = FakeHintStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(settingsStore, hintStore)

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
