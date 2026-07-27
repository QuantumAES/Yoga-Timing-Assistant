package com.quantumaes.yogatiming.domain.model

/**
 * Способ вычисления общей длительности занятия.
 *
 * В v1.0 поддерживается только [SUM]. [FIXED] с автораспределением остатка
 * перенесён в v1.1, но присутствует в модели и схеме БД с первой версии,
 * чтобы не потребовалась миграция (docs/06-MVP-SCOPE.md §1.2).
 */
enum class TotalDurationMode {
    /** Общее время = сумма длительностей этапов. */
    SUM,

    /** Общее время задано, остаток распределяется между этапами. v1.1. */
    FIXED,
    ;

    companion object {
        val DEFAULT = SUM

        fun fromName(raw: String?): TotalDurationMode = entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}
