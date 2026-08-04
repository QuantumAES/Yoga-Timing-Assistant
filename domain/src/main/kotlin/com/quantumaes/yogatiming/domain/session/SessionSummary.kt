package com.quantumaes.yogatiming.domain.session

/** Чем закончилось занятие. */
enum class SessionOutcome {
    /** Последний этап дошёл до конца. */
    COMPLETED,

    /** Занятие остановлено вручную — с экрана или из шторки. */
    STOPPED,
}

/**
 * Что стало с одним этапом за занятие.
 *
 * Нужен ровно для одного вопроса — стоит ли предлагать сохранить занятие
 * новым профилем и с какими длительностями (замечание 7 полевой проверки
 * 2026-08-04). Фактическое время здесь не годится: инструктор жмёт «След.» на
 * полминуты раньше просто потому, что группа готова, и превращать эти
 * случайности в профиль — значит завести профиль, который никто не задавал.
 * Годится **эффективная** длительность: она меняется только осознанной правкой
 * ±30 с или сжатием плана под бюджет.
 *
 * @param stageId идентификатор этапа в профиле. У двусторонней асаны обе
 *   половины несут один и тот же — это по-прежнему один этап.
 * @param plannedMs что было в профиле до занятия.
 * @param effectiveMs что получилось после правок.
 */
data class StageOutcome(
    val stageId: Long,
    val name: String,
    val plannedMs: Long,
    val effectiveMs: Long,
) {
    val adjustmentMs: Long get() = effectiveMs - plannedMs
}

/**
 * Итоги проведённого занятия (полевая проверка 2026-07-31, замечание 7).
 *
 * Модель домена, а не поле экрана: те же числа нужны экрану «Занятие
 * завершено» сегодня и журналу занятий в статистике завтра
 * (docs/09-STATISTICS.md). Собирает её `:timer-service` в момент конца
 * занятия — позже этих чисел уже нет: состояние движка сбрасывается к плану.
 *
 * @param startedAtWallMs стенные часы начала занятия. Только для показа
 *   «18:05 → 19:03»: расчёты длительности идут по монотонным (принцип П-3).
 * @param plannedDurationMs сумма плановых длительностей; FREE-этапы в неё не
 *   входят, поэтому у профиля со свободным этапом факт законно больше плана.
 * @param actualDurationMs сколько занятие шло на самом деле. Пауза занятия
 *   сюда не входит: это время практики, а не время между «начали» и
 *   «закончили».
 * @param holdMs сколько занятие простояло на паузе этапа. В [actualDurationMs]
 *   не входит, но зал был занят — поэтому для аренды считается.
 * @param targetDurationMs целевое время занятия; `null` — цели не было.
 * @param stagesCompleted пройденные целиком этапы. У брошенного занятия —
 *   меньше [stageCount], и разница как раз и есть «дошли до пятого из шести».
 * @param stages что стало с каждым этапом — для предложения сохранить профиль.
 */
data class SessionSummary(
    val profileId: Long,
    val profileName: String,
    val outcome: SessionOutcome,
    val startedAtWallMs: Long,
    val finishedAtWallMs: Long,
    val plannedDurationMs: Long,
    val actualDurationMs: Long,
    val holdMs: Long = 0L,
    val targetDurationMs: Long? = null,
    val stagesCompleted: Int,
    val stageCount: Int,
    val stages: List<StageOutcome> = emptyList(),
) {
    /** Насколько занятие разошлось с планом: «+2:30» к плановому времени. */
    val deviationMs: Long get() = actualDurationMs - plannedDurationMs

    /** Сколько занятие заняло по часам: практика плюс паузы этапа. */
    val occupiedMs: Long get() = actualDurationMs + holdMs

    /** Уложилось ли занятие в целевое время; `null` — цели не было. */
    val targetDeviationMs: Long? get() = targetDurationMs?.let { occupiedMs - it }

    /**
     * Правил ли инструктор длительности по ходу занятия.
     *
     * Именно этот признак включает предложение сохранить новый профиль: если
     * ничего не правили, предлагать нечего — профиль уже такой.
     */
    val wasAdjusted: Boolean get() = stages.any { it.adjustmentMs != 0L }

    /**
     * Длительности этапов в том виде, в каком их стоит записать в новый профиль.
     *
     * Ключ — идентификатор этапа, значение — секунды. Половины двусторонней
     * асаны схлопываются в одну запись: правка на них зеркальна, и в профиле у
     * такого этапа одно поле — длительность стороны.
     */
    val adjustedDurationsSec: Map<Long, Int>
        get() =
            stages
                .filter { it.adjustmentMs != 0L }
                .associate { it.stageId to (it.effectiveMs / MS_IN_SECOND).toInt() }

    private companion object {
        const val MS_IN_SECOND = 1_000L
    }
}
