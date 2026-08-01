package com.quantumaes.yogatiming.core.database.mapper

import com.quantumaes.yogatiming.core.database.entity.ProfileTotalsProjection
import com.quantumaes.yogatiming.core.database.entity.SessionDayProjection
import com.quantumaes.yogatiming.core.database.entity.SessionLogEntity
import com.quantumaes.yogatiming.core.database.entity.SessionTotalsProjection
import com.quantumaes.yogatiming.domain.session.SessionOutcome
import com.quantumaes.yogatiming.domain.stats.ProfileTotals
import com.quantumaes.yogatiming.domain.stats.SessionDay
import com.quantumaes.yogatiming.domain.stats.SessionLogEntry
import com.quantumaes.yogatiming.domain.stats.SessionTotals
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Журнал: строка таблицы ↔ строка домена.
 *
 * Дата хранится строкой ISO (D-S4) и разбирается здесь. Испорченное значение
 * не роняет экран статистики: строку с непонятной датой лучше показать
 * сегодняшним днём, чем уронить приложение при открытии журнала. Взяться
 * такому значению неоткуда — колонку заполняет только [toEntity], — но экран
 * статистики читает файл, переживший Auto Backup и чужие версии приложения.
 */
internal fun SessionLogEntity.toDomain(): SessionLogEntry =
    SessionLogEntry(
        id = id,
        profileId = profileId,
        profileName = profileName,
        localDate = localDate.toLocalDateOrToday(),
        startedAtMs = startedAtMs,
        finishedAtMs = finishedAtMs,
        durationMs = durationMs,
        plannedMs = plannedMs,
        stagesCompleted = stagesCompleted,
        stageCount = stageCount,
        outcome = outcome.toOutcome(),
    )

internal fun SessionLogEntry.toEntity(): SessionLogEntity =
    SessionLogEntity(
        id = id,
        profileId = profileId,
        profileName = profileName,
        localDate = localDate.toString(),
        startedAtMs = startedAtMs,
        finishedAtMs = finishedAtMs,
        durationMs = durationMs,
        plannedMs = plannedMs,
        stagesCompleted = stagesCompleted,
        stageCount = stageCount,
        outcome = outcome.name,
    )

internal fun SessionDayProjection.toDomain(): SessionDay =
    SessionDay(
        date = localDate.toLocalDateOrToday(),
        sessionCount = sessionCount,
        durationMs = durationMs,
    )

internal fun SessionTotalsProjection.toDomain(): SessionTotals =
    SessionTotals(
        sessionCount = sessionCount,
        totalDurationMs = durationMs,
        daysPracticed = daysPracticed,
    )

internal fun ProfileTotalsProjection.toDomain(): ProfileTotals =
    ProfileTotals(
        profileName = profileName,
        sessionCount = sessionCount,
        totalDurationMs = durationMs,
    )

/** Неизвестный исход — «остановлено»: честнее преувеличить незавершённость. */
private fun String.toOutcome(): SessionOutcome =
    SessionOutcome.entries.firstOrNull { it.name == this } ?: SessionOutcome.STOPPED

private fun String.toLocalDateOrToday(): LocalDate =
    try {
        LocalDate.parse(this)
    } catch (_: DateTimeParseException) {
        LocalDate.now()
    }
