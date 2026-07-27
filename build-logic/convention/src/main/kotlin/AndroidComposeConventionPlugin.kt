import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Подключается поверх [AndroidApplicationConventionPlugin] или
 * [AndroidLibraryConventionPlugin]. Версии Compose приходят из BOM.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.getByType<CommonExtension>().buildFeatures.compose = true

            dependencies {
                val bom = platform(libs.library("androidx-compose-bom"))
                add("implementation", bom)
                add("androidTestImplementation", bom)

                add("implementation", libs.library("androidx-compose-ui"))
                add("implementation", libs.library("androidx-compose-ui-graphics"))
                add("implementation", libs.library("androidx-compose-foundation"))
                add("implementation", libs.library("androidx-compose-material3"))
                add("implementation", libs.library("androidx-compose-material-icons-core"))
                add("implementation", libs.library("androidx-compose-ui-tooling-preview"))

                add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
                add("debugImplementation", libs.library("androidx-compose-ui-test-manifest"))
                add("androidTestImplementation", libs.library("androidx-compose-ui-test-junit4"))
            }
        }
}
