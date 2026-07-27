plugins {
    id("yta.android.library")
    id("yta.android.hilt")
}

android {
    namespace = "com.quantumaes.yogatiming.timer.service"
}

// FGS, WakeLock, уведомление, watchdog-аларм, AndroidTimeSource — Фаза 3
// (docs/adr/001-timing-mechanism.md). Критический путь проекта.
dependencies {
    implementation(project(":timer-engine"))
    implementation(project(":domain"))
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
