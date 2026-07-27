# Yoga Timing Assistant

Android-приложение для инструкторов йоги: профили занятий из этапов, звуковые
и голосовые оповещения о переходах, надёжный отсчёт при выключенном экране.

Полное описание — в [`ТЗ.md`](ТЗ.md); состав первой версии зафиксирован в
[`docs/06-MVP-SCOPE.md`](docs/06-MVP-SCOPE.md) (при расхождениях приоритет у него).

## Окружение

```bash
./scripts/setup-dev-env.sh          # JDK 21 в ~/.jdks + проверка Android SDK
./scripts/setup-dev-env.sh --check  # только диагностика, ничего не ставит
```

Скрипт ничего не меняет в системе: без sudo, без apt, без `update-alternatives`.
JDK 21 распаковывается в пользовательский каталог, системный `java` остаётся
прежним. Gradle сам поднимает демон на JDK 21 согласно
`gradle/gradle-daemon-jvm.properties`.

**JDK 21 обязателен:** AGP 9.x и Kotlin 2.4 не собираются на JDK 25.

## Сборка

```bash
./gradlew build           # компиляция + юнит-тесты
./gradlew spotlessApply   # форматирование (ktlint)
./gradlew detekt          # статический анализ
./gradlew lint            # Android Lint
./gradlew :app:installDebug
```

## Структура

| Модуль | Назначение |
|---|---|
| `:app` | сборка, DI-граф, навигационный граф |
| `:core:common` | pure JVM: форматтеры времени, утилиты |
| `:core:designsystem` | тема M3, токены, компоненты |
| `:core:database` | Room: сущности, DAO, миграции *(Фаза 2)* |
| `:core:datastore` | настройки и персист сессии *(Фаза 2)* |
| `:core:audio` | AlertPlayer, TTS, вибрация *(Фаза 4)* |
| `:domain` | pure JVM: модели, use cases, резолвер оповещений |
| `:timer-engine` | pure JVM: машина состояний отсчёта — **ключевой модуль** |
| `:timer-service` | Android: FGS, WakeLock, watchdog-аларм *(Фаза 3)* |
| `:feature:profiles` | список профилей |
| `:feature:editor` | редакторы профиля / этапа / оповещений |
| `:feature:timer` | рабочий экран занятия |
| `:feature:settings` | настройки и онбординг |

Правило зависимостей: `feature:*` → `domain` → ничего.
`:timer-engine` не зависит ни от чего, кроме stdlib, coroutines и сериализации —
это позволяет проверять 90-минутную сессию юнит-тестом за миллисекунды.

Общие настройки модулей вынесены в конвенционные плагины `build-logic`
(`yta.android.application`, `yta.android.feature`, `yta.jvm.library` и др.),
версии зависимостей — только в `gradle/libs.versions.toml`.

## Стек

Gradle 9.6.1 · AGP 9.3.1 (новый DSL, встроенный Kotlin) · Kotlin 2.4.10 ·
KSP 2.3.10 · Compose BOM 2026.06.01 · Hilt 2.60.1 · Room 2.8.4 ·
`compileSdk` 37, `targetSdk` 36, `minSdk` 26.

`compileSdk` выше `targetSdk` намеренно: собираемся против свежего API ради
актуальных AndroidX, но в рантайм-поведение Android 17 не опрокидываемся до
прогона матрицы устройств (docs/06-MVP-SCOPE.md, C-3).

Качество на каждой сборке: ktlint (spotless) · detekt · Android Lint
с `warningsAsErrors`. Юнит-тесты JVM-модулей — JUnit 5.

## Документация

| Документ | Содержание |
|---|---|
| [`docs/00-ANALYSIS.md`](docs/00-ANALYSIS.md) | разбор ТЗ, риски P0/P1 |
| [`docs/01-ROADMAP.md`](docs/01-ROADMAP.md) | план по фазам, вехи M0–M6 |
| [`docs/02-TIMER-CORE-DESIGN.md`](docs/02-TIMER-CORE-DESIGN.md) | тех-дизайн ядра таймера |
| [`docs/03-GESTURES.md`](docs/03-GESTURES.md) | карта жестов рабочего экрана |
| [`docs/04-DEVICE-MATRIX.md`](docs/04-DEVICE-MATRIX.md) | матрица устройств для приёмки |
| [`docs/05-PLAY-DECLARATIONS.md`](docs/05-PLAY-DECLARATIONS.md) | декларации для Google Play |
| [`docs/06-MVP-SCOPE.md`](docs/06-MVP-SCOPE.md) | scope v1.0 и критерии приёмки |
| [`docs/adr/`](docs/adr) | архитектурные решения |
