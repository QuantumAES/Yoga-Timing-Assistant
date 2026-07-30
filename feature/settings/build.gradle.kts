plugins {
    id("yta.android.feature")
}

android {
    namespace = "com.quantumaes.yogatiming.feature.settings"
}

dependencies {
    implementation(project(":domain"))
    // Онбординг просит разрешение на уведомления:
    // rememberLauncherForActivityResult живёт здесь.
    implementation(libs.androidx.activity.compose)
}
