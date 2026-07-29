package com.quantumaes.yogatiming.feature.profiles

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.ProfileCategory
import com.quantumaes.yogatiming.domain.model.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
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
        val viewModel = ProfilesViewModel(repository)
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
