import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Чистый Kotlin/JVM-модуль без единой ссылки на android.*
 * (:timer-engine, :domain, :core:common).
 *
 * Собирается на JDK 21 (toolchain), но в байткод Java 17 — эти классы
 * потребляются D8 внутри Android-модулей.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            // Standalone-lint: без него Android Lint считает JVM-модули внешней
            // зависимостью и не анализирует их исходники.
            pluginManager.apply("com.android.lint")

            val javaVersion = JavaVersion.toVersion(libs.version("javaTarget"))

            extensions.configure<Lint> { configureLint(this) }

            extensions.configure<KotlinJvmProjectExtension> {
                jvmToolchain(libs.intVersion("jdkToolchain"))
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
                }
            }

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = javaVersion
                targetCompatibility = javaVersion
            }

            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
            }

            dependencies {
                add("testImplementation", platform(libs.library("junit5-bom")))
                add("testImplementation", libs.library("junit5-jupiter"))
                add("testRuntimeOnly", libs.library("junit5-platform-launcher"))
                add("testImplementation", libs.library("truth"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
            }
        }
}
