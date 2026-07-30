package com.quantumaes.yogatiming.feature.editor.alert

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumaes.yogatiming.domain.alert.AlertPlayer
import com.quantumaes.yogatiming.domain.alert.AlertRequest
import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.alert.Alert
import com.quantumaes.yogatiming.domain.model.alert.AlertChannel
import com.quantumaes.yogatiming.domain.model.alert.AlertConfig
import com.quantumaes.yogatiming.domain.model.alert.AlertPreset
import com.quantumaes.yogatiming.domain.model.alert.AlertPresets
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

/** Смещение нового предупреждения: минута до конца — самое частое. */
private const val NEW_WARNING_OFFSET_SEC = 60

/** Шаг ползунка громкости, в процентах. */
const val VOLUME_STEP_PERCENT = 5

data class AlertConfigUiState(
    val isLoading: Boolean = true,
    val isStageScope: Boolean = false,
    /** Название владельца конфига — профиля или этапа. */
    val ownerName: String = "",
    val config: AlertConfig = AlertPresets.standard(),
    /**
     * Этап свободный: предупреждения и END привязаны к концу, которого нет
     * (решение B-5). Секции показываются, но с честным предупреждением.
     */
    val isFreeStage: Boolean = false,
    /** Длительность этапа: по ней видно, какие предупреждения не сработают (B-7). */
    val stageDurationSec: Int = 0,
    /**
     * Разрешён ли голос вообще (Экран 6 настроек).
     *
     * Настроенный канал VOICE при выключенном голосе промолчит, и узнать об
     * этом здесь честнее, чем посреди занятия.
     */
    val voiceEnabled: Boolean = false,
) {
    val warnings: List<Alert> get() = config.warningsByTime

    /** Предупреждение со смещением больше длительности этапа пропускается молча (B-7). */
    fun isWarningUnreachable(alert: Alert): Boolean =
        !isFreeStage && stageDurationSec > 0 && alert.offsetSec >= stageDurationSec
}

sealed interface AlertConfigEvent {
    data object Saved : AlertConfigEvent
}

/**
 * Экран 5 «Редактор оповещений».
 *
 * Владельцем конфига может быть профиль (`defaultAlertConfig`) или этап
 * (`Stage.alertConfig`) — разница только в том, куда конфиг ложится при
 * сохранении. Всё остальное поведение общее, поэтому и экран один.
 *
 * Любая ручная правка переводит пресет в [AlertPreset.CUSTOM]: набор перестал
 * быть «Стандартом», и продолжать называть его стандартным значило бы врать
 * в списке этапов.
 */
