plugins {
    id("yta.android.library")
    id("yta.android.compose")
}

android {
    namespace = "com.quantumaes.yogatiming.core.designsystem"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
