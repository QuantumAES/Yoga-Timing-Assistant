package com.quantumaes.yogatiming.core.designsystem.theme

import androidx.compose.ui.unit.dp

/** Шаг сетки — 4 dp. Все отступы кратны ему. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

object Dimens {
    /** Минимальная область нажатия (Material + требование доступности). */
    val minTouchTarget = 48.dp

    /** Кнопки рабочего экрана: нажимаются мокрыми руками с коврика, не глядя. */
    val timerControlSize = 72.dp

    /** Толщина прогресс-кольца рабочего экрана. */
    val progressRingWidth = 12.dp

    val cardCornerRadius = 16.dp
    val listItemMinHeight = 72.dp
}
