package com.quantumaes.yogatiming.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ProfileTest {
    private fun stage(
        name: String,
        durationSec: Int,
        type: StageType = StageType.NORMAL,
    ) = Stage(name = name, durationSec = durationSec, type = type)

    @Test
    fun `общее время — сумма плановых длительностей этапов`() {
        val profile =
            profileWith(
                stage("Разминка", 480),
                stage("Асаны", 1800),
                stage("Шавасана", 600, StageType.REST),
            )

        assertThat(profile.totalDurationSec).isEqualTo(2880)
        assertThat(profile.hasFreeStages).isFalse()
    }

    @Test
    fun `FREE-этап не входит в сумму — она становится нижней границей, решение B-4`() {
        val profile =
            profileWith(
                stage("Разминка", 480),
                stage("Свободная практика", 0, StageType.FREE),
                stage("Шавасана", 600, StageType.REST),
            )

        assertThat(profile.totalDurationSec).isEqualTo(1080)
        assertThat(profile.hasFreeStages).isTrue()
    }

    @Test
    fun `профиль без этапов запускать нельзя — решение B-6`() {
        assertThat(profileWith().isRunnable).isFalse()
        assertThat(profileWith(stage("Разминка", 480)).isRunnable).isTrue()
    }

    @Test
    fun `неизвестные значения перечислений из БД деградируют к умолчанию`() {
        assertThat(ProfileCategory.fromName("НЕТ_ТАКОЙ")).isEqualTo(ProfileCategory.GENERAL)
        assertThat(ProfileCategory.fromName(null)).isEqualTo(ProfileCategory.GENERAL)
        assertThat(ProfileCategory.fromName("YIN")).isEqualTo(ProfileCategory.YIN)

        assertThat(TotalDurationMode.fromName("НЕТ_ТАКОГО")).isEqualTo(TotalDurationMode.SUM)
        assertThat(TotalDurationMode.fromName("FIXED")).isEqualTo(TotalDurationMode.FIXED)
    }

    @Test
    fun `FREE-этап не имеет плановой длительности, остальные имеют`() {
        assertThat(stage("Свободный", 0, StageType.FREE).hasPlannedDuration).isFalse()
        listOf(StageType.NORMAL, StageType.TRANSITION, StageType.REST).forEach { type ->
            assertThat(stage("Этап", 60, type).hasPlannedDuration).isTrue()
        }
    }

    private fun profileWith(vararg stages: Stage) =
        Profile(
            uuid = "test-uuid",
            name = "Тестовый профиль",
            stages = stages.toList(),
        )
}
