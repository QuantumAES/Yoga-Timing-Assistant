package com.quantumaes.yogatiming.core.designsystem.component

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quantumaes.yogatiming.core.designsystem.R
import com.quantumaes.yogatiming.core.designsystem.theme.Spacing

private val LOGO_SIZE = 28.dp

/**
 * С какого размера мелкий растр перестаёт годиться.
 *
 * `ic_yta_logo` собран под 24 dp, и на xhdpi это 48 точек: растянуть их до
 * 120 dp значит показать муть вместо знака — ровно то, что было видно на
 * последнем слайде онбординга (замечание 1 полевой проверки 2026-08-05).
 * Порог с запасом вдвое: до 48 dp увеличение незаметно, дальше — заметно.
 */
private val LARGE_LOGO_FROM = 48.dp

/**
 * Название приложения — из манифеста, а не из строкового ресурса модуля.
 *
 * Ресурс `app_name` объявлен в `:app` и модулям экранов недоступен: у каждого
 * свой `R`. Дублировать название в каждом модуле значит однажды переименовать
 * приложение в одном месте из трёх. Системная метка при этом всегда та же,
 * что под иконкой запуска, и переводится вместе с манифестом.
 */
@Composable
fun appLabel(): String {
    val context = LocalContext.current
    return remember(context) {
        context.applicationInfo.loadLabel(context.packageManager).toString()
    }
}

/**
 * Версия из манифеста: то, что пользователь назовёт в письме о проблеме.
 *
 * У отладочной сборки суффикс на месте — это и требуется: понять, о какой
 * именно сборке речь.
 */
@Composable
fun appVersionName(): String {
    val context = LocalContext.current
    return remember(context) { context.versionName() }
}

private fun Context.versionName(): String =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }.versionName
    }.getOrNull().orEmpty()

/**
 * Знак приложения.
 *
 * Собственные цвета, без тинта: логотип — многоцветный лотос, и перекрасить
 * его в один цвет схемы значит превратить в пятно. Раньше знак был
 * одноцветным вектором и тинтовался под тему; растровый логотип из
 * `scripts/generate-logo.sh` живёт в обеих темах как есть — он на прозрачном
 * фоне и достаточно контрастен и на светлом, и на тёмном.
 *
 * Размер — параметр, а не дело вызывающего: от него зависит, какой из двух
 * растров брать, и `Modifier.size` снаружи об этом выборе ничего не сообщил бы.
 * Крупный знак живёт отдельным файлом (480 точек), мелкий — ладдером плотностей
 * под 24 dp; держать один растр на оба случая значит либо мутить онбординг,
 * либо носить полмегабайта ради значка в шапке списка.
 *
 * @param size сторона квадрата, в который вписан знак.
 */
@Composable
fun YtaLogo(
    modifier: Modifier = Modifier,
    size: Dp = LOGO_SIZE,
) {
    Image(
        painter =
            painterResource(
                if (size < LARGE_LOGO_FROM) R.drawable.ic_yta_logo else R.drawable.ic_yta_logo_large,
            ),
        // Логотип рядом с названием — украшение: TalkBack прочитает название
        // текстом, а «логотип приложения» вслух не нужен никому.
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

/**
 * Логотип и название — шапка главного экрана.
 *
 * Отдельный компонент в системе дизайна, а не разметка внутри экрана списка:
 * то же самое нужно разделу «О программе» в настройках, и расходиться этим
 * двум местам незачем.
 */
@Composable
fun AppBrand(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        YtaLogo()
        Text(
            text = appLabel(),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
