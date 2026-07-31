package com.quantumaes.yogatiming.domain.session

/** Чем закончилось занятие. */
enum class SessionOutcome {
    /** Последний этап дошёл до конца. */
    COMPLETED,

    /** Занятие остановлено вручную — с экрана или из шторки. */
    STOPPED,
}

/**
 * Итоги проведённого занятия (полевая проверка 2026-07-31, замечание 7).
 *
 * Модель домена, а не поле экрана: те же семь чисел нужны экрану «Занятие
 * завершено» сегодня и журналу занятий в статистике завтра
 * (docs/09-STATISTICS.md). Собирает её `:timer-service` в момент конца
 * занятия — позже этих чисел уже нет: состояние движка сбрасывается к плану.
 *
 * @param startedAtWallMs стенные часы начала занятия. Только для показа
 *   «18:05 → 19:03»: расчёты длительности идут по монотонным (принцип П-3).
 * @param plannedDurationMs сумма плановых длительностей; FREE-этапы в неё не
 *   входят, поэтому у профиля со свободным этапом факт законно больше плана.
 * @param actualDurationMs сколько занятие шло на самом деле. Пауза сюда не
 *   входит: это время практики, а не время между «начали» и «закончили».
 * @param stagesCompleted пройденные целиком этапы. У брошенного занятия —
 *   меньше [stageCount], и разница как раз и есть «дошли до пятого из шести».
 */
data class SessionSummary(
    val profileId: Long,
    val profileName: String,
    val outcome: SessionOutcome,
    val startedAtWallMs: Long,
    val finishedAtWallMs: Long,
    val plannedDurationMs: Long,
    val actualDurationMs: Long,
    val stagesCompleted: Int,
    val stageCount: Int,
) {
    /** Насколько занятие разошлось с планом: «+2:30» к плановому времени. */
    val deviationMs: Long get() = actualDurationMs - plannedDurationMs
}
