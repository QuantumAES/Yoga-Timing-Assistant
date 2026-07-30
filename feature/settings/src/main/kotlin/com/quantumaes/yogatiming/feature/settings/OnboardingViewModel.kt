package com.quantumaes.yogatiming.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumaes.yogatiming.domain.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Онбординг помнит ровно один факт — что он был показан.
 *
 * Флаг ставится по завершении, а не при первом кадре: пользователь, закрывший
 * приложение на втором слайде, ещё ничего не узнал, и в следующий раз онбординг
 * обязан начаться заново.
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val settingsStore: SettingsStore,
    ) : ViewModel() {
        fun complete() {
            viewModelScope.launch { settingsStore.setOnboardingCompleted(true) }
        }
    }
