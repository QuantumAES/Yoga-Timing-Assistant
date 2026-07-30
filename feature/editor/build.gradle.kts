plugins {
    id("yta.android.feature")
}

android {
    namespace = "com.quantumaes.yogatiming.feature.editor"
}

dependencies {
    implementation(project(":domain"))
    // Свой звук этапа выбирается системным выборщиком документов:
    // rememberLauncherForActivityResult живёт здесь.
    implementation(libs.androidx.activity.compose)
}
