# Тех-дизайн ядра таймера

**Статус:** ✅ утверждён 2026-07-26
**Заменяет:** утраченный раздел 3 `ТЗ.md` (строки 1136–1200, повреждены)
**Связанные решения:** ADR-001 (механизм отсчёта), `06-MVP-SCOPE.md` §3 (доопределённое поведение)

---

## 0. Три принципа, из которых следует всё остальное

**П-1. Время не «тикает» — время вычисляется.**
Состояние сессии никогда не изменяется по таймеру. Оно изменяется только командами пользователя и наступлением запланированных событий. Текущий остаток — это *чистая функция* от сохранённых меток и текущего момента. Следствие: заморозка процесса на 10 минут не ломает состояние, а лишь откладывает вычисление.

**П-2. Ядро не знает про Android.**
Модуль `:timer-engine` — чистый Kotlin/JVM. Ни одного импорта `android.*`. Следствие: 90-минутная сессия проверяется юнит-тестом за миллисекунды на виртуальном времени, а не сидением с секундомером.

**П-3. Монотонные часы для отсчёта, стенные — только для отображения и персиста.**
`elapsedRealtime()` не прыгает при NTP-синхронизации, смене часового пояса и ручной правке времени пользователем, и продолжает идти во сне устройства. `currentTimeMillis()` используется исключительно для «прошло ли 5 минут с сохранения» и детекта перезагрузки.

---

## 1. Границы модулей

```
:timer-engine        pure Kotlin/JVM  — состояние, редьюсер, планировщик событий
   ↑ зависит только от kotlin-stdlib, kotlinx-coroutines-core, kotlinx-serialization

:timer-service       Android          — FGS, WakeLock, уведомление, watchdog-аларм,
                                        персист, адаптер TimeSource
:domain              pure Kotlin/JVM  — сборка SessionPlan, резолвер оповещений
```

Правило: `:timer-engine` не знает ни про Room, ни про DataStore, ни про Service. Он получает готовый `SessionPlan` и отдаёт `SessionSnapshot` + поток одноразовых `TimerEvent`.

### Абстракция времени

```kotlin
interface TimeSource {
    /** Монотонные миллисекунды с момента загрузки. Идут во сне. Не прыгают. */
    fun elapsed(): Long
    /** Стенные часы. Только для персиста и детекта перезагрузки. */
    fun wall(): Long
}
```

| Реализация | Где |
|---|---|
| `AndroidTimeSource` → `SystemClock.elapsedRealtime()` / `System.currentTimeMillis()` | `:timer-service` |
| `VirtualTimeSource` → управляется `TestCoroutineScheduler` | тесты |

---

## 2. Машина состояний

Отличие от ТЗ §3.1: **состояние `TRANSITION` удалено** (решение B-15).

```
                 ┌──────────┐
                 │   IDLE   │  план загружен, отсчёт не начат
                 └────┬─────┘
                      │ Start
                      ▼
      Resume     ┌──────────┐      Next / Previous / Adjust
    ┌───────────►│ RUNNING  │◄──── (остаются в RUNNING)
    │            └──┬────┬──┘
    │               │    │ последний этап завершён
    │         Pause │    │
    │               ▼    ▼
┌───┴────┐      ┌──────────┐
│ PAUSED │      │ FINISHED │
└───┬────┘      └────┬─────┘
    │ Stop           │ Restart / Exit
    └────────────────┴──────► IDLE / выход
```

**Почему убран TRANSITION.** В ТЗ он существовал ради анимации смены этапа («auto — после анимации»). Как состояние движка он вреден: либо отсчёт на время анимации останавливается — и тогда каждый переход крадёт 300–500 мс, а за 8 этапов накапливается до 4 с дрейфа, что нарушает критерий T-1; либо не останавливается — и тогда состояние ничего не значит.

Переход стал одноразовым событием `TimerEvent.StageChanged`. UI анимирует его, пока следующий этап уже идёт. Никакого дрейфа.

