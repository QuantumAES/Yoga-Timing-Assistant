package com.quantumaes.yogatiming.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Пока композиция жива — экран не гаснет и не приглушается.
 *
 * Через `View.keepScreenOn`, а не через `WindowManager.LayoutParams`: флаг
 * снимается вместе с самим View, поэтому забыть его снять невозможно даже при
 * нештатном уходе с экрана. Ставится там, где пользователь смотрит на цифры
 * издалека и не может дотянуться до телефона, — на рабочем экране занятия.
 *
 * Отсчёт от этого не зависит: процессорное время сервису даёт partial WakeLock
 * (`SessionWakeLock`), и занятие идёт при выключенном экране. Флаг отвечает
 * ровно за одно — за то, что цифры видно.
 */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
