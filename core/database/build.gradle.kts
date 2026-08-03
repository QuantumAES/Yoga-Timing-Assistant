plugins {
    id("yta.android.library")
    id("yta.android.hilt")
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.quantumaes.yogatiming.core.database"
}

// Схемы БД экспортируются в git: без них невозможен ни тест миграций,
// ни ревью изменения структуры (Фаза 2 дорожной карты).
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // Подписка на Flow журнала: без неё проверяется запрос, но не то, что
    // экран статистики обновляется сам.
    androidTestImplementation(libs.turbine)
}
