package com.quantumaes.yogatiming.core.database.repository

import androidx.room.withTransaction
import com.quantumaes.yogatiming.core.database.YtaDatabase
import com.quantumaes.yogatiming.core.database.dao.ProfileDao
import com.quantumaes.yogatiming.core.database.dao.StageDao
import com.quantumaes.yogatiming.core.database.entity.ProfileSummaryProjection
import com.quantumaes.yogatiming.core.database.mapper.normalizeForSearch
import com.quantumaes.yogatiming.core.database.mapper.toDomain
import com.quantumaes.yogatiming.core.database.mapper.toEntity
import com.quantumaes.yogatiming.domain.model.NEW_ID
import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.ProfileSummary
import com.quantumaes.yogatiming.domain.repository.ProfileFilter
import com.quantumaes.yogatiming.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl
    @Inject
    constructor(
        private val database: YtaDatabase,
        private val profileDao: ProfileDao,
        private val stageDao: StageDao,
    ) : ProfileRepository {
        override fun observeProfileSummaries(filter: ProfileFilter): Flow<List<ProfileSummary>> =
            profileDao
                .observeSummaries(
                    query = filter.query.forLikeSearch(),
                    category = filter.category?.name,
                    favoritesOnly = filter.favoritesOnly,
                ).map { rows -> rows.map(ProfileSummaryProjection::toDomain) }

        override fun observeProfile(id: Long): Flow<Profile?> =
            profileDao.observeProfileWithStages(id).map { it?.toDomain() }

        override suspend fun getProfile(id: Long): Profile? = profileDao.getProfileWithStages(id)?.toDomain()

        /**
         * Профиль и этапы сохраняются одной транзакцией: наполовину сохранённое
         * занятие хуже, чем не сохранённое вовсе. Транзакцию собирает репозиторий,
         * потому что она затрагивает два DAO.
         */
        override suspend fun saveProfile(profile: Profile): Long =
            database.withTransaction {
                val timestamp = System.currentTimeMillis()
                val createdAt = if (profile.id == NEW_ID) timestamp else profile.createdAt
                val entity = profile.toEntity(createdAt = createdAt, updatedAt = timestamp)

                val profileId =
                    if (profile.id == NEW_ID) {
                        profileDao.insert(entity)
                    } else {
                        profileDao.update(entity)
                        profile.id
                    }

                // Порядок этапов задаётся их позицией в списке: редактор двигает элементы
                // drag-and-drop'ом, и хранить рассинхронизованный sort_order нет смысла.
                val stages =
                    profile.stages.mapIndexed { index, stage ->
                        stage.toEntity(profileId = profileId, sortOrder = index)
                    }

                // Редактор присылает полное состояние профиля, а не дельту, поэтому
                // всё, чего в нём нет, удаляется.
                val keepIds = stages.map { it.id }.filter { it != NEW_ID }
                if (keepIds.isEmpty()) {
                    stageDao.deleteAllForProfile(profileId)
                } else {
                    stageDao.deleteNotIn(profileId, keepIds)
                }
                if (stages.isNotEmpty()) {
                    stageDao.upsertAll(stages)
                }

                profileId
            }

        override suspend fun deleteProfile(id: Long) = profileDao.delete(id)

        override suspend fun duplicateProfile(
            id: Long,
            newName: String,
        ): Long {
            val source =
                requireNotNull(profileDao.getProfileWithStages(id)?.toDomain()) {
                    "Профиль $id не найден — дублировать нечего"
                }
            val copy =
                source.copy(
                    id = NEW_ID,
                    // Новый uuid: копия — самостоятельная сущность, иначе импорт
                    // на другом устройстве принял бы её за ту же запись (P1-7).
                    uuid = UUID.randomUUID().toString(),
                    name = newName,
                    isFavorite = false,
                    stages = source.stages.map { it.copy(id = NEW_ID, profileId = NEW_ID) },
                )
            return saveProfile(copy)
        }

        override suspend fun setFavorite(
            id: Long,
            isFavorite: Boolean,
        ) = profileDao.setFavorite(id, isFavorite, System.currentTimeMillis())

        /**
         * Подготовка пользовательского ввода к `LIKE`.
         *
         * Регистр приводится к нижнему (в SQLite он кириллицу не понимает),
         * а `%`, `_` и `\` экранируются — иначе «100%» превратится в шаблон
         * «что угодно» и найдёт все профили.
         */
        private fun String.forLikeSearch(): String =
            normalizeForSearch()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
    }
