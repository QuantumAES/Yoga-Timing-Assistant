package com.quantumaes.yogatiming.core.database.seed

import com.quantumaes.yogatiming.domain.model.Profile
import com.quantumaes.yogatiming.domain.model.ProfileCategory
import com.quantumaes.yogatiming.domain.model.Stage
import com.quantumaes.yogatiming.domain.model.StageType
import com.quantumaes.yogatiming.domain.model.alert.AlertPresets

/**
 * Демо-профили первого запуска (docs/06-MVP-SCOPE.md §1.1).
 *
 * Не «пример данных», а рабочие сценарии: инструктор должен иметь возможность
 * провести по ним реальное занятие сразу после установки, не заполняя ничего.
 *
 * Этапы типа REST сидируются с уже применённым тихим пресетом — это и есть
 * решение C-6 в действии: подмены каналов в рантайме нет, тишина шавасаны
 * задана данными и видна пользователю в редакторе.
 *
 * У всех трёх задано целевое время занятия и допуск (Фаза 11): настройка,
 * которую не видел ни на одном профиле, не существует для пользователя.
 * Групповые занятия получают эластичное окно, медитация — строгое: это и есть
 * два сценария из замечания 12 полевой проверки 2026-08-04, показанные данными.
 *
 * UUID зафиксированы: по ним демо-профиль опознаётся при импорте и в тестах.
 */
internal object DemoProfiles {
    private const val COLOR_GREEN = "#4CAF50"
    private const val COLOR_LIME = "#8BC34A"
    private const val COLOR_AMBER = "#FFC107"
    private const val COLOR_ORANGE = "#FF9800"
    private const val COLOR_TEAL = "#009688"
    private const val COLOR_PURPLE = "#9C27B0"
    private const val COLOR_INDIGO = "#5C6BC0"
    private const val COLOR_BLUE = "#42A5F5"

    private const val MIN = 60

    /**
     * Санскрит синтезатор читает с ударением по своим правилам, и «шавáсана»
     * превращается в «шавасáну». Демо-профили показывают, чем это лечится, —
     * плюсом перед ударной гласной (поле «Произношение» у этапа).
     */
    private const val SHAVASANA_VOICE = "шав+асана"

    fun all(): List<Profile> = listOf(hatha60(), yin90(), meditation20())

    /** Классические 60 минут хатхи из шести блоков — сценарий A-1 критериев приёмки. */
    private fun hatha60() =
        Profile(
            uuid = "8f1d0a6e-0f2a-4c8b-9c1f-1a2b3c4d5e01",
            name = "Хатха 60 мин",
            category = ProfileCategory.HATHA,
            colorTag = COLOR_GREEN,
            // Групповое занятие в арендованном зале: пять минут сверху обычно
            // никого не беспокоят, а отсечка за десять минут приходится ровно
            // на вход в шавасану.
            targetDurationSec = 60 * MIN,
            targetToleranceSec = 5 * MIN,
            isFavorite = true,
            sortOrder = 0,
            defaultAlertConfig = AlertPresets.standard(),
            stages =
                listOf(
                    stage(
                        "Настройка и пранаяма",
                        5 * MIN,
                        COLOR_TEAL,
                        note = "Дыхание уджайи, счёт 4–4",
                        voiceName = "настройка и пранай+ама",
                    ),
                    stage("Разминка, сурья намаскар", 8 * MIN, COLOR_LIME, voiceName = "разминка, с+урья намаск+ар"),
                    stage("Асаны стоя", 18 * MIN, COLOR_AMBER, note = "Вирабхадрасана I–II, триконасана"),
                    stage("Асаны сидя и наклоны", 15 * MIN, COLOR_ORANGE),
                    stage("Скрутки и перевёрнутые", 4 * MIN, COLOR_INDIGO),
                    restStage("Шавасана", 10 * MIN, COLOR_PURPLE, voiceName = SHAVASANA_VOICE),
                ),
        )

    /** Инь: длинные удержания, поэтому предупреждения «за 2 и 1 минуту» особенно уместны. */
    private fun yin90() =
        Profile(
            uuid = "8f1d0a6e-0f2a-4c8b-9c1f-1a2b3c4d5e02",
            name = "Инь-йога 90 мин",
            category = ProfileCategory.YIN,
            colorTag = COLOR_INDIGO,
            targetDurationSec = 90 * MIN,
            targetToleranceSec = 10 * MIN,
            sortOrder = 1,
            defaultAlertConfig = AlertPresets.standard(),
            stages =
                listOf(
                    stage("Настройка дыхания", 6 * MIN, COLOR_TEAL),
                    stage("Бабочка", 8 * MIN, COLOR_BLUE),
                    // Длительность двустороннего этапа — на одну сторону; в
                    // занятии он развернётся в два отрезка по пять минут.
                    stage("Дракон", 5 * MIN, COLOR_BLUE, bilateral = true),
                    stage("Седло", 8 * MIN, COLOR_BLUE),
                    stage("Скрутка лёжа", 4 * MIN, COLOR_INDIGO, bilateral = true),
                    stage("Гусеница", 10 * MIN, COLOR_INDIGO),
                    stage("Полубабочка", 8 * MIN, COLOR_INDIGO),
                    stage("Ноги на стене", 12 * MIN, COLOR_PURPLE),
                    restStage("Медитация сидя", 8 * MIN, COLOR_PURPLE),
                    restStage("Шавасана", 12 * MIN, COLOR_PURPLE, voiceName = SHAVASANA_VOICE),
                ),
        )

    /** Короткая сидячая практика: вход, тишина, мягкий выход. */
    private fun meditation20() =
        Profile(
            uuid = "8f1d0a6e-0f2a-4c8b-9c1f-1a2b3c4d5e03",
            name = "Медитация 20 мин",
            category = ProfileCategory.MEDITATION,
            colorTag = COLOR_PURPLE,
            // Двадцать минут — это двадцать минут: практика короткая, и допуска
            // у неё нет. Отсечка сдвинута к трём минутам: десять от двадцати —
            // половина занятия, такая отсечка ничего не сообщает.
            targetDurationSec = 20 * MIN,
            targetToleranceSec = 0,
            wrapUpOffsetSec = 3 * MIN,
            sortOrder = 2,
            defaultAlertConfig = AlertPresets.silent(),
            stages =
                listOf(
                    stage("Устройство позы и дыхание", 3 * MIN, COLOR_TEAL),
                    restStage("Наблюдение дыхания", 14 * MIN, COLOR_PURPLE, note = "Без сигналов до конца этапа"),
                    stage("Возвращение", 3 * MIN, COLOR_TEAL),
                ),
        )

    private fun stage(
        name: String,
        durationSec: Int,
        color: String,
        note: String? = null,
        voiceName: String? = null,
        bilateral: Boolean = false,
    ) = Stage(
        name = name,
        type = StageType.NORMAL,
        colorTag = color,
        durationSec = durationSec,
        note = note,
        voiceName = voiceName,
        bilateral = bilateral,
    )

    /** REST-этап всегда получает тихий пресет — иначе гонг посреди шавасаны (C-6). */
    private fun restStage(
        name: String,
        durationSec: Int,
        color: String,
        note: String? = null,
        voiceName: String? = null,
    ) = Stage(
        name = name,
        type = StageType.REST,
        colorTag = color,
        durationSec = durationSec,
        note = note,
        alertConfig = AlertPresets.silent(),
        voiceName = voiceName,
    )
}
