package com.quantumaes.yogatiming.domain.stats

import com.google.common.truth.Truth.assertThat
import com.quantumaes.yogatiming.domain.session.SessionOutcome
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private val MOSCOW: ZoneId = ZoneId.of("Europe/Moscow")

private const val MINUTE_MS = 60_000L

private val LABELS =
    SessionCsvLabels(
        date = "Дата",
        start = "Начало",
        finish = "Завершение",
        duration = "Минут",
        planned = "План, мин",
        profile = "Профиль",
        stages = "Этапы",
        outcome = "Исход",
        completed = "проведено",
        stopped = "остановлено",
    )

/**
 * Выгрузка журнала в CSV (docs/09-STATISTICS.md, фаза S7).
 *
 * Проверяется то, из-за чего файл не открывается в таблице: разделитель,
 * метка кодировки, перевод строки и экранирование имени профиля. Всё это
 * ломается один раз и целиком, а замечают это уже в отчёте студии.
 */
class SessionCsvTest {
    @Test
    fun `файл начинается меткой кодировки и заголовком колонок`() {
        val csv = SessionCsv.render(listOf(entry()), LABELS, MOSCOW)

        assertThat(csv).startsWith(SessionCsv.BOM)
        assertThat(csv.removePrefix(SessionCsv.BOM).lineSequence().first())
            .isEqualTo("Дата;Начало;Завершение;Минут;План, мин;Профиль;Этапы;Исход")
    }

    @Test
    fun `занятие превращается в строку целиком`() {
        // 18:05 → 19:03 по московской зоне, 58 минут факта при часе плана.
        val csv =
            SessionCsv.render(
                listOf(
                    entry(
                        date = LocalDate.of(2026, 11, 3),
                        startedAt = LocalDateTime.of(2026, 11, 3, 18, 5),
                        durationMs = 58 * MINUTE_MS,
                        plannedMs = 60 * MINUTE_MS,
                        stagesCompleted = 6,
                        stageCount = 6,
                    ),
                ),
                LABELS,
                MOSCOW,
            )

        assertThat(csv.rows().single())
            .isEqualTo("2026-11-03;18:05;19:03;58;60;Хатха 60;6/6;проведено")
    }

    @Test
    fun `остановленное вручную занятие помечено`() {
        val csv =
            SessionCsv.render(
                listOf(entry(outcome = SessionOutcome.STOPPED, stagesCompleted = 4)),
                LABELS,
                MOSCOW,
            )

        assertThat(csv.rows().single()).endsWith("4/6;остановлено")
    }

    /**
     * Порядок в файле обратный экранному: на экране сверху ждут последнее
     * занятие, в отчёте — начало месяца.
     */
    @Test
    fun `строки идут от старых к свежим`() {
        val csv =
            SessionCsv.render(
                listOf(
                    entry(date = LocalDate.of(2026, 11, 9), startedAt = LocalDateTime.of(2026, 11, 9, 7, 0)),
                    entry(date = LocalDate.of(2026, 11, 2), startedAt = LocalDateTime.of(2026, 11, 2, 18, 0)),
                ),
                LABELS,
                MOSCOW,
            )

        assertThat(csv.rows().map { it.substringBefore(SessionCsv.SEPARATOR) })
            .containsExactly("2026-11-02", "2026-11-09")
            .inOrder()
    }

    /**
     * Имя профиля пишет пользователь, и точка с запятой в нём разъезжается на
     * две колонки, унося за собой всю строку.
     */
    @Test
    fun `имя профиля с разделителем и кавычкой экранируется`() {
        val csv = SessionCsv.render(listOf(entry(profileName = "Хатха 60; \"вечер\"")), LABELS, MOSCOW)

        // Поле целиком в кавычках, внутренняя кавычка удвоена — колонки на
        // месте, и таблица прочтёт имя одной ячейкой.
        assertThat(csv.rows().single())
            .isEqualTo("2026-11-03;18:05;19:03;58;60;\"Хатха 60; \"\"вечер\"\"\";6/6;проведено")
    }

