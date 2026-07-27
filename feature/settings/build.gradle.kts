plugins {
    id("yta.android.feature")
}

android {
    namespace = "com.quantumaes.yogatiming.feature.settings"
}

dependencies {
    implementation(project(":domain"))
}
