plugins {
    id("yta.android.feature")
}

android {
    namespace = "com.quantumaes.yogatiming.feature.profiles"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":timer-engine"))
    // Список профилей показывает идущее занятие и не даёт править запущенный
    // профиль. И то и другое — вопрос к состоянию движка, а оно живёт
    // в синглтоне-контроллере (docs/02-TIMER-CORE-DESIGN.md §9.1).
    implementation(project(":timer-service"))
}
