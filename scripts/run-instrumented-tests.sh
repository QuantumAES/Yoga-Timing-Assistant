#!/usr/bin/env bash
#
# Прогон инструментальных тестов на машине с ограниченной памятью.
#
# ЗАЧЕМ ОТДЕЛЬНЫЙ СКРИПТ. `./gradlew connectedDebugAndroidTest` держит
# одновременно демон Gradle (~2 ГБ), демон Kotlin (~1 ГБ) и эмулятор (~1,5 ГБ).
# На 8 ГБ это своп и молчаливая смерть эмулятора — Gradle сообщает
# «No connected devices», хотя устройство только что было.
#
# Порядок здесь другой:
#   1. собрать тестовый APK, пока эмулятор не запущен;
#   2. погасить демоны Gradle и Kotlin — освободить ~3 ГБ;
#   3. поднять эмулятор (или использовать подключённое устройство);
#   4. установить APK и запустить тесты напрямую через adb;
#   5. погасить эмулятор, если поднимали его сами.
#
# Использование:
#   ./scripts/run-instrumented-tests.sh                       # :core:database
#   ./scripts/run-instrumented-tests.sh core/database
#   ./scripts/run-instrumented-tests.sh core/database ProfileDaoTest
#   YTA_AVD=citadel YTA_EMU_RAM=1536 ./scripts/run-instrumented-tests.sh
#
# Если к машине подключено реальное устройство, эмулятор не запускается.
#
set -euo pipefail

MODULE="${1:-core/database}"
CLASS_FILTER="${2:-}"
GRADLE_PATH=":${MODULE//\//:}"

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB="$SDK_ROOT/platform-tools/adb"
EMULATOR_BIN="$SDK_ROOT/emulator/emulator"
EMU_RAM="${YTA_EMU_RAM:-1536}"
EMU_CORES="${YTA_EMU_CORES:-2}"
BOOT_TIMEOUT_SEC="${YTA_BOOT_TIMEOUT:-240}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

STARTED_EMULATOR=0

if [[ -t 1 ]]; then
    C_OK=$'\033[32m'; C_WARN=$'\033[33m'; C_ERR=$'\033[31m'; C_HEAD=$'\033[1;36m'; C_OFF=$'\033[0m'
else
    C_OK=''; C_WARN=''; C_ERR=''; C_HEAD=''; C_OFF=''
fi
section() { printf '\n%s== %s ==%s\n' "$C_HEAD" "$*" "$C_OFF"; }
ok()      { printf '%s  ✔%s %s\n' "$C_OK"   "$C_OFF" "$*"; }
warn()    { printf '%s  ●%s %s\n' "$C_WARN" "$C_OFF" "$*"; }
fail()    { printf '%s  ✘%s %s\n' "$C_ERR"  "$C_OFF" "$*"; }
die()     { fail "$*"; exit 1; }

