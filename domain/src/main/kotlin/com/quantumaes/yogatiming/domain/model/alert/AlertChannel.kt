package com.quantumaes.yogatiming.domain.model.alert

import kotlinx.serialization.Serializable

/**
 * Канал доставки оповещения (ТЗ §5.1).
 *
 * Битовая маска из ТЗ §2 намеренно не используется: набор каналов хранится
 * внутри JSON-конфига, где читаемый массив имён надёжнее числа — он переживает
 * добавление каналов и виден глазами при отладке и в экспорте.
 *
 * Канал FLASH (экран / фонарик) перенесён в v1.1 (docs/06-MVP-SCOPE.md §1.2).
 */
@Serializable
enum class AlertChannel {
    SOUND,
    VOICE,
    VIBRATION,
}
