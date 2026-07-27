import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project

/**
 * Общая настройка любого Android-модуля.
 *
 * Kotlin здесь намеренно не конфигурируется: в AGP 9 встроенная поддержка Kotlin
 * берёт jvmTarget из `compileOptions.targetCompatibility`, поэтому достаточно
 * задать его один раз.
 *
 * Обращение к DSL идёт через свойства, а не через блоки: в новом DSL AGP 9
 * `CommonExtension` перестал быть generic-типом и Action-перегрузки
 * (`defaultConfig { }`, `lint { }`) объявлены только на конкретных расширениях
 * — `ApplicationExtension` и `LibraryExtension`.
 */
internal fun Project.configureAndroidCommon(commonExtension: CommonExtension) {
    val javaVersion = JavaVersion.toVersion(libs.version("javaTarget"))

    commonExtension.compileSdk = libs.intVersion("compileSdk")
    commonExtension.defaultConfig.minSdk = libs.intVersion("minSdk")

    commonExtension.compileOptions.apply {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    commonExtension.testOptions.unitTests.isReturnDefaultValues = true

    configureLint(commonExtension.lint)
}

/** Android Lint — часть гейта качества (Фаза 1, DoD): предупреждение = ошибка. */
internal fun configureLint(lint: Lint) {
    lint.apply {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true

        // targetSdk намеренно ниже compileSdk: компилируемся против свежего API
        // ради актуальных AndroidX, но в новое рантайм-поведение не опрокидываемся
        // без прогона матрицы устройств. Поднимается осознанно в Фазах 8–9.
        disable += "OldTargetApi"
    }
}
