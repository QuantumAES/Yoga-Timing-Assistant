package com.quantumaes.yogatiming

import android.content.Intent
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.quantumaes.yogatiming.core.designsystem.theme.YtaTheme
import com.quantumaes.yogatiming.domain.settings.AppSettings
import com.quantumaes.yogatiming.domain.settings.ThemeMode
import com.quantumaes.yogatiming.navigation.YtaNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Первый кадр рисуется уже в выбранной теме, а не в системной с
        // последующей перекраской.
        splashScreen.setKeepOnScreenCondition { viewModel.settings.value == null }

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val resolved = settings ?: AppSettings()
            val darkTheme = resolved.themeMode.isDark()

            SystemBarsAppearance(window, darkTheme)

            val navController = rememberNavController()
            DeepLinkHandler(navController)

            YtaTheme(darkTheme = darkTheme, dynamicColor = resolved.dynamicColor) {
                // Экран без собственного фона рисуется поверх фона окна, а тот
                // задан ресурсом и темы не знает. Одна поверхность на всё
                // приложение — гарантия, что такой экран невозможно написать.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // Граф строится только по прочитанным настройкам:
                    // `startDestination` учитывается ровно при первом
                    // построении, и собранный вслепую граф начался бы с
                    // онбординга у того, кто прошёл его год назад. Пустоту
                    // в эти миллисекунды закрывает splash.
                    settings?.let { loaded ->
                        YtaNavHost(navController, showOnboarding = !loaded.onboardingCompleted)
                    }
                }
            }
        }
    }
}

/**
 * Ссылки, пришедшие в уже открытое окно.
 *
 * Интент холодного старта навигация разбирает сама, когда получает граф.
 * А вот тап по уведомлению занятия при живом приложении приходит в
 * `onNewIntent`, и без этой подписки он не делал бы ничего: окно поднималось бы
 * на тот экран, где его оставили.
 */
@Composable
private fun ComponentActivity.DeepLinkHandler(navController: NavHostController) {
    DisposableEffect(navController) {
        val listener = Consumer<Intent> { navController.handleDeepLink(it) }
        addOnNewIntentListener(listener)
        onDispose { removeOnNewIntentListener(listener) }
    }
}

/** Явный выбор пользователя старше системной темы (решение C-4). */
@Composable
private fun ThemeMode.isDark(): Boolean =
    when (this) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

/**
 * Значки системных панелей под тему приложения, а не под системную.
 *
 * Без этого светлая тема, выбранная вручную при тёмной системной, оставляет
 * белые значки статус-бара на белом фоне.
 */
@Composable
private fun SystemBarsAppearance(
    window: Window,
    darkTheme: Boolean,
) {
    val view = LocalView.current
    DisposableEffect(view, darkTheme) {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
        onDispose { }
    }
}
