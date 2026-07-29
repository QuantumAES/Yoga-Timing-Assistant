package com.quantumaes.yogatiming.feature.editor

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.Stage
import com.quantumaes.yogatiming.domain.model.StageType
import com.quantumaes.yogatiming.domain.model.alert.AlertChannel
import com.quantumaes.yogatiming.domain.model.alert.AlertPreset
import com.quantumaes.yogatiming.domain.model.alert.AlertPresets
import com.quantumaes.yogatiming.domain.model.alert.AlertSound
import com.quantumaes.yogatiming.feature.editor.alert.AlertConfigViewModel
import com.quantumaes.yogatiming.feature.editor.alert.toggleChannel
import com.quantumaes.yogatiming.timer.engine.model.AlertTrigger
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
private const val NINETY_SECONDS = 90
private const val TWO_MINUTES_SEC = 120

@OptIn(ExperimentalCoroutinesApi::class)
class AlertConfigViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val player = FakeAlertPlayer()
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
                                durationSec = NINETY_SECONDS,
                                alertConfig = AlertPresets.standard(),
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

    private fun TestScope.viewModel(stageId: Long = NEW_ENTITY_ID): AlertConfigViewModel {
        val viewModel =
            AlertConfigViewModel(
                repository,
                player,
                SavedStateHandle(mapOf("profileId" to PROFILE_ID, "stageId" to stageId)),
            )
        testScheduler.runCurrent()
        return viewModel
    }

    @Test
    fun `без этапа редактируется конфиг профиля`() =
        runTest(dispatcher) {
            val state = viewModel().uiState.value

            assertThat(state.isStageScope).isFalse()
            assertThat(state.ownerName).isEqualTo("Хатха 60 мин")
            assertThat(state.config).isEqualTo(AlertPresets.standard())
        }

    @Test
    fun `с этапом редактируется конфиг этапа`() =
        runTest(dispatcher) {
            val state = viewModel(stageId = STAGE_ID).uiState.value

            assertThat(state.isStageScope).isTrue()
            assertThat(state.ownerName).isEqualTo("Разминка")
        }

    @Test
    fun `пресет заменяет набор целиком`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.applyPreset(AlertPreset.SILENT)

            assertThat(viewModel.uiState.value.config).isEqualTo(AlertPresets.silent())
        }

    /** Набор, поправленный вручную, перестал быть «Стандартом» — и не должен так называться. */
    @Test
    fun `ручная правка переводит пресет в CUSTOM`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.updateStart { it.copy(sound = AlertSound.BELL) }

            val config = viewModel.uiState.value.config
            assertThat(config.preset).isEqualTo(AlertPreset.CUSTOM)
            assertThat(config.start?.sound).isEqualTo(AlertSound.BELL)
        }

    @Test
    fun `канал переключается независимо от остальных`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.updateStart { it.toggleChannel(AlertChannel.VIBRATION) }

            val start = requireNotNull(viewModel.uiState.value.config.start)
            assertThat(start.channels)
                .containsExactly(AlertChannel.SOUND, AlertChannel.VOICE, AlertChannel.VIBRATION)
        }

    @Test
    fun `выключенный триггер сохраняет свои настройки`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.updateEnd { it.copy(sound = AlertSound.BELL) }
            viewModel.setTriggerEnabled(AlertTrigger.END, false)
            viewModel.setTriggerEnabled(AlertTrigger.END, true)

            val end = requireNotNull(viewModel.uiState.value.config.end)
            assertThat(end.enabled).isTrue()
            assertThat(end.sound).isEqualTo(AlertSound.BELL)
        }

    /**
     * Движок различает предупреждения одного этапа по смещению: два «за минуту»
     * стали бы одним непредсказуемым (`SessionPlanFactory.resolve`).
     */
    @Test
    fun `новое предупреждение не повторяет существующее смещение`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.addWarning()
            viewModel.addWarning()

            val offsets =
                viewModel.uiState.value.config.warnings
                    .map { it.offsetSec }
            assertThat(offsets).hasSize(offsets.toSet().size)
        }

    @Test
    fun `смещение, занятое другим предупреждением, не принимается`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.setWarningOffset(TWO_MINUTES_SEC, newOffsetSec = 60)

            val offsets =
                viewModel.uiState.value.config.warnings
                    .map { it.offsetSec }
            assertThat(offsets).containsExactly(TWO_MINUTES_SEC, 60)
        }

    @Test
    fun `удаление предупреждения убирает его из набора`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.removeWarning(TWO_MINUTES_SEC)

            assertThat(
                viewModel.uiState.value.config.warnings
                    .map { it.offsetSec },
            ).containsExactly(60)
        }

    /** Решение B-7: предупреждение длиннее этапа движок пропустит молча. */
    @Test
    fun `недостижимое предупреждение помечено на этапе короче смещения`() =
        runTest(dispatcher) {
            val state = viewModel(stageId = STAGE_ID).uiState.value

            val twoMinutes = state.warnings.first { it.offsetSec == TWO_MINUTES_SEC }
            val oneMinute = state.warnings.first { it.offsetSec == 60 }
            assertThat(state.isWarningUnreachable(twoMinutes)).isTrue()
            assertThat(state.isWarningUnreachable(oneMinute)).isFalse()
        }

    @Test
    fun `у свободного этапа предупреждения не считаются недостижимыми`() =
        runTest(dispatcher) {
            repository.saveProfile(
                requireNotNull(repository.getProfile(PROFILE_ID)).let { profile ->
                    profile.copy(stages = profile.stages.map { it.copy(type = StageType.FREE, durationSec = 0) })
                },
            )

            val state = viewModel(stageId = STAGE_ID).uiState.value

            assertThat(state.isFreeStage).isTrue()
            assertThat(state.isWarningUnreachable(state.warnings.first())).isFalse()
        }

    @Test
    fun `сохранение кладёт конфиг профилю`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.applyPreset(AlertPreset.VIBRO_ONLY)
            viewModel.save()
            testScheduler.runCurrent()

            assertThat(repository.getProfile(PROFILE_ID)?.defaultAlertConfig)
                .isEqualTo(AlertPresets.vibrationOnly())
        }

    @Test
    fun `сохранение кладёт конфиг этапу, не трогая профиль`() =
        runTest(dispatcher) {
            val viewModel = viewModel(stageId = STAGE_ID)

            viewModel.applyPreset(AlertPreset.SILENT)
            viewModel.save()
            testScheduler.runCurrent()

            val profile = requireNotNull(repository.getProfile(PROFILE_ID))
            assertThat(profile.stages.first().alertConfig).isEqualTo(AlertPresets.silent())
            assertThat(profile.defaultAlertConfig).isEqualTo(AlertPresets.standard())
        }

    /**
     * Предпрослушивание прогревает проигрыватель, но никогда его не
     * останавливает: он общий с сервисом занятия.
     */
    @Test
    fun `предпрослушивание играет с унаследованной громкостью и не глушит проигрыватель`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val start = requireNotNull(viewModel.uiState.value.config.start)

            viewModel.preview(start, AlertTrigger.START)

            assertThat(player.prepared).isEqualTo(1)
            assertThat(player.stopped).isEqualTo(0)
            assertThat(
                player.played
                    .single()
                    .alert.volumePercent,
            ).isEqualTo(AlertPresets.standard().masterVolumePercent)
        }
}
