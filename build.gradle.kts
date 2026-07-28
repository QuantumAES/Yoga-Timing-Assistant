import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.spotless)
}

// ─── detekt: статический анализ во всех модулях ──────────────────────────────
allprojects {
    apply(plugin = "dev.detekt")

    extensions.configure<DetektExtension> {
        parallel.set(true)
        buildUponDefaultConfig.set(true)
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        basePath.set(rootProject.layout.projectDirectory)
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget.set(libs.versions.javaTarget.get())
        reports {
            html.required.set(true)
            // SARIF — формат для code scanning в GitHub Actions.
            sarif.required.set(true)
            checkstyle.required.set(false)
            markdown.required.set(false)
        }
    }
}

// ─── ktlint через spotless: форматирование ───────────────────────────────────
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
}
