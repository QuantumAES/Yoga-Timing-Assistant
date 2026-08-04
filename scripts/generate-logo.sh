#!/usr/bin/env bash
#
# Пересборка всех размеров логотипа из одного исходника.
#
# Логотип меняется целиком и редко, а мест, где он лежит, семь: пять плотностей
# иконки запуска, монохромный слой для тематических иконок, знак в интерфейсе и
# картинка для Google Play. Пересобирать их руками — гарантированно забыть одно
# из них и обнаружить это на витрине магазина.
#
#   ./scripts/generate-logo.sh                     # из app/src/Yoga-Timing-Assistant.jpg
#   ./scripts/generate-logo.sh path/to/logo.png    # из другого файла
#   ./scripts/generate-logo.sh --check             # только проверить окружение
#
# Что делает:
#   1. Убирает фон исходника (заливка от углов с допуском) и обрезает поля.
#   2. Кладёт знак в безопасную зону адаптивной иконки — центральные 66 из
#      108 dp: всё, что снаружи, обрезается маской оболочки, и на Pixel круг
#      съел бы лепестки лотоса.
#   3. Делает монохромный силуэт для тематических иконок Android 13+.
#   4. Пишет цвет подложки иконки из фона исходника.
#   5. Собирает знак для интерфейса и картинку 512×512 для Play.
#
# Требуется Python 3 с Pillow. ImageMagick не нужен — одна зависимость вместо
# двух, и та уже есть в окружении сборки (см. scripts/generate-alert-sounds.py).

set -euo pipefail

readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly DEFAULT_SOURCE="$ROOT/app/src/Yoga-Timing-Assistant.jpg"

usage() {
    sed -n '3,27p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

require_pillow() {
    if ! python3 -c "import PIL" 2>/dev/null; then
        echo "Нужен Python 3 с Pillow: pip install --user Pillow" >&2
        exit 1
    fi
}

main() {
    local source="${1:-$DEFAULT_SOURCE}"

    case "$source" in
        -h | --help)
            usage
            exit 0
            ;;
        --check)
            require_pillow
            echo "Pillow на месте, исходник: $DEFAULT_SOURCE"
            [ -f "$DEFAULT_SOURCE" ] || { echo "Исходника нет" >&2; exit 1; }
            exit 0
            ;;
    esac

    require_pillow

    if [ ! -f "$source" ]; then
        echo "Файл не найден: $source" >&2
        exit 1
    fi

    env ROOT="$ROOT" SOURCE="$source" python3 "$ROOT/scripts/generate_logo.py"
}

main "$@"
