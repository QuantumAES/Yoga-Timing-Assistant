package com.quantumaes.yogatiming.core.database.repository

import com.quantumaes.yogatiming.core.database.dao.SessionLogDao
import com.quantumaes.yogatiming.core.database.entity.ProfileTotalsProjection
import com.quantumaes.yogatiming.core.database.entity.SessionDayProjection
import com.quantumaes.yogatiming.core.database.entity.SessionLogEntity
import com.quantumaes.yogatiming.core.database.mapper.toDomain
import com.quantumaes.yogatiming.core.database.mapper.toEntity
import com.quantumaes.yogatiming.domain.repository.SessionLogRepository
import com.quantumaes.yogatiming.domain.stats.ProfileTotals
import com.quantumaes.yogatiming.domain.stats.SessionDay
import com.quantumaes.yogatiming.domain.stats.SessionLogEntry
import com.quantumaes.yogatiming.domain.stats.SessionTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Журнал занятий поверх Room.
 *
 * Границы периода приезжают датами, а в SQL уходят строками ISO: колонка
 * `local_date` хранит именно их (D-S4), и преобразование — единственное, что
 * здесь происходит помимо маппинга проекций.
 */
@Singleton
class SessionLogRepositoryImpl
    @Inject
    constructor(
        private val dao: SessionLogDao,
    ) : SessionLogRepository {
        override suspend fun record(entry: SessionLogEntry): Long = dao.insert(entry.toEntity())

        override suspend fun delete(id: Long) = dao.delete(id)

        override fun observeSessions(
            from: LocalDate,
            to: LocalDate,
        ): Flow<List<SessionLogEntry>> =
            dao.observeSessions(from.iso(), to.iso()).map { rows -> rows.map(SessionLogEntity::toDomain) }

        override fun observeDays(
            from: LocalDate,
            to: LocalDate,
        ): Flow<List<SessionDay>> =
            dao.observeDays(from.iso(), to.iso()).map { rows -> rows.map(SessionDayProjection::toDomain) }

        override fun observeTotals(
            from: LocalDate,
            to: LocalDate,
        ): Flow<SessionTotals> = dao.observeTotals(from.iso(), to.iso()).map { it.toDomain() }

        override fun observeByProfile(
            from: LocalDate,
            to: LocalDate,
        ): Flow<List<ProfileTotals>> =
            dao.observeByProfile(from.iso(), to.iso()).map { rows -> rows.map(ProfileTotalsProjection::toDomain) }
    }

private fun LocalDate.iso(): String = toString()
