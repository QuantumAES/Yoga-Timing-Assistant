package com.quantumaes.yogatiming.feature.stats

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Экран статистики занятий (docs/09-STATISTICS.md, фаза S3). */
@Serializable
data object StatsRoute

/**
 * Экран один и без параметров: период живёт в модели, а не в маршруте.
 *
 * Класть период в маршрут значило бы плодить точки входа («статистика за
 * ноябрь») там, где вход ровно один — иконка в шапке списка профилей.
 */
fun NavGraphBuilder.statsScreen(onBack: () -> Unit) {
    composable<StatsRoute> {
        StatsScreen(onBack = onBack)
    }
}
