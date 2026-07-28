plugins {
    id("yta.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

// Правило зависимостей: :domain не знает ни про Android, ни про Room, ни про UI.
// Ниже него — только ядро таймера, которое не зависит вообще ни от чего.
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    // api: доменный Alert реализует AlertPayload движка, а SessionPlanFactory
    // возвращает его SessionPlan — типы ядра видны в публичном контракте домена.
    api(project(":timer-engine"))
}