`Pause` и `Resume` из PAUSED — единственные переходы, меняющие ход времени. `Next`, `Previous`, `Adjust` в PAUSED тоже разрешены (ТЗ §3: «PAUSED → resume, next, prev, stop») и не возобновляют отсчёт.

---

## 3. Модель состояния

### 3.1. План сессии (иммутабельный вход)

```kotlin
data class SessionPlan(
    val profileId: Long,
    val profileName: String,
    val stages: List<PlannedStage>,
)

data class PlannedStage(
    val id: Long,
    val name: String,
    val type: StageType,               // NORMAL | TRANSITION | REST | FREE
    val colorTag: String,
    val plannedDurationMs: Long,       // 0 для FREE
    val note: String?,
    val alerts: ResolvedAlertConfig,   // наследование Profile→Stage уже применено
)
```

> Наследование конфигов оповещений и «тихий» пресет для REST разрешаются **на этапе сборки плана** в `:domain`, а не в рантайме. Движок получает уже финальные оповещения. Это держит его чистым и делает поведение предсказуемым (решение C-6).

### 3.2. Внутреннее состояние (единственный источник истины)

```kotlin
data class SessionState(
    val plan: SessionPlan,
    val runState: RunState,                  // IDLE | RUNNING | PAUSED | FINISHED
    val currentIndex: Int,

    /** Сколько текущего этапа уже пройдено к моменту последнего Resume. */
    val stageElapsedAtResumeMs: Long,

    /** Монотонная метка последнего Start/Resume/входа в этап. Валидна только в RUNNING. */
    val resumedAtMs: Long,

    /** Накопленные правки ±30 с по индексу этапа. */
    val adjustmentsMs: Map<Int, Long>,

    /** Фактические длительности завершённых этапов (могут отличаться от плановых). */
    val actualDurationsMs: Map<Int, Long>,

    /** Уже отработавшие в текущем этапе оповещения — защита от повторов. */
    val firedAlertIds: Set<String>,
)
```

Всего **семь** полей несут состояние. Всё остальное выводится.

### 3.3. Производные величины

```kotlin
fun SessionState.effectiveDurationMs(index: Int): Long {
    val base = plan.stages[index].plannedDurationMs
    val adj  = adjustmentsMs[index] ?: 0L
    return (base + adj).coerceIn(MIN_STAGE_MS, MAX_STAGE_MS)   // 5 с … 4 ч (B-3)
}

fun SessionState.stageElapsedMs(now: Long): Long = when (runState) {
    RunState.RUNNING -> stageElapsedAtResumeMs + (now - resumedAtMs)
    else             -> stageElapsedAtResumeMs
}

fun SessionState.stageRemainingMs(now: Long): Long? =
    if (currentStage.type == StageType.FREE) null            // бессрочный (B-4)
    else (effectiveDurationMs(currentIndex) - stageElapsedMs(now)).coerceAtLeast(0)

fun SessionState.totalElapsedMs(now: Long): Long =
    actualDurationsMs.values.sum() + stageElapsedMs(now)

fun SessionState.totalRemainingMs(now: Long): Long {
    val future = (currentIndex + 1..plan.stages.lastIndex)
        .filter { plan.stages[it].type != StageType.FREE }
        .sumOf { effectiveDurationMs(it) }
    return future + (stageRemainingMs(now) ?: 0L)
}

/** true, если в остатке есть FREE-этапы → UI показывает «≥ 42 мин» вместо «42 мин». */
fun SessionState.totalRemainingIsLowerBound(): Boolean =
    (currentIndex..plan.stages.lastIndex).any { plan.stages[it].type == StageType.FREE }
```

**Почему `actualDurationsMs`, а не сумма плановых.** Инструктор нажал «След.» на середине этапа — фактически прошло меньше планового. Общее «прошло» обязано отражать реальность, иначе прогресс-полоса врёт.

### 3.4. Снапшот для UI

