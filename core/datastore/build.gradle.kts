plugins {
    id("yta.android.library")
    id("yta.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.quantumaes.yogatiming.core.datastore"
}

// Настройки и персист сессии — Фазы 2–3 (docs/01-ROADMAP.md).
dependencies {
    implementation(project(":domain"))
    implementation(project(":timer-engine"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
