# Декларации и разрешения для публикации в Google Play

**Статус:** черновик готов · 2026-07-26
**Назначение:** снять блокеры P0-3, P0-4, P0-8 и подготовить материалы Фазы 9 заранее, а не в момент отказа ревью

---

## 1. Почему это делается на Фазе 0

Три из четырёх деклараций ниже влияют на **код и UX**, а не только на форму в консоли. Обнаружить требование к обоснованию в день подачи на ревью — значит переписывать онбординг и манифест под дедлайном. Формулировки фиксируются сейчас и реализуются по ходу.

---

## 2. Полный список разрешений

```xml
<!-- Фоновый таймер -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>

<!-- Уведомление сессии с кнопками управления -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>

<!-- Тактильные оповещения -->
<uses-permission android:name="android.permission.VIBRATE"/>

<!-- Watchdog-аларм (ADR-001) -->
<uses-permission android:name="android.permission.USE_EXACT_ALARM"     android:minSdkVersion="33"/>
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" android:maxSdkVersion="32"/>
```

**Чего в списке нет и не будет:** интернет, геолокация, камера, микрофон, контакты, хранилище, идентификаторы устройства. Приложение полностью офлайн — это одновременно и требование ТЗ §8, и сильный аргумент при любом ревью.

> `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` **не запрашивается** — см. §5.

---

## 3. Foreground Service: тип `specialUse`

### Почему именно specialUse

Ни один из штатных типов не описывает наш сценарий честно:

| Тип | Почему не подходит |
|---|---|
| `mediaPlayback` | Мы не воспроизводим медиаконтент. Формально можно было бы натянуть на звуковые сигналы, но это ложная декларация — риск при ревью выше, чем польза |
| `dataSync` | Нет синхронизации. С Android 15 подпадает под таймаут — сервис принудительно останавливается |
| `health`, `location`, `camera`, `microphone` | Не наш случай |
| **`specialUse`** | Предназначен ровно для случаев, не покрытых остальными типами, при условии письменного обоснования |

### Объявление

```xml
<service
    android:name=".timer.TimerService"
    android:foregroundServiceType="specialUse"
    android:exported="false">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Interval timing for live yoga classes: the app must keep counting stage durations and deliver audio, voice and haptic cues while the screen is locked and the device lies on the mat, out of the instructor's reach."/>
</service>
```

### Обоснование для Play Console (EN)

> The app is an interval timer for yoga and meditation instructors who run live classes.
>
> During a class the instructor places the phone on the mat and does not look at or touch it — the entire value of the product is delivering audio, voice and haptic cues at exact moments so the instructor can keep the planned schedule while teaching.
>
> A foreground service is required because the timing session must continue with second-level accuracy while the screen is off and the app is in the background, for the full duration of a class (20 to 90 minutes). Cues are scheduled from monotonic timestamps computed by the service; if the process is suspended, the class is silently ruined and the user has no way to recover the schedule.
>
> No existing foreground service type describes this use case: the app does not play media content, does not sync data, does not use location, camera or microphone, and has no network access at all. All data stays on the device.
>
> The service runs only while a class session is explicitly active. It is started by direct user action (pressing Start), shows an ongoing notification with Pause / Next / Stop controls, and stops itself as soon as the session ends or the user stops it.

Ключевые тезисы, которые ревью хочет видеть и которые здесь есть: запуск по явному действию пользователя, видимое уведомление с управлением, самоостановка по завершении, отсутствие альтернативного типа, отсутствие сетевых разрешений.

---

## 4. Точные будильники

### Разделение по версиям

| API | Разрешение | Как выдаётся |
|---|---|---|
| 26–30 | не требуется | — |
| 31–32 | `SCHEDULE_EXACT_ALARM` | при установке, пользователь может отозвать |
| 33+ | `USE_EXACT_ALARM` | при установке, отозвать нельзя |

`USE_EXACT_ALARM` доступен приложениям категории «будильник / календарь / таймер». Приложение относится к этой категории по своей основной функции, а не по вспомогательной — это условие выполняется.

### Обоснование для Play Console (EN)

> The app is a timer: its primary, user-facing function is counting down class stages and firing cues at precise moments. Exact alarms are used solely as a watchdog for the timing session — a single alarm is scheduled for the end of the current stage and re-armed on each stage change. The app schedules at most one exact alarm at any given time and cancels it when the session ends.
>
> Inexact alarms are unusable here: a cue delivered several minutes late is worse than no cue at all, because it misinforms an instructor who is mid-class and relying on it.
>
> If the permission is unavailable, the app degrades gracefully to inexact alarms and warns the user that cues may be delayed.

Формулировка «at most one exact alarm at any given time» — прямое следствие ADR-001 и сильный аргумент: она показывает, что приложение не злоупотребляет механизмом.

---

## 5. Оптимизация батареи — политика