```kotlin
data class SessionSnapshot(
    val profileName: String,
    val runState: RunState,
    val currentIndex: Int,
    val currentStageName: String,
    val currentStageColor: String,
    val currentStageType: StageType,
    val currentNote: String?,
    val stageRemainingMs: Long?,        // null для FREE
    val stageElapsedMs: Long,           // для FREE — основной показатель, счёт вверх
    val stageProgress: Float?,          // 0f..1f, null для FREE
    val stageAdjustmentMs: Long,        // накопленные ±30 с, показываем «+1:00»
    val totalElapsedMs: Long,
    val totalRemainingMs: Long,
    val totalRemainingIsLowerBound: Boolean,
    val totalProgress: Float,
    val nextStageName: String?,
    val nextStageDurationMs: Long?,
    val isLastStage: Boolean,
)
```

Снапшот **вычисляется по запросу**, а не хранится. Экспонируется как `StateFlow<SessionSnapshot>`, обновляемый UI-тикером (§6).

---

## 4. Команды и редьюсер

```kotlin
sealed interface TimerCommand {
    data class  Load(val plan: SessionPlan) : TimerCommand
    data object Start                       : TimerCommand
    data object Pause                       : TimerCommand
    data object Resume                      : TimerCommand
    data object Next                        : TimerCommand
    data object Previous                    : TimerCommand
    data class  Adjust(val deltaMs: Long)   : TimerCommand   // ±30_000
    data object Stop                        : TimerCommand
    data object Restart                     : TimerCommand
}
```

Ядро редьюсера — **чистая функция**:

```kotlin
fun reduce(state: SessionState, cmd: TimerCommand, now: Long): Reduction

data class Reduction(
    val state: SessionState,
    val events: List<TimerEvent>,   // побочные эффекты как данные
)
```

Побочные эффекты (проиграть оповещение, обновить уведомление, записать персист) **не выполняются** в редьюсере, а возвращаются списком. Это делает всю логику тестируемой без моков.

### 4.1. Таблица переходов

| Команда | IDLE | RUNNING | PAUSED | FINISHED |
|---|---|---|---|---|
| `Start` | → RUNNING, вход в этап 0 | — | — | — |
| `Pause` | — | зафиксировать `stageElapsedAtResumeMs`, → PAUSED | — | — |
| `Resume` | — | — | `resumedAtMs = now`, → RUNNING | — |
| `Next` | — | завершить этап (+END-алерт), вход в след. | то же, остаться в PAUSED | — |
| `Previous` | — | вход в пред. этап **без** END-алерта (B-10) | то же | — |
| `Adjust` | — | правка текущего этапа | правка текущего этапа | — |
| `Stop` | — | → IDLE, сброс | → IDLE, сброс | → IDLE |
| `Restart` | — | — | — | → IDLE + `Start` |

### 4.2. Вход в этап

```kotlin
private fun enterStage(state: SessionState, index: Int, now: Long, reason: Reason): Reduction {
    if (index > state.plan.stages.lastIndex) return finish(state, now)

    val next = state.copy(
        currentIndex = index,
        stageElapsedAtResumeMs = 0L,
        resumedAtMs = now,
        firedAlertIds = emptySet(),          // сброс защиты от повторов
    )
    val events = buildList {
        add(TimerEvent.StageChanged(from = state.currentIndex, to = index, reason))
        addAll(next.dueStartAlerts())        // START-оповещения — немедленно
    }
    return Reduction(next, events)
}
```

`Previous` при `currentIndex == 0` — no-op (уже на первом этапе).
`Next` на последнем этапе → `finish()` → `RunState.FINISHED` + `TimerEvent.SessionFinished`.

### 4.3. Правка ±30 с (решения B-1, B-2)

