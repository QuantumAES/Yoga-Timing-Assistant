package com.quantumaes.yogatiming.domain.stats

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private const val MINUTE_MS = 60_000L

/**
 * Границы периодов (docs/09-STATISTICS.md, фаза S2).
 *
 * Здесь проверяется календарная арифметика, а не SQL: переход через год,
 * февраль високосного года, неделя с понедельника и «за всё время». Всё это
 * пишется юнит-тестом за минуту и не пишется на устройстве вовсе.
 */
class StatsPeriodTest {
    @Test
    fun `неделя начинается с понедельника и кончается воскресеньем`() {
        // 5 ноября 2026 — четверг.
        val week = StatsPeriod.of(StatsPeriodType.WEEK, LocalDate.of(2026, 11, 5))

        assertThat(week.from).isEqualTo(LocalDate.of(2026, 11, 2))
        assertThat(week.to).isEqualTo(LocalDate.of(2026, 11, 8))
        assertThat(week.from.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        assertThat(week.days()).hasSize(DAYS_IN_WEEK)
    }

    @Test
    fun `понедельник принадлежит своей неделе, а не предыдущей`() {
        val week = StatsPeriod.of(StatsPeriodType.WEEK, LocalDate.of(2026, 11, 2))

        assertThat(week.from).isEqualTo(LocalDate.of(2026, 11, 2))
    }

    @Test
    fun `неделя может начинаться с воскресенья`() {
        val week =
            StatsPeriod.of(
                StatsPeriodType.WEEK,
                LocalDate.of(2026, 11, 5),
                firstDayOfWeek = DayOfWeek.SUNDAY,
            )

        assertThat(week.from).isEqualTo(LocalDate.of(2026, 11, 1))
        assertThat(week.to).isEqualTo(LocalDate.of(2026, 11, 7))
    }

    @Test
    fun `неделя листается через границу года`() {
        val week = StatsPeriod.of(StatsPeriodType.WEEK, LocalDate.of(2026, 1, 2)).previous()

        assertThat(week.from).isEqualTo(LocalDate.of(2025, 12, 22))
        assertThat(week.to).isEqualTo(LocalDate.of(2025, 12, 28))
    }

    @Test
    fun `месяц кончается своим последним числом, а не тридцатым`() {
        val february = StatsPeriod.of(StatsPeriodType.MONTH, LocalDate.of(2026, 2, 15))

        assertThat(february.from).isEqualTo(LocalDate.of(2026, 2, 1))
        assertThat(february.to).isEqualTo(LocalDate.of(2026, 2, 28))
        assertThat(february.days()).hasSize(28)
    }

    @Test
    fun `високосный февраль длиннее на день`() {
        val february = StatsPeriod.of(StatsPeriodType.MONTH, LocalDate.of(2028, 2, 15))

        assertThat(february.to).isEqualTo(LocalDate.of(2028, 2, 29))
    }

    @Test
    fun `январь листается назад в декабрь прошлого года`() {
        val december = StatsPeriod.of(StatsPeriodType.MONTH, LocalDate.of(2026, 1, 20)).previous()

        assertThat(december.from).isEqualTo(LocalDate.of(2025, 12, 1))
        assertThat(december.to).isEqualTo(LocalDate.of(2025, 12, 31))
    }

    /** Тридцать первое января при листании вперёд не должно терять месяц. */
    @Test
    fun `конец месяца не проваливается при листании`() {
        val february = StatsPeriod.of(StatsPeriodType.MONTH, LocalDate.of(2026, 1, 31)).next()

        assertThat(february.from).isEqualTo(LocalDate.of(2026, 2, 1))
        assertThat(february.to).isEqualTo(LocalDate.of(2026, 2, 28))
    }

    @Test
    fun `год — с первого января по тридцать первое декабря`() {
        val year = StatsPeriod.of(StatsPeriodType.YEAR, LocalDate.of(2026, 7, 31))

        assertThat(year.from).isEqualTo(LocalDate.of(2026, 1, 1))
        assertThat(year.to).isEqualTo(LocalDate.of(2026, 12, 31))
        assertThat(year.days()).hasSize(365)
    }

    @Test
    fun `високосный год длиннее на день`() {
        assertThat(StatsPeriod.of(StatsPeriodType.YEAR, LocalDate.of(2028, 3, 1)).days()).hasSize(366)
    }

    /**
     * «Всё время» — не `LocalDate.MIN`…`MAX`: их ISO-запись начинается со знака,
     * а границы уходят в SQL строками и сравниваются лексикографически.
     */
    @Test
    fun `за всё время границы остаются четырёхзначными и упорядоченными`() {
        val all = StatsPeriod.of(StatsPeriodType.ALL, LocalDate.of(2026, 7, 31))

        assertThat(all.from.toString()).isEqualTo("1000-01-01")
        assertThat(all.to.toString()).isEqualTo("9999-12-31")
        assertThat(all.from.toString() < "2026-07-31").isTrue()
        assertThat(all.to.toString() > "2026-07-31").isTrue()
    }

    @Test
    fun `за всё время листать некуда`() {
        val all = StatsPeriod.of(StatsPeriodType.ALL, LocalDate.of(2026, 7, 31))

        assertThat(all.previous()).isEqualTo(all)
        assertThat(all.next()).isEqualTo(all)
        assertThat(StatsPeriodType.ALL.isNavigable).isFalse()
    }

    @Test
    fun `границы периода входят в него сами`() {
        val month = StatsPeriod.of(StatsPeriodType.MONTH, LocalDate.of(2026, 11, 15))

        assertThat(LocalDate.of(2026, 11, 1) in month).isTrue()
        assertThat(LocalDate.of(2026, 11, 30) in month).isTrue()
        assertThat(LocalDate.of(2026, 10, 31) in month).isFalse()
        assertThat(LocalDate.of(2026, 12, 1) in month).isFalse()
    }
}

/** Сетка календаря месяца (фаза S4). */
class MonthGridTest {
    @Test
    fun `сетка кратна неделе и начинается с понедельника`() {
        // Ноябрь 2026 начинается с воскресенья — самый неудобный случай.
        val grid = monthGrid(YearMonth.of(2026, 11))

        assertThat(grid.size % DAYS_IN_WEEK).isEqualTo(0)
        assertThat(grid.first().dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        assertThat(grid.last().dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
    }

    @Test
    fun `первая неделя захватывает хвост прошлого месяца`() {
        val grid = monthGrid(YearMonth.of(2026, 11))

        assertThat(grid.first()).isEqualTo(LocalDate.of(2026, 10, 26))
        assertThat(grid).contains(LocalDate.of(2026, 11, 1))
        assertThat(grid).contains(LocalDate.of(2026, 11, 30))
    }

    @Test
    fun `месяц, ровно ложащийся на недели, не получает лишнего ряда`() {
        // Февраль 2027: 1-е — понедельник, 28 дней ровно.
        val grid = monthGrid(YearMonth.of(2027, 2))

        assertThat(grid).hasSize(4 * DAYS_IN_WEEK)
        assertThat(grid.first()).isEqualTo(LocalDate.of(2027, 2, 1))
        assertThat(grid.last()).isEqualTo(LocalDate.of(2027, 2, 28))
    }

    @Test
    fun `сетка с воскресенья тоже целая`() {
        val grid = monthGrid(YearMonth.of(2026, 11), firstDayOfWeek = DayOfWeek.SUNDAY)

        assertThat(grid.size % DAYS_IN_WEEK).isEqualTo(0)
        assertThat(grid.first()).isEqualTo(LocalDate.of(2026, 11, 1))
        assertThat(grid.first().dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
    }
}

/** Недельный график и серии дней. */
class StatsAggregatesTest {
    @Test
    fun `в графике всегда семь столбиков, включая пустые дни`() {
        val totals = weekdayTotals(listOf(day(LocalDate.of(2026, 11, 4), 2, 90 * MINUTE_MS)))

        assertThat(totals).hasSize(DAYS_IN_WEEK)
        assertThat(totals.map { it.dayOfWeek }.first()).isEqualTo(DayOfWeek.MONDAY)
        // 4 ноября 2026 — среда.
        assertThat(totals[2].durationMs).isEqualTo(90 * MINUTE_MS)
        assertThat(totals[2].sessionCount).isEqualTo(2)
        assertThat(totals[0].durationMs).isEqualTo(0L)
    }

    @Test
    fun `один день недели за разные недели складывается`() {
        val totals =
            weekdayTotals(
                listOf(
                    day(LocalDate.of(2026, 11, 4), 1, 60 * MINUTE_MS),
                    day(LocalDate.of(2026, 11, 11), 1, 30 * MINUTE_MS),
                ),
            )

        assertThat(totals[2].sessionCount).isEqualTo(2)
        assertThat(totals[2].durationMs).isEqualTo(90 * MINUTE_MS)
    }

    @Test
    fun `серия считается от сегодня назад`() {
        val today = LocalDate.of(2026, 11, 5)
        val practiced = setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(4))

        assertThat(currentStreak(practiced, today)).isEqualTo(3)
    }

    /** Занятие сегодня ещё может состояться — вчерашняя серия не оборвана. */
    @Test
    fun `вчерашняя практика продолжает серию`() {
        val today = LocalDate.of(2026, 11, 5)
        val practiced = setOf(today.minusDays(1), today.minusDays(2))

        assertThat(currentStreak(practiced, today)).isEqualTo(2)
    }

    @Test
    fun `пропущенный день обрывает серию`() {
        val today = LocalDate.of(2026, 11, 5)

        assertThat(currentStreak(setOf(today.minusDays(2)), today)).isEqualTo(0)
        assertThat(currentStreak(emptySet(), today)).isEqualTo(0)
    }

    @Test
    fun `самая длинная серия находится в середине журнала`() {
        val practiced =
            setOf(
                LocalDate.of(2026, 10, 30),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 3),
                LocalDate.of(2026, 11, 4),
                LocalDate.of(2026, 11, 8),
            )

        assertThat(longestStreak(practiced)).isEqualTo(4)
        assertThat(longestStreak(emptySet())).isEqualTo(0)
    }

    /** Серия обязана продолжаться через границу месяца и года. */
    @Test
    fun `серия не обрывается на новом годе`() {
        val practiced =
            setOf(
                LocalDate.of(2025, 12, 30),
                LocalDate.of(2025, 12, 31),
                LocalDate.of(2026, 1, 1),
            )

        assertThat(longestStreak(practiced)).isEqualTo(3)
        assertThat(currentStreak(practiced, LocalDate.of(2026, 1, 1))).isEqualTo(3)
    }

    private fun day(
        date: LocalDate,
        count: Int,
        durationMs: Long,
    ) = SessionDay(date = date, sessionCount = count, durationMs = durationMs)
}
