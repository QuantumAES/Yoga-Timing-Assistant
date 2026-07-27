#!/usr/bin/env bash
#
# Yoga Timing Assistant — установка изолированного окружения разработки.
#
# ПРИНЦИП: ничего системного не трогаем.
#   • без sudo, без apt, без update-alternatives;
#   • JDK 21 распаковывается в пользовательский каталог ~/.jdks/temurin-21;
#   • системный `java` (JDK 25) и все соседние проекты остаются как были;
#   • Gradle сам находит JDK в ~/.jdks (штатное авто-обнаружение toolchains)
#     и поднимает демон на 21-й версии по gradle/gradle-daemon-jvm.properties.
#
# Почему именно 21: AGP 9.x и Kotlin 2.4 не поддерживают JDK 25 как JVM сборки,
# а 21 — текущая LTS, на неё зафиксирован toolchain всех модулей проекта.
#
# Android SDK не дублируется — используется существующий ~/Android/Sdk;
# при нехватке компонентов они доустанавливаются (это аддитивно и другим
# проектам не мешает).
#
# Использование:
#   ./scripts/setup-dev-env.sh                  # JDK 21 + проверка SDK
#   ./scripts/setup-dev-env.sh --with-emulators # + системные образы API 26/30
#   ./scripts/setup-dev-env.sh --check          # только диагностика
#   JDKS_DIR=/path ./scripts/setup-dev-env.sh   # свой каталог для JDK
#
set -euo pipefail

readonly JDK_FEATURE=21
readonly REQUIRED_PLATFORM="android-37.0"
readonly REQUIRED_BUILD_TOOLS="37.0.0"
readonly ADOPTIUM_API="https://api.adoptium.net/v3/assets/latest/${JDK_FEATURE}/hotspot"

JDKS_DIR="${JDKS_DIR:-$HOME/.jdks}"
JDK_LINK="${JDKS_DIR}/temurin-${JDK_FEATURE}"

WITH_EMULATORS=0
CHECK_ONLY=0
PROBLEMS=0
JAVA21_HOME=""

for arg in "$@"; do
    case "$arg" in
        --with-emulators) WITH_EMULATORS=1 ;;
        --check)          CHECK_ONLY=1 ;;
        -h|--help)        sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Неизвестный аргумент: $arg (см. --help)" >&2; exit 2 ;;
    esac
done

# ── вывод ────────────────────────────────────────────────────────────────────
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
need()    { command -v "$1" >/dev/null || die "требуется утилита '$1'"; }

# ── 1. JDK 21 (изолированно, в $JDKS_DIR) ────────────────────────────────────

