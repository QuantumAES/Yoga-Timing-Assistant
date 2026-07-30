package com.quantumaes.yogatiming.timer.engine.model

/**
 * Иммутабельный вход движка (docs/02-TIMER-CORE-DESIGN.md §3.1).
 *
 * План собирается в `:domain` из профиля и этапов. К моменту, когда движок его
 * получил, все решения уже приняты: наследование оповещений разрешено, «тихий»
 * пресет для REST применён, длительности переведены в миллисекунды.
 *
 * @throws IllegalArgumentException если этапов нет: профиль без этапов запустить
 *   нельзя (решение B-6), и проверять это в каждом методе движка незачем.
 */
data class SessionPlan(
    val profileId: Long,
    val profileName: String,
    val stages: List<PlannedStage>,
) {
    init {
        require(stages.isNotEmpty()) { "SessionPlan без этапов запустить нельзя (B-6)" }
    }

    val lastIndex: Int get() = stages.lastIndex

    /**
     * Сумма плановых длительностей.
     *
     * FREE-этапы исключены: их длительность неизвестна заранее, поэтому сумма —
     * нижняя граница (решение B-4).
     */
    val plannedDurationMs: Long get() = stages.filter { it.kind.hasDeadline }.sumOf { it.plannedDurationMs }

    val hasFreeStages: Boolean get() = stages.any { !it.kind.hasDeadline }
}

/**
 * Этап в плане сессии.
 *
 * @param plannedDurationMs плановая длительность; для [StageKind.FREE] — 0,
 *   этап длится до ручного перехода.
 * @param note заметка инструктору, показывается на рабочем экране.
 * @param voiceName как этап произносится вслух; `null` — как написан [name].
 *   Движок его не читает и не показывает: несёт как часть плана тому, кто
 *   озвучивает оповещения.
 */
data class PlannedStage(
    val id: Long,
    val name: String,
    val kind: StageKind = StageKind.NORMAL,
    val colorTag: String,
    val plannedDurationMs: Long,
    val note: String? = null,
    val voiceName: String? = null,
    val alerts: ResolvedAlertConfig = ResolvedAlertConfig.SILENT,
)
