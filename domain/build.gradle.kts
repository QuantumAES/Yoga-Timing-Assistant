plugins {
    id("yta.jvm.library")
}

// Правило зависимостей: :domain не зависит ни от чего, кроме stdlib и coroutines.
// Модели, use cases и резолвер оповещений появятся в Фазах 2–4.
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":timer-engine"))
}
