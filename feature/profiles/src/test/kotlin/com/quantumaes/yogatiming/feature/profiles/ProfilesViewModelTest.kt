package com.quantumaes.yogatiming.feature.profiles

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.ProfileCategory
import com.quantumaes.yogatiming.domain.model.Stage
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionSnapshot
import com.quantumaes.yogatiming.timer.engine.model.StageKind
import com.quantumaes.yogatiming.timer.service.ActiveSessionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val TEN_MINUTES_SEC = 600

/** Идущее занятие в тесте — один поток, который тест сам и заполняет. */
private class FakeActiveSessionSource : ActiveSessionSource {
    private val state = MutableStateFlow<SessionSnapshot?>(null)

    override val snapshot: StateFlow<SessionSnapshot?> = state

    fun run(
        profileId: Long,
        runState: RunState = RunState.RUNNING,
    ) {
        state.value = snapshotOf(profileId, runState)
    }

    fun clear() {
        state.value = null
    }
}

private fun snapshotOf(
    profileId: Long,
    runState: RunState,
) = SessionSnapshot(
    profileId = profileId,
    profileName = "Хатха 60 мин",
    runState = runState,
    currentIndex = 2,
    stageCount = 6,
    currentStageName = "Асаны стоя",
    currentStageColor = "#4CAF50",
    currentStageKind = StageKind.NORMAL,
    currentNote = null,
    stageRemainingMs = 754_000,
    stageElapsedMs = 46_000,
    stageDurationMs = 800_000,
    stageProgress = 0.05f,
    stageAdjustmentMs = 0,
    totalElapsedMs = 46_000,
    totalRemainingMs = 3_554_000,
    totalRemainingIsLowerBound = false,
    totalProgress = 0.01f,
    nextStageName = "Балансы",
    nextStageDurationMs = 720_000,
    isLastStage = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val session = FakeActiveSessionSource()
    private val repository =
        FakeProfileRepository(
            listOf(
                profile(id = 1, name = "Хатха 60 мин", category = ProfileCategory.HATHA, favorite = true),
                profile(id = 2, name = "Инь-йога 90 мин", category = ProfileCategory.YIN),
                profile(id = 3, name = "Медитация 20 мин", category = ProfileCategory.MEDITATION),
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

    /**
     * Состояние живёт под `WhileSubscribed`, поэтому без подписчика оно не
     * обновляется вовсе. Тест держит подписку и читает устоявшееся значение,
     * а не считает эмиссии: их количество — деталь реализации `combine`,
     * а проверять надо результат.
     */
    private fun TestScope.viewModel(): ProfilesViewModel {
        val viewModel = ProfilesViewModel(repository, session)
        subscribe(backgroundScope, viewModel)
        testScheduler.runCurrent()
        return viewModel
    }

    private fun subscribe(
        scope: CoroutineScope,
        viewModel: ProfilesViewModel,
    ) {
        scope.launch { viewModel.uiState.collect { } }
    }

    @Test
    fun `сначала показываются все профили`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            val state = viewModel.uiState.value
            assertThat(state.isLoading).isFalse()
            assertThat(state.profiles).hasSize(3)
            assertThat(state.hasFilter).isFalse()
        }

    @Test
    fun `поиск отбирает по названию`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.setQuery("инь")
            testScheduler.runCurrent()

            val state = viewModel.uiState.value
            assertThat(state.profiles.map { it.name }).containsExactly("Инь-йога 90 мин")
            assertThat(state.hasFilter).isTrue()
        }

    @Test
    fun `повторный выбор категории снимает фильтр`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.toggleCategory(ProfileCategory.YIN)
            testScheduler.runCurrent()
            assertThat(viewModel.uiState.value.profiles).hasSize(1)

            viewModel.toggleCategory(ProfileCategory.YIN)
            testScheduler.runCurrent()

            val state = viewModel.uiState.value
            assertThat(state.profiles).hasSize(3)
            assertThat(state.category).isNull()
        }

    @Test
    fun `фильтр избранного оставляет только избранные`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.toggleFavoritesOnly()
            testScheduler.runCurrent()

            assertThat(
                viewModel.uiState.value.profiles
                    .map { it.name },
            ).containsExactly("Хатха 60 мин")
        }

    @Test
    fun `сброс фильтров возвращает весь список`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.setQuery("инь")
            viewModel.toggleFavoritesOnly()
            testScheduler.runCurrent()
            assertThat(viewModel.uiState.value.profiles).isEmpty()

            viewModel.clearFilters()
            testScheduler.runCurrent()

            val state = viewModel.uiState.value
            assertThat(state.profiles).hasSize(3)
            assertThat(state.hasFilter).isFalse()
        }

    @Test
    fun `удаление сообщает экрану имя удалённого профиля`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.uiEvents.test {
                viewModel.delete(2)
                testScheduler.runCurrent()

                assertThat(awaitItem()).isEqualTo(ProfilesEvent.Deleted("Инь-йога 90 мин"))
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(repository.stored.map { it.id }).containsExactly(1L, 3L)
        }

    /**
     * Восстановленный профиль получает новую строку, но прежний `uuid`:
     * настоящий идентификатор профиля именно он (P1-7).
     */
    @Test
    fun `отмена удаления возвращает профиль вместе с этапами и uuid`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val original = requireNotNull(repository.getProfile(2))

            viewModel.delete(2)
            testScheduler.runCurrent()
            viewModel.undoDelete()
            testScheduler.runCurrent()

            val restored = repository.stored.first { it.uuid == original.uuid }
            assertThat(restored.name).isEqualTo(original.name)
            assertThat(restored.stages.map { it.name }).isEqualTo(original.stages.map { it.name })
        }

    @Test
    fun `повторная отмена ничего не восстанавливает`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.delete(2)
            testScheduler.runCurrent()
            viewModel.undoDelete()
            testScheduler.runCurrent()
            viewModel.undoDelete()
            testScheduler.runCurrent()

            assertThat(repository.stored).hasSize(3)
        }

    @Test
    fun `дублирование создаёт копию с переданным именем и без избранного`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.duplicate(1, "Хатха 60 мин — копия")
            testScheduler.runCurrent()

            val copy = repository.stored.first { it.name == "Хатха 60 мин — копия" }
            assertThat(copy.isFavorite).isFalse()
            assertThat(copy.uuid).isNotEqualTo(repository.getProfile(1)?.uuid)
        }

    @Test
    fun `идущее занятие видно в состоянии списка`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            session.run(profileId = 2)
            testScheduler.runCurrent()

            val active = viewModel.uiState.value.activeSession
            assertThat(active?.profileId).isEqualTo(2)
            assertThat(active?.stageNumber).isEqualTo(3)
            assertThat(active?.stageCount).isEqualTo(6)
            assertThat(active?.paused).isFalse()
        }

    @Test
    fun `завершённое занятие идущим не считается`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            session.run(profileId = 2, runState = RunState.FINISHED)
            testScheduler.runCurrent()

            assertThat(viewModel.uiState.value.activeSession).isNull()
        }

    @Test
    fun `запущенный профиль не открывается на правку`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            session.run(profileId = 2)
            testScheduler.runCurrent()

            viewModel.uiEvents.test {
                assertThat(viewModel.requestEdit(2)).isFalse()
                assertThat(awaitItem()).isEqualTo(ProfilesEvent.BlockedByRunningSession)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `остальные профили правятся и во время занятия`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            session.run(profileId = 2)
            testScheduler.runCurrent()

            assertThat(viewModel.requestEdit(1)).isTrue()
        }

    @Test
    fun `запущенный профиль не удаляется`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            session.run(profileId = 2)
            testScheduler.runCurrent()

            viewModel.delete(2)
            testScheduler.runCurrent()

            // Удалить профиль под идущим занятием — значит лишить сессию плана,
            // по которому её восстанавливают после смерти процесса.
            assertThat(repository.stored.map { it.id }).contains(2L)
        }

    @Test
    fun `остановленное занятие снимает запрет`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            session.run(profileId = 2)
            testScheduler.runCurrent()
            session.clear()
            testScheduler.runCurrent()

            assertThat(viewModel.requestEdit(2)).isTrue()
        }
}

private fun profile(
    id: Long,
    name: String,
    category: ProfileCategory,
    favorite: Boolean = false,
) = Profile(
    id = id,
    uuid = "uuid-$id",
    name = name,
    category = category,
    isFavorite = favorite,
    stages =
        listOf(
            Stage(id = id * 10, profileId = id, name = "Разминка", durationSec = TEN_MINUTES_SEC, sortOrder = 0),
            Stage(id = id * 10 + 1, profileId = id, name = "Шавасана", durationSec = TEN_MINUTES_SEC, sortOrder = 1),
        ),
)