# Ищем уже пригодный JDK 21 в пользовательских каталогах.
# Системные /usr/lib/jvm намеренно НЕ используем как цель установки, но если
# 21-й там уже стоит — берём его и ничего не качаем.
find_jdk21() {
    local candidate
    for candidate in \
        "$JDK_LINK" \
        "$JDKS_DIR"/*"${JDK_FEATURE}"* \
        "$HOME/.sdkman/candidates/java/${JDK_FEATURE}"* \
        "/usr/lib/jvm/java-${JDK_FEATURE}-openjdk-"* \
        "/usr/lib/jvm/temurin-${JDK_FEATURE}-jdk-"*; do
        if [[ -x "${candidate}/bin/javac" ]] \
           && "${candidate}/bin/java" -version 2>&1 | head -1 | grep -q "\"${JDK_FEATURE}\."; then
            echo "$candidate"
            return 0
        fi
    done
    return 1
}

install_jdk21() {
    section "JDK ${JDK_FEATURE} (изолированно)"

    if JAVA21_HOME="$(find_jdk21)"; then
        ok "уже есть: $JAVA21_HOME"
        return 0
    fi

    if (( CHECK_ONLY )); then
        fail "JDK ${JDK_FEATURE} не найден — запустите скрипт без --check"
        (( ++PROBLEMS ))
        return 0
    fi

    need curl; need python3; need tar

    local arch json url sha release tmp
    case "$(uname -m)" in
        x86_64)  arch="x64" ;;
        aarch64) arch="aarch64" ;;
        *) die "неподдерживаемая архитектура: $(uname -m)" ;;
    esac

    warn "качаю Temurin ${JDK_FEATURE} (~200 МБ) в ${JDKS_DIR}"
    json="$(curl -fsSL "${ADOPTIUM_API}?os=linux&architecture=${arch}&image_type=jdk&vendor=eclipse")" \
        || die "не удалось обратиться к api.adoptium.net"

    read -r release url sha <<<"$(printf '%s' "$json" | python3 -c '
import sys, json
a = json.load(sys.stdin)[0]
p = a["binary"]["package"]
print(a["release_name"], p["link"], p["checksum"])
')"
    [[ -n "$url" ]] || die "Adoptium не вернул ссылку на сборку"
    ok "версия: $release"

    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' RETURN

    curl -fL --progress-bar -o "${tmp}/jdk.tar.gz" "$url" || die "загрузка не удалась"

    local actual
    actual="$(sha256sum "${tmp}/jdk.tar.gz" | cut -d' ' -f1)"
    [[ "$actual" == "$sha" ]] || die "контрольная сумма не совпала: $actual != $sha"
    ok "sha256 совпал"

    mkdir -p "$JDKS_DIR"
    local target="${JDKS_DIR}/${release}"
    rm -rf "$target"
    mkdir -p "$target"
    tar -xzf "${tmp}/jdk.tar.gz" -C "$target" --strip-components=1

    ln -sfn "$target" "$JDK_LINK"
    ok "распаковано: $target"
    ok "симлинк: $JDK_LINK"

    JAVA21_HOME="$JDK_LINK"
}

verify_jdk21() {
    [[ -n "$JAVA21_HOME" ]] || return 0
    local v
    v="$("${JAVA21_HOME}/bin/java" -version 2>&1 | head -1)"
    [[ "$v" == *"\"${JDK_FEATURE}."* ]] || die "ожидался JDK ${JDK_FEATURE}, получено: $v"
    ok "$v"
}

# ── 2. Android SDK (существующий, только доустановка недостающего) ───────────
detect_sdk() {
    section "Android SDK"
    SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
    [[ -d "$SDK_ROOT" ]] || die "SDK не найден: $SDK_ROOT (задайте ANDROID_HOME)"
    ok "root: $SDK_ROOT"

    SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
    [[ -x "$SDKMANAGER" ]] || die "нет sdkmanager: $SDKMANAGER — поставьте cmdline-tools"
}

sdk_install() {   # $1 = пакет, $2 = путь-маркер существования
    if [[ -e "$2" ]]; then
        ok "$1"
        return 0
    fi
    if (( CHECK_ONLY )); then
        fail "$1 — отсутствует"
        (( ++PROBLEMS ))
        return 0
    fi
    warn "$1 — ставлю"
    JAVA_HOME="$JAVA21_HOME" "$SDKMANAGER" --install "$1" >/dev/null
    ok "$1 — установлен"
}

setup_sdk() {
    sdk_install "platforms;${REQUIRED_PLATFORM}"      "$SDK_ROOT/platforms/${REQUIRED_PLATFORM}"
    sdk_install "build-tools;${REQUIRED_BUILD_TOOLS}" "$SDK_ROOT/build-tools/${REQUIRED_BUILD_TOOLS}"
    sdk_install "platform-tools"                      "$SDK_ROOT/platform-tools/adb"

    if (( CHECK_ONLY == 0 )); then
        yes 2>/dev/null | JAVA_HOME="$JAVA21_HOME" "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
        ok "лицензии приняты"
    fi
}

setup_emulators() {
    section "Системные образы для матрицы устройств"
    local abi="x86_64"
    sdk_install "system-images;android-26;google_apis;${abi}" \
                "$SDK_ROOT/system-images/android-26/google_apis/${abi}"
    sdk_install "system-images;android-30;google_apis;${abi}" \
                "$SDK_ROOT/system-images/android-30/google_apis/${abi}"
    sdk_install "emulator" "$SDK_ROOT/emulator/emulator"

    if (( CHECK_ONLY == 0 )); then
        cat <<EOF

  AVD создаются так:
    "\$SDK_ROOT/cmdline-tools/latest/bin/avdmanager" create avd \\
        -n yta_api26 -k "system-images;android-26;google_apis;${abi}" -d pixel_2
    "\$SDK_ROOT/cmdline-tools/latest/bin/avdmanager" create avd \\
        -n yta_api30 -k "system-images;android-30;google_apis;${abi}" -d pixel_4
EOF
    fi
}

# ── 3. Проект ────────────────────────────────────────────────────────────────
project_check() {
    section "Проект"
    ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

    if [[ -f "$ROOT/gradlew" ]]; then
        ok "gradle wrapper на месте"
    else
        warn "gradlew ещё не создан"
    fi

    if [[ -f "$ROOT/local.properties" ]]; then
        ok "local.properties на месте"
    elif (( CHECK_ONLY )); then
        warn "local.properties отсутствует — создастся при обычном запуске"
    else
        printf 'sdk.dir=%s\n' "$SDK_ROOT" > "$ROOT/local.properties"
        ok "создан local.properties (в git не попадает)"
    fi
}

summary() {
    section "Готово"
    cat <<EOF
  JDK ${JDK_FEATURE}:     $JAVA21_HOME
  Android SDK: $SDK_ROOT

  Системное окружение не изменено:
    java по умолчанию — $(java -version 2>&1 | head -1)
    PATH, alternatives, apt-пакеты не трогались

  Сборка:
    cd $ROOT && ./gradlew build

  Gradle сам поднимет демон на JDK ${JDK_FEATURE}: каталог ~/.jdks входит в штатное
  авто-обнаружение toolchains, версия затребована в gradle/gradle-daemon-jvm.properties.
  Экспортировать JAVA_HOME не нужно. Если понадобится вручную:
    export JAVA_HOME=$JAVA21_HOME
EOF
}

main() {
    install_jdk21
    verify_jdk21
    detect_sdk
    setup_sdk
    if (( WITH_EMULATORS )); then setup_emulators; fi
    project_check

    if (( CHECK_ONLY )); then
        section "Итог диагностики"
        if (( PROBLEMS )); then
            fail "проблем: ${PROBLEMS} — запустите ./scripts/setup-dev-env.sh без --check"
            exit 1
        fi
        ok "окружение готово"
        exit 0
    fi
    summary
}

main "$@"
