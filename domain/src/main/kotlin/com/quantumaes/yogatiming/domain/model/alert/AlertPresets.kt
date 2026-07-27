package com.quantumaes.yogatiming.domain.model.alert

/**
 * Фабрика готовых конфигураций оповещений.
 *
 * [standard] — дословная реализация дефолтной схемы ТЗ §5.2:
 *
 * | Триггер | Каналы |
 * |---|---|
 * | старт этапа | мягкий гонг + название этапа голосом |
 * | за 2 мин | одиночная вибрация |
 * | за 1 мин | двойная вибрация + голос «осталась минута» |
 * | конец этапа | гонг + голос следующего этапа |
 *
 * Отсчёта «за 10 секунд» в стандартной схеме нет намеренно: он конфликтует
 * с очередью TTS и на коротких этапах превращается в шум (решение B-9).
 * Включён только в [maximum].
 */
object AlertPresets {
    private const val TWO_MINUTES_SEC = 120
    private const val ONE_MINUTE_SEC = 60
    private const val FIVE_MINUTES_SEC = 300
    private const val TEN_SECONDS_SEC = 10

    private const val QUIET_VOLUME = 45
    private const val LOUD_VOLUME = 90

    fun of(preset: AlertPreset): AlertConfig =
        when (preset) {
            AlertPreset.STANDARD -> standard()
            AlertPreset.SILENT -> silent()
            AlertPreset.VIBRO_ONLY -> vibrationOnly()
            AlertPreset.MAX -> maximum()
            AlertPreset.CUSTOM -> standard().copy(preset = AlertPreset.CUSTOM)
        }

    fun standard(): AlertConfig =
        AlertConfig(
            preset = AlertPreset.STANDARD,
            start =
                Alert(
                    channels = setOf(AlertChannel.SOUND, AlertChannel.VOICE),
                    sound = AlertSound.SOFT_GONG,
                    voice = VoicePhrase.STAGE_NAME,
                ),
            warnings =
                listOf(
                    Alert(
                        offsetSec = TWO_MINUTES_SEC,
                        channels = setOf(AlertChannel.VIBRATION),
                        sound = AlertSound.NONE,
                        vibration = VibrationPattern.SINGLE,
                    ),
                    Alert(
                        offsetSec = ONE_MINUTE_SEC,
                        channels = setOf(AlertChannel.VIBRATION, AlertChannel.VOICE),
                        sound = AlertSound.NONE,
                        voice = VoicePhrase.TIME_REMAINING,
                        vibration = VibrationPattern.DOUBLE,
                    ),
                ),
            end =
                Alert(
                    channels = setOf(AlertChannel.SOUND, AlertChannel.VOICE),
                    sound = AlertSound.SOFT_GONG,
                    voice = VoicePhrase.NEXT_STAGE,
                ),
        )

    /**
     * Тихий пресет для шавасаны и медитации.
     *
     * Предупреждений нет вовсе: вибрация в глубоком расслаблении выдёргивает
     * не мягче звука. Вход и выход — одна поющая чаша на пониженной громкости.
     */
    fun silent(): AlertConfig =
        AlertConfig(
            preset = AlertPreset.SILENT,
            masterVolumePercent = QUIET_VOLUME,
            start =
                Alert(
                    channels = setOf(AlertChannel.SOUND),
                    sound = AlertSound.SINGING_BOWL,
                ),
            warnings = emptyList(),
            end =
                Alert(
                    channels = setOf(AlertChannel.SOUND),
                    sound = AlertSound.SINGING_BOWL,
                ),
        )

    fun vibrationOnly(): AlertConfig =
        AlertConfig(
            preset = AlertPreset.VIBRO_ONLY,
            start =
                Alert(
                    channels = setOf(AlertChannel.VIBRATION),
                    sound = AlertSound.NONE,
                    vibration = VibrationPattern.SINGLE,
                ),
            warnings =
                listOf(
                    Alert(
                        offsetSec = ONE_MINUTE_SEC,
                        channels = setOf(AlertChannel.VIBRATION),
                        sound = AlertSound.NONE,
                        vibration = VibrationPattern.SINGLE,
                    ),
                ),
            end =
                Alert(
                    channels = setOf(AlertChannel.VIBRATION),
                    sound = AlertSound.NONE,
                    vibration = VibrationPattern.DOUBLE,
                ),
        )

    fun maximum(): AlertConfig =
        AlertConfig(
            preset = AlertPreset.MAX,
            masterVolumePercent = LOUD_VOLUME,
            start =
                Alert(
                    channels = setOf(AlertChannel.SOUND, AlertChannel.VOICE, AlertChannel.VIBRATION),
                    sound = AlertSound.BELL,
                    voice = VoicePhrase.STAGE_NAME,
                    vibration = VibrationPattern.SINGLE,
                ),
            warnings =
                listOf(
                    Alert(
                        offsetSec = FIVE_MINUTES_SEC,
                        channels = setOf(AlertChannel.VOICE),
                        sound = AlertSound.NONE,
                        voice = VoicePhrase.TIME_REMAINING,
                    ),
                    Alert(
                        offsetSec = TWO_MINUTES_SEC,
                        channels = setOf(AlertChannel.VIBRATION, AlertChannel.VOICE),
                        sound = AlertSound.NONE,
                        voice = VoicePhrase.TIME_REMAINING,
                        vibration = VibrationPattern.SINGLE,
                    ),
                    Alert(
                        offsetSec = ONE_MINUTE_SEC,
                        channels = setOf(AlertChannel.VIBRATION, AlertChannel.VOICE),
                        sound = AlertSound.NONE,
                        voice = VoicePhrase.TIME_REMAINING,
                        vibration = VibrationPattern.DOUBLE,
                    ),
                    // Отсчёт последних секунд — только тики, без TTS (решение B-9).
                    Alert(
                        offsetSec = TEN_SECONDS_SEC,
                        channels = setOf(AlertChannel.SOUND),
                        sound = AlertSound.TICK,
                    ),
                ),
            end =
                Alert(
                    channels = setOf(AlertChannel.SOUND, AlertChannel.VOICE, AlertChannel.VIBRATION),
                    sound = AlertSound.SOFT_GONG,
                    voice = VoicePhrase.NEXT_STAGE,
                    vibration = VibrationPattern.LONG,
                ),
        )
}
