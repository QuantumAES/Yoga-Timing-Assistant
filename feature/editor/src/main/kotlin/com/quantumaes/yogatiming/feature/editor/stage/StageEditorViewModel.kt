package com.quantumaes.yogatiming.feature.editor.stage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumaes.yogatiming.domain.alert.AlertPlayer
import com.quantumaes.yogatiming.domain.alert.AlertRequest
import com.quantumaes.yogatiming.domain.model.DEFAULT_COLOR_TAG
import com.quantumaes.yogatiming.domain.model.NEW_ID
import com.quantumaes.yogatiming.domain.model.Stage
import com.quantumaes.yogatiming.domain.model.StageType
import com.quantumaes.yogatiming.domain.model.alert.Alert
import com.quantumaes.yogatiming.domain.model.alert.AlertChannel
import com.quantumaes.yogatiming.domain.model.alert.AlertConfig
import com.quantumaes.yogatiming.domain.model.alert.AlertPresets
import com.quantumaes.yogatiming.domain.model.alert.AlertSound
import com.quantumaes.yogatiming.domain.model.alert.VoicePhrase
import com.quantumaes.yogatiming.domain.repository.ProfileRepository
import com.quantumaes.yogatiming.domain.settings.SettingsStore
import com.quantumaes.yogatiming.feature.editor.NEW_ENTITY_ID
import com.quantumaes.yogatiming.feature.editor.RouteArgs
import com.quantumaes.yogatiming.timer.engine.model.AlertTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Длительность нового этапа по умолчанию: пять минут — типичный блок. */
private const val DEFAULT_DURATION_SEC = 300

/**
 * Поля этапа в том виде, в каком они уходят в базу.
 *
 * Снимок последней записи сравнивается с текущими полями — так виден ответ на
 * вопрос «есть ли что терять при выходе» (полевая проверка 2026-07-31,
 * замечание 8). Значения по умолчанию совпадают с [StageEditorUiState]:
 * только что открытый новый этап несохранённых правок не имеет.
 */
data class StageForm(
    val name: String = "",
    val type: StageType = StageType.NORMAL,
    val colorTag: String = DEFAULT_COLOR_TAG,
    val durationSec: Int = DEFAULT_DURATION_SEC,
    val note: String = "",
    val voiceName: String = "",
    val alertConfig: AlertConfig? = null,
)

data class StageEditorUiState(
    val isNew: Boolean = true,
    val isLoading: Boolean = true,
    val name: String = "",
    val type: StageType = StageType.NORMAL,
    val colorTag: String = DEFAULT_COLOR_TAG,
    val durationSec: Int = DEFAULT_DURATION_SEC,
    val note: String = "",
    /**
     * Как этап произносится вслух. Пусто — как написан.
     *
     * Отдельно от названия: на экране должно остаться «Шавасана», а произнести
     * синтезатор обязан «шавáсану», иначе ударение уедет на последний слог.
     */
    val voiceName: String = "",
    /** Разрешён ли голос вообще (Экран 6): иначе произношение проверить нечем. */
    val voiceEnabled: Boolean = false,
    /** `null` — этап наследует оповещения профиля (ADR-002). */
    val alertConfig: AlertConfig? = null,
    val nameErrorShown: Boolean = false,
    val durationErrorShown: Boolean = false,
    /**
     * Тихий пресет только что подставлен автоматически при выборе типа REST
     * (решение C-6). Пользователь вправе отказаться, поэтому подсказка с
     * отменой висит до следующего действия.
     */
    val restPresetOffered: Boolean = false,
    /** Снимок последней записи в базу. */
    val savedForm: StageForm = StageForm(),
) {
    val form: StageForm
        get() = StageForm(name.trim(), type, colorTag, durationSec, note.trim(), voiceName.trim(), alertConfig)

    val hasUnsavedChanges: Boolean get() = form != savedForm

    val isNameValid: Boolean get() = name.isNotBlank()

    /** FREE-этап длится до ручного перехода, длительность у него не спрашивают. */
    val hasDuration: Boolean get() = type != StageType.FREE

    val isDurationValid: Boolean
        get() = !hasDuration || durationSec in Stage.MIN_DURATION_SEC..Stage.MAX_DURATION_SEC

    val hasOwnAlerts: Boolean get() = alertConfig != null
}

