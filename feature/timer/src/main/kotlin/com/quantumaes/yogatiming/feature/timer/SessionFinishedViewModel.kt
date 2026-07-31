package com.quantumaes.yogatiming.feature.timer

import androidx.lifecycle.ViewModel
import com.quantumaes.yogatiming.domain.session.SessionSummary
import com.quantumaes.yogatiming.timer.service.SessionSummarySource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Модель экрана итогов.
 *
 * Читает готовые итоги и ничего не считает: длительности сложены в момент
 * конца занятия, когда состояние движка ещё существовало
 * (`SessionController.publishSummary`).
 */
@HiltViewModel
class SessionFinishedViewModel
    @Inject
    constructor(
        summarySource: SessionSummarySource,
    ) : ViewModel() {
        /** `null` — итогов нет: приложение перезапустили после занятия. */
        val summary: StateFlow<SessionSummary?> = summarySource.lastSummary
    }
