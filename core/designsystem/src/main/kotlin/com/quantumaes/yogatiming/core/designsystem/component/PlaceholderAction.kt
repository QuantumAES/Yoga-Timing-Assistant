package com.quantumaes.yogatiming.core.designsystem.component

/** Кнопка перехода на экране-заглушке [PlaceholderScreen]. */
data class PlaceholderAction(
    val label: String,
    val onClick: () -> Unit,
)
