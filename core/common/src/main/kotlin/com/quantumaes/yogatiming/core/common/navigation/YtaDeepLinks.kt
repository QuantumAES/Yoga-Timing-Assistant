package com.quantumaes.yogatiming.core.common.navigation

/**
 * Внутренние ссылки приложения.
 *
 * Живут в `:core:common`, потому что у ссылки два конца в разных модулях, и
 * разъехаться они не имеют права: `:timer-service` собирает её для уведомления,
 * `:feature:timer` объявляет по ней маршрут. Третий экземпляр — схема в
 * манифесте `:app`; описать её в Kotlin нельзя, поэтому рядом с ней стоит
 * ссылка на этот файл.
 *
 * Схема своя, а не `https`: ссылка нужна только внутри приложения, у неё нет
 * веб-адреса и подтверждать домен нечем. `BROWSABLE` у фильтра намеренно нет —
 * снаружи занятие открывать некому.
 */
object YtaDeepLinks {
    const val SCHEME: String = "yta"

    /** Хост ссылки на идущее занятие. */
    const val SESSION_HOST: String = "session"

    /** Базовый путь маршрута занятия: `profileId` навигация добавит сама. */
    const val SESSION_BASE: String = "$SCHEME://$SESSION_HOST"

    /** Ссылка на занятие конкретного профиля. */
    fun session(profileId: Long): String = "$SESSION_BASE/$profileId"
}