```kotlin
private fun adjust(state: SessionState, deltaMs: Long, now: Long): Reduction {
    val idx = state.currentIndex
    val newAdj = (state.adjustmentsMs[idx] ?: 0L) + deltaMs
    var next = state.copy(adjustmentsMs = state.adjustmentsMs + (idx to newAdj))

    // Оповещения перепланируются: сдвиг конца этапа сдвигает все WARNING/END.
    // Снимаем отметки о срабатывании тех, что ещё не наступили.
    next = next.copy(firedAlertIds = next.firedAlertIds.filter { it in next.alreadyPassed(now) }.toSet())

    // B-2: если остаток ушёл в ноль — немедленный переход
    val remaining = next.stageRemainingMs(now)
    return if (remaining != null && remaining <= 0L) {
        completeStage(next, now, Reason.AUTO)     // END-оповещение отрабатывает
    } else {
        Reduction(next, listOf(TimerEvent.PlanChanged))
    }
}
```

Режим SUM: общее время занятия меняется автоматически, потому что `totalRemainingMs` считает по `effectiveDurationMs`. Никакой отдельной логики не требуется — это дивиденд от того, что всё выводится, а не хранится.

---

## 5. Планировщик событий

Вместо «тика с проверкой условий» движок вычисляет **отсортированный список дедлайнов** текущего этапа.

```kotlin
data class ScheduledEvent(
    val id: String,            // "stage3:warn:120" — стабильный, для firedAlertIds
    val atElapsedMs: Long,     // монотонный дедлайн
    val kind: Kind,
) {
    sealed interface Kind {
        data class Alert(val alert: ResolvedAlert) : Kind
        data object StageEnd                       : Kind
    }
}
```

```kotlin
fun SessionState.scheduleForCurrentStage(): List<ScheduledEvent> {
    val stage = plan.stages[currentIndex]
    if (stage.type == StageType.FREE) return emptyList()      // B-5: нет конца — нет дедлайнов

    val startedAt = resumedAtMs - stageElapsedAtResumeMs      // виртуальное «начало этапа»
    val endAt     = startedAt + effectiveDurationMs(currentIndex)

    return buildList {
        stage.alerts.warnings
            .filter { it.enabled && it.offsetMs < effectiveDurationMs(currentIndex) }   // B-7
            .forEach { add(ScheduledEvent("stage$currentIndex:warn:${it.offsetMs}", endAt - it.offsetMs, Alert(it))) }

        stage.alerts.end?.takeIf { it.enabled }
            ?.let { add(ScheduledEvent("stage$currentIndex:end", endAt, Alert(it))) }

        add(ScheduledEvent("stage$currentIndex:complete", endAt, StageEnd))
    }.sortedBy { it.atElapsedMs }
}

fun SessionState.nextDeadline(now: Long): Long? =
    if (runState != RunState.RUNNING) null
    else scheduleForCurrentStage()
        .filter { it.id !in firedAlertIds && it.atElapsedMs > now }
        .minOfOrNull { it.atElapsedMs }
```

**Ключевое свойство:** расписание пересчитывается заново после каждой команды. Никакого инкрементального состояния планировщика — нечему рассинхронизироваться после ±30 с, паузы или ручного перехода.

---

## 6. Цикл исполнения

Два независимых цикла с разными задачами и разной ценой ошибки.

### 6.1. Цикл событий — отвечает за корректность

```kotlin
private suspend fun eventLoop() {
    while (currentCoroutineContext().isActive) {
        val deadline = state.nextDeadline(time.elapsed())
        if (deadline == null) {
            commands.receive().let { apply(it) }               // спим до команды
            continue
        }
        val waitMs = deadline - time.elapsed()
        if (waitMs > 0) {
            // гонка: наступил дедлайн ИЛИ пришла команда
            withTimeoutOrNull(waitMs) { commands.receive() }?.let { apply(it); continue }
        }
        processDueEvents(time.elapsed())
    }
}
```

Просыпается **ровно на события**, а не 5 раз в секунду. За 60-минутное занятие из 6 этапов — около 25 пробуждений вместо ~18 000.

### 6.2. UI-тикер — отвечает только за плавность

```kotlin
private suspend fun uiTicker() {
    while (currentCoroutineContext().isActive) {
        if (state.runState == RunState.RUNNING) {
            _snapshot.value = state.snapshot(time.elapsed())
            delay(msUntilNextWholeSecond())        // выравнивание по границе секунды
        } else {
            _snapshot.value = state.snapshot(time.elapsed())
            awaitStateChange()
        }
    }
}
```

