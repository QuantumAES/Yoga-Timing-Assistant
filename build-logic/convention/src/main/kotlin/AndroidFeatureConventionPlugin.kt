import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Модуль-экран: Android-библиотека + Compose + Hilt + навигация.
 * Маршруты объявляются самим модулем (@Serializable), :app только связывает их.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("yta.android.library")
            pluginManager.apply("yta.android.compose")
            pluginManager.apply("yta.android.hilt")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:common"))

                add("implementation", libs.library("androidx-core-ktx"))
                add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.library("androidx-navigation-compose"))
                add("implementation", libs.library("androidx-hilt-navigation-compose"))
                add("implementation", libs.library("kotlinx-serialization-json"))

                add("testImplementation", libs.library("turbine"))
            }
        }
}
