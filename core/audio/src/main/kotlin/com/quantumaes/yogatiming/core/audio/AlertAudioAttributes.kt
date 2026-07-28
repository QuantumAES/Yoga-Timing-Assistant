package com.quantumaes.yogatiming.core.audio

import android.media.AudioAttributes

/**
 * Канал воспроизведения всех оповещений — будильник (ADR-003).
 *
 * `USAGE_ALARM` выбран не за громкость, а за три свойства, без которых продукт
 * не выполняет свою задачу: собственный системный ползунок, слышимость в
 * беззвучном режиме и проход через «Не беспокоить» в конфигурации по
 * умолчанию. Те же атрибуты ставятся TTS и вибрации — иначе голос уйдёт в
 * другой стрим и будет звучать тише сигналов.
 *
 * Отдельный файл, а не соседство с [gainOf]: в JVM-тестах `android.media`
 * заглушена, и статическая инициализация утащила бы за собой чистые функции.
 */
internal val alertAudioAttributes: AudioAttributes =
    AudioAttributes
        .Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