sealed interface StageEditorEvent {
    data class OpenAlerts(
        val profileId: Long,
        val stageId: Long,
    ) : StageEditorEvent

    data object Saved : StageEditorEvent
}

/**
 * Экран 3 «Редактор этапа».
 *
 * Этап сохраняется не сам по себе, а вместе с профилем: `saveProfile` — это
 * полное состояние профиля одной транзакцией, и вклиниваться в неё отдельной
 * записью этапа значило бы завести второй путь сохранения с собственными
 * правилами порядка (см. `ProfileRepositoryImpl.saveProfile`).
 */
@HiltViewModel
class StageEditorViewModel
    @Inject
    constructor(
        private val repository: ProfileRepository,
        private val alertPlayer: AlertPlayer,
        settingsStore: SettingsStore,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val profileId: Long = requireNotNull(savedStateHandle.get<Long>(RouteArgs.PROFILE_ID))
        private var stageId: Long =
            savedStateHandle
                .get<Long>(RouteArgs.STAGE_ID)
                ?.takeIf { it != NEW_ENTITY_ID }
                ?: NEW_ID

        private val _uiState = MutableStateFlow(StageEditorUiState())
        val uiState: StateFlow<StageEditorUiState> = _uiState.asStateFlow()

        private val events = Channel<StageEditorEvent>(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
        val uiEvents: Flow<StageEditorEvent> = events.receiveAsFlow()

        init {
            viewModelScope.launch { load() }
            viewModelScope.launch {
                settingsStore.settings.collect { settings ->
                    _uiState.update { it.copy(voiceEnabled = settings.voiceEnabled) }
                }
            }
        }

        /** Оповещения правит соседний экран — при возвращении их надо перечитать. */
        fun refreshAlerts() {
            if (stageId == NEW_ID) return
            viewModelScope.launch {
                val stage = repository.getProfile(profileId)?.stages?.firstOrNull { it.id == stageId } ?: return@launch
                // Оповещения пришли из базы — значит, они же теперь и сохранённые.
                _uiState.update {
                    it.copy(
                        alertConfig = stage.alertConfig,
                        restPresetOffered = false,
                        savedForm = it.savedForm.copy(alertConfig = stage.alertConfig),
                    )
                }
            }
        }

        private suspend fun load() {
            val stage = repository.getProfile(profileId)?.stages?.firstOrNull { it.id == stageId }
            if (stage == null) {
                stageId = NEW_ID
                _uiState.update { it.copy(isNew = true, isLoading = false) }
                return
            }
            // copy, а не новое состояние целиком: настройка голоса приезжает
            // своим потоком и может успеть раньше чтения этапа.
            _uiState.update {
                val loaded =
                    it.copy(
                        isNew = false,
                        isLoading = false,
                        name = stage.name,
                        type = stage.type,
                        colorTag = stage.colorTag,
                        durationSec = stage.durationSec,
                        note = stage.note.orEmpty(),
                        voiceName = stage.voiceName.orEmpty(),
                        alertConfig = stage.alertConfig,
                    )
                loaded.copy(savedForm = loaded.form)
            }
        }

        fun setName(name: String) = _uiState.update { it.copy(name = name, nameErrorShown = false) }

        fun setVoiceName(voiceName: String) = _uiState.update { it.copy(voiceName = voiceName) }

        /**
         * Прослушать произношение.
         *
         * Настроить ударение вслепую нельзя: помогла пометка или нет, слышно
         * только на слух. Проигрывается ровно то, что скажет START этапа, —
         * тем же проигрывателем и с теми же правилами, включая общий
         * выключатель голоса.
         */
        fun previewVoice() {
            val state = _uiState.value
            val spoken = state.voiceName.trim().takeIf { it.isNotEmpty() } ?: state.name.trim()
            if (spoken.isEmpty()) return
            alertPlayer.prepare()
            alertPlayer.play(
                AlertRequest(
                    alert =
                        Alert(
                            channels = setOf(AlertChannel.VOICE),
                            sound = AlertSound.NONE,
                            voice = VoicePhrase.STAGE_NAME,
                            volumePercent = AlertConfig.DEFAULT_MASTER_VOLUME,
                        ),
                    trigger = AlertTrigger.START,
                    stageName = state.name,
                    nextStageName = null,
                    stageVoiceName = state.voiceName,
                ),
            )
        }

        /**
         * Смена типа на REST предлагает тихий пресет (решение C-6).
         *
         * Именно предлагает: подмены каналов в рантайме нет, тишина шавасаны
         * задана данными и видна пользователю в редакторе оповещений.
         * Предложение применяется сразу и отменяется одним нажатием — так
         * пользователь видит результат, а не описание результата.
         */
        fun setType(type: StageType) {
            _uiState.update { state ->
                val offerSilent = type == StageType.REST && state.alertConfig == null
                state.copy(
                    type = type,
                    alertConfig = if (offerSilent) AlertPresets.silent() else state.alertConfig,
                    restPresetOffered = offerSilent,
                )
            }
        }

        /** Отказ от предложенного тихого пресета: вернуть наследование профиля. */
        fun declineRestPreset() = _uiState.update { it.copy(alertConfig = null, restPresetOffered = false) }

        fun setColorTag(colorTag: String) = _uiState.update { it.copy(colorTag = colorTag) }

        fun setDuration(durationSec: Int) =
            _uiState.update { it.copy(durationSec = durationSec, durationErrorShown = false) }

        fun setNote(note: String) = _uiState.update { it.copy(note = note) }

        /**
         * Переключатель «свои оповещения».
         *
         * Включение копирует конфиг профиля, а не создаёт пустой: пользователь
         * хочет поправить схему, а не собрать её с нуля.
         */
        fun setOwnAlerts(own: Boolean) {
            viewModelScope.launch {
                val inherited = repository.getProfile(profileId)?.defaultAlertConfig ?: AlertPresets.standard()
                _uiState.update {
                    it.copy(
                        alertConfig = if (own) it.alertConfig ?: inherited else null,
                        restPresetOffered = false,
                    )
                }
            }
        }

        fun save() = persistThen { events.trySend(StageEditorEvent.Saved) }

        fun openAlerts() = persistThen { id -> events.trySend(StageEditorEvent.OpenAlerts(profileId, id)) }

        private fun persistThen(action: (Long) -> Unit) {
            val state = _uiState.value
            if (!state.isNameValid || !state.isDurationValid) {
                _uiState.update {
                    it.copy(
                        nameErrorShown = !state.isNameValid,
                        durationErrorShown = !state.isDurationValid,
                    )
                }
                return
            }
            viewModelScope.launch {
                val profile = repository.getProfile(profileId) ?: return@launch
                val knownIds = profile.stages.map { it.id }.toSet()
                val edited = state.toStage()
                val stages =
                    if (stageId == NEW_ID) {
                        profile.stages + edited
                    } else {
                        profile.stages.map { if (it.id == stageId) edited else it }
                    }
                repository.saveProfile(profile.copy(stages = stages))

                // Новый этап получает идентификатор только после записи —
                // забираем его, иначе повторное сохранение создаст второй этап.
                if (stageId == NEW_ID) {
                    val saved = repository.getProfile(profileId)?.stages.orEmpty()
                    stageId = saved.firstOrNull { it.id !in knownIds }?.id ?: NEW_ID
                    _uiState.update { it.copy(isNew = false) }
                }
                _uiState.update { it.copy(savedForm = state.form) }
                action(stageId)
            }
        }

        private fun StageEditorUiState.toStage(): Stage =
            Stage(
                id = stageId,
                profileId = profileId,
                name = name.trim(),
                type = type,
                colorTag = colorTag,
                // FREE-этап длится до ручного перехода: плановая длительность
                // у него нулевая, а не «сколько было в поле до смены типа».
                durationSec =
                    if (hasDuration) {
                        durationSec.coerceIn(Stage.MIN_DURATION_SEC, Stage.MAX_DURATION_SEC)
                    } else {
                        0
                    },
                note = note.trim().takeIf { it.isNotEmpty() },
                alertConfig = alertConfig,
                voiceName = voiceName.trim().takeIf { it.isNotEmpty() },
            )
    }
