#!/usr/bin/env python3
"""Синтез звуковых пресетов оповещений (Фаза 4, docs/07-AUDIO-ASSETS.md).

Ассеты не скачиваются, а порождаются этим скриптом: у сгенерированного звука
нет правообладателя, кроме проекта, и нет риска, что лицензия CC0 окажется
проставленной по ошибке (P1-8). Результат детерминирован — один и тот же
коммит скрипта даёт побитово те же файлы, поэтому их не нужно ревьюить на слух
при каждом изменении сборки.

Запуск:  python3 scripts/generate-alert-sounds.py
Выход:   core/audio/src/main/res/raw/alert_*.ogg  (Vorbis, 44.1 кГц, моно)

Требуется ffmpeg с libvorbis — только для упаковки; сам синтез на stdlib.
"""

from __future__ import annotations

import math
import struct
import subprocess
import sys
import tempfile
import wave
from dataclasses import dataclass, field
from pathlib import Path

SAMPLE_RATE = 44_100
OUTPUT_DIR = Path(__file__).resolve().parent.parent / "core/audio/src/main/res/raw"

# Хвост, который тише этого порога, обрезается: он не слышен, но занимает место.
SILENCE_FLOOR = 0.0006


@dataclass(frozen=True)
class Partial:
    """Одна составляющая тембра.

    ratio      — отношение к основному тону. Нецелые значения дают призвуки,
                 из-за которых металл звучит металлом, а не органом.
    amplitude  — вклад в сумму до нормализации.
    decay      — во сколько раз быстрее основного тона затухает эта составляющая.
                 Высокие призвуки в реальном металле гаснут первыми: именно это
                 превращает удар в «гонг», а не в «писк».
    detune_hz  — расстройка второго голоса той же частоты. Даёт биения, от
                 которых звук перестаёт быть синтетически ровным.
    """

    ratio: float
    amplitude: float
    decay: float = 1.0
    detune_hz: float = 0.0


@dataclass(frozen=True)
class Voice:
    name: str
    base_hz: float
    partials: list[Partial]
    duration_s: float
    decay_s: float
    attack_s: float = 0.004
    peak: float = 0.9
    noise_burst_s: float = 0.0
    tremolo_hz: float = 0.0
    tremolo_depth: float = 0.0
    comment: str = field(default="")


VOICES = [
    Voice(
        name="alert_soft_gong",
        comment="Старт и конец этапа в стандартной схеме — мягко, но слышно через зал",
        base_hz=196.0,
        partials=[
            Partial(1.00, 1.00, decay=0.75, detune_hz=0.5),
            Partial(2.01, 0.55, decay=1.10),
            Partial(2.98, 0.32, decay=1.45, detune_hz=0.9),
            Partial(4.07, 0.20, decay=1.90),
            Partial(5.31, 0.12, decay=2.40),
            Partial(6.93, 0.07, decay=3.20),
        ],
        duration_s=3.2,
        decay_s=1.15,
        attack_s=0.012,
        peak=0.90,
    ),
    Voice(
        name="alert_singing_bowl",
        comment="Шавасана и медитация: долгий вход без удара, с живыми биениями",
        base_hz=272.0,
        partials=[
            Partial(1.00, 1.00, decay=0.60, detune_hz=1.4),
            Partial(2.33, 0.42, decay=0.85, detune_hz=2.1),
            Partial(3.52, 0.22, decay=1.20),
            Partial(4.97, 0.11, decay=1.70),
        ],
        duration_s=4.5,
        decay_s=1.90,
        attack_s=0.055,
        peak=0.78,
        tremolo_hz=1.6,
        tremolo_depth=0.16,
    ),
    Voice(
        name="alert_bell",
        comment="Пресет «Максимум»: яркая атака, читается поверх музыки",
        base_hz=523.0,
        partials=[
            Partial(0.50, 0.30, decay=0.70),
            Partial(1.00, 1.00, decay=0.80),
            Partial(1.19, 0.60, decay=1.05, detune_hz=0.7),
            Partial(1.56, 0.36, decay=1.30),
            Partial(2.00, 0.24, decay=1.60),
            Partial(2.66, 0.14, decay=2.10),
            Partial(3.01, 0.09, decay=2.60),
        ],
        duration_s=2.2,
        decay_s=0.62,
        attack_s=0.003,
        peak=0.90,
    ),
    Voice(
        name="alert_tone",
        comment="Нейтральный короткий сигнал для тех, кому колокол мешает",
        base_hz=880.0,
        partials=[
            Partial(1.00, 1.00, decay=1.00),
            Partial(2.00, 0.10, decay=1.40),
        ],
        duration_s=0.42,
        decay_s=0.16,
        attack_s=0.006,
        peak=0.80,
    ),
    Voice(
        name="alert_tick",
        comment="Отсчёт последних секунд: сухой щелчок, на этапах отдыха выключен (B-9)",
        base_hz=1_320.0,
        partials=[
            Partial(1.00, 1.00, decay=1.00),
            Partial(2.40, 0.35, decay=1.60),
        ],
        duration_s=0.09,
        decay_s=0.022,
        attack_s=0.001,
        peak=0.55,
        noise_burst_s=0.004,
    ),
]


