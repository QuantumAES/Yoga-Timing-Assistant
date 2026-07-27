package com.quantumaes.yogatiming.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumaes.yogatiming.domain.model.ProfileSummary
import com.quantumaes.yogatiming.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Состояние экрана списка профилей. */
sealed interface ProfilesUiState {
    data object Loading : ProfilesUiState

    /** Профилей нет вовсе — например, инструктор удалил демо-набор. */
    data object Empty : ProfilesUiState

    data class Content(
        val profiles: List<ProfileSummary>,
    ) : ProfilesUiState
}

@HiltViewModel
class ProfilesViewModel
    @Inject
    constructor(
        private val repository: ProfileRepository,
    ) : ViewModel() {
        /**
         * Читается лёгкая проекция (P1-5): этапы и конфиги оповещений на этом
         * экране не нужны, а тянулись бы при каждом обновлении.
         */
        val uiState: StateFlow<ProfilesUiState> =
            repository
                .observeProfileSummaries()
                .map { profiles ->
                    if (profiles.isEmpty()) ProfilesUiState.Empty else ProfilesUiState.Content(profiles)
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                    initialValue = ProfilesUiState.Loading,
                )

        fun setFavorite(
            profileId: Long,
            isFavorite: Boolean,
        ) {
            viewModelScope.launch {
                repository.setFavorite(profileId, isFavorite)
            }
        }

        private companion object {
            /** Пережить поворот экрана, но не держать подписку в фоне. */
            const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
        }
    }
