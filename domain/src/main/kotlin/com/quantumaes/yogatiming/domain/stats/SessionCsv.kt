package com.quantumaes.yogatiming.domain.stats

import com.quantumaes.yogatiming.domain.session.SessionOutcome
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Подписи колонок выгрузки — приходят с экрана.
 *
 * Язык файла равен языку интерфейса: отчёт студии пишет тот же человек,
 * который смотрел статистику, и колонка `duration_ms` в нём никому не нужна.
 * Домен подписи не сочиняет — он не знает ни о ресурсах, ни о локали.
 */
data class SessionCsvLabels(
    val date: String,
    val start: String,
    val finish: String,
    val duration: String,
    val planned: String,
    val profile: String,
    val stages: String,
    val outcome: String,
    val completed: String,
    val stopped: String,
)

/**
 * Выгрузка журнала в CSV (docs/09-STATISTICS.md, фаза S7).
 *
 * Файл открывают в таблицах — Excel, LibreOffice, Google Sheets, — и все
 * решения ниже приняты ради того, чтобы он открылся в них без вопросов, а не
 * ради красоты формата.
 */
object SessionCsv {
    /**
     * Разделитель — точка с запятой, а не запятая.
     *
     * Excel разбирает CSV по системному разделителю списка, и в русской (как и
     * в немецкой, французской, испанской) локали это `;`. Файл с запятыми
     * открывается там одной колонкой, и «выгрузка не работает» — ровно то, как
     * это выглядит со стороны. Таблицы, определяющие разделитель сами
     * (LibreOffice, Google Sheets), точку с запятой понимают наравне.
     */
    const val SEPARATOR = ';'

    /**
     * Метка порядка байтов в начале файла.
     *
     * Без неё Excel читает UTF-8 как системную кодировку, и «Хатха 60»
     * превращается в «Ð¥Ð°Ñ‚Ñ…Ð°». Три лишних байта — цена того, чтобы
     * кириллица открылась двойным щелчком, а не через мастер импорта.
     *
     * Записан кодом, а не символом: в исходнике метка невидима, и «пустая
     * строка», которую однажды кто-нибудь почистит, обошлась бы дорого.
     */
    const val BOM = "\uFEFF"

    /** Конец строки по RFC 4180 — его ждут и Excel, и Numbers. */
    const val LINE_BREAK = "\r\n"

    const val MIME_TYPE = "text/csv"

    private const val EXTENSION = ".csv"

    /** Имя файла — латиницей: файл уезжает в чужие каталоги и облака. */
    private const val FILE_PREFIX = "yoga-journal-"

    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private const val MS_IN_MINUTE = 60_000L

    /** Длина `YYYY-MM` в ISO-дате. */
    private const val MONTH_LENGTH = 7

    /** Символы, из-за которых поле обязано ехать в кавычках (RFC 4180). */
    private val SPECIAL_CHARS = charArrayOf(SEPARATOR, '"', '\n', '\r')

    /**
     * Журнал за период в виде CSV.
     *
     * Порядок — от старых к свежим, обратно экранному: на экране сверху ждут
     * последнее занятие, в отчёте — начало месяца. Таблица всё равно
     * пересортируется одним щелчком, но открыться она должна в том порядке,
     * в каком отчёт читают.
     *
     * Длительности — целыми минутами, а не «1,5 ч» и не миллисекундами:
     * дробное число упирается в десятичный разделитель, который у локалей
     * разный, а миллисекунды в отчёте студии считать никто не станет.
     *
     * Время — всегда 24-часовое `HH:mm`, даже если на устройстве 12-часовой
     * формат: в колонке таблицы «6:05 PM» не сортируется и не вычитается.
     */
    fun render(
        entries: List<SessionLogEntry>,
        labels: SessionCsvLabels,
        zone: ZoneId,
    ): String {
        val header =
            listOf(
                labels.date,
                labels.start,
                labels.finish,
                labels.duration,
                labels.planned,
                labels.profile,
                labels.stages,
                labels.outcome,
            )
        val rows =
            entries.sortedBy { it.startedAtMs }.map { entry ->
                listOf(
                    entry.localDate.toString(),
                    entry.startedAtMs.wallClock(zone),
                    entry.finishedAtMs.wallClock(zone),
                    entry.durationMs.minutes(),
                    entry.plannedMs.minutes(),
                    entry.profileName,
                    "${entry.stagesCompleted}/${entry.stageCount}",
                    if (entry.outcome == SessionOutcome.STOPPED) labels.stopped else labels.completed,
                )
            }

        return (listOf(header) + rows).joinToString(
            separator = LINE_BREAK,
            prefix = BOM,
            // Завершающий перевод строки: без него последняя строка для части
            // разборщиков — обрывок файла, а не запись.
            postfix = LINE_BREAK,
        ) { row -> row.joinToString(SEPARATOR.toString()) { field -> escape(field) } }
    }

    /**
     * Имя файла по периоду: `yoga-journal-2026-11.csv`.
     *
     * Период в имени, потому что выгрузок будет несколько: ноябрьский отчёт
     * рядом с октябрьским должен отличаться от него до открытия. Только
     * латиница и дефисы — файл уезжает в облака и на чужие компьютеры, где
     * кириллица в имени превращается в проценты.
     */
    fun fileName(period: StatsPeriod): String {
        val body =
            when (period.type) {
                StatsPeriodType.WEEK -> "${period.from}_${period.to}"
                StatsPeriodType.MONTH -> period.from.toString().take(MONTH_LENGTH)
                StatsPeriodType.YEAR -> period.from.year.toString()
                StatsPeriodType.ALL -> "all"
            }
        return "$FILE_PREFIX$body$EXTENSION"
    }

    /**
     * Экранирование по RFC 4180: поле в кавычках, если в нём есть разделитель,
     * кавычка или перевод строки; внутренняя кавычка удваивается.
     *
     * Экранируется только имя профиля — остальные колонки собраны из чисел и
     * дат. Но имя пишет пользователь, и «Хатха 60; вечер» без кавычек
     * разъезжается на две колонки, унося за собой всю строку.
     */
    private fun escape(field: String): String =
        if (field.any { it in SPECIAL_CHARS }) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }

    private fun Long.wallClock(zone: ZoneId): String =
        Instant
            .ofEpochMilli(this)
            .atZone(zone)
            .toLocalTime()
            .format(TIME_FORMAT)

    /** Минуты с округлением к ближайшей: 58 мин 40 с в отчёте — это 59 минут. */
    private fun Long.minutes(): String = ((this + MS_IN_MINUTE / 2) / MS_IN_MINUTE).toString()
}