cleanup() {
    if (( STARTED_EMULATOR )); then
        section "Гашу эмулятор"
        "$ADB" emu kill >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

memory_hint() {
    local available
    available=$(awk '/MemAvailable/ {printf "%d", $2/1024}' /proc/meminfo 2>/dev/null || echo 0)
    printf '  доступно памяти: %s МБ\n' "$available"
    if (( available > 0 && available < EMU_RAM + 512 )); then
        warn "меньше, чем нужно эмулятору (${EMU_RAM} МБ) — закройте лишние приложения"
    fi
}

# ── 1. Сборка тестового APK ──────────────────────────────────────────────────
section "1/5 Сборка тестового APK"
./gradlew "${GRADLE_PATH}:assembleDebugAndroidTest"

APK="$(find "$MODULE/build/outputs/apk/androidTest/debug" -name '*.apk' -print -quit 2>/dev/null || true)"
[[ -n "$APK" ]] || die "APK не найден в $MODULE/build/outputs/apk/androidTest/debug"
ok "$(basename "$APK")"

# Пакет берём из самого APK, а не из namespace: так не разъедется,
# если модуль переименуют.
AAPT2="$(find "$SDK_ROOT/build-tools" -name aapt2 -print | sort -r | head -1)"
[[ -x "$AAPT2" ]] || die "не найден aapt2 в $SDK_ROOT/build-tools"
TEST_PACKAGE="$("$AAPT2" dump packagename "$APK")"
ok "инструментация: $TEST_PACKAGE"

# ── 2. Освобождение памяти ───────────────────────────────────────────────────
section "2/5 Останавливаю демоны Gradle и Kotlin"
./gradlew --stop >/dev/null 2>&1 || true
pkill -f 'KotlinCompileDaemon' 2>/dev/null || true
sleep 1
memory_hint

# ── 3. Устройство ────────────────────────────────────────────────────────────
section "3/5 Устройство"
"$ADB" start-server >/dev/null 2>&1 || true

device_ready() { [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; }

if "$ADB" devices | awk 'NR>1 && $2=="device" {found=1} END {exit !found}'; then
    ok "использую уже подключённое устройство"
else
    AVD="${YTA_AVD:-$("$EMULATOR_BIN" -list-avds 2>/dev/null | head -1)}"
    [[ -n "$AVD" ]] || die "нет ни устройства, ни AVD (создайте: ./scripts/setup-dev-env.sh --with-emulators)"
    warn "поднимаю эмулятор $AVD (${EMU_RAM} МБ, ${EMU_CORES} ядра, без окна)"

    "$EMULATOR_BIN" -avd "$AVD" \
        -no-window -no-audio -no-boot-anim -no-snapshot-save \
        -memory "$EMU_RAM" -cores "$EMU_CORES" \
        -gpu swiftshader_indirect \
        -netdelay none -netspeed full \
        >/tmp/yta-emulator.log 2>&1 &
    STARTED_EMULATOR=1

    "$ADB" wait-for-device
    deadline=$((SECONDS + BOOT_TIMEOUT_SEC))
    until device_ready; do
        (( SECONDS < deadline )) || die "эмулятор не загрузился за ${BOOT_TIMEOUT_SEC} с (лог: /tmp/yta-emulator.log)"
        sleep 3
    done
    ok "загрузился, API $("$ADB" shell getprop ro.build.version.sdk | tr -d '\r')"
fi

# ── 4. Установка и запуск ────────────────────────────────────────────────────
section "4/5 Установка APK"
"$ADB" install -r -t "$APK" >/dev/null
ok "установлен"

section "5/5 Тесты"
INSTRUMENT_ARGS=()
if [[ -n "$CLASS_FILTER" ]]; then
    # Короткое имя достаточно: пакет дописываем сами.
    if [[ "$CLASS_FILTER" == *.* ]]; then
        INSTRUMENT_ARGS+=(-e class "$CLASS_FILTER")
    else
        INSTRUMENT_ARGS+=(-e class "${TEST_PACKAGE%.test}.$CLASS_FILTER")
    fi
    ok "фильтр: ${INSTRUMENT_ARGS[*]}"
fi

OUTPUT="$(mktemp)"
"$ADB" shell am instrument -w "${INSTRUMENT_ARGS[@]}" \
    "$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner" 2>&1 | tee "$OUTPUT"

# `am instrument` возвращает 0 даже при упавших тестах — разбираем вывод.
if grep -q "FAILURES!!!" "$OUTPUT"; then
    fail "есть упавшие тесты"
    rm -f "$OUTPUT"
    exit 1
fi
if ! grep -qE "^OK \([0-9]+ tests?\)" "$OUTPUT"; then
    fail "тесты не отчитались об успехе — смотрите вывод выше"
    rm -f "$OUTPUT"
    exit 1
fi

ok "$(grep -oE "OK \([0-9]+ tests?\)" "$OUTPUT" | head -1)"
rm -f "$OUTPUT"
