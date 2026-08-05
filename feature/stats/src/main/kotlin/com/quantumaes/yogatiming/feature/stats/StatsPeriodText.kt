package com.quantumaes.yogatiming.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.quantumaes.yogatiming.domain.stats.StatsPeriod
import com.quantumaes.yogatiming.domain.stats.StatsPeriodType
import java.time.LocalDate
import java.util.Locale

// Подпись периода вынесена из `StatsText.kt` отдельным файлом: правила здесь
// свои — падеж месяца, год только когда он не текущий, неделя на границе
// месяцев, — и читать их удобнее подряд.

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
