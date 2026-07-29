package com.quantumaes.yogatiming.feature.editor.component

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

private const val HALF = 2

/**
 * Перетаскивание элементов внутри `LazyColumn`.
 *
 * Своя реализация вместо библиотеки: единственное, что здесь нужно, —
 * переставить этап в коротком списке, а внешняя зависимость ради этого тянет
 * собственный жизненный цикл и своё понимание ключей.
 *
 * Индекс перетаскиваемого элемента приходит снаружи, а не вычисляется по
 * координате нажатия: жест живёт внутри ручки и меряет смещение в её системе
 * координат, а границы элементов из [LazyListState.layoutInfo] заданы
 * в координатах списка. Сравнивать их напрямую нельзя.
 *
 * Целевая позиция определяется по фактическим границам элементов, а не по
 * предполагаемой высоте строки: строки разной высоты (длинное название
 * переносится) иначе меняются местами не в тот момент.
 *
 * @param onMove вызывается по мере перетаскивания, а не в конце: список
 *   перестраивается под пальцем, и пользователь видит, куда попадёт элемент.
 */
class ReorderableState internal constructor(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    /** Индекс перетаскиваемого элемента; `null` — перетаскивания нет. */
    var draggingIndex: Int? by mutableStateOf(null)
        private set

    /** Сдвиг перетаскиваемого элемента относительно его текущего места, в пикселях. */
    var dragOffset: Float by mutableFloatStateOf(0f)
        private set

    internal fun onDragStart(index: Int) {
        draggingIndex = index
        dragOffset = 0f
    }

    internal fun onDrag(deltaY: Float) {
        val current = draggingIndex ?: return
        dragOffset += deltaY

        val target = targetIndex(current) ?: return
        onMove(current, target)
        draggingIndex = target
        // Смещение отсчитывается от нового места: элемент уже переехал,
        // и продолжать сдвигать его от старого значило бы удвоить движение.
        dragOffset = 0f
    }

    /** Элемент, на который сейчас указывает центр перетаскиваемого; `null` — тот же самый. */
    private fun targetIndex(current: Int): Int? {
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.firstOrNull { it.index == current } ?: return null
        val center = dragged.offset + dragged.size / HALF + dragOffset
        return items
            .firstOrNull { center >= it.offset && center <= it.offset + it.size }
            ?.index
            ?.takeIf { it != current }
    }

    internal fun onDragEnd() {
        draggingIndex = null
        dragOffset = 0f
    }
}

@Composable
fun rememberReorderableState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
): ReorderableState {
    val currentOnMove by rememberUpdatedState(onMove)
    return remember(listState) {
        ReorderableState(listState) { from, to -> currentOnMove(from, to) }
    }
}

/**
 * Ручка перетаскивания. Вешается на отдельный элемент строки, а не на всю
 * строку: по строке пользователь нажимает, чтобы открыть этап, и долгое
 * нажатие где угодно превратило бы промах в перетаскивание.
 */
fun Modifier.dragHandle(
    state: ReorderableState,
    index: Int,
): Modifier =
    pointerInput(state, index) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.onDragStart(index) },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDrag(dragAmount.y)
            },
            onDragEnd = { state.onDragEnd() },
            onDragCancel = { state.onDragEnd() },
        )
    }
