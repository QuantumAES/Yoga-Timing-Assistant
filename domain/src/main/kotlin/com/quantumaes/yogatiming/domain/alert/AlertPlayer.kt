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
 * таймер-сервис, а таймер-сервис вызывал, не зная про SoundPool и TTS.
 * Полноценная реализация — Фаза 4 (ADR-003).
 */
interface AlertPlayer {
    fun play(request: AlertRequest)

    /** Занятие закончилось или сброшено: снять audio focus, очистить очередь TTS. */
    fun stop()
}
