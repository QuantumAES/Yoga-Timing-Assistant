package com.quantumaes.yogatiming.domain.model

/** Категория профиля — используется фильтр-чипами на экране списка (ТЗ, Экран 1). */
enum class ProfileCategory {
    GENERAL,
    HATHA,
    VINYASA,
    YIN,
    MEDITATION,
    STRETCHING,
    BREATHING,
    ;

    companion object {
        val DEFAULT = GENERAL

        /** Неизвестное значение из БД не роняет чтение — деградируем к [DEFAULT]. */
        fun fromName(raw: String?): ProfileCategory = entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}
