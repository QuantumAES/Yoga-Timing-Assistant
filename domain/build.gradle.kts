plugins {
    id("yta.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

// Правило зависимостей: :domain не знает ни про Android, ни про Room, ни про UI.
// Ниже него — только ядро таймера, которое не зависит вообще ни от чего.
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    implementation(project(":timer-engine"))
}
