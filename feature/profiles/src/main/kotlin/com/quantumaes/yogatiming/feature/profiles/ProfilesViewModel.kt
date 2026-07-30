package com.quantumaes.yogatiming.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumaes.yogatiming.domain.model.NEW_ID
import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.ProfileCategory
import com.quantumaes.yogatiming.domain.model.ProfileSummary
import com.quantumaes.yogatiming.domain.repository.ProfileFilter
import com.quantumaes.yogatiming.domain.repository.ProfileRepository
import com.quantumaes.yogatiming.timer.service.ActiveSessionSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Пережить поворот экрана, но не держать подписку в фоне. */
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/**
 * Состояние экрана списка профилей.
 *
 * Условия отбора живут здесь же, а не только в репозитории: пустой список
 * означает разное в зависимости от того, есть ли активный фильтр, и экран
 * обязан различать «профилей нет» и «ничего не нашлось».
 */
data class ProfilesUiState(
    val profiles: List<ProfileSummary> = emptyList(),
    val query: String = "",
    val category: ProfileCategory? = null,
    val favoritesOnly: Boolean = false,
    val isLoading: Boolean = true,
    /** Занятие, которое идёт прямо сейчас; `null` — не идёт никакого. */
    val activeSession: ActiveSession? = null,
) {
    val hasFilter: Boolean get() = query.isNotBlank() || category != null || favoritesOnly

    val isEmpty: Boolean get() = !isLoading && profiles.isEmpty()

    /** Профиль запущенного занятия: его нельзя ни править, ни удалять. */
    val activeProfileId: Long? get() = activeSession?.profileId

    fun isRunning(profileId: Long): Boolean = profileId == activeProfileId
}

/** Одноразовые сообщения экрану: то, что нельзя выразить состоянием. */
sealed interface ProfilesEvent {
    /** Профиль удалён; пока показывается снекбар, удаление можно отменить. */
    data class Deleted(
        val name: String,
    ) : ProfilesEvent

    /**
     * Действие отклонено: профиль занят идущим занятием.
     *
     * Правка запущенного профиля меняет план у него под ногами: движок получил
     * план при старте и второй раз его не собирает (docs/02-TIMER-CORE-DESIGN.md
     * §3.1). Удаление — тем более: восстанавливать после смерти процесса станет
     * нечего. Поэтому сначала «Стоп», потом правка.
     */
    data object BlockedByRunningSession : ProfilesEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfilesViewModel
    @Inject
    constructor(
        private val repository: ProfileRepository,
        session: ActiveSessionSource,
    ) : ViewModel() {
        private val filter = MutableStateFlow(ProfileFilter())

        /**
         * Снапшот идущего занятия — напрямую из синглтона, как и на рабочем
         * экране: движок и интерфейс живут в одном процессе, второго состояния,
         * способного с ним разойтись, в системе нет. Список читает занятие, но
         * не управляет им — отсюда узкий контракт вместо контроллера целиком.
         */
        private val activeSession = session.snapshot

        /**
         * Отменённое удаление держится в памяти целиком, вместе с этапами.
         *
         * Удаление выполняется сразу, а не откладывается на время показа
         * снекбара: отложенное удаление теряется, если пользователь успеет уйти
         * с экрана, и тогда профиль, который он считал удалённым, возвращается
         * сам собой. Восстановление создаёт новую строку с прежним `uuid` —
         * настоящий идентификатор профиля именно он, а не номер строки (P1-7).
         */
        private var lastDeleted: Profile? = null

        private val events = Channel<ProfilesEvent>(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
        val uiEvents: Flow<ProfilesEvent> = events.receiveAsFlow()

        /**
         * Читается лёгкая проекция (P1-5): этапы и конфиги оповещений на этом
         * экране не нужны, а тянулись бы при каждом обновлении.
         */
        val uiState: StateFlow<ProfilesUiState> =
            combine(
                filter,
                filter.flatMapLatest(repository::observeProfileSummaries),
                activeSession,
            ) { current, profiles, session ->
                ProfilesUiState(
                    profiles = profiles,
                    query = current.query,
                    category = current.category,
                    favoritesOnly = current.favoritesOnly,
                    isLoading = false,
                    activeSession = session.toActiveSession(),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = ProfilesUiState(),
            )

        fun setQuery(query: String) = filter.update { it.copy(query = query) }

        /** Повторный выбор той же категории снимает фильтр — так ведут себя чипы M3. */
        fun toggleCategory(category: ProfileCategory) =
            filter.update { it.copy(category = if (it.category == category) null else category) }

        fun toggleFavoritesOnly() = filter.update { it.copy(favoritesOnly = !it.favoritesOnly) }

        fun clearFilters() = filter.update { ProfileFilter() }

        fun setFavorite(
            profileId: Long,
            isFavorite: Boolean,
        ) {
            viewModelScope.launch { repository.setFavorite(profileId, isFavorite) }
        }

        /**
         * @param copyName имя копии приходит с экрана: «— копия» переводится
         *   вместе с интерфейсом, а слой данных строк из ресурсов не читает.
         */
        fun duplicate(
            profileId: Long,
            copyName: String,
        ) {
            viewModelScope.launch { repository.duplicateProfile(profileId, copyName) }
        }

        /**
         * Правку запрещает модель, а не экран: свайп, меню и карточка ведут
         * к одному и тому же действию, и правило должно быть одно на всех.
         *
         * @return `false` — профиль занят идущим занятием, переход не состоится.
         */
        fun requestEdit(profileId: Long): Boolean {
            if (!uiState.value.isRunning(profileId)) return true
            events.trySend(ProfilesEvent.BlockedByRunningSession)
            return false
        }

        fun delete(profileId: Long) {
            if (uiState.value.isRunning(profileId)) {
                events.trySend(ProfilesEvent.BlockedByRunningSession)
                return
            }
            viewModelScope.launch {
                val snapshot = repository.getProfile(profileId) ?: return@launch
                lastDeleted = snapshot
                repository.deleteProfile(profileId)
                events.trySend(ProfilesEvent.Deleted(snapshot.name))
            }
        }

        /** Отмена удаления. Повторный вызов ничего не делает — восстанавливать уже нечего. */
        fun undoDelete() {
            val restored = lastDeleted ?: return
            lastDeleted = null
            viewModelScope.launch {
                repository.saveProfile(
                    restored.copy(
                        id = NEW_ID,
                        stages = restored.stages.map { it.copy(id = NEW_ID, profileId = NEW_ID) },
                    ),
                )
            }
        }
    }
