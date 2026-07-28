package com.quantumaes.yogatiming.core.audio

import com.quantumaes.yogatiming.domain.model.alert.AlertConfig

/**
 * Программный гейн поверх системной громкости будильника.
 *
 * Итоговая громкость = системная громкость будильника × этот множитель.
 * Приложение никогда не трогает системные настройки пользователя (ADR-003),
 * поэтому ползунок «Громкость сигналов» — именно множитель, а не команда
 * `setStreamVolume`.
 */
internal fun gainOf(volumePercent: Int?): Float {
    val percent = volumePercent ?: AlertConfig.DEFAULT_MASTER_VOLUME
    return percent.coerceIn(AlertConfig.MIN_VOLUME, AlertConfig.MAX_VOLUME) / AlertConfig.MAX_VOLUME.toFloat()
}
