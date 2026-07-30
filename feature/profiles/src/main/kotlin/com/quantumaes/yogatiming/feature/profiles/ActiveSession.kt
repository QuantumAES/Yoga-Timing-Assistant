package com.quantumaes.yogatiming.feature.profiles

import com.quantumaes.yogatiming.timer.engine.model.RunState
import com.quantumaes.yogatiming.timer.engine.model.SessionSnapshot

/**
 * Идущее занятие в том виде, в каком его показывает список профилей.
 *
 * Отдельная модель, а не `SessionSnapshot`: экрану нужно шесть полей из
 * двадцати, а превью и тесты не должны собирать состояние движка целиком.
 */
data class ActiveSession(
    val profileId: Long,
    val profileName: String,
    val stageName: String,
    val stageNumber: Int,
    val stageCount: Int,
    /** `null` у свободного этапа — там счёт идёт вверх (решение B-5). */
    val remainingMs: Long?,
    val elapsedMs: Long,
    val paused: Boolean,
)

/**
 * @return `null`, если показывать нечего: занятия нет, оно ещё не начато или
 *   уже завершено. Завершённое занятие живёт до перехода на экран итогов, и
 *   объявлять его «идущим» в списке профилей было бы неправдой.
 */
fun SessionSnapshot?.toActiveSession(): ActiveSession? {
    val snapshot = this ?: return null
    if (!snapshot.runState.isActive) return null
    return ActiveSession(
        profileId = snapshot.profileId,
        profileName = snapshot.profileName,
        stageName = snapshot.currentStageName,
        stageNumber = snapshot.currentIndex + 1,
        stageCount = snapshot.stageCount,
        remainingMs = snapshot.stageRemainingMs,
        elapsedMs = snapshot.stageElapsedMs,
        paused = snapshot.runState == RunState.PAUSED,
    )
}