Выравнивание по границе секунды устраняет визуальное «подёргивание» (цифра меняется через неравные интервалы, если тикать фиксированные 1000 мс от произвольного момента).

**Если UI-тикер пропустит удары — не произойдёт ничего плохого.** Он не участвует в корректности. Это и есть смысл принципа П-1.

---

## 7. Catch-up: восстановление после заморозки

Самая важная часть дизайна и то, чего в исходном ТЗ не было вовсе.

Процесс может быть заморожен Doze, убит OEM-надстройкой и воскрешён watchdog-алармом, или просто не получить процессорное время. При возврате `now` может оказаться далеко за несколькими дедлайнами.

```kotlin
private fun processDueEvents(now: Long) {
    val driftMs = now - (state.nextDeadline(now) ?: now)
    var dropped = 0

    while (true) {
        val due = state.scheduleForCurrentStage()
            .filter { it.id !in state.firedAlertIds && it.atElapsedMs <= now }
            .sortedBy { it.atElapsedMs }
        if (due.isEmpty()) break

        for (ev in due) when (ev.kind) {
            is Kind.Alert -> {
                val lateBy = now - ev.atElapsedMs
                if (lateBy <= LATE_TOLERANCE_MS) emit(PlayAlert(ev.kind.alert))
                else dropped++                       // просроченное не проигрываем
                markFired(ev.id)
            }
            is Kind.StageEnd -> completeStage(now, Reason.AUTO)   // может перейти дальше
        }
        if (state.runState == RunState.FINISHED) break
    }

    if (driftMs > DRIFT_REPORT_THRESHOLD_MS) {
        emit(TimerEvent.DriftDetected(driftMs, dropped))
    }
}
```

```kotlin
const val LATE_TOLERANCE_MS         = 2_000L    // опоздание, при котором ещё имеет смысл играть
const val DRIFT_REPORT_THRESHOLD_MS = 5_000L    // порог диагностики
```

### Почему просроченные оповещения отбрасываются

Проиграть «осталось 2 минуты», когда этап уже закончился три минуты назад, — хуже, чем промолчать: инструктор получает дезинформацию посреди занятия. Молчание он спишет на настройки; ложный сигнал собьёт с плана.

Исключение: если `StageEnd` пропущен, движок всё равно проходит стадии до актуальной и проигрывает **START-оповещение того этапа, где мы фактически оказались** — чтобы инструктор понял, где он находится.

### `DriftDetected` — диагностика, а не косметика

Это событие — наш единственный полевой инструмент против риска R-1. Оно позволяет:
- показать пользователю честный баннер «приложение было приостановлено системой на 3 мин»;
- накопить статистику по моделям устройств во время закрытого тестирования;
- отличить «MIUI убивает сервис» от «баг в наших расчётах» без доступа к устройству пользователя.

---

## 8. Персистентность и восстановление

### 8.1. Формат

```kotlin
@Serializable
data class PersistedSession(
    val schemaVersion: Int = 1,
    val profileId: Long,
    val savedAtWallMs: Long,            // стенные часы — для правила «< 5 минут»
    val savedAtElapsedMs: Long,         // монотонные — для детекта перезагрузки
    val runState: RunState,
    val currentIndex: Int,
    val stageElapsedAtResumeMs: Long,
    val resumedAtMs: Long,
    val adjustmentsMs: Map<Int, Long>,
    val actualDurationsMs: Map<Int, Long>,
    val firedAlertIds: Set<String>,
)
```

### 8.2. Когда пишем

**Только по событиям** (исправление P1-3), не по расписанию:

`Start` · `Pause` · `Resume` · смена этапа · `Adjust` · `Stop` · `Finish`

За 90-минутное занятие — около 20 записей вместо ~1100 при подходе «раз в 5 секунд» из исходного ТЗ. Это возможно именно потому, что между событиями состояние **не меняется** (П-1): хранятся метки, а не счётчики.

