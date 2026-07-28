package com.quantumaes.yogatiming.domain.session

import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.Stage
import com.quantumaes.yogatiming.domain.model.StageType
import com.quantumaes.yogatiming.domain.model.alert.Alert
import com.quantumaes.yogatiming.domain.model.alert.AlertChannel
import com.quantumaes.yogatiming.domain.model.alert.AlertPresets
import com.quantumaes.yogatiming.domain.model.alert.AlertSound
import com.quantumaes.yogatiming.timer.engine.model.AlertTrigger
import com.quantumaes.yogatiming.timer.engine.model.StageKind
import org.junit.jupiter.api.Test

class SessionPlanFactoryTest {
    @Test
    fun `профиль без этапов запустить нельзя`() {
        assertThat(SessionPlanFactory.create(profile())).isNull()
    }

    @Test
    fun `этапы упорядочиваются по sortOrder, а длительность переводится в миллисекунды`() {
        val plan =
            SessionPlanFactory.create(
                profile(
                    stage("Шавасана", 600, sortOrder = 2),
                    stage("Разминка", 480, sortOrder = 1),
                ),
            )!!

        assertThat(plan.stages.map { it.name }).containsExactly("Разминка", "Шавасана").inOrder()
        assertThat(plan.stages.first().plannedDurationMs).isEqualTo(480_000L)
        assertThat(plan.plannedDurationMs).isEqualTo(1_080_000L)
    }

    @Test
    fun `этап без своего конфига наследует оповещения профиля`() {
        val plan = SessionPlanFactory.create(profile(stage("Асаны", 600)))!!

        val alerts = plan.stages.single().alerts
        assertThat(alerts.start?.trigger).isEqualTo(AlertTrigger.START)
        assertThat(alerts.warnings.map { it.offsetMs }).containsExactly(120_000L, 60_000L).inOrder()
        assertThat(alerts.end?.trigger).isEqualTo(AlertTrigger.END)
        assertThat(alerts.start?.domainAlert()?.voice).isEqualTo(AlertPresets.standard().start?.voice)
    }

    @Test
    fun `громкость наследуется от конфига при сборке плана`() {
        val config = AlertPresets.standard().copy(masterVolumePercent = 55)
        val plan = SessionPlanFactory.create(profile(stage("Асаны", 600, alertConfig = config)))!!

        val alerts = plan.stages.single().alerts
        assertThat(alerts.start?.domainAlert()?.volumePercent).isEqualTo(55)
        assertThat(alerts.warnings.map { it.domainAlert().volumePercent }).containsExactly(55, 55)
    }

    @Test
    fun `явная громкость оповещения сильнее общей`() {
        val config =
            AlertPresets.standard().copy(
                masterVolumePercent = 55,
                end = Alert(volumePercent = 100),
            )
        val plan = SessionPlanFactory.create(profile(stage("Асаны", 600, alertConfig = config)))!!

        assertThat(
            plan.stages
                .single()
                .alerts.end
                ?.domainAlert()
                ?.volumePercent,
        ).isEqualTo(100)
    }

    @Test
    fun `собственный конфиг этапа перекрывает профильный`() {
        val quiet = AlertPresets.silent()
        val plan = SessionPlanFactory.create(profile(stage("Шавасана", 600, alertConfig = quiet)))!!

        val alerts = plan.stages.single().alerts
        assertThat(alerts.warnings).isEmpty()
        assertThat(alerts.start?.domainAlert()?.sound).isEqualTo(AlertSound.SINGING_BOWL)
    }

    @Test
    fun `выключенные и немые оповещения в план не попадают`() {
        val config =
            AlertPresets
                .standard()
                .copy(
                    start = Alert(enabled = false),
                    warnings = listOf(Alert(offsetSec = 60, channels = emptySet())),
                )
        val plan = SessionPlanFactory.create(profile(stage("Асаны", 600, alertConfig = config)))!!

        val alerts = plan.stages.single().alerts
        assertThat(alerts.start).isNull()
        assertThat(alerts.warnings).isEmpty()
        assertThat(alerts.end).isNotNull()
    }

    @Test
    fun `у свободного этапа нет ни плановой длительности, ни предупреждений, но END остаётся`() {
        val plan = SessionPlanFactory.create(profile(stage("Свободный", 600, type = StageType.FREE)))!!

        val stage = plan.stages.single()
        assertThat(stage.kind).isEqualTo(StageKind.FREE)
        assertThat(stage.plannedDurationMs).isEqualTo(0L)
        assertThat(stage.alerts.warnings).isEmpty()
        assertThat(stage.alerts.end).isNotNull()
        assertThat(plan.hasFreeStages).isTrue()
    }

    @Test
    fun `на этапе отдыха отсчёт последних секунд выключается принудительно`() {
        val plan =
            SessionPlanFactory.create(
                profile(
                    stage("Шавасана", 600, type = StageType.REST, alertConfig = AlertPresets.maximum()),
                ),
            )!!

        val offsets =
            plan.stages
                .single()
                .alerts.warnings
                .map { it.offsetMs }
        assertThat(offsets).containsExactly(300_000L, 120_000L, 60_000L).inOrder()
    }

    @Test
    fun `предупреждения с одинаковым смещением схлопываются`() {
        val config =
            AlertPresets.standard().copy(
                warnings =
                    listOf(
                        Alert(offsetSec = 60, channels = setOf(AlertChannel.SOUND)),
                        Alert(offsetSec = 60, channels = setOf(AlertChannel.VIBRATION)),
                    ),
            )
        val plan = SessionPlanFactory.create(profile(stage("Асаны", 600, alertConfig = config)))!!

        assertThat(
            plan.stages
                .single()
                .alerts.warnings,
        ).hasSize(1)
    }

    private fun profile(vararg stages: Stage) =
        Profile(
            id = 7L,
            uuid = "b8b2a3f4-0000-4000-8000-000000000001",
            name = "Хатха 60 мин",
            stages = stages.toList(),
        )

    private fun stage(
        name: String,
        durationSec: Int,
        type: StageType = StageType.NORMAL,
        sortOrder: Int = 0,
        alertConfig: com.quantumaes.yogatiming.domain.model.alert.AlertConfig? = null,
    ) = Stage(name = name, type = type, durationSec = durationSec, sortOrder = sortOrder, alertConfig = alertConfig)
}
