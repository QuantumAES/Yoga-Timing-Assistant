package com.quantumaes.yogatiming.feature.editor

import com.quantumaes.yogatiming.domain.alert.AlertPlayer
import com.quantumaes.yogatiming.domain.alert.AlertRequest
import com.quantumaes.yogatiming.domain.model.NEW_ID
import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.ProfileSummary
import com.quantumaes.yogatiming.domain.repository.ProfileFilter
import com.quantumaes.yogatiming.domain.repository.ProfileRepository
import com.quantumaes.yogatiming.domain.settings.AppSettings
import com.quantumaes.yogatiming.domain.settings.SettingsStore
import com.quantumaes.yogatiming.domain.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Репозиторий в памяти, повторяющий поведение `ProfileRepositoryImpl` в том,
 * что важно редактору: этапы без идентификатора получают его при сохранении,
 * а порядок задаётся позицией в списке, а не полем `sortOrder`.
 */
class FakeProfileRepository(
    initial: List<Profile> = emptyList(),
) : ProfileRepository {
    private val profiles = MutableStateFlow(initial)

    private var nextProfileId: Long = (initial.maxOfOrNull { it.id } ?: 0L) + 1
    private var nextStageId: Long = (initial.flatMap { it.stages }.maxOfOrNull { it.id } ?: 100L) + 1

    val stored: List<Profile> get() = profiles.value

    override fun observeProfileSummaries(filter: ProfileFilter): Flow<List<ProfileSummary>> =
        profiles.map { emptyList() }

    override fun observeProfile(id: Long): Flow<Profile?> = profiles.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun getProfile(id: Long): Profile? = profiles.value.firstOrNull { it.id == id }

    override suspend fun saveProfile(profile: Profile): Long {
        val id = if (profile.id == NEW_ID) nextProfileId++ else profile.id
        val stages =
            profile.stages.mapIndexed { index, stage ->
                stage.copy(
                    id = if (stage.id == NEW_ID) nextStageId++ else stage.id,
                    profileId = id,
                    sortOrder = index,
                )
            }
        val saved = profile.copy(id = id, stages = stages)
        profiles.value = profiles.value.filterNot { it.id == id } + saved
        return id
    }

    override suspend fun deleteProfile(id: Long) {
        profiles.value = profiles.value.filterNot { it.id == id }
    }

    override suspend fun duplicateProfile(
        id: Long,
        newName: String,
    ): Long = id

    override suspend fun setFavorite(
        id: Long,
        isFavorite: Boolean,
    ) = Unit
}

/** Настройки в памяти. Редактору важна одна из них — разрешён ли голос. */
class FakeSettingsStore(
    initial: AppSettings = AppSettings(),
) : SettingsStore {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = state.value.copy(themeMode = mode)
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        state.value = state.value.copy(dynamicColor = enabled)
    }

    override suspend fun setVoiceEnabled(enabled: Boolean) {
        state.value = state.value.copy(voiceEnabled = enabled)
    }
}

/** Проигрыватель, который только запоминает, что его просили сыграть. */
class FakeAlertPlayer : AlertPlayer {
    var prepared = 0
        private set

    var stopped = 0
        private set

    val played = mutableListOf<AlertRequest>()

    override fun prepare() {
        prepared++
    }

    override fun play(request: AlertRequest) {
        played += request
    }

    override fun stop() {
        stopped++
    }
}
