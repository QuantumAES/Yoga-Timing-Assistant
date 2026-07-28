package com.quantumaes.yogatiming.feature.timer

import androidx.lifecycle.ViewModel
import com.quantumaes.yogatiming.timer.engine.TimerCommand
import com.quantumaes.yogatiming.timer.engine.TimerLimits
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionSnapshot
import com.quantumaes.yogatiming.timer.service.SessionController
import com.quantumaes.yogatiming.timer.service.SessionLauncher
import com.quantumaes.yogatiming.timer.service.restrictions.TimerRestrictions
import com.quantumaes.yogatiming.timer.service.restrictions.TimerRestrictionsDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

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
    ) : ViewModel() {
        val snapshot: StateFlow<SessionSnapshot?> = controller.snapshot

        val restrictions: StateFlow<TimerRestrictions> = restrictionsDetector.restrictions

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
