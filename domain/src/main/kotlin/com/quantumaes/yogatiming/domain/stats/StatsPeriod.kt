package com.quantumaes.yogatiming.domain.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Первый и последний день, которые могут встретиться в журнале.
 *
 * Не `LocalDate.MIN`/`MAX`: их ISO-запись начинается со знака (`-999999999-01-01`,
 * `+999999999-12-31`), а `local_date` сравнивается в SQL как строка. И минус, и
 * плюс лексикографически меньше любой цифры, поэтому «за всё время» с такими
 * границами не нашло бы ни одного занятия. Четырёхзначный год этого не ломает —
 * лексикографический порядок ISO-дат совпадает с хронологическим.
 */
val FIRST_POSSIBLE_DAY: LocalDate = LocalDate.of(1000, 1, 1)

/** См. [FIRST_POSSIBLE_DAY]. */
val LAST_POSSIBLE_DAY: LocalDate = LocalDate.of(9999, 12, 31)

/** Что показывает экран статистики: переключатель периода в шапке. */
enum class StatsPeriodType {
    WEEK,
    MONTH,
    YEAR,

    /** За всё время. Листать некуда — период один. */
    ALL,
    ;

    /** Можно ли листать период стрелками «‹ ноябрь ›». */
    val isNavigable: Boolean get() = this != ALL
}

/**
 * Отрезок календаря, за который считается статистика (docs/09-STATISTICS.md §4).
 *
 * Границы — локальные дни включительно с обеих сторон, ровно в том виде, в
 * каком их ждёт журнал: день занятия там уже посчитан при записи (D-S4), и
 * сравнивать его с моментом времени незачем.
 *
 * Начало недели — параметр, а не константа: понедельник верен для России и
 * почти всей Европы, но воскресенье в качестве первого дня недели существует, и
 * прятать этот выбор в неизменяемую константу значит однажды переписывать все
 * границы разом. По умолчанию — понедельник (DoD фазы S2).
 */
data class StatsPeriod(
    val type: StatsPeriodType,
    val from: LocalDate,
    val to: LocalDate,
) {
    /** Дни периода по порядку. Для «всего времени» не вызывается — их миллион. */
    fun days(): List<LocalDate> = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(to)

    /** Предыдущая неделя, месяц, год. «Всё время» остаётся собой. */
    fun previous(): StatsPeriod = shifted(-1)

    /** Следующая неделя, месяц, год. «Всё время» остаётся собой. */
    fun next(): StatsPeriod = shifted(1)

    private fun shifted(step: Long): StatsPeriod =
        when (type) {
            StatsPeriodType.WEEK -> copy(from = from.plusWeeks(step), to = to.plusWeeks(step))
            StatsPeriodType.MONTH -> of(type, from.plusMonths(step))
            StatsPeriodType.YEAR -> of(type, from.plusYears(step))
            StatsPeriodType.ALL -> this
        }

    companion object {
        /**
         * Период, в который попадает [anchor].
         *
         * Месяц и год строятся от первого и последнего дня самого месяца и года,
         * а не прибавлением тридцати дней: февраль, високосный год и месяц из
         * 31 дня иначе разъезжаются, и «октябрь» начинал бы захватывать хвост
         * сентября.
         */
        fun of(
            type: StatsPeriodType,
            anchor: LocalDate,
            firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        ): StatsPeriod =
            when (type) {
                StatsPeriodType.WEEK -> {
                    val start = anchor.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
                    StatsPeriod(type, start, start.plusDays(DAYS_IN_WEEK - 1L))
                }

                StatsPeriodType.MONTH -> {
                    val month = YearMonth.from(anchor)
                    StatsPeriod(type, month.atDay(1), month.atEndOfMonth())
                }

                StatsPeriodType.YEAR -> {
                    StatsPeriod(type, anchor.withDayOfYear(1), anchor.withDayOfYear(anchor.lengthOfYear()))
                }

                StatsPeriodType.ALL -> {
                    StatsPeriod(type, FIRST_POSSIBLE_DAY, LAST_POSSIBLE_DAY)
                }
            }
    }
}

const val DAYS_IN_WEEK = 7

/**
 * Сетка месяца целыми неделями (фаза S4).
 *
 * Возвращает подряд идущие дни от начала недели, в которую попало первое число,
 * до конца недели, в которую попало последнее. Хвосты соседних месяцев — это
 * настоящие даты, а не пустые клетки: по ним можно тапнуть и увидеть занятия
 * того дня, а показать их бледнее решает экран. Длина всегда кратна семи,
 * поэтому сетка раскладывается рядами без арифметики на стороне UI.
 */
fun monthGrid(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
): List<LocalDate> {
    val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val end = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(firstDayOfWeek.minus(1)))
    return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
}

/** Дни недели в порядке от [firstDayOfWeek] — подписи и столбики графика. */
fun weekdaysFrom(firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): List<DayOfWeek> =
    List(DAYS_IN_WEEK) { firstDayOfWeek.plus(it.toLong()) }

/** Столбик недельного графика: сколько практики пришлось на этот день недели. */
data class WeekdayTotal(
    val dayOfWeek: DayOfWeek,
    val sessionCount: Int,
    val durationMs: Long,
)

/**
 * Недельный график: суммы по дням недели за период.
 *
 * Ровно семь столбиков в фиксированном порядке, включая пустые: график без
 * пустых дней врал бы формой — «среда» на месте вторника читается как практика
 * во вторник.
 */
fun weekdayTotals(
    days: List<SessionDay>,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
): List<WeekdayTotal> {
    val byWeekday = days.groupBy { it.date.dayOfWeek }
    return weekdaysFrom(firstDayOfWeek).map { weekday ->
        val group = byWeekday[weekday].orEmpty()
        WeekdayTotal(
            dayOfWeek = weekday,
            sessionCount = group.sumOf { it.sessionCount },
            durationMs = group.sumOf { it.durationMs },
        )
    }
}

/**
 * Серия дней подряд, заканчивающаяся сегодня или вчера.
 *
 * Вчера считается концом серии наравне с сегодня: занятие сегодня ещё может
 * состояться, и обнулять серию в полночь значит сообщать человеку об обрыве,
 * которого не было. Оборванной серия становится, когда пропущен целый день.
 *
 * Считается по всему журналу, а не по периоду: серия не обязана начинаться
 * первого числа.
 */
fun currentStreak(
    practiced: Collection<LocalDate>,
    today: LocalDate,
): Int {
    val dates = practiced.toSet()
    val start =
        when {
            today in dates -> today
            today.minusDays(1) in dates -> today.minusDays(1)
            else -> return 0
        }
    return generateSequence(start) { it.minusDays(1) }.takeWhile { it in dates }.count()
}

/** Самая длинная серия дней подряд. Для пустого журнала — ноль. */
fun longestStreak(practiced: Collection<LocalDate>): Int {
    val sorted = practiced.distinct().sorted()
    var best = 0
    var run = 0
    var previous: LocalDate? = null
    sorted.forEach { date ->
        run = if (previous?.plusDays(1) == date) run + 1 else 1
        if (run > best) best = run
        previous = date
    }
    return best
}
