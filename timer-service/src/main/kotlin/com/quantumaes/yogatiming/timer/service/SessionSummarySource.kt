package com.quantumaes.yogatiming.timer.service

import com.quantumaes.yogatiming.domain.session.SessionSummary
import kotlinx.coroutines.flow.StateFlow

/**
 * Итоги последнего закончившегося занятия — только чтение.
 *
 * Отдельный контракт рядом с [ActiveSessionSource] по той же причине: экрану
 * «Занятие завершено» нужны девять чисел и ни одной команды, а `SessionController`
 * принёс бы вместе с ними `submit`, `startSession` и `restoreSession`.
 *
 * Итоги живут в памяти процесса и переживают экран, но не перезапуск
 * приложения: экран итогов открывается сразу за занятием, и восстанавливать
 * их из хранилища сегодня незачем. Журнал занятий, который это изменит, —
 * отдельная работа (docs/09-STATISTICS.md).
 */
interface SessionSummarySource {
    /** `null` — в этом запуске приложения занятий ещё не заканчивалось. */
    val lastSummary: StateFlow<SessionSummary?>
}
