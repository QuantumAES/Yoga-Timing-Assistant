package com.quantumaes.yogatiming.timer.engine.model

// Часы занятия и бюджет: всё, что считается не по этапам, а по времени.
//
// Расширениями, а не методами `SessionState`: состояние отвечает на вопросы
// про этапы — «сколько прошло», «когда конец текущего», — и это его работа.
// Бюджет же — вторая система координат поверх той же пары меток, и держать её
// рядом с `SessionBudget` честнее, чем дописывать четыре метода в класс,
// который и так знает про всё занятие.

/**
 * Сколько времени занятие простояло на паузе этапа, включая текущую.
 *
 * Пауза занятия сюда не входит по определению: она останавливает и часы
 * занятия тоже.
 */
fun SessionState.holdElapsedMs(now: Long): Long =
    holdMs +
        if (runState == RunState.PAUSED && pauseMode == PauseMode.STAGE) {
            (now - pausedAtMs).coerceAtLeast(0L)
        } else {
            0L
        }

/**
 * Сколько занятие идёт по часам.
 *
 * Отличается от [totalElapsedMs] ровно на время пауз этапа. Именно от этого
 * числа считается бюджет — и именно поэтому остаток по бюджету не прыгает
 * при ручных переходах и правках ±30 с (замечание 12 полевой проверки
 * 2026-08-04).
 */
fun SessionState.sessionElapsedMs(now: Long): Long = totalElapsedMs(now) + holdElapsedMs(now)

/**
 * Сколько осталось до целевого конца занятия; `null` — бюджета нет.
 *
 * Может уйти в минус: перерасход надо показывать, а не прятать за нулём.
 */
fun SessionState.budgetRemainingMs(now: Long): Long? = plan.budget?.let { it.targetMs - sessionElapsedMs(now) }

/**
 * На сколько план не помещается в остаток бюджета; `null` — бюджета нет.
 *
 * Положительное число — дефицит: если ничего не менять, занятие выйдет за
 * целевое время на столько. Отрицательное — запас.
 */
fun SessionState.budgetDeficitMs(now: Long): Long? = budgetRemainingMs(now)?.let { totalRemainingMs(now) - it }
