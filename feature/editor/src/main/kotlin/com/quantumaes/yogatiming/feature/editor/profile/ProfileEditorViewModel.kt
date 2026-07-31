package com.quantumaes.yogatiming.feature.editor.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumaes.yogatiming.domain.model.DEFAULT_COLOR_TAG
import com.quantumaes.yogatiming.domain.model.NEW_ID
import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.ProfileCategory
import com.quantumaes.yogatiming.domain.model.Stage
import com.quantumaes.yogatiming.domain.model.TotalDurationMode
import com.quantumaes.yogatiming.domain.model.alert.AlertConfig
import com.quantumaes.yogatiming.domain.model.alert.AlertPresets
import com.quantumaes.yogatiming.domain.repository.ProfileRepository
import com.quantumaes.yogatiming.feature.editor.NEW_ENTITY_ID
import com.quantumaes.yogatiming.feature.editor.RouteArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Поля профиля в том виде, в каком они уходят в базу.
 *
 * Нужен ровно для одного вопроса — менялось ли что-нибудь с последней записи
 * (полевая проверка 2026-07-31, замечание 8). Флага «грязно» вместо снимка
 * недостаточно: правку легко вернуть обратно руками, и спрашивать после этого
 * не о чем.
 */
data class ProfileForm(
    val name: String = "",
    val category: ProfileCategory = ProfileCategory.DEFAULT,
    val colorTag: String = DEFAULT_COLOR_TAG,
    val isFavorite: Boolean = false,
    val stages: List<Stage> = emptyList(),
)

/**
 * Состояние редактора профиля.
 *
 * Этапы лежат здесь же, а не читаются потоком напрямую: их порядок правится
 * перетаскиванием, и промежуточные состояния перестановки не должны ходить
 * в базу на каждый кадр.
 */
data class ProfileEditorUiState(
    val isNew: Boolean = true,
    val isLoading: Boolean = true,
    val name: String = "",
    val category: ProfileCategory = ProfileCategory.DEFAULT,
    val colorTag: String = DEFAULT_COLOR_TAG,
    val isFavorite: Boolean = false,
    val stages: List<Stage> = emptyList(),
    /** Показывать ли ошибку пустого имени. До первой попытки сохранить — нет. */
    val nameErrorShown: Boolean = false,
    /** Снимок последней записи в базу. Пустой у профиля, которого там ещё нет. */
    val savedForm: ProfileForm = ProfileForm(),
) {
    val form: ProfileForm get() = ProfileForm(name.trim(), category, colorTag, isFavorite, stages)

    val hasUnsavedChanges: Boolean get() = form != savedForm

    val isNameValid: Boolean get() = name.isNotBlank()

    /** Профиль без этапов запустить нельзя (решение B-6) — но сохранить можно. */
    val isRunnable: Boolean get() = stages.isNotEmpty()

    val totalDurationSec: Int get() = stages.filter { it.hasPlannedDuration }.sumOf { it.durationSec }

    val hasFreeStages: Boolean get() = stages.any { !it.hasPlannedDuration }
}

/**
 * Куда уйти после того, как профиль получил идентификатор.
 *
 * Экраны этапа и оповещений работают с сохранённым профилем: у несохранённого
 * нет идентификатора, а придумывать временный — значит завести вторую модель
 * хранения ради одного перехода.
 */
sealed interface ProfileEditorEvent {
    data class OpenStage(
        val profileId: Long,
        val stageId: Long,
    ) : ProfileEditorEvent

    data class OpenAlerts(
        val profileId: Long,
    ) : ProfileEditorEvent

    data object Saved : ProfileEditorEvent
}

