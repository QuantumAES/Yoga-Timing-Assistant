package com.quantumaes.yogatiming.feature.timer

import androidx.compose.runtime.Composable
import com.quantumaes.yogatiming.core.designsystem.component.PlaceholderAction
import com.quantumaes.yogatiming.core.designsystem.component.PlaceholderScreen

/**
 * Экран 4 «Занятие» — главный рабочий экран.
 *
 * Заглушка Фазы 1. Реальный экран строится в Фазе 6 поверх движка Фазы 3
 * и использует фиксированную палитру `TimerPalette` (не Material You,
 * см. docs/06-MVP-SCOPE.md §4).
 */
@Composable
internal fun TimerScreen(
    profileId: Long,
    onFinish: () -> Unit,
    onExit: () -> Unit,
) {
    PlaceholderScreen(
        title = "Занятие",
        description = "Экран 4: рабочий экран, профиль #$profileId. Заглушка Фазы 1.",
        actions =
            listOf(
                PlaceholderAction("Завершить занятие") { onFinish() },
                PlaceholderAction("Выйти к профилям") { onExit() },
            ),
    )
}

/** Экран после завершения занятия: «В начало» / «Повторить». */
@Composable
internal fun SessionFinishedScreen(
    onRepeat: () -> Unit,
    onExit: () -> Unit,
) {
    PlaceholderScreen(
        title = "Занятие завершено",
        description = "Заглушка Фазы 1.",
        actions =
            listOf(
                PlaceholderAction("Повторить") { onRepeat() },
                PlaceholderAction("К списку профилей") { onExit() },
            ),
    )
}
