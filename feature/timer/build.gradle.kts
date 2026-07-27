plugins {
    id("yta.android.feature")
}

android {
    namespace = "com.quantumaes.yogatiming.feature.timer"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":timer-engine"))
}
