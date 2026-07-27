plugins {
    id("yta.android.feature")
}

android {
    namespace = "com.quantumaes.yogatiming.feature.profiles"
}

dependencies {
    implementation(project(":domain"))
}