### 8.3. Детект перезагрузки — без разрешений и чтения /proc

```kotlin
fun PersistedSession.isValidNow(time: TimeSource): Restorability {
    // elapsedRealtime монотонно растёт внутри одной загрузки.
    // Меньшее значение возможно только после перезагрузки.
    if (time.elapsed() < savedAtElapsedMs) return Restorability.REBOOTED       // B-11

    // Перекрёстная проверка: стенные часы должны были уйти примерно настолько же,
    // насколько ушли монотонные. Расхождение = ручная правка времени или NTP-скачок.
    val elapsedDelta = time.elapsed() - savedAtElapsedMs
    val wallDelta    = time.wall()    - savedAtWallMs
    if (abs(wallDelta - elapsedDelta) > CLOCK_JUMP_TOLERANCE_MS) {
        return Restorability.CLOCK_CHANGED     // восстанавливаем по elapsed, игнорируя wall
    }

    if (elapsedDelta > RESTORE_WINDOW_MS) return Restorability.TOO_OLD          // > 5 мин (B-12)
    return Restorability.OK
}
```

Ни `READ_PHONE_STATE`, ни чтения `/proc/sys/kernel/random/boot_id` (блокируется SELinux на части прошивок) — только два числа, которые мы и так сохраняем.

| Результат | Поведение |
|---|---|
| `OK` | Диалог «Продолжить занятие?» с показом актуального остатка |
| `TOO_OLD` | Тихо отбросить снапшот |
| `REBOOTED` | Отбросить + уведомление «Занятие было прервано перезагрузкой» (B-11) |
| `CLOCK_CHANGED` | Восстановить по монотонным часам, стенные проигнорировать (B-14) |

---

## 9. Android-обвязка

### 9.1. TimerService

```kotlin
@AndroidEntryPoint
class TimerService : Service() {

    @Inject lateinit var controller: SessionController   // @Singleton, держит движок
    @Inject lateinit var alertPlayer: AlertPlayer
    @Inject lateinit var watchdog: WatchdogAlarm
    @Inject lateinit var store: SessionStore

    private lateinit var wakeLock: PowerManager.WakeLock
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification(controller.snapshot.value), FGS_TYPE)
        acquireWakeLock()

        scope.launch { controller.events.collect(::handleEvent) }
        scope.launch { controller.snapshot.collect(::updateNotificationThrottled) }

        return START_STICKY      // при воскрешении intent == null → восстановление из store
    }

    private fun handleEvent(e: TimerEvent) = when (e) {
        is TimerEvent.PlayAlert      -> alertPlayer.play(e.alert, e.context)
        is TimerEvent.StageChanged   -> { watchdog.rearm(controller.nextStageEnd()); store.save(controller.persist()) }
        is TimerEvent.SessionFinished-> { watchdog.cancel(); store.clear(); stopSelfSafely() }
        is TimerEvent.DriftDetected  -> diagnostics.record(e)
        is TimerEvent.PlanChanged    -> { watchdog.rearm(controller.nextStageEnd()); store.save(controller.persist()) }
    }

    override fun onDestroy() {
        watchdog.cancel()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }
}
```

**Почему `SessionController` — `@Singleton`, а не Binder.** Движок и UI живут в одном процессе; если процесс умрёт, умрёт всё вместе. Сервис нужен не как транспорт данных, а как *маркер приоритета процесса для системы*. Поэтому UI подписывается на `StateFlow` синглтона напрямую, без `bindService`, `ServiceConnection` и обработки разрыва соединения. Минус ~200 строк boilerplate и целый класс гонок при пересоздании Activity.

### 9.2. WakeLock

```kotlin
private fun acquireWakeLock() {
    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "yoga-timer:session")
        .apply { acquire(MAX_SESSION_MS) }   // 5 ч — страховка от утечки при баге
}
```

`PARTIAL_WAKE_LOCK` держит только CPU. Экран не будится — им управляет Activity через `FLAG_KEEP_SCREEN_ON`, и только пока рабочий экран на переднем плане.

