plugins {
    id("yta.android.application")
    id("yta.android.compose")
    id("yta.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.quantumaes.yogatiming"

    defaultConfig {
        applicationId = "com.quantumaes.yogatiming"
        versionCode = 1
        versionName = "0.1.0-alpha01"
    }

    buildTypes {
        debug {
            // Отладочная сборка ставится рядом с релизной — удобно на матрице устройств.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":domain"))

    // Реализации доменных контрактов подключает сборочный модуль: feature-модули
    // знают только интерфейсы, а Hilt собирает граф из того, что есть в :app.
    implementation(project(":core:database"))

    implementation(project(":feature:profiles"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:timer"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
}
