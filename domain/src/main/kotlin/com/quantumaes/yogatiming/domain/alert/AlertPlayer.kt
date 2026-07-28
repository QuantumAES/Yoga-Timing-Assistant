package com.quantumaes.yogatiming.domain.alert

import com.quantumaes.yogatiming.domain.model.alert.Alert
import com.quantumaes.yogatiming.timer.engine.model.AlertTrigger

/**
 * Запрос на воспроизведение оповещения.
 *
 * Названия этапов передаются готовыми: TTS-фразы вида «далее: шавасана»
 * собираются из строковых ресурсов в момент произнесения (решение P1-6), а
 * подставлять в них нечего, если проигрыватель не знает, где находится занятие.
 *
 * У [alert] к этому моменту заполнена и громкость: наследование от
 * `masterVolumePercent` разрешено при сборке плана, как и всё остальное
 * наследование (см. `SessionPlanFactory`).
 */
data class AlertRequest(
    val alert: Alert,
    val trigger: AlertTrigger,
    val stageName: String,
    val nextStageName: String?,
)

/**
 * Воспроизведение оповещений: звук, голос, вибрация.
 *
 * Контракт объявлен в домене, чтобы `:core:audio` реализовал его, не зная про
 * таймер-сервис, а таймер-сервис вызывал, не зная про SoundPool и TTS
 * (реализация — Фаза 4, ADR-003).
 */
interface AlertPlayer {
    /**
     * Прогреть тяжёлые ресурсы до первого оповещения.
     *
     * Вызывается при старте сервиса, а не при первом сигнале: и загрузка
     * сэмплов, и инициализация TTS асинхронны и занимают десятки миллисекунд,
     * а START первого этапа звучит сразу за командой запуска. Прогрев — цена
     * того, чтобы первый сигнал занятия не пропал.
     */
    fun prepare()

    fun play(request: AlertRequest)

    /** Занятие закончилось или сброшено: снять audio focus, очистить очередь TTS. */
    fun stop()
}
