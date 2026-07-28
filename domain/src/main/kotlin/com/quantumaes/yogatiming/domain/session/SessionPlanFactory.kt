package com.quantumaes.yogatiming.domain.session

import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.Stage
import com.quantumaes.yogatiming.domain.model.StageType
import com.quantumaes.yogatiming.domain.model.alert.Alert
import com.quantumaes.yogatiming.domain.model.alert.AlertConfig
import com.quantumaes.yogatiming.timer.engine.model.AlertTrigger
import com.quantumaes.yogatiming.timer.engine.model.PlannedAlert
import com.quantumaes.yogatiming.timer.engine.model.PlannedStage
import com.quantumaes.yogatiming.timer.engine.model.ResolvedAlertConfig
import com.quantumaes.yogatiming.timer.engine.model.SessionPlan
import com.quantumaes.yogatiming.timer.engine.model.StageKind

private const val MS_IN_SECOND = 1_000L

/**
 * Максимальное смещение предупреждения, которое считается отсчётом последних
 * секунд. На этапах отдыха такой отсчёт выключается (решение B-9).
 */
private const val COUNTDOWN_OFFSET_SEC = 10

/**
 * Сборка плана сессии для `:timer-engine`.
 *
 * Здесь и только здесь разрешается наследование оповещений Profile → Stage
 * (docs/02-TIMER-CORE-DESIGN.md §3.1, решение C-6). Движок получает финальные
 * оповещения и не знает ни про наследование, ни про пресеты — это держит его
 * чистым, а поведение предсказуемым: что собрано при старте занятия, то и
 * прозвучит.
 *
 * Выключенные и заведомо немые оповещения в план не попадают вовсе: движку
 * незачем планировать событие, которое ничего не сделает.
 */
object SessionPlanFactory {
    /**
     * @return `null`, если профиль без этапов: запускать нечего (решение B-6).
     */
    fun create(profile: Profile): SessionPlan? {
        if (!profile.isRunnable) return null
        return SessionPlan(
            profileId = profile.id,
            profileName = profile.name,
            stages = profile.stages.sortedBy { it.sortOrder }.map { it.toPlannedStage(profile.defaultAlertConfig) },
        )
    }
}

/** Доменное оповещение из нагрузки, которую движок пронёс нетронутой. */
fun PlannedAlert.domainAlert(): Alert = payload as Alert

private fun Stage.toPlannedStage(profileDefaults: AlertConfig): PlannedStage =
    PlannedStage(
        id = id,
        name = name,
        kind = type.toStageKind(),
        colorTag = colorTag,
        plannedDurationMs = if (type == StageType.FREE) 0L else durationSec * MS_IN_SECOND,
        note = note,
        alerts = (alertConfig ?: profileDefaults).resolve(type),
    )

private fun StageType.toStageKind(): StageKind =
    when (this) {
        StageType.NORMAL -> StageKind.NORMAL
        StageType.TRANSITION -> StageKind.TRANSITION
        StageType.REST -> StageKind.REST
        StageType.FREE -> StageKind.FREE
    }

/**
 * Конфиг оповещений в готовом к исполнению виде.
 *
 * У FREE-этапа отбрасываются только предупреждения: они привязаны к концу,
 * которого нет. START и END остаются — END прозвучит в момент ручного
 * перехода (решение B-5).
 *
 * Отсчёт последних секунд на этапах отдыха выключается принудительно: тиканье
 * в шавасане — ровно то, что запрещает критерий A-3.
 *
 * Предупреждения с одинаковым смещением схлопываются: движок различает их по
 * смещению, и два оповещения «за минуту» стали бы одним непредсказуемым.
 */
private fun AlertConfig.resolve(type: StageType): ResolvedAlertConfig =
    ResolvedAlertConfig(
        start = start?.takeIf { !it.isSilent }?.let { PlannedAlert(AlertTrigger.START, payload = withVolume(it)) },
        warnings =
            if (type == StageType.FREE) {
                emptyList()
            } else {
                warnings
                    .filter { !it.isSilent && it.offsetSec > 0 }
                    .filterNot { type == StageType.REST && it.offsetSec <= COUNTDOWN_OFFSET_SEC }
                    .distinctBy { it.offsetSec }
                    .sortedByDescending { it.offsetSec }
                    .map { PlannedAlert(AlertTrigger.WARNING, it.offsetSec * MS_IN_SECOND, withVolume(it)) }
            },
        end = end?.takeIf { !it.isSilent }?.let { PlannedAlert(AlertTrigger.END, payload = withVolume(it)) },
    )

/**
 * Громкость тоже наследуется здесь, а не в момент проигрывания: иначе
 * проигрывателю пришлось бы тащить с собой конфиг владельца оповещения.
 */
private fun AlertConfig.withVolume(alert: Alert): Alert =
    if (alert.volumePercent != null) alert else alert.copy(volumePercent = masterVolumePercent)
