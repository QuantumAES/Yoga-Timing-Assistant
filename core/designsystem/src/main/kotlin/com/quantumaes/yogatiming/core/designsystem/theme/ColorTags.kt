package com.quantumaes.yogatiming.core.designsystem.theme

import androidx.compose.ui.graphics.Color

private const val OPAQUE_ALPHA = 0xFF00_0000L
private const val HEX_RADIX = 16

/**
 * Цветовые метки профилей и этапов.
 *
 * Цвет хранится строкой «#RRGGBB» — это формат ТЗ и формат будущего экспорта,
 * поэтому строка, а не `Int`: её видно глазами в базе и в JSON.
 */
object ColorTags {
    /** Совпадает с `DEFAULT_COLOR_TAG` домена (ТЗ §2). */
    const val DEFAULT = "#4CAF50"

    /**
     * Палитра выбора в редакторах.
     *
     * Тона Material 500: различимы и на светлом фоне, и на тёмном, поэтому
     * набор один на обе темы. Оттенки идут по кругу цветового круга, а не по
     * яркости, — метку выбирают по смыслу («разминка зелёная, шавасана
     * фиолетовая»), а не по контрасту.
     */
    val palette: List<String> =
        listOf(
            "#4CAF50",
            "#8BC34A",
            "#CDDC39",
            "#FFC107",
            "#FF9800",
            "#FF5722",
            "#F44336",
            "#E91E63",
            "#9C27B0",
            "#5C6BC0",
            "#42A5F5",
            "#009688",
        )

    /**
     * Некорректное значение не должно ломать экран: подставляем нейтральный.
     * Строка приходит из базы, из импорта и из старых версий — гарантий нет.
     */
    fun toColor(tag: String): Color =
        runCatching { Color(tag.removePrefix("#").toLong(radix = HEX_RADIX) or OPAQUE_ALPHA) }
            .getOrDefault(FALLBACK)

    private val FALLBACK = Color(0xFF4CAF50)
}
