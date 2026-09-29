"""Sintetiza os sons curtos que o projeto não tinha (sem dependências).

Gera, em assets/audio/:
- blip.wav      : som por letra do diálogo (onda quadrada suave, 28 ms)
- sting_rift.wav: abertura do Rift na abertura animada (varredura + ruído)
- sting_title.wav: montagem do título (arpejo curto)

Reproduzível: sem aleatoriedade não semeada. Uso: python tools/build_orb_audio.py
"""

import math
import random
import struct
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "assets" / "audio"
RATE = 22050


def write(name, samples):
    OUT.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT / name), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in samples))
    print("gerado", name, f"{len(samples) / RATE:.3f}s")


def envelope(i, n, attack=0.1, release=0.6):
    t = i / n
    if t < attack:
        return t / attack
    if t > 1 - release:
        return max(0.0, (1 - t) / release)
    return 1.0


def blip():
    n = int(RATE * 0.028)
    out = []
    for i in range(n):
        f = 880 - 220 * i / n
        sq = 1.0 if math.sin(2 * math.pi * f * i / RATE) >= 0 else -1.0
        out.append(0.18 * sq * envelope(i, n, 0.05, 0.7))
    return out


def sting_rift():
    rnd = random.Random(29)
    n = int(RATE * 1.4)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / n
        f = 70 + 900 * t * t
        phase += 2 * math.pi * f / RATE
        tone = math.sin(phase) * 0.35 + (1.0 if math.sin(phase * 0.5) > 0 else -1.0) * 0.08
        noise = (rnd.random() * 2 - 1) * 0.25 * t
        out.append((tone + noise) * envelope(i, n, 0.05, 0.35))
    return out


def sting_title():
    notes = [523.25, 659.25, 783.99, 1046.5]  # dó-mi-sol-dó
    out = []
    for k, f in enumerate(notes):
        n = int(RATE * (0.11 if k < 3 else 0.45))
        for i in range(n):
            sq = 1.0 if math.sin(2 * math.pi * f * i / RATE) >= 0 else -1.0
            tri = 2 / math.pi * math.asin(math.sin(2 * math.pi * f * i / RATE))
            out.append((0.10 * sq + 0.18 * tri) * envelope(i, n, 0.02, 0.5 if k < 3 else 0.8))
    return out


if __name__ == "__main__":
    write("blip.wav", blip())
    write("sting_rift.wav", sting_rift())
    write("sting_title.wav", sting_title())
