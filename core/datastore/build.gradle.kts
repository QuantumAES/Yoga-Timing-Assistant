plugins {
    id("yta.android.library")
    id("yta.android.hilt")
}

android {
    namespace = "com.quantumaes.yogatiming.core.datastore"
}

// Настройки и персист сессии — Фазы 2–3 (docs/01-ROADMAP.md).
dependencies {
    implementation(project(":domain"))
}
