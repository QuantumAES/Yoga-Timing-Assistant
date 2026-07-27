plugins {
    id("yta.jvm.library")
}

// Правило зависимостей (docs/01-ROADMAP.md §3): модуль не знает ни про Android,
// ни про Room/DataStore/Service. Только stdlib, coroutines и сериализация.
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
