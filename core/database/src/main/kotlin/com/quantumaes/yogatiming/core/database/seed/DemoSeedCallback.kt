package com.quantumaes.yogatiming.core.database.seed

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Наполняет базу демо-профилями в момент её создания.
 *
 * Вынесено отдельным классом, чтобы тест наполнения собирал базу тем же
 * кодом, что и приложение, — иначе проверялась бы не та схема наполнения,
 * которая реально работает у пользователя.
 */
internal class DemoSeedCallback(
    private val seeder: DemoDataSeeder = DemoDataSeeder(),
) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seeder.seed(db)
    }
}
