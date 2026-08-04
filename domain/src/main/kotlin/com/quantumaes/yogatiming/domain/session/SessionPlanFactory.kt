package com.quantumaes.yogatiming.domain.session

import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.Stage
import com.quantumaes.yogatiming.domain.model.StageType
import com.quantumaes.yogatiming.domain.model.alert.Alert
import com.quantumaes.yogatiming.domain.model.alert.AlertChannel
import com.quantumaes.yogatiming.domain.model.alert.AlertConfig
import com.quantumaes.yogatiming.domain.model.alert.AlertSound
import com.quantumaes.yogatiming.domain.model.alert.VoicePhrase
import com.quantumaes.yogatiming.timer.engine.model.AlertTrigger
import com.quantumaes.yogatiming.timer.engine.model.PlannedAlert
import com.quantumaes.yogatiming.timer.engine.model.PlannedStage
import com.quantumaes.yogatiming.timer.engine.model.ResolvedAlertConfig
import com.quantumaes.yogatiming.timer.engine.model.SessionBudget
import com.quantumaes.yogatiming.timer.engine.model.SessionPlan
import com.quantumaes.yogatiming.timer.engine.model.StageKind
import com.quantumaes.yogatiming.timer.engine.model.StageSide

private const val MS_IN_SECOND = 1_000L

/**
 * Максимальное смещение предупреждения, которое считается отсчётом последних
 * секунд. На этапах отдыха такой отсчёт выключается (решение B-9).
 */
private const val COUNTDOWN_OFFSET_SEC = 10

/**
 * Как называть стороны двусторонней асаны.
 *
 * Приходят готовыми строками, а не ключами ресурсов: `:domain` — чистый JVM и
 * про Android не знает, а название стороны обязано быть локализованным и
 * произносимым. Кто собирает план, тот и подставляет слова своего языка.
 *
 * Правая идёт первой: так принято в традиции, и предсказуемый порядок важнее
 * настройки, которую всё равно никто не откроет.
 */
data class SideLabels(
    val first: String,
    val second: String,
) {
    companion object {
        /** Значение для тестов и превью: без локали, но различимо. */
        val DEBUG = SideLabels(first = "R", second = "L")
    }
}

/**
 * Сборка плана сессии для `:timer-engine`.
 *
 * Здесь и только здесь разрешается наследование оповещений Profile → Stage
 * (docs/02-TIMER-CORE-DESIGN.md §3.1, решение C-6). Движок получает финальные
 * оповещения и не знает ни про наследование, ни про пресеты — это держит его
 * чистым, а поведение предсказуемым: что собрано при старте занятия, то и
 * прозвучит.
 *
 * Здесь же двусторонние этапы разворачиваются в две половины, а целевое время
 * профиля превращается в бюджет занятия: движок работает с плоским списком
 * этапов и одним числом «сколько времени есть» — ни про зеркальные асаны, ни
 * про аренду зала ему знать незачем.
 *
 * Выключенные и заведомо немые оповещения в план не попадают вовсе: движку
 * незачем планировать событие, которое ничего не сделает.
 */
object SessionPlanFactory {
    /**
     * @return `null`, если профиль без этапов: запускать нечего (решение B-6).
     */
    fun create(
        profile: Profile,
        sides: SideLabels = SideLabels.DEBUG,
    ): SessionPlan? {
        if (!profile.isRunnable) return null
        return SessionPlan(
            profileId = profile.id,
            profileName = profile.name,
            stages =
                profile.stages
                    .sortedBy { it.sortOrder }
                    .flatMap { it.toPlannedStages(profile.defaultAlertConfig, sides) },
            budget = profile.toBudget(),
        )
    }
}

/** Доменное оповещение из нагрузки, которую движок пронёс нетронутой. */
fun PlannedAlert.domainAlert(): Alert = payload as Alert

/**
 * Целевое время профиля в терминах движка.
 *
 * Отсечка молчит, если голос выключен целиком, — но об этом знает не фабрика,
 * а проигрыватель: он один и тот же для всех оповещений, и заводить второе
 * место, где решается «звучит ли голос», значит однажды их рассогласовать.
 */
private fun Profile.toBudget(): SessionBudget? {
    val target = targetDurationSec?.takeIf { it > 0 } ?: return null
    val offset = wrapUpOffsetSec.coerceAtLeast(0)
    return SessionBudget(
        targetMs = target * MS_IN_SECOND,
        toleranceMs = targetToleranceSec.coerceAtLeast(0) * MS_IN_SECOND,
        wrapUpOffsetMs = offset * MS_IN_SECOND,
        wrapUpAlert =
            offset.takeIf { it > 0 }?.let {
                PlannedAlert(
                    trigger = AlertTrigger.WRAP_UP,
                    offsetMs = offset * MS_IN_SECOND,
                    payload = wrapUpAlert(offset, defaultAlertConfig.masterVolumePercent),
                )
            },
    )
}

/**
 * Чем звучит отсечка.
 *
 * Колокол, а не гонг: гонгом размечены границы этапов, и отсечка обязана
 * отличаться от них на слух — иначе инструктор примет её за очередной переход
 * и не поймёт, что речь о конце занятия. Голос при этом главный канал: сам по
 * себе сигнал не сообщает, сколько осталось.
 */
private fun wrapUpAlert(
    offsetSec: Int,
    volumePercent: Int,
): Alert =
    Alert(
        offsetSec = offsetSec,
        channels = setOf(AlertChannel.SOUND, AlertChannel.VOICE, AlertChannel.VIBRATION),
        sound = AlertSound.BELL,
        voice = VoicePhrase.WRAP_UP,
        volumePercent = volumePercent,
    )

/**
 * Этап профиля в терминах плана: один обычный или две зеркальные половины.
 *
 * Половины несут один и тот же [Stage.id] — это по-прежнему один этап профиля,
 * и движок опознаёт пару именно по совпадению идентификатора со сменой стороны.
 * Заметка инструктора повторяется на обеих: она про асану, а не про сторону.
 */
private fun Stage.toPlannedStages(
    profileDefaults: AlertConfig,
    sides: SideLabels,
): List<PlannedStage> {
    val base = toPlannedStage(profileDefaults)
    if (!isBilateral) return listOf(base)
    return listOf(
        base.withSide(StageSide.FIRST, sides.first),
        base.withSide(StageSide.SECOND, sides.second),
    )
}

private fun PlannedStage.withSide(
    side: StageSide,
    label: String,
): PlannedStage =
    copy(
        name = "$name · $label",
        voiceName = voiceName?.let { "$it, $label" },
        side = side,
    )

private fun Stage.toPlannedStage(profileDefaults: AlertConfig): PlannedStage =
    PlannedStage(
        id = id,
        name = name,
        kind = type.toStageKind(),
        colorTag = colorTag,
        plannedDurationMs = if (type == StageType.FREE) 0L else durationSec * MS_IN_SECOND,
        note = note,
        voiceName = voiceName,
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
