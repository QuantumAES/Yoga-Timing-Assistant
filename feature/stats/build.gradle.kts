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

    // Экспорт CSV: файл выбирает системный диалог (SAF), а
    // rememberLauncherForActivityResult живёт здесь.
    implementation(libs.androidx.activity.compose)

    // `Uri` в JVM-тесте — заглушка из android.jar: её методы бросают, а поля
    // пусты. Модель носит адрес файла насквозь и внутрь не смотрит, поэтому
    // подделки достаточно, но без mockk её неоткуда взять.
    testImplementation(libs.mockk)
}
