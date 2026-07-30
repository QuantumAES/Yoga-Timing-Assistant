package com.quantumaes.yogatiming.domain.model

import com.quantumaes.yogatiming.domain.model.alert.AlertConfig

/**
 * Этап занятия.
 *
 * @param durationSec плановая длительность. Для [StageType.FREE] равна нулю —
 *   этап длится до ручного перехода.
 * @param alertConfig `null` означает наследование конфига профиля. Это не
 *   «пустые оповещения», а именно отсутствие переопределения (ADR-002).
 * @param voiceName как этап произносится вслух. `null` — как написан.
 *   Санскритские названия асан синтезатор читает с чужими ударениями, и
 *   исправить это можно только текстом: «шав+асана» или прямая перезапись
 *   произношения. На экране всегда виден [name] — поле правит озвучку, а не
 *   название.
 */
data class Stage(
    val id: Long = NEW_ID,
    val profileId: Long = NEW_ID,
    val name: String,
    val type: StageType = StageType.NORMAL,
    val colorTag: String = DEFAULT_COLOR_TAG,
    val durationSec: Int = 0,
    val note: String? = null,
    val sortOrder: Int = 0,
    val alertConfig: AlertConfig? = null,
    val voiceName: String? = null,
) {
    /** Участвует ли этап в подсчёте общего времени занятия (решение B-4). */
    val hasPlannedDuration: Boolean get() = type != StageType.FREE

    companion object {
        /** Границы длительности этапа: 5 секунд … 4 часа (решение B-3). */
        const val MIN_DURATION_SEC = 5
        const val MAX_DURATION_SEC = 4 * 60 * 60
    }
}
