package com.quantumaes.yogatiming.feature.timer.component

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Яркость окна приложения.
 *
 * Автозатемнение режима фокуса (ТЗ, Экран 4) — это `screenBrightness` окна, а
 * не системная яркость: пользователь оставил телефон на коврике, и менять ему
 * настройки устройства ради своего экрана приложение не вправе. Флаг действует,
 * пока окно на переднем плане, и снимается вместе с композицией.
 *
 * @param level 0..1; `null` — вернуть системную яркость.
 */
@Composable
fun WindowBrightness(level: Float?) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, level) {
        activity.applyBrightness(level)
        onDispose { activity.applyBrightness(null) }
    }
}

private fun android.app.Activity.applyBrightness(level: Float?) {
    val attributes = window.attributes
    attributes.screenBrightness = level ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    window.attributes = attributes
}
