package com.quantumaes.yogatiming.domain.settings

/**
 * Оформление приложения (Экран 6 настроек).
 *
 * Приоритет применения (docs/06-MVP-SCOPE.md, решение C-4):
 * явный выбор пользователя → системная тема.
 *
 * [SYSTEM] стоит первым в перечислении и служит значением по умолчанию:
 * пользователь, настроивший тему один раз в системе, вправе не настраивать её
 * заново в каждом приложении.
 */
enum class ThemeMode {
    /** Следовать системной теме. */
    SYSTEM,

    LIGHT,

    DARK,
    ;

    companion object {
        val DEFAULT = SYSTEM

        /** Неизвестное значение из хранилища не роняет запуск — деградируем к [DEFAULT]. */
        fun fromName(raw: String?): ThemeMode = entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}

/**
 * Пользовательские настройки приложения.
 *
 * В v1.0 здесь только оформление: громкость, TTS и wake lock приезжают в Фазу 7
 * (docs/01-ROADMAP.md). Класс единый намеренно — экран настроек читает одно
 * состояние, а не десяток независимых потоков, которые обновляются вразнобой.
 *
 * @param dynamicColor палитра Material You из обоев. На рабочий экран занятия
 *   не распространяется никогда (docs/06-MVP-SCOPE.md §4).
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DEFAULT,
    val dynamicColor: Boolean = false,
)