Таймаут в `acquire()` обязателен: если сервис умрёт нештатно, не освободив лок, батарея не будет разряжаться бесконечно.

### 9.3. Watchdog-аларм

По ADR-001 — **ровно один** exact-аларм в любой момент времени, взведённый на **конец текущего этапа**:

```kotlin
class WatchdogAlarm @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val am = ctx.getSystemService<AlarmManager>()!!

    fun rearm(stageEndElapsedMs: Long?) {
        cancel()
        if (stageEndElapsedMs == null) return              // PAUSED или FREE-этап
        val pi = pendingIntent()                            // стабильный requestCode = 1
        if (canScheduleExact()) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, stageEndElapsedMs, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, stageEndElapsedMs, pi)
            flags.exactAlarmUnavailable = true              // → баннер в UI
        }
    }

    fun cancel() = am.cancel(pendingIntent())
}
```

Единственный `requestCode` снимает проблему P1-1/P1-2: нечего терять при смерти процесса, `cancel()` всегда находит нужный PendingIntent, а квота `AllowWhileIdle` не расходуется (перевзвод происходит раз в несколько минут, на границах этапов).

Ресивер не проигрывает оповещения сам — он лишь **будит сервис**, а тот проходит обычную процедуру `processDueEvents()` с catch-up. Одна кодовая ветка вместо двух.

### 9.4. Уведомление

- Канал `timer_session`, importance `LOW` (без звука — звуком занимается `AlertPlayer`).
- Обновление не чаще 1 Гц и только при изменении текста (`throttle` + `distinctUntilChanged`).
- Действия: Пауза/Возобновить · Далее · Стоп — через `PendingIntent` на сервис.
- `setOngoing(true)`, `setSilent(true)`, `setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)`.

### 9.5. Манифест

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-permission android:name="android.permission.VIBRATE"/>
<uses-permission android:name="android.permission.USE_EXACT_ALARM"      android:minSdkVersion="33"/>
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"  android:maxSdkVersion="32"/>

<service
    android:name=".timer.TimerService"
    android:foregroundServiceType="specialUse"
    android:exported="false">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Interval timing for live yoga classes: the app must keep counting stages and deliver audio cues while the screen is locked and the phone lies on the mat."/>
