package com.quantumaes.yogatiming.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.quantumaes.yogatiming.core.common.time.TimeFormatter
import com.quantumaes.yogatiming.domain.stats.StatsPeriod
import com.quantumaes.yogatiming.domain.stats.StatsPeriodType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.text.intl.Locale as ComposeLocale

private const val MINUTES_IN_HOUR = 60L

/**
 * Язык интерфейса в том виде, в каком его понимает `java.time`.
 *
 * Названия месяцев и дней недели берутся у платформы, а не из ресурсов:
 * склонения и сокращения — это работа локали, и дублировать их строками
 * значит однажды разойтись с системой на новом языке.
 *
 * Через `Locale.current` Compose, а не через `LocalContext.current.resources`:
 * второе Lint запрещает (`LocalContextConfigurationRead`) — прочитанная так
 * конфигурация не обновляется при смене языка.
 */
@Composable
private fun currentLocale(): Locale {
    val tag = ComposeLocale.current.toLanguageTag()
    return remember(tag) { Locale.forLanguageTag(tag) }
}

/**
 * «14 ч 20 мин» или «20 мин».
 *
 * Часы и минуты, а не «860 минут»: инструктор мыслит занятие часами, и сумма
 * за месяц читается тем же способом, что и расписание. Секунд нет намеренно —
 * в сводке за месяц они точность не добавляют, а место занимают.
 */
@Composable
internal fun durationText(millis: Long): String {
    val minutes = TimeFormatter.roundedMinutes(millis)
    val hours = minutes / MINUTES_IN_HOUR
    return if (hours > 0) {
        stringResource(R.string.stats_duration_hm, hours, minutes % MINUTES_IN_HOUR)
    } else {
        stringResource(R.string.stats_duration_m, minutes)
    }
}

/**
 * Подпись выбранного периода: «ноябрь», «3–9 ноября», «2026», «за всё время».
 *
 * Год показывается только тогда, когда он не текущий: «ноябрь» в ноябре 2026-го
 * не нуждается в уточнении, а «ноябрь 2024» — нуждается.
 */
@Composable
internal fun periodTitle(
    period: StatsPeriod,
    today: LocalDate,
): String {
    val locale = currentLocale()
    return when (period.type) {
        StatsPeriodType.WEEK -> weekTitle(period, today, locale)
        StatsPeriodType.MONTH -> monthTitle(period, today, locale)
        StatsPeriodType.YEAR -> period.from.year.toString()
        StatsPeriodType.ALL -> stringResource(R.string.stats_period_all_title)
    }
}

/** «3–9 ноября» внутри одного месяца и «28 окт. — 3 нояб.» на его границе. */
private fun weekTitle(
    period: StatsPeriod,
    today: LocalDate,
    locale: Locale,
): String {
    val sameYear = period.to.year == today.year
    return if (period.from.month == period.to.month) {
        val pattern = if (sameYear) "d MMMM" else "d MMMM yyyy"
        "${period.from.dayOfMonth}–${period.to.format(pattern, locale)}"
    } else {
        val pattern = if (sameYear) "d MMM" else "d MMM yyyy"
        "${period.from.format("d MMM", locale)} — ${period.to.format(pattern, locale)}"
    }
}

/** «ноябрь» или «ноябрь 2024». Именительный падеж (`LLLL`) — это заголовок, а не дата. */
private fun monthTitle(
    period: StatsPeriod,
    today: LocalDate,
    locale: Locale,
): String {
    val pattern = if (period.from.year == today.year) "LLLL" else "LLLL yyyy"
    return period.from.format(pattern, locale).replaceFirstChar { it.titlecase(locale) }
}

private fun LocalDate.format(
    pattern: String,
    locale: Locale,
): String = format(DateTimeFormatter.ofPattern(pattern, locale))

/** «пн», «вт» — подписи столбиков недельного графика. */
@Composable
internal fun weekdayLabel(dayOfWeek: DayOfWeek): String {
    val locale = currentLocale()
    return dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
}

/** «понедельник» — полное имя для TalkBack: «пн» синтезатор читает как «пэ-эн». */
@Composable
internal fun weekdayFullName(dayOfWeek: DayOfWeek): String {
    val locale = currentLocale()
    return dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale)
}
