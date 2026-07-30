plugins {
    id("yta.android.feature")
}

android {
    namespace = "com.quantumaes.yogatiming.feature.timer"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":timer-engine"))
    // BackHandler, LocalActivity: перехват системной «Назад» во время занятия
    // и яркость окна в режиме фокуса (docs/03-GESTURES.md §3, §5).
    implementation(libs.androidx.activity.compose)
    // Рабочий экран подписывается на StateFlow синглтона напрямую, без
    // bindService и ServiceConnection (docs/02-TIMER-CORE-DESIGN.md §9.1).
    implementation(project(":timer-service"))
}
