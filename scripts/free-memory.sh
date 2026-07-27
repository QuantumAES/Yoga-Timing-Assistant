#!/usr/bin/env bash
#
# Выгружает из памяти всё, что сборка держит между запусками.
#
# На 8 ГБ это обязательная привычка: демон Gradle (до 2 ГБ), демон Kotlin
# (до 1 ГБ), сервер adb и эмулятор живут после окончания сборки и не отдают
# память, пока их не попросить. Демоны Gradle настроены умирать сами через
# три минуты простоя (org.gradle.daemon.idletimeout), но перед запуском
# эмулятора ждать эти минуты незачем.
#
# Использование:
#   ./scripts/free-memory.sh            # демоны сборки
#   ./scripts/free-memory.sh --all      # плюс эмулятор и сервер adb
#
set -euo pipefail

WITH_DEVICES=0
for arg in "$@"; do
    case "$arg" in
        --all) WITH_DEVICES=1 ;;
        -h|--help) sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Неизвестный аргумент: $arg" >&2; exit 2 ;;
    esac
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB="$SDK_ROOT/platform-tools/adb"

available() { awk '/MemAvailable/ {printf "%d", $2/1024}' /proc/meminfo; }

before="$(available)"

# Демон Gradle: сначала вежливо, штатной командой.
if [[ -x "$ROOT/gradlew" ]]; then
    (cd "$ROOT" && ./gradlew --stop >/dev/null 2>&1) || true
fi

# Демон Kotlin живёт отдельным процессом и командой Gradle не гасится.
pkill -f 'KotlinCompileDaemon' 2>/dev/null || true

# Демоны других проектов на этой машине — не наши, их не трогаем.

if (( WITH_DEVICES )); then
    if [[ -x "$ADB" ]]; then
        "$ADB" emu kill >/dev/null 2>&1 || true
        "$ADB" kill-server >/dev/null 2>&1 || true
    fi
    pkill -f 'qemu-system.*-avd' 2>/dev/null || true
fi

sleep 1
after="$(available)"

printf 'Свободная память: %s МБ → %s МБ (освободилось %s МБ)\n' \
    "$before" "$after" "$(( after - before ))"

remaining="$(pgrep -af 'GradleDaemon|KotlinCompileDaemon' 2>/dev/null | wc -l)"
if (( remaining > 0 )); then
    printf 'Ещё живут JVM-процессы сборки: %s (могут принадлежать другим проектам)\n' "$remaining"
fi
