package com.quantumaes.yogatiming.timer.service

import android.content.Context
import com.quantumaes.yogatiming.domain.session.SideLabels
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Как называть стороны двусторонней асаны — на языке интерфейса.
 *
 * Отдельный контракт, а не `Context` внутри контроллера. Причины две.
 *
 * Первая: `SessionController` — единственное место, где сходятся движок,
 * персист и watchdog, и он проверяется юнит-тестом без Android. Контекст ради
 * двух строк превратил бы этот тест в инструментальный.
 *
 * Вторая: строки читаются **в момент сборки плана**, а не при создании графа.
 * Язык интерфейса меняется на ходу, и занятие, начатое после переключения,
 * обязано называть стороны по-новому.
 */
fun interface SideLabelsSource {
    fun labels(): SideLabels
}

@Singleton
class ResourceSideLabels
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SideLabelsSource {
        override fun labels(): SideLabels =
            SideLabels(
                first = context.getString(R.string.timer_side_first),
                second = context.getString(R.string.timer_side_second),
            )
    }
