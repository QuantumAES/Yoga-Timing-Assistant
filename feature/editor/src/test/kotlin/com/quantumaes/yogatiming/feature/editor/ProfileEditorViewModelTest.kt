package com.quantumaes.yogatiming.feature.editor

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.Stage
import com.quantumaes.yogatiming.feature.editor.profile.ProfileEditorEvent
import com.quantumaes.yogatiming.feature.editor.profile.ProfileEditorViewModel
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
private const val FIVE_MINUTES_SEC = 300

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository =
        FakeProfileRepository(
            listOf(
                Profile(
                    id = PROFILE_ID,
                    uuid = "uuid-1",
                    name = "Хатха 60 мин",
                    stages =
                        listOf(
                            Stage(id = 11, profileId = PROFILE_ID, name = "Разминка", durationSec = FIVE_MINUTES_SEC),
                            Stage(id = 12, profileId = PROFILE_ID, name = "Асаны", durationSec = FIVE_MINUTES_SEC),
                            Stage(id = 13, profileId = PROFILE_ID, name = "Шавасана", durationSec = FIVE_MINUTES_SEC),
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

    private fun TestScope.viewModel(profileId: Long = PROFILE_ID): ProfileEditorViewModel {
        val viewModel = ProfileEditorViewModel(repository, SavedStateHandle(mapOf("profileId" to profileId)))
        testScheduler.runCurrent()
        return viewModel
    }

    @Test
    fun `существующий профиль читается в состояние целиком`() =
        runTest(dispatcher) {
            val state = viewModel().uiState.value

            assertThat(state.isNew).isFalse()
            assertThat(state.name).isEqualTo("Хатха 60 мин")
            assertThat(state.stages.map { it.name })
                .containsExactly("Разминка", "Асаны", "Шавасана")
                .inOrder()
        }

    @Test
    fun `профиль без имени не сохраняется`() =
        runTest(dispatcher) {
            val viewModel = viewModel(profileId = NEW_ENTITY_ID)

            viewModel.save()
            testScheduler.runCurrent()

            assertThat(viewModel.uiState.value.nameErrorShown).isTrue()
            assertThat(repository.stored).hasSize(1)
        }

    @Test
    fun `новый профиль сохраняется с обрезанным именем`() =
        runTest(dispatcher) {
            val viewModel = viewModel(profileId = NEW_ENTITY_ID)

            viewModel.setName("  Виньяса 45  ")
            viewModel.save()
            testScheduler.runCurrent()

            assertThat(repository.stored.map { it.name }).contains("Виньяса 45")
        }

    @Test
    fun `перестановка меняет порядок этапов`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.moveStage(0, 2)

            assertThat(
                viewModel.uiState.value.stages
                    .map { it.name },
            ).containsExactly("Асаны", "Шавасана", "Разминка")
                .inOrder()
        }

    @Test
    fun `перестановка за пределы списка игнорируется`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.moveStage(0, -1)
            viewModel.moveStage(0, 99)

            assertThat(
                viewModel.uiState.value.stages
                    .map { it.name },
            ).containsExactly("Разминка", "Асаны", "Шавасана")
                .inOrder()
        }

    @Test
    fun `порядок этапов попадает в базу при сохранении`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.moveStage(2, 0)
            viewModel.save()
            testScheduler.runCurrent()

            val saved = requireNotNull(repository.getProfile(PROFILE_ID))
            assertThat(saved.stages.map { it.name })
                .containsExactly("Шавасана", "Разминка", "Асаны")
                .inOrder()
            assertThat(saved.stages.map { it.sortOrder }).containsExactly(0, 1, 2).inOrder()
        }

    @Test
    fun `удалённый этап исчезает после сохранения`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.removeStage(12)
            viewModel.save()
            testScheduler.runCurrent()

            assertThat(repository.getProfile(PROFILE_ID)?.stages?.map { it.name })
                .containsExactly("Разминка", "Шавасана")
                .inOrder()
        }

    /**
     * Переход к этапу возможен только у сохранённого профиля: у несохранённого
     * нет идентификатора, а придумывать временный — значит завести вторую
     * модель хранения ради одного перехода.
     */
    @Test
    fun `переход к новому этапу сначала сохраняет профиль`() =
        runTest(dispatcher) {
            val viewModel = viewModel(profileId = NEW_ENTITY_ID)
            viewModel.setName("Виньяса 45")

            viewModel.uiEvents.test {
                viewModel.openStage()
                testScheduler.runCurrent()

                val event = awaitItem()
                assertThat(event).isInstanceOf(ProfileEditorEvent.OpenStage::class.java)
                val opened = event as ProfileEditorEvent.OpenStage
                assertThat(opened.stageId).isEqualTo(NEW_ENTITY_ID)
                assertThat(repository.getProfile(opened.profileId)?.name).isEqualTo("Виньяса 45")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `дублирование этапа сохраняется и получает собственный идентификатор`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.duplicateStage(11)
            testScheduler.runCurrent()

            val stages = requireNotNull(repository.getProfile(PROFILE_ID)).stages
            assertThat(stages.map { it.name })
                .containsExactly("Разминка", "Разминка", "Асаны", "Шавасана")
                .inOrder()
            assertThat(stages.map { it.id }.toSet()).hasSize(stages.size)
        }
}
