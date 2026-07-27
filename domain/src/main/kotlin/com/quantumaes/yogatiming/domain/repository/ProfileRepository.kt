package com.quantumaes.yogatiming.domain.repository

import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.ProfileCategory
import com.quantumaes.yogatiming.domain.model.ProfileSummary
import kotlinx.coroutines.flow.Flow

/** Условия отбора для экрана списка профилей (ТЗ, Экран 1). */
data class ProfileFilter(
    val query: String = "",
    val category: ProfileCategory? = null,
    val favoritesOnly: Boolean = false,
)

/**
 * Доступ к профилям занятий.
 *
 * Реализация живёт в `:core:database`; домен знает только этот контракт,
 * поэтому ViewModel'и тестируются на фейках без Room и Android.
 */
interface ProfileRepository {
    /** Лёгкие проекции для списка — без этапов и конфигов оповещений (P1-5). */
    fun observeProfileSummaries(filter: ProfileFilter = ProfileFilter()): Flow<List<ProfileSummary>>

    /** Профиль целиком, со всеми этапами. `null`, если удалён. */
    fun observeProfile(id: Long): Flow<Profile?>

    suspend fun getProfile(id: Long): Profile?

    /**
     * Создаёт или обновляет профиль вместе с этапами одной транзакцией.
     * Этапы, которых нет в [profile], удаляются.
     *
     * @return идентификатор сохранённого профиля.
     */
    suspend fun saveProfile(profile: Profile): Long

    /** Удаляет профиль; этапы уходят каскадом. */
    suspend fun deleteProfile(id: Long)

    /**
     * Полная независимая копия профиля со всеми этапами и конфигами
     * оповещений. Копия получает новый `uuid` (P1-7).
     *
     * @param newName имя копии. Приходит снаружи, потому что «— копия» —
     *   это UI-текст, который обязан переводиться вместе с интерфейсом,
     *   а слой данных строк из ресурсов не читает.
     */
    suspend fun duplicateProfile(
        id: Long,
        newName: String,
    ): Long

    suspend fun setFavorite(
        id: Long,
        isFavorite: Boolean,
    )
}
