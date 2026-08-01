package com.quantumaes.yogatiming.domain.stats

import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.domain.session.SessionOutcome
import com.quantumaes.yogatiming.domain.session.SessionSummary
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private val MOSCOW: ZoneId = ZoneId.of("Europe/Moscow")
private val LISBON: ZoneId = ZoneId.of("Europe/Lisbon")

private const val MINUTE_MS = 60_000L

/**
 * Правила попадания занятия в журнал (docs/09-STATISTICS.md, решения D-S2 … D-S4).
 *
 * Проверяется то, что на устройстве воспроизводится только перелётом: день
 * занятия, начатого перед полуночью, и он же при чтении журнала из другой зоны.
 */
class SessionLogTest {
    @Test
    fun `занятие короче минуты в журнал не попадает`() {
        assertThat(SessionLog.entryFor(summary(durationMs = 59_999L), MOSCOW)).isNull()
    }

    @Test
    fun `ровно минута — уже занятие`() {
        assertThat(SessionLog.entryFor(summary(durationMs = SessionLog.MIN_DURATION_MS), MOSCOW)).isNotNull()
    }

    @Test
    fun `брошенное занятие записывается с пометкой, а не выбрасывается`() {
        val entry =
            SessionLog.entryFor(
                summary(durationMs = 40 * MINUTE_MS, outcome = SessionOutcome.STOPPED),
                MOSCOW,
            )!!

        assertThat(entry.outcome).isEqualTo(SessionOutcome.STOPPED)
        assertThat(entry.durationMs).isEqualTo(40 * MINUTE_MS)
    }

    @Test
    fun `занятие через полночь остаётся вечерним — день берётся по началу`() {
        val startedAt = wallMs(LocalDateTime.of(2026, 11, 3, 23, 40), MOSCOW)
        val entry =
            SessionLog.entryFor(
                summary(durationMs = 70 * MINUTE_MS, startedAtWallMs = startedAt),
                MOSCOW,
            )!!

        assertThat(entry.localDate).isEqualTo(LocalDate.of(2026, 11, 3))
    }

    /**
     * Тот же момент времени, две зоны. Занятие, проведённое в Москве в 23:30,
     * в лиссабонской зоне приходится на 20:30 того же дня, но само по себе
     * это ничего не гарантирует: дата обязана считаться зоной **записи** и
     * потом не пересчитываться (D-S4). Здесь проверяется именно то, что
     * функция берёт зону из аргумента, а не подставляет системную.
     */
    @Test
    fun `дата считается переданной зоной, а не системной`() {
        val startedAt = wallMs(LocalDateTime.of(2026, 1, 1, 0, 30), MOSCOW)
        val summary = summary(durationMs = 60 * MINUTE_MS, startedAtWallMs = startedAt)

        assertThat(SessionLog.entryFor(summary, MOSCOW)?.localDate).isEqualTo(LocalDate.of(2026, 1, 1))
        // Лиссабон в этот момент ещё 31 декабря — три часа назад.
        assertThat(SessionLog.entryFor(summary, LISBON)?.localDate).isEqualTo(LocalDate.of(2025, 12, 31))
    }

    @Test
    fun `строка журнала повторяет итоги занятия без потерь`() {
        val summary = summary(durationMs = 55 * MINUTE_MS)

        val entry = SessionLog.entryFor(summary, MOSCOW)!!

        assertThat(entry.profileId).isEqualTo(summary.profileId)
        assertThat(entry.profileName).isEqualTo(summary.profileName)
        assertThat(entry.startedAtMs).isEqualTo(summary.startedAtWallMs)
        assertThat(entry.finishedAtMs).isEqualTo(summary.finishedAtWallMs)
        assertThat(entry.plannedMs).isEqualTo(summary.plannedDurationMs)
        assertThat(entry.stagesCompleted).isEqualTo(summary.stagesCompleted)
        assertThat(entry.stageCount).isEqualTo(summary.stageCount)
    }

    private fun summary(
        durationMs: Long,
        outcome: SessionOutcome = SessionOutcome.COMPLETED,
        startedAtWallMs: Long = wallMs(LocalDateTime.of(2026, 11, 3, 18, 5), MOSCOW),
    ) = SessionSummary(
        profileId = 7L,
        profileName = "Хатха 60 мин",
        outcome = outcome,
        startedAtWallMs = startedAtWallMs,
        finishedAtWallMs = startedAtWallMs + durationMs,
        plannedDurationMs = 60 * MINUTE_MS,
        actualDurationMs = durationMs,
        stagesCompleted = 5,
        stageCount = 6,
    )

    private fun wallMs(
        local: LocalDateTime,
        zone: ZoneId,
    ): Long = local.atZone(zone).toInstant().toEpochMilli()
}

/** Сводка за период: среднее занятие и пустой период. */
class SessionTotalsTest {
    @Test
    fun `среднее пустого периода — ноль, а не деление на ноль`() {
        assertThat(SessionTotals().averageDurationMs).isEqualTo(0L)
    }

    @Test
    fun `среднее занятие считается по факту`() {
        val totals = SessionTotals(sessionCount = 4, totalDurationMs = 240 * MINUTE_MS, daysPracticed = 3)

        assertThat(totals.averageDurationMs).isEqualTo(60 * MINUTE_MS)
    }
}
