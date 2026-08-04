package com.quantumaes.yogatiming.timer.engine.persist

import com.quantumaes.yogatiming.timer.engine.TimeSource
import com.quantumaes.yogatiming.timer.engine.TimerLimits
import com.quantumaes.yogatiming.timer.engine.model.PauseMode
import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionPlan
import com.quantumaes.yogatiming.timer.engine.model.SessionState
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Снимок сессии для восстановления после смерти процесса
 * (docs/02-TIMER-CORE-DESIGN.md §8).
 *
 * Пишется **по событиям**, а не по расписанию: за 90-минутное занятие около
 * двадцати записей вместо ~1100 при подходе «раз в пять секунд». Это возможно
 * ровно потому, что между событиями состояние не меняется (принцип П-1) —
 * хранятся метки, а не счётчики.
 *
 * План здесь не сохраняется: он собирается заново из профиля по [profileId].
 * Так восстановление не воскрешает устаревшую копию профиля, отредактированного
 * между сохранением и запуском.
 *
 * @param savedAtWallMs стенные часы — только для перекрёстной проверки.
 * @param savedAtElapsedMs монотонные часы — по ним и детект перезагрузки,
 *   и возраст снимка.
 * @param startedAtWallMs когда занятие началось по стенным часам. Отсчёту не
 *   нужно, нужно итогам: «18:05 → 19:03» на экране завершения переживает смерть
 *   процесса только так. `0` — снимок старой версии, начало неизвестно.
 * @param pauseMode что именно остановлено паузой (v2). У снимков v1 поля нет —
 *   там паузы были только одного рода, и значение по умолчанию описывает их
 *   в точности.
 * @param holdMs накопленное время пауз этапа (v2).
 */
@Serializable
data class PersistedSession(
    val schemaVersion: Int = SCHEMA_VERSION,
    val profileId: Long,
    val savedAtWallMs: Long,
    val savedAtElapsedMs: Long,
    val startedAtWallMs: Long = 0L,
    val runState: RunState,
    val currentIndex: Int,
    val stageElapsedAtResumeMs: Long,
    val resumedAtMs: Long,
    val adjustmentsMs: Map<Int, Long> = emptyMap(),
    val actualDurationsMs: Map<Int, Long> = emptyMap(),
    val firedAlertIds: Set<String> = emptySet(),
    val pauseMode: PauseMode = PauseMode.DEFAULT,
    val pausedAtMs: Long = 0L,
    val holdMs: Long = 0L,
) {
    companion object {
        /** v2 — режим паузы и накопленное время удержания (Фаза 11). */
        const val SCHEMA_VERSION = 2
    }
}

/** Пригодность снимка к восстановлению (docs/02-TIMER-CORE-DESIGN.md §8.3). */
enum class Restorability {
    /** Предложить «Продолжить занятие?» (решение B-12). */
    OK,

    /** Прошло больше пяти минут — снимок тихо отбрасывается. */
    TOO_OLD,

    /** Устройство перезагружалось: сессия не восстанавливается (решение B-11). */
    REBOOTED,

    /** Часы переводили: восстанавливаем по монотонным, стенные игнорируем (решение B-14). */
    CLOCK_CHANGED,
    ;

    /** Можно ли вообще продолжать это занятие. */
    val isRestorable: Boolean get() = this == OK || this == CLOCK_CHANGED
}

/** Снимок текущего состояния. Стенные часы читаются только здесь. */
fun SessionState.persist(time: TimeSource): PersistedSession =
    PersistedSession(
        profileId = plan.profileId,
        savedAtWallMs = time.wall(),
        savedAtElapsedMs = time.elapsed(),
        runState = runState,
        currentIndex = currentIndex,
        stageElapsedAtResumeMs = stageElapsedAtResumeMs,
        resumedAtMs = resumedAtMs,
        adjustmentsMs = adjustmentsMs,
        actualDurationsMs = actualDurationsMs,
        firedAlertIds = firedAlertIds,
        pauseMode = pauseMode,
        pausedAtMs = pausedAtMs,
        holdMs = holdMs,
    )

/**
 * Восстановление состояния поверх заново собранного плана.
 *
 * Монотонные метки переносятся как есть — в пределах одной загрузки они
 * остаются валидными, а другую загрузку отсекает [restorability].
 *
 * @return `null`, если снимок не подходит к плану: профиль другой или этап,
 *   на котором остановилось занятие, был удалён при редактировании (решение B-13).
 */
fun PersistedSession.restoreInto(plan: SessionPlan): SessionState? {
    if (profileId != plan.profileId) return null
    if (currentIndex !in plan.stages.indices) return null

    return SessionState(
        plan = plan,
        runState = runState,
        currentIndex = currentIndex,
        stageElapsedAtResumeMs = stageElapsedAtResumeMs,
        resumedAtMs = resumedAtMs,
        adjustmentsMs = adjustmentsMs,
        actualDurationsMs = actualDurationsMs,
        firedAlertIds = firedAlertIds,
        pauseMode = pauseMode,
        pausedAtMs = pausedAtMs,
        holdMs = holdMs,
    )
}

/**
 * Проверка снимка без единого разрешения и без чтения `/proc`.
 *
 * `elapsedRealtime` монотонно растёт в пределах одной загрузки, поэтому
 * значение меньше сохранённого возможно только после перезагрузки. Ни
 * `READ_PHONE_STATE`, ни `boot_id` (закрыт SELinux на части прошивок) не нужны
 * — хватает двух чисел, которые мы и так сохраняем.
 *
 * Порядок проверок отличается от §8.3: возраст снимка проверяется **до**
 * расхождения часов. Часы могли перевести и на сессии двухчасовой давности,
 * а монотонная метка в этом случае — единственный честный источник возраста.
 */
fun PersistedSession.restorability(time: TimeSource): Restorability {
    val elapsedDelta = time.elapsed() - savedAtElapsedMs
    val wallDelta = time.wall() - savedAtWallMs

    return when {
        // Монотонные часы не могут пойти назад внутри одной загрузки.
        elapsedDelta < 0 -> Restorability.REBOOTED

        elapsedDelta > TimerLimits.RESTORE_WINDOW_MS -> Restorability.TOO_OLD

        // Стенные часы должны были уйти примерно настолько же, насколько
        // монотонные. Расхождение — правка времени или прыжок NTP.
        abs(wallDelta - elapsedDelta) > TimerLimits.CLOCK_JUMP_TOLERANCE_MS -> Restorability.CLOCK_CHANGED

        else -> Restorability.OK
    }
}
