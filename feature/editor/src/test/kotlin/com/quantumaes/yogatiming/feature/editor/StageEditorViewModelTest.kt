package com.quantumaes.yogatiming.feature.editor

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.Stage
import com.quantumaes.yogatiming.domain.model.StageType
import com.quantumaes.yogatiming.domain.model.alert.AlertPreset
import com.quantumaes.yogatiming.domain.model.alert.AlertPresets
import com.quantumaes.yogatiming.feature.editor.stage.StageEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val PROFILE_ID = 1L
private const val STAGE_ID = 11L
private const val FIVE_MINUTES_SEC = 300

@OptIn(ExperimentalCoroutinesApi::class)
class StageEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository =
        FakeProfileRepository(
            listOf(
                Profile(
                    id = PROFILE_ID,
                    uuid = "uuid-1",
                    name = "Хатха 60 мин",
                    defaultAlertConfig = AlertPresets.standard(),
                    stages =
                        listOf(
                            Stage(
                                id = STAGE_ID,
                                profileId = PROFILE_ID,
                                name = "Разминка",
                                durationSec = FIVE_MINUTES_SEC,
                            ),
                        ),
                ),
            ),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(stageId: Long = STAGE_ID): StageEditorViewModel {
        val viewModel =
            StageEditorViewModel(
                repository,
                FakeAlertPlayer(),
                FakeSettingsStore(),
                SavedStateHandle(mapOf("profileId" to PROFILE_ID, "stageId" to stageId)),
            )
        testScheduler.runCurrent()
        return viewModel
    }

    @Test
    fun `произношение сохраняется отдельно от названия`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.setVoiceName("шав+асана")
            viewModel.save()
            testScheduler.runCurrent()

            val stage = requireNotNull(repository.getProfile(PROFILE_ID)).stages.first { it.id == STAGE_ID }
            assertThat(stage.name).isEqualTo("Разминка")
            assertThat(stage.voiceName).isEqualTo("шав+асана")
        }

    @Test
    fun `пустое произношение не сохраняется вовсе`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            // Пусто — «произносить как написано», а не «произносить пустоту».
            viewModel.setVoiceName("   ")
            viewModel.save()
            testScheduler.runCurrent()

            val stage = requireNotNull(repository.getProfile(PROFILE_ID)).stages.first { it.id == STAGE_ID }
            assertThat(stage.voiceName).isNull()
        }

    @Test
    fun `существующий этап читается в состояние`() =
        runTest(dispatcher) {
            val state = viewModel().uiState.value

            assertThat(state.isNew).isFalse()
            assertThat(state.name).isEqualTo("Разминка")
            assertThat(state.durationSec).isEqualTo(FIVE_MINUTES_SEC)
            assertThat(state.hasOwnAlerts).isFalse()
        }

    /**
     * Решение C-6: редактор предлагает тихий пресет, а не подменяет каналы
     * в рантайме. Предложение видно в состоянии и снимается одним действием.
     */
    @Test
    fun `тип отдыха подставляет тихий пресет`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.setType(StageType.REST)

            val state = viewModel.uiState.value
            assertThat(state.restPresetOffered).isTrue()
            assertThat(state.alertConfig?.preset).isEqualTo(AlertPreset.SILENT)
        }

    @Test
    fun `от тихого пресета можно отказаться`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.setType(StageType.REST)
            viewModel.declineRestPreset()

            val state = viewModel.uiState.value
            assertThat(state.alertConfig).isNull()
            assertThat(state.restPresetOffered).isFalse()
        }

    @Test
    fun `у этапа с собственным конфигом тихий пресет не навязывается`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.setOwnAlerts(true)
            testScheduler.runCurrent()

            viewModel.setType(StageType.REST)

            val state = viewModel.uiState.value
            assertThat(state.restPresetOffered).isFalse()
            assertThat(state.alertConfig?.preset).isEqualTo(AlertPreset.STANDARD)
        }

    @Test
    fun `свои оповещения начинаются с копии профильных`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.setOwnAlerts(true)
            testScheduler.runCurrent()

            assertThat(viewModel.uiState.value.alertConfig).isEqualTo(AlertPresets.standard())
        }

    @Test
    fun `свободный этап сохраняется с нулевой длительностью`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.setType(StageType.FREE)
            viewModel.save()
            testScheduler.runCurrent()

            val stage = requireNotNull(repository.getProfile(PROFILE_ID)).stages.first()
            assertThat(stage.type).isEqualTo(StageType.FREE)
            assertThat(stage.durationSec).isEqualTo(0)
        }

    @Test
    fun `слишком короткая длительность не сохраняется`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.setDuration(0)
            viewModel.save()
            testScheduler.runCurrent()

            assertThat(viewModel.uiState.value.durationErrorShown).isTrue()
            assertThat(
                repository
                    .getProfile(PROFILE_ID)
                    ?.stages
                    ?.first()
                    ?.durationSec,
            ).isEqualTo(FIVE_MINUTES_SEC)
        }

    @Test
    fun `этап без имени не сохраняется`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.setName("   ")
            viewModel.save()
            testScheduler.runCurrent()

            assertThat(viewModel.uiState.value.nameErrorShown).isTrue()
            assertThat(
                repository
                    .getProfile(PROFILE_ID)
                    ?.stages
                    ?.first()
                    ?.name,
            ).isEqualTo("Разминка")
        }

    @Test
    fun `новый этап дописывается в конец профиля`() =
        runTest(dispatcher) {
            val viewModel = viewModel(stageId = NEW_ENTITY_ID)

            viewModel.setName("Шавасана")
            viewModel.setDuration(FIVE_MINUTES_SEC)
            viewModel.save()
            testScheduler.runCurrent()

            val stages = requireNotNull(repository.getProfile(PROFILE_ID)).stages
            assertThat(stages.map { it.name }).containsExactly("Разминка", "Шавасана").inOrder()
        }

    /** Повторное сохранение обязано править тот же этап, а не создавать второй. */
    @Test
    fun `повторное сохранение нового этапа не удваивает его`() =
        runTest(dispatcher) {
            val viewModel = viewModel(stageId = NEW_ENTITY_ID)
            viewModel.setName("Шавасана")
            viewModel.setDuration(FIVE_MINUTES_SEC)

            viewModel.save()
            testScheduler.runCurrent()
            viewModel.setName("Шавасана 10")
            viewModel.save()
            testScheduler.runCurrent()

            val stages = requireNotNull(repository.getProfile(PROFILE_ID)).stages
            assertThat(stages.map { it.name }).containsExactly("Разминка", "Шавасана 10").inOrder()
        }
}
