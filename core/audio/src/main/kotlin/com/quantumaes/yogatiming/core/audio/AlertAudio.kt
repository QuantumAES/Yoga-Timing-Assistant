package com.quantumaes.yogatiming.core.audio

import com.quantumaes.yogatiming.domain.model.alert.AlertConfig

/**
 * Программный гейн поверх системной громкости будильника.
 *
 * Итоговая громкость = системная громкость будильника × громкость оповещения ×
 * общий множитель из настроек. Приложение никогда не трогает системные
 * настройки пользователя (ADR-003), поэтому оба ползунка — именно множители,
 * а не команда `setStreamVolume`.
 *
 * Множителей два, потому что и вопросов два: «насколько громко это оповещение
 * относительно остальных» решается в профиле, «насколько громко приложение
 * вообще» — один раз в настройках (Экран 6).
 *
 * @param volumeFactor общий множитель, 0..1 ([AppSettings.alertVolumeFactor]).
 */
internal fun gainOf(
    volumePercent: Int?,
    volumeFactor: Float = 1f,
): Float {
    val percent = volumePercent ?: AlertConfig.DEFAULT_MASTER_VOLUME
    val alertGain =
        percent.coerceIn(AlertConfig.MIN_VOLUME, AlertConfig.MAX_VOLUME) / AlertConfig.MAX_VOLUME.toFloat()
    return alertGain * volumeFactor.coerceIn(0f, 1f)
}
