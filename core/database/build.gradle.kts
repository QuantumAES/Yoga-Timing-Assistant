plugins {
    id("yta.android.library")
    id("yta.android.hilt")
}

android {
    namespace = "com.quantumaes.yogatiming.core.database"
}

// Room, сущности, DAO и миграции — Фаза 2 (docs/01-ROADMAP.md).
dependencies {
    implementation(project(":domain"))
}