    @Test
    fun `строки разделены переводом строки по RFC 4180`() {
        val csv = SessionCsv.render(listOf(entry(), entry()), LABELS, MOSCOW)

        assertThat(csv).endsWith(SessionCsv.LINE_BREAK)
        // Три перевода: после заголовка, после первой строки и в конце файла.
        assertThat(csv.split(SessionCsv.LINE_BREAK)).hasSize(4)
    }

    @Test
    fun `пустой журнал даёт файл из одного заголовка`() {
        val csv = SessionCsv.render(emptyList(), LABELS, MOSCOW)

        assertThat(csv.rows()).isEmpty()
    }

    /** Секунды в отчёте не считают: минуты округляются к ближайшей. */
    @Test
    fun `неполная минута округляется к ближайшей`() {
        val csv = SessionCsv.render(listOf(entry(durationMs = 58 * MINUTE_MS + 40_000L)), LABELS, MOSCOW)

        assertThat(csv.rows().single().split(SessionCsv.SEPARATOR)[3]).isEqualTo("59")
    }

    /**
     * Время в файле всегда 24-часовое, даже если на устройстве 12-часовой
     * формат: «6:05 PM» в колонке таблицы не сортируется и не вычитается.
     */
    @Test
    fun `время пишется в 24-часовом формате`() {
        val csv =
            SessionCsv.render(
                listOf(entry(startedAt = LocalDateTime.of(2026, 11, 3, 18, 5))),
                LABELS,
                MOSCOW,
            )

        assertThat(csv.rows().single().split(SessionCsv.SEPARATOR)[1]).isEqualTo("18:05")
    }

    @Test
    fun `имя файла называет период`() {
        val november = LocalDate.of(2026, 11, 12)

        assertThat(SessionCsv.fileName(StatsPeriod.of(StatsPeriodType.MONTH, november)))
            .isEqualTo("yoga-journal-2026-11.csv")
        assertThat(SessionCsv.fileName(StatsPeriod.of(StatsPeriodType.YEAR, november)))
            .isEqualTo("yoga-journal-2026.csv")
        assertThat(SessionCsv.fileName(StatsPeriod.of(StatsPeriodType.WEEK, november)))
            .isEqualTo("yoga-journal-2026-11-09_2026-11-15.csv")
        assertThat(SessionCsv.fileName(StatsPeriod.of(StatsPeriodType.ALL, november)))
            .isEqualTo("yoga-journal-all.csv")
    }

    /** Строки файла без метки кодировки и без заголовка колонок. */
    private fun String.rows(): List<String> =
        removePrefix(SessionCsv.BOM)
            .split(SessionCsv.LINE_BREAK)
            .drop(1)
            .filter { it.isNotEmpty() }

    private fun entry(
        date: LocalDate = LocalDate.of(2026, 11, 3),
        startedAt: LocalDateTime = LocalDateTime.of(2026, 11, 3, 18, 5),
        durationMs: Long = 58 * MINUTE_MS,
        plannedMs: Long = 60 * MINUTE_MS,
        profileName: String = "Хатха 60",
        stagesCompleted: Int = 6,
        stageCount: Int = 6,
        outcome: SessionOutcome = SessionOutcome.COMPLETED,
    ): SessionLogEntry {
        val startedAtMs = startedAt.atZone(MOSCOW).toInstant().toEpochMilli()
        return SessionLogEntry(
            id = startedAtMs,
            profileId = 1,
            profileName = profileName,
            localDate = date,
            startedAtMs = startedAtMs,
            finishedAtMs = startedAtMs + durationMs,
            durationMs = durationMs,
            plannedMs = plannedMs,
            stagesCompleted = stagesCompleted,
            stageCount = stageCount,
            outcome = outcome,
        )
    }
}
