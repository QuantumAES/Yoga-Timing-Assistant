plugins {
    id("yta.android.feature")
}

android {
    namespace = "com.quantumaes.yogatiming.feature.timer"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":timer-engine"))
    // Рабочий экран подписывается на StateFlow синглтона напрямую, без
    // bindService и ServiceConnection (docs/02-TIMER-CORE-DESIGN.md §9.1).
    implementation(project(":timer-service"))
}