@HiltViewModel
class ProfileEditorViewModel
    @Inject
    constructor(
        private val repository: ProfileRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        /** Идентификатор растёт из `NEW_ID` в настоящий при первом сохранении. */
        private var profileId: Long =
            savedStateHandle
                .get<Long>(RouteArgs.PROFILE_ID)
                ?.takeIf { it != NEW_ENTITY_ID }
                ?: NEW_ID

        /**
         * Поля профиля, которых на этом экране нет.
         *
         * Их приходится помнить, потому что `saveProfile` пишет профиль целиком:
         * собрать `Profile` из одних лишь полей формы — значит затереть всё
         * остальное значениями по умолчанию. Именно так и терялись оповещения
         * по умолчанию, настроенные на соседнем экране: возврат в редактор и
         * любое сохранение возвращали профилю «Стандарт».
         */
        private var uuid: String = UUID.randomUUID().toString()
        private var createdAt: Long = 0
        private var sortOrder: Int = 0
        private var iconId: String? = null
        private var totalDurationMode: TotalDurationMode = TotalDurationMode.DEFAULT
        private var fixedTotalSec: Int? = null
        private var defaultAlertConfig: AlertConfig = AlertPresets.standard()

        private val _uiState = MutableStateFlow(ProfileEditorUiState())
        val uiState: StateFlow<ProfileEditorUiState> = _uiState.asStateFlow()

        private val events = Channel<ProfileEditorEvent>(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
        val uiEvents: Flow<ProfileEditorEvent> = events.receiveAsFlow()

        init {
            viewModelScope.launch { load() }
        }

        /**
         * Перечитывание при каждом возвращении на экран.
         *
         * Этапы и оповещения по умолчанию правятся на соседних экранах и
         * сохраняются ими самими, поэтому их приходится забирать заново. Поля
         * формы при этом не трогаются: пользователь мог отредактировать
         * название и уйти добавлять этап.
         */
        fun refreshStages() {
            if (profileId == NEW_ID) return
            viewModelScope.launch {
                val profile = repository.getProfile(profileId) ?: return@launch
                defaultAlertConfig = profile.defaultAlertConfig
                // Этапы пришли из базы — значит, они же теперь и сохранённые.
                _uiState.update {
                    it.copy(stages = profile.stages, savedForm = it.savedForm.copy(stages = profile.stages))
                }
            }
        }

        private suspend fun load() {
            if (profileId == NEW_ID) {
                _uiState.update { it.copy(isNew = true, isLoading = false) }
                return
            }
            val profile = repository.observeProfile(profileId).first()
            if (profile == null) {
                // Профиль удалили из-под редактора — вести себя как с новым.
                profileId = NEW_ID
                _uiState.update { it.copy(isNew = true, isLoading = false) }
                return
            }
            uuid = profile.uuid
            createdAt = profile.createdAt
            sortOrder = profile.sortOrder
            iconId = profile.iconId
            totalDurationMode = profile.totalDurationMode
            fixedTotalSec = profile.fixedTotalSec
            defaultAlertConfig = profile.defaultAlertConfig
            val loaded =
                ProfileEditorUiState(
                    isNew = false,
                    isLoading = false,
                    name = profile.name,
                    category = profile.category,
                    colorTag = profile.colorTag,
                    isFavorite = profile.isFavorite,
                    stages = profile.stages,
                )
            _uiState.value = loaded.copy(savedForm = loaded.form)
        }

        fun setName(name: String) = _uiState.update { it.copy(name = name, nameErrorShown = false) }

        fun setCategory(category: ProfileCategory) = _uiState.update { it.copy(category = category) }

        fun setColorTag(colorTag: String) = _uiState.update { it.copy(colorTag = colorTag) }

        fun toggleFavorite() = _uiState.update { it.copy(isFavorite = !it.isFavorite) }

        /** Перестановка этапа. Порядок задаётся позицией в списке, а не полем. */
        fun moveStage(
            from: Int,
            to: Int,
        ) {
            _uiState.update { state ->
                val stages = state.stages
                if (from !in stages.indices || to !in stages.indices) return@update state
                state.copy(stages = stages.toMutableList().apply { add(to, removeAt(from)) })
            }
        }

        fun removeStage(stageId: Long) {
            _uiState.update { it.copy(stages = it.stages.filterNot { stage -> stage.id == stageId }) }
        }

        /**
         * Копия этапа сохраняется сразу, а не копится в памяти: у неё ещё нет
         * идентификатора, а два безымянных этапа в списке неразличимы — ни для
         * ключей списка, ни для последующей перестановки.
         */
        fun duplicateStage(stageId: Long) {
            _uiState.update { state ->
                val index = state.stages.indexOfFirst { it.id == stageId }
                if (index < 0) return@update state
                val copy = state.stages[index].copy(id = NEW_ID)
                state.copy(stages = state.stages.toMutableList().apply { add(index + 1, copy) })
            }
            persistThen { refreshStages() }
        }

        /** Сохранение по кнопке: экран закрывается только после успеха. */
        fun save() {
            persistThen { events.trySend(ProfileEditorEvent.Saved) }
        }

        fun openStage(stageId: Long = NEW_ENTITY_ID) {
            persistThen { id -> events.trySend(ProfileEditorEvent.OpenStage(id, stageId)) }
        }

        fun openAlerts() {
            persistThen { id -> events.trySend(ProfileEditorEvent.OpenAlerts(id)) }
        }

        private fun persistThen(action: (Long) -> Unit) {
            val state = _uiState.value
            if (!state.isNameValid) {
                _uiState.update { it.copy(nameErrorShown = true) }
                return
            }
            viewModelScope.launch {
                val id = repository.saveProfile(state.toProfile())
                profileId = id
                // Сохранённым считается ровно то, что записано, — не то, что
                // на экране: у новых этапов идентификаторы появятся только
                // после чтения (`refreshStages`).
                _uiState.update { it.copy(isNew = false, savedForm = state.form) }
                action(id)
            }
        }

        private fun ProfileEditorUiState.toProfile(): Profile =
            Profile(
                id = profileId,
                uuid = uuid,
                name = name.trim(),
                category = category,
                colorTag = colorTag,
                iconId = iconId,
                totalDurationMode = totalDurationMode,
                fixedTotalSec = fixedTotalSec,
                isFavorite = isFavorite,
                sortOrder = sortOrder,
                defaultAlertConfig = defaultAlertConfig,
                createdAt = createdAt,
                stages = stages,
            )
    }
