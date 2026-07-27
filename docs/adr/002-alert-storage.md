# ADR-002. Хранение конфигурации оповещений

**Статус:** принято · 2026-07-26
**Контекст:** заменяет схему `ТЗ.md` «Раздел 2», снимает блокер P0-7

---

## Контекст

ТЗ предлагает полностью нормализованную схему из четырёх таблиц:

```
profiles ──1:1──► alert_configs ──1:N──► alerts
stages   ──0:1──►      ↑
```

## Проблема

### 1. Каскады направлены против владения

```sql
-- profiles
default_alert_id INTEGER NOT NULL,
FOREIGN KEY (default_alert_id) REFERENCES alert_configs(id) ON DELETE CASCADE
```

Каскад идёт от конфига к профилю: **удаление конфигурации оповещений удаляет профиль пользователя**. Семантически владелец — профиль, а не наоборот.

### 2. Неограниченная утечка сирот

```sql
-- stages
FOREIGN KEY (alert_config_id) REFERENCES alert_configs(id) ON DELETE SET NULL
```

При удалении этапа его персональный `alert_config` и все связанные `alerts` остаются в базе навсегда. Сборщика мусора в ТЗ нет. Пользователь, регулярно правящий профили, накапливает мусор без ограничения сверху.

### 3. Циклическая зависимость при вставке

`profiles.default_alert_id` объявлен `NOT NULL` → конфиг обязан существовать до профиля, хотя логически принадлежит ему. Требует ручной многошаговой транзакции (`saveFullProfile()` в ТЗ описан псевдокодом с комментарием «всё в одной транзакции для целостности» — реализации нет).

### 4. Нормализация не окупается

`alerts` **никогда не запрашиваются независимо**. Во всех сценариях ТЗ они читаются и пишутся целиком вместе с владельцем: открыть профиль, запустить занятие, экспортировать, продублировать. Ни одного запроса вида «найти все оповещения с каналом VIBRATION» в требованиях нет и не предвидится.

## Решение

`AlertConfig` вместе с вложенным списком `Alert` хранится **сериализованным JSON в колонке** владельца. Таблиц остаётся две.

```kotlin
@Serializable
data class AlertConfig(
    val presetType: AlertPreset = AlertPreset.STANDARD,
    val masterVolume: Int = 70,
    val start: Alert? = null,
    val warnings: List<Alert> = emptyList(),
    val end: Alert? = null,
)

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    // ...
    @ColumnInfo(name = "default_alert_config") val defaultAlertConfig: AlertConfig,
)

@Entity(
    tableName = "stages",
    foreignKeys = [ForeignKey(
        entity = ProfileEntity::class,
        parentColumns = ["id"], childColumns = ["profile_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["profile_id", "sort_order"])]
)
data class StageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    // ...
    /** null = наследовать конфиг профиля */
    @ColumnInfo(name = "alert_config") val alertConfig: AlertConfig? = null,
)

class AlertConfigConverter {
    @TypeConverter fun toJson(v: AlertConfig?): String? = v?.let(json::encodeToString)
    @TypeConverter fun fromJson(s: String?): AlertConfig? = s?.let(json::decodeFromString)
}
```

Структура `start / warnings / end` вместо плоского списка с полем `triggerType` дополнительно убирает целый класс невалидных состояний: невозможно создать два START-оповещения или END с ненулевым offset.

## Последствия

**Положительные**

| Было | Стало |
|---|---|
| 4 таблицы, 3 `@Relation`-обёртки | 2 таблицы, 1 связь |
| 4 внешних ключа, два из них с неверным каскадом | 1 внешний ключ с очевидным каскадом |
| Сироты накапливаются без сборщика | Невозможны by design — конфиг физически внутри строки владельца |
| Циклическая зависимость при вставке | Отсутствует |
| `saveFullProfile()` — ручная транзакция из 3 шагов | Обычный upsert |
| Изменение структуры `Alert` → SQL-миграция | Версионирование внутри JSON, миграция схемы не нужна |
| Формат БД и формат экспорта — разные | Один и тот же `@Serializable`-класс |

Блокер P0-7 устраняется целиком, а не патчится: механизма, порождающего сирот, больше не существует.

**Отрицательные**

- Невозможен SQL-запрос по внутренностям конфига (`WHERE channel_mask = ...`). В требованиях таких запросов нет; при появлении в v2.0 решается денормализованной колонкой-индексом.
- JSON в колонке не проверяется схемой БД — валидация переезжает на уровень домена и покрывается юнит-тестами.
- При эволюции формата нужна собственная дисциплина версионирования внутри JSON. Вводится поле `schemaVersion` на уровне `AlertConfig` с первого релиза.

**Оценка выигрыша:** ~3–4 рабочих дня на Фазе 2 и весь класс дефектов целостности данных.
