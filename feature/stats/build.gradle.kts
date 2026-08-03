plugins {
    id("yta.android.feature")
}

android {
    namespace = "com.quantumaes.yogatiming.feature.stats"
}

dependencies {
    // Экран читает журнал через порт домена и считает разрезы его же чистыми
    // функциями (docs/09-STATISTICS.md §3). О Room он не знает ничего.
    implementation(project(":domain"))
}
