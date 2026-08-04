package com.quantumaes.yogatiming.timer.engine.schedule

import com.quantumaes.yogatiming.timer.engine.model.PlannedAlert
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionState
import com.quantumaes.yogatiming.timer.engine.model.sessionElapsedMs

/**
 * Префикс событий, привязанных к занятию, а не к этапу.
 *
 * Отметка «уже сработало» у таких событий обязана пережить смену этапа: вход в
 * новый этап очищает [SessionState.firedAlertIds], и без этого различия отсечка
 * бюджета звучала бы заново на каждом переходе.
 */
internal const val SESSION_EVENT_PREFIX = "session:"

/**
 * Расписание текущего этапа целиком.
 *
 * Пересчитывается заново после каждой команды — инкрементального состояния у
 * планировщика нет, поэтому рассинхронизироваться после паузы, правки ±30 с
 * или ручного перехода нечему.
 *
 * У FREE-этапа расписание пусто: конца нет, значит нет и дедлайнов
 * (решение B-5). START-оповещение в расписание не входит — оно срабатывает
 * в момент входа в этап, а не по дедлайну.
 */
fun SessionState.scheduleForCurrentStage(now: Long): List<ScheduledEvent> {
    val stage = currentStage
    if (!stage.kind.hasDeadline) return emptyList()

    val duration = effectiveDurationMs(currentIndex)
    val endAt = virtualStageStartMs(now) + duration

    return buildList {
        stage.alerts.warnings
            // Предупреждение, которое приходится на момент до начала этапа,
            // пропускается молча: «за 2 мин» на 90-секундном этапе (решение B-7).
            .filter { it.offsetMs in 1L..<duration }
            .forEach {
                add(
                    ScheduledEvent(warningId(currentIndex, it), endAt - it.offsetMs, ScheduledEvent.Kind.Alert(it)),
                )
            }

        stage.alerts.end?.let {
            add(ScheduledEvent(endAlertId(currentIndex), endAt, ScheduledEvent.Kind.Alert(it)))
        }

        add(ScheduledEvent(stageEndId(currentIndex), endAt, ScheduledEvent.Kind.StageEnd))
    }.sortedBy { it.atElapsedMs }
}

/**
 * Отсечка бюджета — единственное событие, живущее не в этапе, а в занятии.
 *
 * Считается от часов занятия ([SessionState.sessionElapsedMs]), поэтому ни
 * ручной переход, ни правка ±30 с её не двигают: в этом весь смысл бюджета.
 * Пустой список, если бюджета нет, отсечка выключена или сигналить нечем.
 */
fun SessionState.scheduleForBudget(now: Long): List<ScheduledEvent> {
    val budget = plan.budget ?: return emptyList()
    val alert = budget.wrapUpAlert ?: return emptyList()
    val at = budget.wrapUpAtMs ?: return emptyList()
    val sessionStart = now - sessionElapsedMs(now)
    return listOf(ScheduledEvent(WRAP_UP_ID, sessionStart + at, ScheduledEvent.Kind.Alert(alert)))
}

/** Всё, чего ждёт занятие прямо сейчас: события этапа и события бюджета. */
fun SessionState.schedule(now: Long): List<ScheduledEvent> =
    (scheduleForCurrentStage(now) + scheduleForBudget(now)).sortedBy { it.atElapsedMs }

/**
 * Ближайший момент, ради которого стоит просыпаться; `null` — спать до команды.
 *
 * Именно это число превращает «тик пять раз в секунду» в ~25 пробуждений за
 * часовое занятие, и оно же взводит watchdog-аларм (ADR-001).
 */
fun SessionState.nextDeadline(now: Long): Long? =
    if (runState != RunState.RUNNING) {
        null
    } else {
        schedule(now)
            .filter { it.id !in firedAlertIds && it.atElapsedMs > now }
            .minOfOrNull { it.atElapsedMs }
    }

/** Наступившие, но ещё не отработавшие события — от самого раннего. */
fun SessionState.dueEvents(now: Long): List<ScheduledEvent> =
    schedule(now).filter { it.id !in firedAlertIds && it.atElapsedMs <= now }

/**
 * Идентификаторы событий, чьё время уже прошло.
 *
 * Нужны после правки ±30 с: отметка «уже сработало» сохраняется только для
 * того, что и по новому расписанию осталось в прошлом. Всё, что правка увела
 * в будущее, обязано сработать заново — иначе продление этапа на минуту
 * съедало бы предупреждение «осталась минута».
 */
fun SessionState.passedEventIds(now: Long): Set<String> =
    schedule(now).filter { it.atElapsedMs <= now }.mapTo(mutableSetOf()) { it.id }

/**
 * Отметки, которые переживают вход в новый этап.
 *
 * У этапа своё расписание и своя защита от повторов — она начинается с чистого
 * листа. У занятия расписание одно на всё занятие, и забывать его отметки при
 * каждом переходе значит проигрывать отсечку по разу на этап.
 */
fun SessionState.keptAlertIds(): Set<String> =
    firedAlertIds.filterTo(mutableSetOf()) {
        it.startsWith(SESSION_EVENT_PREFIX)
    }

private fun warningId(
    index: Int,
    alert: PlannedAlert,
): String = "stage$index:warn:${alert.offsetMs}"

/** Идентификатор END-оповещения этапа. Общий для расписания и ручного перехода. */
internal fun endAlertId(index: Int): String = "stage$index:end"

/** Идентификатор отсечки бюджета. Занятие проходит её ровно один раз. */
internal const val WRAP_UP_ID = "${SESSION_EVENT_PREFIX}wrapup"

private fun stageEndId(index: Int): String = "stage$index:complete"