### Чего мы НЕ делаем

Прямой вызов `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` **не используется**. Это ограниченное намерение, допустимое только когда основная функция приложения без него невозможна; трактовка ревью непредсказуема, а отказ по этому основанию стоит недели.

Отклонение от ТЗ §Экран 7 («предложение отключить оптимизацию батареи» на слайде онбординга) — осознанное, блокер P0-8.

### Что мы делаем вместо

1. **Не в онбординге.** Просьба показывается при **первом запуске занятия**, когда пользователь уже понимает, зачем это нужно. Конверсия выше, а раздражение ниже, чем у экрана разрешений при первом открытии.
2. **Deep-link в системные настройки**, а не прямой запрос:
   ```kotlin
   Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
   ```
3. **Объяснение до перехода**: «Android может приостанавливать приложения в фоне. Чтобы сигналы срабатывали точно, разрешите работу без ограничений». Без запугивания и без повторных показов — предложение появляется один раз и потом доступно в настройках.
4. **Диагностика вместо обещаний.** Если ограничение не снято и сессия зафиксировала `DriftDetected`, пользователю показывается конкретный факт: «Система приостановила приложение на 3 минуты во время прошлого занятия» — с кнопкой перехода в настройки.

Пункт 4 — то, чего в ТЗ не было вовсе, и то, что превращает неустранимую проблему платформы в понятный пользователю диалог.

---

## 6. Data Safety

| Раздел формы | Ответ |
|---|---|
| Собираются ли данные? | **Нет** |
| Передаются ли данные третьим лицам? | **Нет** |
| Шифрование при передаче | н/п — передачи нет |
| Возможность удаления данных | Да, удалением приложения; данные только на устройстве |
| Разрешение на интернет | Не запрашивается |

Если на Фазе 8 будет решено подключить crash reporting (открытый вопрос №5 из `00-ANALYSIS.md` §9), эта таблица меняется целиком: появляется сбор диагностики, требуется `INTERNET` и раскрытие в форме.

> Рекомендация: **не подключать** сторонний crash reporting в v1.0. Отсутствие сетевых разрешений — заметное конкурентное преимущество для приложения, которое инструктор кладёт рядом с собой на занятии, и сильный аргумент при ревью `specialUse`. Диагностика `DriftDetected` собирается локально и может быть показана пользователю либо отправлена им вручную по кнопке «Отправить отчёт».

---

## 7. Политика конфиденциальности

Обязательна для публикации даже при нулевом сборе данных. Требуется публичный URL.

Минимальное содержание:
- приложение не собирает, не передаёт и не хранит персональные данные вне устройства;
- профили занятий и настройки хранятся локально;
- сетевые разрешения не запрашиваются;
- удаление приложения удаляет все данные;
- контакт для связи.

Готовится на Фазе 8, публикуется до подачи на ревью.

---

## 8. Чек-лист публикации (Фаза 9)

- [ ] Keystore создан, зарезервирован, Play App Signing подключён
- [ ] `targetSdk = 36`
- [ ] Обоснование `specialUse` FGS внесено в консоль (§3)
- [ ] Обоснование `USE_EXACT_ALARM` внесено в консоль (§4)
- [ ] Форма Data Safety заполнена (§6)
- [ ] Политика конфиденциальности опубликована, URL указан
- [ ] Возрастной рейтинг пройден
- [ ] Store listing на RU и EN: описание, скриншоты, иконка, feature graphic
- [ ] Internal testing → Closed testing (для новых аккаунтов разработчика Play требует 12 тестировщиков в течение 14 дней)
- [ ] Поэтапный production rollout: 10% → 50% → 100%

---

## 9. Риски ревью и запасные варианты

| Риск | Вероятность | План Б |
|---|---|---|
| Отказ по `specialUse` FGS | Средняя | Уточнить обоснование, приложить видео сценария использования. В крайнем случае — переход на `mediaPlayback` с реальным воспроизведением тихого аудиотрека (рабочий, но нечестный вариант; применять только при повторном отказе) |
| Отказ по `USE_EXACT_ALARM` | Низкая | Категория «таймер» подтверждается основной функцией. При отказе — полный отказ от exact-алармов: watchdog переводится на неточные, обещания в описании корректируются |
| Требование раскрыть сбор данных | Низкая | Данных нет; при подключении crash reporting — обновить форму (§6) |
| Задержка из-за требования 12 тестировщиков × 14 дней | **Высокая** для нового аккаунта | Начать набор закрытых тестировщиков **на Фазе 6**, не дожидаясь RC. Это единственный пункт, который нельзя ускорить деньгами или кодом |

> Последняя строка — самый недооценённый риск графика. Требование Play к новым аккаунтам добавляет минимум две календарные недели между готовым RC и публикацией. В дорожной карте это учтено, но набор людей нужно начинать заранее.
