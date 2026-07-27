plugins {
    id("yta.android.feature")
}

android {
    namespace = "com.quantumaes.yogatiming.feature.editor"
}

dependencies {
    implementation(project(":domain"))
}
