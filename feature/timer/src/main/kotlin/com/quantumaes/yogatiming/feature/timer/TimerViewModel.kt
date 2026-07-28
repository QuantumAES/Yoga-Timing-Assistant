package com.quantumaes.yogatiming.feature.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumaes.yogatiming.domain.hint.Hint
import com.quantumaes.yogatiming.domain.hint.HintStore
import com.quantumaes.yogatiming.timer.engine.TimerCommand
import com.quantumaes.yogatiming.timer.engine.TimerLimits
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionSnapshot
import com.quantumaes.yogatiming.timer.service.SessionController
import com.quantumaes.yogatiming.timer.service.SessionLauncher
import com.quantumaes.yogatiming.timer.service.restrictions.RestrictionSettings
import com.quantumaes.yogatiming.timer.service.restrictions.TimerRestriction
import com.quantumaes.yogatiming.timer.service.restrictions.TimerRestrictionsDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val NOTICES_SUBSCRIPTION_TIMEOUT_MS = 5_000L

/**
 * Модель рабочего экрана.
 *
 * Снапшот приходит напрямую из синглтона-контроллера: движок и интерфейс живут
 * в одном процессе, поэтому промежуточного состояния, способного разойтись
 * с движком, здесь нет (docs/02-TIMER-CORE-DESIGN.md §9.1).
 */
@HiltViewModel
class TimerViewModel
    @Inject
    constructor(
        private val controller: SessionController,
        private val launcher: SessionLauncher,
        private val restrictionsDetector: TimerRestrictionsDetector,
        private val restrictionSettings: RestrictionSettings,
        private val hintStore: HintStore,
    ) : ViewModel() {
        val snapshot: StateFlow<SessionSnapshot?> = controller.snapshot

        /**
         * Закрытые пользователем сообщения: разово — на сессию, подсказки — навсегда.
         *
         * Стартовое значение прячет все разовые подсказки и открывает те из них,
         * которые пользователь ещё не видел, — после чтения из хранилища. Иначе
         * подсказка, закрытая полгода назад, успевала бы моргнуть на экране,
         * пока читается флаг.
         */
        private val dismissed = MutableStateFlow(TimerRestriction.entries.filter { it.hint != null }.toSet())

        /**
         * Что показать поверх таймера — от самого опасного к наименее.
         *
         * Пустой список для занятия — норма, а не удача: сообщение появляется
         * только тогда, когда пользователю есть что с ним сделать.
         */
        val notices: StateFlow<List<TimerRestriction>> =
            combine(restrictionsDetector.restrictions, dismissed) { restrictions, hidden ->
                TimerRestriction.entries.filter { it in restrictions && it !in hidden }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(NOTICES_SUBSCRIPTION_TIMEOUT_MS),
                initialValue = emptyList(),
            )

        init {
            viewModelScope.launch {
                val unseen =
                    TimerRestriction.entries.filter { restriction ->
                        val hint = restriction.hint
                        hint != null && !hintStore.isDismissed(hint)
                    }
                dismissed.update { it - unseen.toSet() }
            }
        }

        /**
         * Занятие начинается один раз: повторный вход на экран (поворот, возврат
         * из шторки) не должен обнулять уже идущую сессию.
         *
         * Завершённое занятие идущим не считается — «Повторить» обязано поднять
         * сервис заново и пересобрать план: профиль мог быть отредактирован.
         */
        fun ensureSession(profileId: Long) {
            val current = controller.snapshot.value
            if (current?.profileId == profileId && current.runState.isActive) return
            launcher.start(profileId)
        }

        fun refreshRestrictions() = restrictionsDetector.refresh()

        /** Переход в системные настройки. Возврат оттуда перечитает состояние. */
        fun openSettings(restriction: TimerRestriction) = restrictionSettings.open(restriction)

        /**
         * Совет закрывается навсегда, предупреждение — до конца занятия.
         *
         * Разница по существу: совет описывает риск, о котором достаточно узнать
         * один раз (docs/05-PLAY-DECLARATIONS.md §5), а предупреждение — уже
         * случившуюся поломку, о которой в следующий раз надо сказать снова.
         */
        fun dismiss(restriction: TimerRestriction) {
            dismissed.update { it + restriction }
            val hint = restriction.hint ?: return
            viewModelScope.launch { hintStore.dismiss(hint) }
        }

        fun togglePause() {
            val command =
                if (snapshot.value?.runState == RunState.PAUSED) TimerCommand.Resume else TimerCommand.Pause
            controller.submit(command)
        }

        fun next() = controller.submit(TimerCommand.Next)

        fun previous() = controller.submit(TimerCommand.Previous)

        fun stop() = controller.submit(TimerCommand.Stop)

        fun addTime() = controller.submit(TimerCommand.Adjust(TimerLimits.ADJUST_STEP_MS))

        fun subtractTime() = controller.submit(TimerCommand.Adjust(-TimerLimits.ADJUST_STEP_MS))
    }

/** Ограничения, о которых достаточно сказать один раз за всё время жизни установки. */
private val TimerRestriction.hint: Hint?
    get() =
        when (this) {
            TimerRestriction.BATTERY_OPTIMIZED -> Hint.BATTERY_OPTIMIZATION
            TimerRestriction.EXACT_ALARMS_UNAVAILABLE -> Hint.EXACT_ALARMS
            TimerRestriction.NOTIFICATIONS_DISABLED, TimerRestriction.ALARMS_SILENCED_BY_DND -> null
        }
