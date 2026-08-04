package com.quantumaes.yogatiming.timer.engine

import com.quantumaes.yogatiming.timer.engine.model.SessionState
import com.quantumaes.yogatiming.timer.engine.model.budgetDeficitMs

/**
 * «Ужать план» — привести остаток занятия к остатку бюджета
 * (замечание 11 полевой проверки 2026-08-04).
 *
 * Считать в уме, сколько снять с каждого из четырёх оставшихся этапов, посреди
 * занятия невозможно, а именно это и требуется, когда к сороковой минуте
 * набежало восемь минут правок. Дефицит распределяется пропорционально
 * длительностям — короткие этапы теряют меньше, — и ни один этап не опускается
 * ниже нижней границы (решение B-3).
 *
 * Растягивать план команда не умеет намеренно: запас времени инструктор
 * распределит сам и лучше, чем пропорция, а дефицит закрывать надо быстро.
 */
internal fun fitToBudget(
    state: SessionState,
    now: Long,
): Reduction {
    if (!state.runState.isActive) return Reduction.unchanged(state)
    val deficit = state.budgetDeficitMs(now) ?: return Reduction.unchanged(state)
    if (deficit <= 0L) return Reduction.unchanged(state)

    val future =
        (state.currentIndex + 1..state.plan.lastIndex).filter {
            state.plan.stages[it]
                .kind.hasDeadline
        }
    val cuts = shrink(future.associateWith { state.effectiveDurationMs(it) }, deficit)
    if (cuts.isEmpty()) return Reduction.unchanged(state)

    val adjustments =
        state.adjustmentsMs +
            cuts.mapValues { (index, cut) -> (state.adjustmentsMs[index] ?: 0L) - cut }
    return Reduction(state.copy(adjustmentsMs = adjustments), listOf(TimerEvent.PlanChanged))
}

/**
 * Сколько снять с каждого этапа, чтобы в сумме получилось [need].
 *
 * Два прохода вместо одного: пропорция с целочисленным делением почти всегда
 * недобирает несколько миллисекунд, а этапы, упёршиеся в нижнюю границу, —
 * и вовсе свою долю. Второй проход добирает остаток с тех, у кого запас ещё
 * есть, начиная с самого длинного.
 *
 * @param durations эффективные длительности будущих этапов по индексам.
 * @return только ненулевые сокращения; пустая карта — снимать нечего.
 */
private fun shrink(
    durations: Map<Int, Long>,
    need: Long,
): Map<Int, Long> {
    val slack = durations.mapValues { (_, duration) -> (duration - TimerLimits.MIN_STAGE_MS).coerceAtLeast(0L) }
    val pool = slack.values.sum()
    if (pool <= 0L) return emptyMap()

    val target = minOf(need, pool)
    val cuts = slack.mapValues { (_, available) -> target * available / pool }.toMutableMap()

    var rest = target - cuts.values.sum()
    for (index in slack.keys.sortedByDescending { slack.getValue(it) }) {
        if (rest <= 0L) break
        val room = slack.getValue(index) - cuts.getValue(index)
        val extra = minOf(rest, room)
        cuts[index] = cuts.getValue(index) + extra
        rest -= extra
    }
    return cuts.filterValues { it > 0L }
}
