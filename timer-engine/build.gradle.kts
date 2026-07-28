import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    id("yta.jvm.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

// Правило зависимостей (docs/01-ROADMAP.md §3): модуль не знает ни про Android,
// ни про Room/DataStore/Service. Только stdlib, coroutines и сериализация.
dependencies {
    // api, а не implementation: StateFlow и SharedFlow — часть публичного
    // контракта движка, и потребители обязаны видеть эти типы.
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.turbine)
}

// Критерий приёмки N-4 (docs/06-MVP-SCOPE.md §5.3): движок — единственный
// модуль с жёстким порогом покрытия. Сборка падает, если он не выдержан.
kover {
    reports {
        verify {
            rule("Покрытие :timer-engine — критерий N-4") {
                bound {
                    minValue = 85
                    coverageUnits = CoverageUnit.LINE
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("koverVerify"))
}
