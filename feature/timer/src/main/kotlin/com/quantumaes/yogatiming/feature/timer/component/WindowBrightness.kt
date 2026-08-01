package com.quantumaes.yogatiming.feature.timer.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Яркость окна приложения.
 *
 * Автозатемнение режима фокуса (ТЗ, Экран 4) — это `screenBrightness` окна, а
 * не системная яркость: пользователь оставил телефон на коврике, и менять ему
 * настройки устройства ради своего экрана приложение не вправе. Флаг действует,
 * пока окно на переднем плане, и снимается вместе с композицией.
 *
 * Окно ищется двумя путями — через `LocalActivity` и, если его не предоставили,
 * разматыванием контекста `View`. Раньше отсутствие `LocalActivity` означало
 * молчаливый выход из функции: затемнение просто не происходило, и на экране
 * это выглядело как «настройка не работает» (полевая проверка 2026-07-31,
 * третий круг, замечание 2). Яркость окна в любом случае лишь половина дела —
 * видимую часть работы делает пелена поверх экрана (`DIM_SCRIM_ALPHA`), потому
 * что `screenBrightness` абсолютен и в тёмном зале почти ничего не меняет.
 *
 * @param level 0..1; `null` — вернуть системную яркость.
 */
@Composable
fun WindowBrightness(level: Float?) {
    val provided = LocalActivity.current
    val view = LocalView.current
    val activity = remember(provided, view) { provided ?: view.context.findActivity() }

    DisposableEffect(activity, level) {
        activity?.applyBrightness(level)
        onDispose { activity?.applyBrightness(null) }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * Атрибуты правятся на месте и присваиваются обратно — так и задумано в
 * `Window`: присваивание уходит в `WindowManager.updateViewLayout`, а тот
 * доносит новую яркость до системы. Создавать новый `LayoutParams` нельзя:
 * `copyFrom` переносит не все поля окна.
 */
private fun Activity.applyBrightness(level: Float?) {
    val attributes = window.attributes
    attributes.screenBrightness = level ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    window.attributes = attributes
}
