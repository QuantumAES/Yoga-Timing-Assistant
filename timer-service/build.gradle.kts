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
    // api: SessionController отдаёт наружу SessionSnapshot и TimerCommand —
    // рабочий экран работает с ними напрямую, без промежуточных обёрток
    // (docs/02-TIMER-CORE-DESIGN.md §9.1).
    api(project(":timer-engine"))
    api(project(":domain"))
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
