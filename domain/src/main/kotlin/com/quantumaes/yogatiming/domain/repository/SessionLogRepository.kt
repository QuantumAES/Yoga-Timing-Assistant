package com.quantumaes.yogatiming.domain.repository

import com.quantumaes.yogatiming.domain.stats.ProfileTotals
import com.quantumaes.yogatiming.domain.stats.SessionDay
import com.quantumaes.yogatiming.domain.stats.SessionLogEntry
import com.quantumaes.yogatiming.domain.stats.SessionTotals
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Журнал проведённых занятий (docs/09-STATISTICS.md, фаза S1).
 *
 * Реализация живёт в `:core:database`; домен знает только этот контракт.
 * Границы периода — включительно с обеих сторон и в локальных днях, а не в
 * метках времени: день занятия зафиксирован строкой при записи (D-S4), и
 * сравнивать его с моментом времени было бы возвращением ровно к той
 * арифметике со смещениями, от которой эта колонка избавляет.
 *
 * Агрегаты считает SQLite, а не Kotlin по списку строк (R-S4): экран
 * статистики не должен тянуть в память весь журнал ради четырёх чисел.
 */
interface SessionLogRepository {
    /** Записывает занятие. @return идентификатор строки. */
    suspend fun record(entry: SessionLogEntry): Long

    /** Удаление строки вручную — тестовый запуск длиннее минуты порог не отсеет. */
    suspend fun delete(id: Long)

    /** Журнал за период, от свежих к старым. */
    fun observeSessions(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<SessionLogEntry>>

    /** Дни с занятиями — для календаря и недельного графика. */
    fun observeDays(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<SessionDay>>

    /** Количество, сумма и число дней с практикой — для плиток сводки. */
    fun observeTotals(
        from: LocalDate,
        to: LocalDate,
    ): Flow<SessionTotals>

    /** Разрез по профилям, от большего времени к меньшему. */
    fun observeByProfile(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<ProfileTotals>>

    /**
     * День последнего занятия в журнале или `null`, если журнал пуст (фаза S6).
     *
     * Пустой период у человека с историей и пустая статистика у нового
     * пользователя — разные состояния, и ответы у них разные: первому нужно
     * знать, где его занятия, второму — что они появятся сами. Отличить одно
     * от другого по периоду нельзя, а тянуть ради этого весь журнал незачем:
     * `MAX` по индексированной колонке даёт ответ одной строкой (R-S4).
     */
    fun observeLastSessionDate(): Flow<LocalDate?>
}
