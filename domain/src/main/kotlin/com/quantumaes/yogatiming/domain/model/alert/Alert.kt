package com.quantumaes.yogatiming.domain.model.alert

import com.quantumaes.yogatiming.timer.engine.model.AlertPayload
import kotlinx.serialization.Serializable

/** Звук-пресет. Реальные ассеты подбираются в Фазе 4 (лицензии — P1-8). */
@Serializable
enum class AlertSound {
    NONE,
    SOFT_GONG,
    SINGING_BOWL,
    BELL,
    TONE,
    TICK,

    /**
     * Файл пользователя из [Alert.customSoundUri].
     *
     * Единственный пресет без собственного сэмпла в банке: что именно прозвучит,
     * известно только вместе с оповещением. Без ссылки на файл ведёт себя
     * как [NONE] — иначе оповещение молчало бы, обещая звук.
     */
    CUSTOM,
}

/** Паттерн вибрации (ТЗ §5.1). */
@Serializable
enum class VibrationPattern {
    SINGLE,
    DOUBLE,
    LONG,
}

/**
 * Тип произносимой фразы.
 *
 * Хранится именно тип, а не готовый русский текст (решение P1-6): иначе при
 * переключении интерфейса на английский озвучка осталась бы русской.
 * Текст рендерится из строковых ресурсов в момент произнесения,
 * подстановки `{stage}` / `{next}` выполняются там же.
 */
@Serializable
enum class VoicePhrase {
    NONE,

    /** «Разминка» — название начинающегося этапа. */
    STAGE_NAME,

    /** «Далее: шавасана» — название следующего этапа. */
    NEXT_STAGE,

    /** «Осталась одна минута». */
    TIME_REMAINING,

    /** «Занятие завершено». */
    SESSION_FINISHED,

    /** Произвольный текст пользователя из [Alert.customVoiceText]. */
    CUSTOM,
}

/**
 * Одно оповещение.
 *
 * Момент срабатывания не хранится полем «тип триггера»: START, WARNING и END
 * различаются местом в [AlertConfig]. Это исключает невалидные состояния —
 * два START или END с ненулевым смещением (ADR-002).
 *
 * Реализует [AlertPayload]: движок таймера переносит оповещение от расписания
 * к проигрывателю, ни разу не заглянув внутрь. Каналы, звуки и фразы остаются
 * делом домена и меняются, не задевая ядро отсчёта.
 *
 * @param offsetSec за сколько секунд до конца этапа сработать. Осмысленно
 *   только для предупреждений; для START и END игнорируется.
 * @param volumePercent 0..100, `null` — наследовать [AlertConfig.masterVolumePercent].
 * @param customSoundUri `content://`-ссылка на файл пользователя. Осмысленна
 *   только вместе с [AlertSound.CUSTOM]. Хранится строкой, а не `Uri`: домен
 *   про Android не знает, а формат хранения и формат экспорта тут совпадают.
 */
@Serializable
data class Alert(
    val enabled: Boolean = true,
    val offsetSec: Int = 0,
    val channels: Set<AlertChannel> = setOf(AlertChannel.SOUND),
    val sound: AlertSound = AlertSound.SOFT_GONG,
    val voice: VoicePhrase = VoicePhrase.NONE,
    val customVoiceText: String? = null,
    val vibration: VibrationPattern = VibrationPattern.SINGLE,
    val volumePercent: Int? = null,
    val customSoundUri: String? = null,
) : AlertPayload {
    /** Оповещение, которое ничего не сделает: выключено или без единого канала. */
    val isSilent: Boolean get() = !enabled || channels.isEmpty()

    /**
     * Прозвучит ли звуковой канал.
     *
     * `CUSTOM` без ссылки на файл — не звук, а обещание звука: пользователь
     * выбрал «свой файл» и не выбрал файл.
     */
    val hasPlayableSound: Boolean
        get() =
            hasChannel(AlertChannel.SOUND) &&
                when (sound) {
                    AlertSound.NONE -> false
                    AlertSound.CUSTOM -> !customSoundUri.isNullOrBlank()
                    else -> true
                }

    /**
     * Произнесёт ли это оповещение название своего этапа.
     *
     * Нужно на границе этапов: END уходящего этапа и START приходящего
     * срабатывают в один и тот же момент, и если START объявит этап сам,
     * то «далее: X» перед ним превращает подсказку в эхо (см. `voiceTextOf`).
     */
    val announcesStageName: Boolean
        get() = hasChannel(AlertChannel.VOICE) && voice == VoicePhrase.STAGE_NAME

    fun hasChannel(channel: AlertChannel): Boolean = enabled && channel in channels
}