def render(voice: Voice) -> list[float]:
    """Аддитивный синтез: сумма затухающих синусоид под общей огибающей."""
    total = int(voice.duration_s * SAMPLE_RATE)
    samples = [0.0] * total
    noise = _noise(int(voice.noise_burst_s * SAMPLE_RATE))

    for index in range(total):
        t = index / SAMPLE_RATE
        # Атака сглажена экспонентой: мгновенный старт даёт щелчок,
        # который на громкости зала слышен отчётливее самого сигнала.
        attack = 1.0 - math.exp(-t / voice.attack_s)
        value = 0.0

        for partial in voice.partials:
            envelope = math.exp(-t / (voice.decay_s / partial.decay))
            if envelope < SILENCE_FLOOR:
                continue
            frequency = voice.base_hz * partial.ratio
            value += partial.amplitude * envelope * math.sin(2 * math.pi * frequency * t)
            if partial.detune_hz:
                value += (
                    partial.amplitude
                    * envelope
                    * math.sin(2 * math.pi * (frequency + partial.detune_hz) * t)
                ) * 0.5

        if voice.tremolo_depth:
            value *= 1.0 - voice.tremolo_depth * (1.0 - math.cos(2 * math.pi * voice.tremolo_hz * t)) / 2

        if index < len(noise):
            value += noise[index] * math.exp(-t / max(voice.noise_burst_s, 1e-6))

        samples[index] = value * attack

    _fade_out(samples)
    return _normalize(samples, voice.peak)


def _noise(length: int) -> list[float]:
    """Детерминированный псевдошум: щелчку нужна ширина спектра, а не случайность."""
    state = 0x2545F491
    result = []
    for _ in range(length):
        state = (1_103_515_245 * state + 12_345) & 0x7FFFFFFF
        result.append((state / 0x3FFFFFFF) - 1.0)
    return result


def _fade_out(samples: list[float], fade_s: float = 0.02) -> None:
    """Обрыв на ненулевом уровне слышен как щелчок в конце — гасим хвост."""
    fade = min(int(fade_s * SAMPLE_RATE), len(samples))
    for i in range(fade):
        samples[len(samples) - fade + i] *= 1.0 - i / fade


def _normalize(samples: list[float], peak: float) -> list[float]:
    loudest = max((abs(s) for s in samples), default=0.0)
    if loudest == 0.0:
        return samples
    scale = peak / loudest
    return [s * scale for s in samples]


def write_wav(path: Path, samples: list[float]) -> None:
    frames = b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32_767)) for s in samples)
    with wave.open(str(path), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(SAMPLE_RATE)
        out.writeframes(frames)


def encode_ogg(wav: Path, ogg: Path) -> None:
    subprocess.run(
        ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
         "-i", str(wav), "-c:a", "libvorbis", "-q:a", "4", str(ogg)],
        check=True,
    )


def main() -> int:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for voice in VOICES:
            wav = Path(tmp) / f"{voice.name}.wav"
            ogg = OUTPUT_DIR / f"{voice.name}.ogg"
            write_wav(wav, render(voice))
            encode_ogg(wav, ogg)
            print(f"{ogg.name:28} {ogg.stat().st_size / 1024:6.1f} КиБ  — {voice.comment}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