</service>
```

Разделение `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` по версиям — не косметика: `USE_EXACT_ALARM` существует только с API 33, а на API 31–32 `SCHEDULE_EXACT_ALARM` выдавалось при установке. Обоснование для ревью Play — в `05-PLAY-DECLARATIONS.md`.

---

## 10. Тест-план движка

Модуль `:timer-engine` тестируется целиком на виртуальном времени. Порог покрытия — 85% (критерий N-4).

### 10.1. Базовые сценарии

| # | Сценарий | Проверяем |
|---|---|---|
| E-01 | Прогон 6 этапов без вмешательства | Все переходы, порядок и состав оповещений, FINISHED в конце |
| E-02 | Пауза 10 мин посередине этапа | Остаток не изменился; после Resume отсчёт продолжен с той же точки |
| E-03 | Пауза → Next → Resume | Переход в PAUSED разрешён, отсчёт не возобновился самовольно |
| E-04 | `Adjust(+30s)` ×4 | Длительность этапа и общее время выросли на 2 мин; WARNING-оповещения перепланированы |
| E-05 | `Adjust(-30s)` при остатке 10 с | Немедленное завершение этапа, END-оповещение отработало (B-2) |
| E-06 | `Previous` с этапа 3 | Вход в этап 2, START отработал, END этапа 3 **не** отработал (B-10) |
| E-07 | `Previous` на этапе 0 | No-op, состояние не изменилось |
| E-08 | FREE-этап | Счёт вверх, `stageRemainingMs == null`, дедлайнов нет, выход только по `Next` (B-5) |
| E-09 | FREE в середине плана | `totalRemainingIsLowerBound == true` (B-4) |
| E-10 | WARNING с offset ≥ длительности | Оповещение молча пропущено (B-7) |
| E-11 | `Next` на последнем этапе | FINISHED, `SessionFinished` с корректным `totalElapsedMs` |

### 10.2. Catch-up и устойчивость

| # | Сценарий | Проверяем |
|---|---|---|
| E-20 | Скачок времени на +3 мин внутри 10-минутного этапа | WARNING «за 2 мин» отброшен как просроченный, `DriftDetected` эмитится |
| E-21 | Скачок времени, перекрывающий 2 этапа целиком | Движок дошёл до верного этапа, индекс и остаток корректны, отброшенные посчитаны |
| E-22 | Скачок ровно на `LATE_TOLERANCE_MS - 1` | Оповещение **проиграно**, не отброшено |
| E-23 | Скачок на `LATE_TOLERANCE_MS + 1` | Оповещение отброшено |
| E-24 | Повторный `processDueEvents` с тем же `now` | Оповещения не дублируются (`firedAlertIds`) |
| E-25 | Персист → десериализация → продолжение | Состояние восстановлено побитово, остаток совпадает |
| E-26 | `isValidNow` при `elapsed < savedElapsed` | `REBOOTED` |
| E-27 | `isValidNow` при расхождении wall/elapsed на 1 ч | `CLOCK_CHANGED`, восстановление по монотонным часам |
| E-28 | `isValidNow` спустя 6 мин | `TOO_OLD` |

### 10.3. Интегральный тест точности (критерий T-1)

```kotlin
@Test
fun `drift stays under one second over a 90 minute session`() = runTest {
    val plan = plan(stages = 8, totalMinutes = 90)
    val engine = TimerEngine(plan, virtualTime)

    engine.start()
    repeat(5)  { advance(7.minutes); engine.pause(); advance(90.seconds); engine.resume() }
    repeat(4)  { engine.adjust(+30.seconds) }
    repeat(2)  { advance(3.minutes); engine.next() }
    advanceUntilFinished()

    val expected = plan.totalMs + 4 * 30_000 - actuallySkippedMs
    assertThat(engine.totalElapsedMs).isCloseTo(expected, within(1000L))
}
```

90 минут модельного времени исполняются за миллисекунды. Такой тест выполняется на каждом PR — в отличие от ручной проверки, которую физически невозможно делать регулярно.

---

## 11. Что этот дизайн осознанно НЕ решает

Честный список ограничений, чтобы они не всплыли на приёмке как сюрприз.

1. **OEM, убивающий процесс без права на воскрешение.** Если MIUI вычистит приложение из памяти и заблокирует watchdog-аларм, занятие остановится. Мы это **обнаружим** (`DriftDetected` при следующем запуске) и сообщим пользователю, но предотвратить не можем. Проверка — веха M2.
2. **Точность выше ±1 с в Doze без снятой оптимизации батареи.** Ни один Android API такого не гарантирует (см. переписанный критерий T-3/T-4).
3. **Восстановление сессии после перезагрузки.** Сознательное решение ТЗ §8, подтверждено (B-11).
4. **Работа при выгруженном приложении и выключенном сервисе.** Не поддерживается: сессия существует только пока жив сервис или пока актуален снапшот (< 5 мин).

---

## 12. Порядок реализации (Фаза 3)

```
1. TimeSource + VirtualTimeSource                         0.5 дн
2. SessionState + производные величины + снапшот          1   дн
3. reduce() + таблица переходов                           1.5 дн
4. Планировщик событий + catch-up                         1.5 дн
5. Тесты E-01…E-28 + интегральный тест точности           2   дн   ← до Android-обвязки
6. SessionController + персист + детект перезагрузки      1   дн
7. TimerService + WakeLock + уведомление                  2   дн
8. WatchdogAlarm + ресивер                                1   дн
9. Прогон матрицы устройств (M2)                          1.5 дн
```

Пункт 5 идёт **до** пункта 7 намеренно: движок должен быть доказанно корректным раньше, чем к нему прикрутят Android, — иначе отладка «сигнал не сработал» превращается в гадание между логикой и системными ограничениями.