@HiltViewModel
class AlertConfigViewModel
    @Inject
    constructor(
        private val repository: ProfileRepository,
        private val alertPlayer: AlertPlayer,
        settingsStore: SettingsStore,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val profileId: Long = requireNotNull(savedStateHandle.get<Long>(RouteArgs.PROFILE_ID))
        private val stageId: Long? =
            savedStateHandle.get<Long>(RouteArgs.STAGE_ID)?.takeIf { it != NEW_ENTITY_ID }

        private val _uiState = MutableStateFlow(AlertConfigUiState())
        val uiState: StateFlow<AlertConfigUiState> = _uiState.asStateFlow()

        private val events = Channel<AlertConfigEvent>(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
        val uiEvents: Flow<AlertConfigEvent> = events.receiveAsFlow()

        init {
            viewModelScope.launch { load() }
            viewModelScope.launch {
                settingsStore.settings.collect { settings ->
                    _uiState.update { it.copy(voiceEnabled = settings.voiceEnabled) }
                }
            }
        }

        private suspend fun load() {
            val profile = repository.getProfile(profileId) ?: return
            val stage = stageId?.let { id -> profile.stages.firstOrNull { it.id == id } }
            // copy, а не новое состояние: настройка голоса приезжает своим
            // потоком и может успеть раньше чтения профиля.
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isStageScope = stage != null,
                    ownerName = stage?.name ?: profile.name,
                    config = stage?.alertConfig ?: profile.defaultAlertConfig,
                    isFreeStage = stage != null && !stage.hasPlannedDuration,
                    stageDurationSec = stage?.durationSec ?: 0,
                )
            }
        }

        /** Пресет заменяет набор целиком — это и есть смысл готового набора. */
        fun applyPreset(preset: AlertPreset) {
            alertPlayer.stopCustomSound()
            _uiState.update { it.copy(config = AlertPresets.of(preset)) }
        }

        fun setMasterVolume(percent: Int) =
            edit { it.copy(masterVolumePercent = percent.coerceIn(AlertConfig.MIN_VOLUME, AlertConfig.MAX_VOLUME)) }

        fun setTriggerEnabled(
            trigger: AlertTrigger,
            enabled: Boolean,
        ) = edit { config ->
            when (trigger) {
                AlertTrigger.START -> {
                    config.copy(
                        start = config.start.enabledOr(enabled, AlertPresets.standard().start),
                    )
                }

                AlertTrigger.END -> {
                    config.copy(end = config.end.enabledOr(enabled, AlertPresets.standard().end))
                }

                AlertTrigger.WARNING -> {
                    config
                }
            }
        }

        fun updateStart(update: (Alert) -> Alert) = edit { it.copy(start = it.start?.let(update)) }

        fun updateEnd(update: (Alert) -> Alert) = edit { it.copy(end = it.end?.let(update)) }

        fun updateWarning(
            offsetSec: Int,
            update: (Alert) -> Alert,
        ) = edit { config ->
            config.copy(warnings = config.warnings.map { if (it.offsetSec == offsetSec) update(it) else it })
        }

        /**
         * Новое предупреждение.
         *
         * Смещения уникальны: движок различает предупреждения одного этапа
         * именно по смещению, и два оповещения «за минуту» стали бы одним
         * непредсказуемым (`SessionPlanFactory.resolve`).
         */
        fun addWarning() =
            edit { config ->
                val offset =
                    generateSequence(NEW_WARNING_OFFSET_SEC) { it + NEW_WARNING_OFFSET_SEC }
                        .first { candidate -> config.warnings.none { it.offsetSec == candidate } }
                config.copy(
                    warnings =
                        config.warnings +
                            AlertPresets
                                .standard()
                                .warnings
                                .first()
                                .copy(offsetSec = offset),
                )
            }

        fun removeWarning(offsetSec: Int) =
            edit { config -> config.copy(warnings = config.warnings.filterNot { it.offsetSec == offsetSec }) }

        /** Смещение, совпавшее с уже существующим, не принимается — иначе одно съест другое. */
        fun setWarningOffset(
            offsetSec: Int,
            newOffsetSec: Int,
        ) = edit { config ->
            if (config.warnings.any { it.offsetSec == newOffsetSec }) {
                config
            } else {
                config.copy(
                    warnings =
                        config.warnings.map {
                            if (it.offsetSec == offsetSec) it.copy(offsetSec = newOffsetSec) else it
                        },
                )
            }
        }

        /**
         * Предпрослушивание.
         *
         * `prepare()` вызывается, а `stop()` — никогда: проигрыватель общий
         * с сервисом занятия, и освобождение SoundPool из редактора оборвало бы
         * сигналы идущего занятия. Ресурсами владеет тот, кто ведёт занятие.
         *
         * Предыдущее предпрослушивание обрывается: файл пользователя звучит
         * секундами, и складывать его со следующим — значит слушать оба сразу.
         */
        fun preview(
            alert: Alert,
            trigger: AlertTrigger,
        ) {
            val state = _uiState.value
            alertPlayer.stopCustomSound()
            alertPlayer.prepare()
            alertPlayer.play(
                AlertRequest(
                    alert = alert.copy(volumePercent = alert.volumePercent ?: state.config.masterVolumePercent),
                    trigger = trigger,
                    stageName = state.ownerName,
                    nextStageName = state.ownerName,
                ),
            )
        }

        /** Уход с экрана обрывает предпрослушивание: минутной мелодии здесь не место. */
        override fun onCleared() {
            alertPlayer.stopCustomSound()
        }

        fun save() {
            val config = _uiState.value.config
            viewModelScope.launch {
                val profile = repository.getProfile(profileId) ?: return@launch
                repository.saveProfile(profile.withConfig(config))
                events.trySend(AlertConfigEvent.Saved)
            }
        }

        private fun Profile.withConfig(config: AlertConfig): Profile =
            if (stageId == null) {
                copy(defaultAlertConfig = config)
            } else {
                copy(stages = stages.map { if (it.id == stageId) it.copy(alertConfig = config) else it })
            }

        /**
         * Ручная правка снимает ярлык готового набора — и обрывает
         * предпрослушивание.
         *
         * Правило живёт здесь, а не в экране: выбор другого звука, замена файла,
         * смена длительности и переключение каналов — всё это делает звучащий
         * сейчас файл неправдой, и перечислять такие места по одному в разметке
         * значит однажды забыть очередное.
         */
        private fun edit(update: (AlertConfig) -> AlertConfig) {
            alertPlayer.stopCustomSound()
            _uiState.update { state ->
                state.copy(config = update(state.config).copy(preset = AlertPreset.CUSTOM))
            }
        }
    }

/**
 * Включение выключенного триггера.
 *
 * Выключенный триггер сохраняет свои настройки — пользователь, выключивший
 * и включивший END, ожидает увидеть прежний гонг, а не тишину. Триггера,
 * которого не было вовсе, берётся из стандартного набора.
 */
private fun Alert?.enabledOr(
    enabled: Boolean,
    fallback: Alert?,
): Alert? =
    when {
        this != null -> copy(enabled = enabled)
        !enabled -> null
        else -> fallback?.copy(enabled = true)
    }

/** Каналы оповещения переключаются независимо: их можно комбинировать (ТЗ §5.1). */
fun Alert.toggleChannel(channel: AlertChannel): Alert =
    copy(channels = if (channel in channels) channels - channel else channels + channel)
