#!/usr/bin/env python3
"""Раскладка логотипа по ресурсам приложения.

Запускается из `scripts/generate-logo.sh` — там же описано, зачем это нужно
и что получается на выходе. Здесь только арифметика размеров.

Единственная неочевидная часть — безопасная зона адаптивной иконки. Оболочка
обрезает иконку маской своей формы: круг на Pixel, «сквиркл» на Samsung,
скруглённый квадрат ещё где-то. Гарантированно видны лишь центральные 66 из
108 dp; всё остальное — поле, которое может исчезнуть. Поэтому знак вписывается
в 66 dp, а не в холст целиком.
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(os.environ["ROOT"])
SOURCE = Path(os.environ["SOURCE"])

# Плотности Android и множитель dp → px для каждой.
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

# Адаптивная иконка: холст 108 dp, безопасная зона 66 dp (Android docs).
ADAPTIVE_CANVAS_DP = 108
ADAPTIVE_SAFE_DP = 66

# Знак в интерфейсе: базовый размер 24 dp, как у значков Material.
LOGO_DP = 24

# Крупный знак: последний слайд онбординга, 120 dp.
#
# Отдельный файл, а не тот же ic_yta_logo покрупнее: в шапке списка профилей
# знак стоит в 28 dp, и держать ради него в памяти растр под 120 dp незачем.
# Обратное — растянуть 24-точечный знак до 120 dp — и давало ту самую муть
# (замечание 1 полевой проверки 2026-08-05).
LARGE_LOGO_DP = 120

# Единственный файл логотипа не в PNG: 480×480 PNG весит 177 КБ, WebP того же
# качества — 43 КБ. Разница между ними на глаз не видна (среднеквадратичное
# отклонение 1,4 из 255 при пиковом 15), а четверть веса — видна в APK.
# minSdk 26 берёт WebP с прозрачностью без оговорок.
LARGE_LOGO_QUALITY = 90

# Картинка для Play Console: строго 512×512, без прозрачности.
STORE_SIZE = 512

# Допуск заливки фона. Исходник — JPEG, и ровного цвета в нём нет: сжатие
# оставляет разброс в несколько единиц на канал даже на плоской заливке.
BACKGROUND_TOLERANCE = 24

# Маркер, которым заливается фон перед переводом в прозрачность. Маджента
# выбрана как заведомо отсутствующая в логотипе: спутать её с лепестком
# невозможно, а значит, невозможно и выесть кусок самого знака.
MARKER = (255, 0, 255)

APP_RES = ROOT / "app" / "src" / "main" / "res"
DESIGN_RES = ROOT / "core" / "designsystem" / "src" / "main" / "res"
STORE_DIR = ROOT / "docs" / "store"

# Векторные версии, на смену которым приходят растровые. Оставить их значит
# получить два ресурса с одним именем — сборка на этом и падает.
OBSOLETE = [
    APP_RES / "drawable" / "ic_launcher_foreground.xml",
    APP_RES / "drawable" / "ic_launcher_monochrome.xml",
    DESIGN_RES / "drawable" / "ic_yta_logo.xml",
]


def load_artwork() -> tuple[Image.Image, tuple[int, int, int]]:
    """Знак без фона и цвет самого фона.

    Фон убирается заливкой от четырёх углов, а не порогом по цвету: порог
    выел бы и светлые части самого знака, если они окажутся близки к фону.
    Заливка же идёт только по связной области, начинающейся за пределами
    рисунка.
    """
    image = Image.open(SOURCE).convert("RGB")
    background = image.getpixel((0, 0))

    filled = image.copy()
    for corner in ((0, 0), (image.width - 1, 0), (0, image.height - 1), (image.width - 1, image.height - 1)):
        ImageDraw.floodfill(filled, corner, MARKER, thresh=BACKGROUND_TOLERANCE)

    rgba = filled.convert("RGBA")
    pixels = rgba.load()
    for y in range(rgba.height):
        for x in range(rgba.width):
            if pixels[x, y][:3] == MARKER:
                pixels[x, y] = (0, 0, 0, 0)

    bbox = rgba.getbbox()
    if bbox is None:
        raise SystemExit("После снятия фона ничего не осталось — проверьте исходник")
    return rgba.crop(bbox), background


def square(image: Image.Image) -> Image.Image:
    """Знак в квадрате: иконки квадратные, а лотос шире, чем выше."""
    side = max(image.width, image.height)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(image, ((side - image.width) // 2, (side - image.height) // 2), image)
    return canvas


def write(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "PNG", optimize=True)
    print(f"  {path.relative_to(ROOT)}  {image.width}×{image.height}")


def write_webp(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "WEBP", quality=LARGE_LOGO_QUALITY)
    print(f"  {path.relative_to(ROOT)}  {image.width}×{image.height}")


def adaptive_layer(art: Image.Image, scale: float) -> Image.Image:
    """Слой адаптивной иконки: знак в безопасной зоне на прозрачном холсте."""
    canvas_px = round(ADAPTIVE_CANVAS_DP * scale)
    safe_px = round(ADAPTIVE_SAFE_DP * scale)
    canvas = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    fitted = art.resize((safe_px, safe_px), Image.LANCZOS)
    offset = (canvas_px - safe_px) // 2
    canvas.paste(fitted, (offset, offset), fitted)
    return canvas


def silhouette(layer: Image.Image) -> Image.Image:
    """Монохромный слой: чёрный силуэт по альфе.

    Тематические иконки Android 13+ перекрашиваются системой, и цвет здесь
    роли не играет — важна только форма. Полупрозрачные края альфы остаются
    полупрозрачными: иначе силуэт получает зубцы.
    """
    black = Image.new("RGBA", layer.size, (0, 0, 0, 255))
    black.putalpha(layer.getchannel("A"))
    return black


def main() -> None:
    print(f"Исходник: {SOURCE.relative_to(ROOT) if SOURCE.is_relative_to(ROOT) else SOURCE}")
    art, background = load_artwork()
    art = square(art)
    print(f"Знак: {art.width}×{art.height}, фон #{background[0]:02X}{background[1]:02X}{background[2]:02X}")

    print("Иконка запуска:")
    for density, scale in DENSITIES.items():
        layer = adaptive_layer(art, scale)
        write(layer, APP_RES / f"mipmap-{density}" / "ic_launcher_foreground.png")
        write(silhouette(layer), APP_RES / f"mipmap-{density}" / "ic_launcher_monochrome.png")

    print("Знак в интерфейсе:")
    for density, scale in DENSITIES.items():
        size = round(LOGO_DP * scale)
        write(art.resize((size, size), Image.LANCZOS), DESIGN_RES / f"drawable-{density}" / "ic_yta_logo.png")

    # Полная лесенка плотностей, а не один файл под xxxhdpi: система умеет
    # уменьшать сама, но тогда телефон mdpi держит в памяти вчетверо больший
    # растр, чем ему нужно. Lint того же мнения и считает неполный набор
    # ошибкой (IconDensities).
    print("Крупный знак (онбординг):")
    for density, scale in DENSITIES.items():
        size = round(LARGE_LOGO_DP * scale)
        write_webp(
            art.resize((size, size), Image.LANCZOS),
            DESIGN_RES / f"drawable-{density}" / "ic_yta_logo_large.webp",
        )

    print("Play Console:")
    store = Image.new("RGB", (STORE_SIZE, STORE_SIZE), background)
    # Поле в 12% — рекомендация Play: иконка не должна упираться в края.
    inner = round(STORE_SIZE * 0.76)
    fitted = art.resize((inner, inner), Image.LANCZOS)
    store.paste(fitted, ((STORE_SIZE - inner) // 2, (STORE_SIZE - inner) // 2), fitted)
    write(store, STORE_DIR / "ic_launcher-512.png")

    print("Цвет подложки иконки:")
    colors = APP_RES / "values" / "colors.xml"
    text = colors.read_text(encoding="utf-8")
    hex_color = f"#{background[0]:02X}{background[1]:02X}{background[2]:02X}"
    import re

    # Считается совпадение, а не изменение текста: при повторном прогоне цвет
    # уже верный, замена ничего не меняет — и сравнение «стало ≠ было» ругалось
    # на пустом месте.
    updated, replaced = re.subn(
        r'(<color name="ic_launcher_background">)[^<]*(</color>)',
        rf"\g<1>{hex_color}\g<2>",
        text,
    )
    if replaced == 0:
        print(f"  ! ic_launcher_background не найден в {colors.relative_to(ROOT)}", file=sys.stderr)
    else:
        colors.write_text(updated, encoding="utf-8")
        print(f"  {colors.relative_to(ROOT)}  {hex_color}")

    removed = [path for path in OBSOLETE if path.exists()]
    if removed:
        print("Убраны векторные версии (имена заняты растровыми):")
        for path in removed:
            path.unlink()
            print(f"  {path.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
