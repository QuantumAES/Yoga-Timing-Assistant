package com.quantumaes.yogatiming.timer.service

import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.ProfileSummary
import com.quantumaes.yogatiming.domain.model.Stage
import com.quantumaes.yogatiming.domain.model.alert.AlertPresets
import com.quantumaes.yogatiming.domain.repository.ProfileFilter
import com.quantumaes.yogatiming.domain.repository.ProfileRepository
import com.quantumaes.yogatiming.domain.repository.SessionLogRepository
import com.quantumaes.yogatiming.domain.stats.ProfileTotals
import com.quantumaes.yogatiming.domain.stats.SessionDay
import com.quantumaes.yogatiming.domain.stats.SessionLogEntry
import com.quantumaes.yogatiming.domain.stats.SessionTotals
import com.quantumaes.yogatiming.timer.engine.TimeSource
import com.quantumaes.yogatiming.timer.engine.persist.PersistedSession
import com.quantumaes.yogatiming.timer.engine.persist.SessionStore
import com.quantumaes.yogatiming.timer.service.watchdog.Watchdog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

private const val TEN_MINUTES_SEC = 600

/** Часы под ручным управлением. */
class FakeTimeSource(
    var elapsedMs: Long,
    var wallMs: Long,
) : TimeSource {
    override fun elapsed(): Long = elapsedMs

    override fun wall(): Long = wallMs
}

/** Хранилище снимка в памяти: единственное поле — это и есть весь контракт. */
class FakeSessionStore(
    var saved: PersistedSession? = null,
) : SessionStore {
    override suspend fun save(session: PersistedSession) {
        saved = session
    }

    override suspend fun load(): PersistedSession? = saved

    override suspend fun clear() {
        saved = null
    }
}

class FakeWatchdog : Watchdog {
    var armedAt: Long? = null
        private set

    var cancelled: Boolean = false
        private set

    override val exactAlarmsUnavailable: Boolean = false

    override fun rearm(stageEndElapsedMs: Long?) {
        armedAt = stageEndElapsedMs
        cancelled = stageEndElapsedMs == null
    }

    override fun cancel() {
        armedAt = null
        cancelled = true
    }
}

class FakeProfileRepository(
    private val profile: Profile,
) : ProfileRepository {
    override fun observeProfileSummaries(filter: ProfileFilter): Flow<List<ProfileSummary>> = flowOf(emptyList())

    override fun observeProfile(id: Long): Flow<Profile?> = flowOf(profile.takeIf { it.id == id })

    override suspend fun getProfile(id: Long): Profile? = profile.takeIf { it.id == id }

    override suspend fun saveProfile(profile: Profile): Long = profile.id

    override suspend fun deleteProfile(id: Long) = Unit

    override suspend fun duplicateProfile(
        id: Long,
        newName: String,
    ): Long = id

    override suspend fun setFavorite(
        id: Long,
        isFavorite: Boolean,
    ) = Unit
}

/**
 * Журнал в памяти: список записанных занятий и, при необходимости, отказ базы.
 *
 * Отказ нужен отдельным полем — запись в журнал не имеет права уронить занятие,
 * и проверить это можно только заставив хранилище упасть.
 */
class FakeSessionLogRepository(
    var failing: Boolean = false,
) : SessionLogRepository {
    val recorded = mutableListOf<SessionLogEntry>()

    override suspend fun record(entry: SessionLogEntry): Long {
        check(!failing) { "журнал недоступен" }
        recorded += entry
        return recorded.size.toLong()
    }

    override suspend fun delete(id: Long) {
        recorded.removeAll { it.id == id }
    }

    override fun observeSessions(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<SessionLogEntry>> = flowOf(recorded.toList())

    override fun observeDays(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<SessionDay>> = flowOf(emptyList())

    override fun observeTotals(
        from: LocalDate,
        to: LocalDate,
    ): Flow<SessionTotals> = flowOf(SessionTotals())

    override fun observeByProfile(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<ProfileTotals>> = flowOf(emptyList())
}

fun demoProfile(
    id: Long,
    stages: List<Stage> = defaultStages(),
): Profile =
    Profile(
        id = id,
        uuid = "0ba1f6d8-0000-4000-8000-000000000001",
        name = "Хатха 60 мин",
        defaultAlertConfig = AlertPresets.standard(),
        stages = stages,
    )

private fun defaultStages(): List<Stage> =
    listOf(
        Stage(name = "Разминка", durationSec = TEN_MINUTES_SEC, sortOrder = 0),
        Stage(name = "Асаны", durationSec = TEN_MINUTES_SEC, sortOrder = 1),
        Stage(name = "Шавасана", durationSec = TEN_MINUTES_SEC, sortOrder = 2),
    )
