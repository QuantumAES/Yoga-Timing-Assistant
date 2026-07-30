package com.quantumaes.yogatiming.timer.service

import com.quantumaes.yogatiming.timer.engine.model.SessionSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * Только чтение состояния идущего занятия.
 *
 * Существует ради экранов, которым занятие интересно, но управлять им не их
 * дело: список профилей показывает полосу идущего занятия и не даёт править
 * запущенный профиль. Отдавать им [SessionController] значило бы отдать вместе
 * с ним `submit`, `startSession` и `restoreSession` — и полагаться на то, что
 * никто не вызовет их из списка.
 *
 * Побочная выгода: подделать один поток в юнит-тесте можно одной строкой,
 * а поднять контроллер — только вместе с движком, хранилищем и watchdog.
 */
interface ActiveSessionSource {
    /** `null` — занятие не загружено. */
    val snapshot: StateFlow<SessionSnapshot?>
}
