plugins {
    id("yta.android.library")
    id("yta.android.hilt")
}

android {
    namespace = "com.quantumaes.yogatiming.core.audio"
}

// AlertPlayer, TTS, вибрация, audio focus — Фаза 4 (docs/adr/003-audio.md).
dependencies {
    implementation(project(":domain"))
    implementation(libs.androidx.core.ktx)
}
