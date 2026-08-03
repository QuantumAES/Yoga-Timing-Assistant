pluginManagement {
    // Конвенционные плагины проекта (yta.*) живут в отдельной сборке.
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "yoga-timing-assistant"

// ─── Сборка, DI-граф, навигация ──────────────────────────────────────────────
include(":app")

// ─── Ядро ────────────────────────────────────────────────────────────────────
include(":core:common") // pure JVM: форматтеры времени, утилиты
include(":core:designsystem") // тема M3, токены, компоненты
include(":core:database") // Room (Фаза 2)
include(":core:datastore") // настройки и персист сессии (Фаза 2)
include(":core:audio") // AlertPlayer, TTS, вибрация (Фаза 4)

// ─── Бизнес-логика ───────────────────────────────────────────────────────────
include(":domain") // pure JVM: модели, use cases, резолвер оповещений
include(":timer-engine") // pure JVM: state machine отсчёта — ключевой модуль
include(":timer-service") // Android-обвязка: FGS, WakeLock, watchdog (Фаза 3)

// ─── Экраны ──────────────────────────────────────────────────────────────────
include(":feature:profiles")
include(":feature:editor")
include(":feature:timer")
include(":feature:settings")
include(":feature:stats")
