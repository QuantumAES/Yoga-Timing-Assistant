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

    /**
     * «До завершения занятия осталось десять минут» — отсечка бюджета.
     *
     * Отличается от [TIME_REMAINING] тем, к чему относится: та про этап, эта
     * про занятие целиком. Инструктору важно не перепутать: «осталась минута»
     * посреди занятия и «осталось десять минут» до его конца требуют разных
     * действий.
     */
    WRAP_UP,

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
 * @param customSoundDurationSec сколько секунд играть файл пользователя.
 *   Оповещение — это сигнал, а не фонограмма: файл может оказаться
 *   пятиминутной мелодией, и без верхней границы этап прошёл бы под музыку,
 *   которую нечем остановить. Последние секунды звучат с затуханием
 *   ([CUSTOM_SOUND_FADE_SEC]) — обрыв на полуноте слышен как сбой.
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
    val customSoundDurationSec: Int = DEFAULT_CUSTOM_SOUND_SEC,
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

    /** Сколько играть файл пользователя, в миллисекундах — уже с проверкой границ. */
    val customSoundLimitMs: Long
        get() = customSoundDurationSec.coerceIn(MIN_CUSTOM_SOUND_SEC, MAX_CUSTOM_SOUND_SEC) * MS_IN_SECOND

    fun hasChannel(channel: AlertChannel): Boolean = enabled && channel in channels

    companion object {
        /**
         * Сколько играть файл пользователя, если он не выбрал иначе.
         *
         * Десять секунд — верхняя оценка сигнала: дольше звучит только чаша
         * (4,5 с) и хвост её затухания. Значение то же, что и у
         * предпрослушивания в редакторе: пользователь слышит ровно то, что
         * прозвучит на занятии.
         */
        const val DEFAULT_CUSTOM_SOUND_SEC = 10

        const val MIN_CUSTOM_SOUND_SEC = 3

        /**
         * Минута — предел, после которого сигнал перестаёт быть сигналом.
         * Фоновая музыка на весь этап — это другая задача (и другой продукт).
         */
        const val MAX_CUSTOM_SOUND_SEC = 60

        /** Сколько длится затухание в конце. Входит в [customSoundDurationSec], а не добавляется к нему. */
        const val CUSTOM_SOUND_FADE_SEC = 2

        private const val MS_IN_SECOND = 1_000L
    }
}
