package com.quantumaes.yogatiming.feature.profiles

import com.quantumaes.yogatiming.domain.model.NEW_ID
import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.ProfileSummary
import com.quantumaes.yogatiming.domain.repository.ProfileFilter
import com.quantumaes.yogatiming.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Репозиторий в памяти.
 *
 * Отбор повторяет SQL из `ProfileDao`, а не заменяется заглушкой: поиск и
 * фильтры — это поведение экрана, и проверять их на репозитории, который всегда
 * возвращает всё, бессмысленно.
 */
class FakeProfileRepository(
    initial: List<Profile> = emptyList(),
) : ProfileRepository {
    private val profiles = MutableStateFlow(initial)

    private var nextId: Long = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    val stored: List<Profile> get() = profiles.value

    override fun observeProfileSummaries(filter: ProfileFilter): Flow<List<ProfileSummary>> =
        profiles.map { all ->
            all
                .filter { filter.query.isBlank() || it.name.contains(filter.query, ignoreCase = true) }
                .filter { filter.category == null || it.category == filter.category }
                .filter { !filter.favoritesOnly || it.isFavorite }
                .map(Profile::toSummary)
        }

    override fun observeProfile(id: Long): Flow<Profile?> = profiles.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun getProfile(id: Long): Profile? = profiles.value.firstOrNull { it.id == id }

    override suspend fun saveProfile(profile: Profile): Long {
        val id = if (profile.id == NEW_ID) nextId++ else profile.id
        val saved = profile.copy(id = id, stages = profile.stages.map { it.copy(profileId = id) })
        profiles.value = profiles.value.filterNot { it.id == id } + saved
        return id
    }

    override suspend fun deleteProfile(id: Long) {
        profiles.value = profiles.value.filterNot { it.id == id }
    }

    override suspend fun duplicateProfile(
        id: Long,
        newName: String,
    ): Long {
        val source = requireNotNull(getProfile(id))
        return saveProfile(
            source.copy(
                id = NEW_ID,
                uuid = UUID.randomUUID().toString(),
                name = newName,
                isFavorite = false,
            ),
        )
    }

    override suspend fun setFavorite(
        id: Long,
        isFavorite: Boolean,
    ) {
        profiles.value = profiles.value.map { if (it.id == id) it.copy(isFavorite = isFavorite) else it }
    }
}

private fun Profile.toSummary() =
    ProfileSummary(
        id = id,
        uuid = uuid,
        name = name,
        category = category,
        colorTag = colorTag,
        iconId = iconId,
        isFavorite = isFavorite,
        stageCount = stages.size,
        totalDurationSec = totalDurationSec,
        hasFreeStages = hasFreeStages,
    )
