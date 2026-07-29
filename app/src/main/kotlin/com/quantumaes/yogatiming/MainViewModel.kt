package com.quantumaes.yogatiming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumaes.yogatiming.domain.settings.AppSettings
import com.quantumaes.yogatiming.domain.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Оформление приложения на время жизни процесса.
 *
 * `null` — настройки ещё читаются. Отличать это состояние от «настройки
 * прочитаны, тема системная» обязательно: иначе приложение успевает нарисовать
 * первый кадр в системной теме и следом перекраситься в выбранную. Пока значение
 * `null`, экран удерживает splash — мигания темы при старте не будет.
 */
@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        settingsStore: SettingsStore,
    ) : ViewModel() {
        private val _settings = MutableStateFlow<AppSettings?>(null)
        val settings: StateFlow<AppSettings?> = _settings.asStateFlow()

        init {
            viewModelScope.launch {
                settingsStore.settings.collect { _settings.value = it }
            }
        }
    }
