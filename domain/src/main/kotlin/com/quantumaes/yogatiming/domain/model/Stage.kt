package com.quantumaes.yogatiming.domain.model

import com.quantumaes.yogatiming.domain.model.alert.AlertConfig

/**
 * Этап занятия.
 *
 * @param durationSec плановая длительность. Для [StageType.FREE] равна нулю —
 *   этап длится до ручного перехода. Для двустороннего этапа ([bilateral]) —
 *   длительность **одной стороны**: инструктор думает и говорит «держим минуту»,
 *   а не «держим две минуты на обе».
 * @param alertConfig `null` означает наследование конфига профиля. Это не
 *   «пустые оповещения», а именно отсутствие переопределения (ADR-002).
 * @param voiceName как этап произносится вслух. `null` — как написан.
 *   Санскритские названия асан синтезатор читает с чужими ударениями, и
 *   исправить это можно только текстом: «шав+асана» или прямая перезапись
 *   произношения. На экране всегда виден [name] — поле правит озвучку, а не
 *   название.
 * @param bilateral асана выполняется зеркально на обе стороны. На занятии такой
 *   этап проходится дважды — по одному отрезку на сторону, с сигналом смены
 *   между ними: инструктору нужно видеть, сколько держать **эту** сторону, а не
 *   сколько осталось на обе (замечание 10 полевой проверки 2026-08-04).
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
    val bilateral: Boolean = false,
) {
    /** Участвует ли этап в подсчёте общего времени занятия (решение B-4). */
    val hasPlannedDuration: Boolean get() = type != StageType.FREE

    /** Двусторонний ли этап на самом деле: у FREE сторон нет — нечего делить. */
    val isBilateral: Boolean get() = bilateral && hasPlannedDuration

    /**
     * Сколько этап занимает в занятии целиком.
     *
     * У двустороннего это две [durationSec]: в план он разворачивается двумя
     * половинами, и сумма профиля обязана это учитывать — иначе «60 минут» в
     * списке профилей означали бы что угодно.
     */
    val plannedDurationSec: Int get() = if (isBilateral) durationSec * SIDES else durationSec

    companion object {
        /** Границы длительности этапа: 5 секунд … 4 часа (решение B-3). */
        const val MIN_DURATION_SEC = 5
        const val MAX_DURATION_SEC = 4 * 60 * 60

        /** Сторон у двусторонней асаны ровно две — правая и левая. */
        const val SIDES = 2
    }
}
