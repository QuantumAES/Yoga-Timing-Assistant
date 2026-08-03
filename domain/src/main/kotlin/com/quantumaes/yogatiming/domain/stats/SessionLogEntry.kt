package com.quantumaes.yogatiming.domain.stats

import com.quantumaes.yogatiming.domain.session.SessionOutcome
import com.quantumaes.yogatiming.domain.session.SessionSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Строка журнала занятий (docs/09-STATISTICS.md, решение D-S1).
 *
 * Одна строка на проведённое занятие — не агрегаты: из строк выводится любой
 * разрез, из агрегатов не выводится ничего сверх заранее задуманного. Пять
 * занятий в день в течение десяти лет — восемнадцать тысяч строк и пара
 * мегабайт; чистить журнал по сроку не от чего.
 *
 * @param profileId `null`, если профиль с тех пор удалён. Занятие при этом
 *   остаётся в журнале: журнал — запись о прошлом, а не проекция текущего
 *   списка профилей (D-S6).
 * @param profileName имя профиля **на момент занятия**. Денормализовано
 *   намеренно, по той же причине: переименование профиля не переписывает
 *   историю.
 * @param localDate день занятия в локальной зоне, зафиксированный при записи
 *   (D-S4). Не вычисляется из [startedAtMs] при чтении: перелёт в другой
 *   часовой пояс не должен переносить занятие на соседний день, а группировка
 *   по дню не должна зависеть от того, где открыт экран статистики.
 * @param durationMs время практики без пауз — то же число, что на экране
 *   итогов. Именно оно суммируется за период (D-S5): получасовая пауза
 *   проведённым занятием не является.
 */
data class SessionLogEntry(
    val id: Long = 0,
    val profileId: Long?,
    val profileName: String,
    val localDate: LocalDate,
    val startedAtMs: Long,
    val finishedAtMs: Long,
    val durationMs: Long,
    val plannedMs: Long,
    val stagesCompleted: Int,
    val stageCount: Int,
    val outcome: SessionOutcome,
)

/** Правила попадания занятия в журнал. */
object SessionLog {
    /**
     * Короче минуты — в журнал не попадает (D-S3).
     *
     * Иначе календарь засорят проверочные запуски: инструктор проверяет звук,
     * запускает профиль, слышит гонг и останавливает. Константа, а не
     * настройка: настройка здесь означала бы, что пользователь должен думать
     * о структуре своего журнала до того, как увидит его первый раз.
     */
    const val MIN_DURATION_MS = 60_000L

    /**
     * Попадёт ли занятие в журнал.
     *
     * Отдельно от [entryFor] потому, что об этом спрашивает экран итогов:
     * порог, о котором нигде не сказано, читается как «статистика не
     * работает» — именно так он и был понят в первой же полевой проверке
     * (2026-08-03). Правило осталось прежним, молчание — нет.
     */
    fun isRecordable(summary: SessionSummary): Boolean = summary.actualDurationMs >= MIN_DURATION_MS

    /**
     * Строка журнала по итогам занятия — или `null`, если занятие короче порога.
     *
     * День берётся по началу занятия, а не по концу: занятие, начатое в 23:40
     * и законченное в 00:50, инструктор помнит вечерним, и в отчёте студии оно
     * стоит тем же числом, что и в его расписании.
     *
     * Брошенные занятия записываются наравне с доведёнными до конца (D-S2):
     * занятие, остановленное на сороковой минуте, состоялось, и вычёркивать
     * его — врать в отчёте. Отличие видно по [SessionLogEntry.outcome].
     */
    fun entryFor(
        summary: SessionSummary,
        zone: ZoneId,
    ): SessionLogEntry? {
        if (!isRecordable(summary)) return null
        return SessionLogEntry(
            profileId = summary.profileId,
            profileName = summary.profileName,
            localDate = Instant.ofEpochMilli(summary.startedAtWallMs).atZone(zone).toLocalDate(),
            startedAtMs = summary.startedAtWallMs,
            finishedAtMs = summary.finishedAtWallMs,
            durationMs = summary.actualDurationMs,
            plannedMs = summary.plannedDurationMs,
            stagesCompleted = summary.stagesCompleted,
            stageCount = summary.stageCount,
            outcome = summary.outcome,
        )
    }
}

/** Один день календаря: сколько занятий и сколько времени (запрос `observeDays`). */
data class SessionDay(
    val date: LocalDate,
    val sessionCount: Int,
    val durationMs: Long,
)

/**
 * Сводка за период.
 *
 * @param daysPracticed сколько дней периода содержат хотя бы одно занятие —
 *   не длина периода и не число занятий: два занятия в один день это один день
 *   практики.
 */
data class SessionTotals(
    val sessionCount: Int = 0,
    val totalDurationMs: Long = 0,
    val daysPracticed: Int = 0,
) {
    /** Среднее занятие за период. Ноль занятий — ноль, а не деление на ноль. */
    val averageDurationMs: Long get() = if (sessionCount == 0) 0 else totalDurationMs / sessionCount
}

/** «По профилям» в сводке: по какому профилю сколько проведено. */
data class ProfileTotals(
    val profileName: String,
    val sessionCount: Int,
    val totalDurationMs: Long,
)
