package com.quantumaes.yogatiming.feature.stats

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.quantumaes.yogatiming.core.common.time.TimeFormatter
import com.quantumaes.yogatiming.domain.session.SessionOutcome
import com.quantumaes.yogatiming.domain.stats.SessionLogEntry
import com.quantumaes.yogatiming.domain.stats.StatsPeriod
import com.quantumaes.yogatiming.domain.stats.StatsPeriodType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
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
internal fun currentLocale(): Locale {
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

internal fun LocalDate.format(
    pattern: String,
    locale: Locale,
): String = format(DateTimeFormatter.ofPattern(pattern, locale))

/**
 * «3 ноября» — подпись дня в календаре и в карточке дня.
 *
 * Без года: год стоит в подписи периода прямо над календарём, и повторять его
 * в каждой из сорока двух клеток значит удлинять фразу TalkBack на ровном месте.
 */
@Composable
internal fun dayTitle(date: LocalDate): String {
    val locale = currentLocale()
    return remember(date, locale) { date.format("d MMMM", locale) }
}

/**
 * «30 октября» или «30 октября 2025» — день с уточнением года, когда он не
 * текущий. Пустому периоду год важен: перерыв в практике бывает и годовым.
 */
@Composable
internal fun dayTitle(
    date: LocalDate,
    today: LocalDate,
): String {
    val locale = currentLocale()
    return remember(date, today, locale) {
        date.format(if (date.year == today.year) "d MMMM" else "d MMMM yyyy", locale)
    }
}

/** «3 ноя» — дата в строке журнала: она повторяется у каждой строки. */
@Composable
internal fun dayShort(date: LocalDate): String {
    val locale = currentLocale()
    return remember(date, locale) { date.format("d MMM", locale) }
}

/**
 * Время по стенным часам в формате устройства.
 *
 * 12- или 24-часовой выбирает система, а не приложение, — тот же приём, что на
 * экране итогов занятия.
 */
@Composable
internal fun wallClock(millis: Long): String {
    val context = LocalContext.current
    val format = remember(context) { DateFormat.getTimeFormat(context) }
    return remember(format, millis) { format.format(Date(millis)) }
}

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

/**
 * Занятие одной фразой: «3 ноября, Хатха 60, с 18:05 до 19:03, 58 мин»
 * (фаза S6, проверка A-1).
 *
 * Строка журнала состоит из четырёх текстов, и глазом они читаются вместе, а
 * TalkBack по умолчанию произносит их четырьмя отдельными узлами: «3 ноя»,
 * «Хатха 60», «18:05 → 19:03», «58 мин» — каждый без связи с соседними.
 * Дата здесь полная, а не «3 ноя»: сокращение синтезатор читает по буквам.
 */
@Composable
internal fun entryDescription(entry: SessionLogEntry): String {
    val template =
        if (entry.outcome == SessionOutcome.STOPPED) {
            R.string.stats_entry_description_stopped
        } else {
            R.string.stats_entry_description
        }
    return stringResource(
        template,
        dayTitle(entry.localDate),
        entry.profileName,
        wallClock(entry.startedAtMs),
        wallClock(entry.finishedAtMs),
        durationText(entry.durationMs),
    )
}
