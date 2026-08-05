package com.quantumaes.yogatiming.domain.session

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

private const val MINUTE_MS = 60_000L

/**
 * Итоги занятия в той их части, которая решает судьбу нового профиля
 * (замечание 7 полевой проверки 2026-08-04).
 *
 * Предложение «сохранить как новый профиль» появляется по [SessionSummary
 * .wasAdjusted], а содержимое профиля берётся из
 * [SessionSummary.adjustedDurationsSec]. Ошибка в любом из двух — либо
 * предложение сохранить занятие, прошедшее ровно по плану, либо профиль с
 * длительностями, которых инструктор не задавал.
 */
class SessionSummaryTest {
    @Test
    fun `занятие без правок сохранять нечем`() {
        val summary = summary(stage("Асаны", planned = 10 * MINUTE_MS, effective = 10 * MINUTE_MS))

        assertThat(summary.wasAdjusted).isFalse()
        assertThat(summary.adjustedDurationsSec).isEmpty()
    }

    @Test
    fun `в новый профиль идут только изменённые этапы`() {
        val summary =
            summary(
                stage("Разминка", planned = 5 * MINUTE_MS, effective = 5 * MINUTE_MS),
                stage("Асаны", planned = 10 * MINUTE_MS, effective = 12 * MINUTE_MS, id = 2),
            )

        assertThat(summary.wasAdjusted).isTrue()
        // Этап, которого не касались, в карту не попадает: копия профиля возьмёт
        // его длительность из исходника.
        assertThat(summary.adjustedDurationsSec).containsExactly(2L, 12 * 60)
    }

    @Test
    fun `сокращение этапа — такая же правка, как удлинение`() {
        val summary = summary(stage("Шавасана", planned = 10 * MINUTE_MS, effective = 7 * MINUTE_MS))

        assertThat(summary.wasAdjusted).isTrue()
        assertThat(summary.adjustedDurationsSec).containsExactly(1L, 7 * 60)
    }

    @Test
    fun `часы занятия считают паузу этапа, а практика — нет`() {
        val summary =
            summary(stage("Асаны", planned = 10 * MINUTE_MS, effective = 10 * MINUTE_MS))
                .copy(actualDurationMs = 50 * MINUTE_MS, holdMs = 5 * MINUTE_MS, targetDurationMs = 60 * MINUTE_MS)

        assertThat(summary.occupiedMs).isEqualTo(55 * MINUTE_MS)
        // В целевое время уложились: зал был занят 55 минут из 60.
        assertThat(summary.targetDeviationMs).isEqualTo(-5 * MINUTE_MS)
    }
}

private fun stage(
    name: String,
    planned: Long,
    effective: Long,
    id: Long = 1,
) = StageOutcome(stageId = id, name = name, plannedMs = planned, effectiveMs = effective)

private fun summary(vararg stages: StageOutcome) =
    SessionSummary(
        profileId = 1,
        profileName = "Хатха 60 мин",
        outcome = SessionOutcome.COMPLETED,
        startedAtWallMs = 1_800_000_000_000,
        finishedAtWallMs = 1_800_003_600_000,
        plannedDurationMs = stages.sumOf { it.plannedMs },
        actualDurationMs = stages.sumOf { it.effectiveMs },
        stagesCompleted = stages.size,
        stageCount = stages.size,
        stages = stages.toList(),
    )
