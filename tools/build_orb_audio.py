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


# ------------------------------------------------ rodada 3 (etapa 3)
# Feedback de jogo no lugar de texto na tela.

def square(f, i):
    return 1.0 if math.sin(2 * math.pi * f * i / RATE) >= 0 else -1.0


def tri(f, i):
    return 2 / math.pi * math.asin(math.sin(2 * math.pi * f * i / RATE))


def shatter():
    """Cristal partindo: estalo agudo + cacos (notas descendo) + chiado curto."""
    rnd = random.Random(7)
    out = []
    n = int(RATE * 0.03)
    for i in range(n):
        out.append((rnd.random() * 2 - 1) * 0.5 * (1 - i / n))
    for f in [2093.0, 1760.0, 1396.9, 1174.7]:
        n = int(RATE * 0.045)
        for i in range(n):
            out.append((0.16 * square(f, i) + 0.14 * tri(f * 2, i)) * envelope(i, n, 0.02, 0.8)
                       + (rnd.random() * 2 - 1) * 0.04 * (1 - i / n))
    return out


def miss():
    """Tiro no ponto errado: baque grave e curto, sem melodia."""
    rnd = random.Random(11)
    n = int(RATE * 0.16)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / n
        phase += 2 * math.pi * (160 - 90 * t) / RATE
        out.append((math.sin(phase) * 0.45 + (rnd.random() * 2 - 1) * 0.12 * (1 - t)) * envelope(i, n, 0.02, 0.7))
    return out


def stair_rumble():
    """Blocos da escadinha se rearranjando (1,25 s): ronco grave com batidas."""
    rnd = random.Random(19)
    n = int(RATE * 1.25)
    out = []
    lp = 0.0
    for i in range(n):
        t = i / n
        lp += 0.04 * ((rnd.random() * 2 - 1) - lp)
        knock = 0.0
        k = (i % int(RATE * 0.11)) / (RATE * 0.11)
        if k < 0.25:
            knock = math.sin(2 * math.pi * 90 * i / RATE) * (1 - k / 0.25) * 0.5
        out.append((lp * 1.8 + knock) * envelope(i, n, 0.08, 0.3) * 0.8)
    return out


def stair_open():
    """Fim do rearranjo: baque e um acorde curto."""
    out = miss()[: int(RATE * 0.08)]
    for f in [392.0, 587.3]:
        n = int(RATE * 0.12)
        for i in range(n):
            out.append((0.12 * square(f, i) + 0.12 * tri(f, i)) * envelope(i, n, 0.02, 0.8))
    return out


def ui_move():
    n = int(RATE * 0.03)
    return [0.14 * square(1318.5, i) * envelope(i, n, 0.05, 0.8) for i in range(n)]


def ui_confirm():
    out = []
    for f, d in [(987.8, 0.04), (1318.5, 0.08)]:
        n = int(RATE * d)
        out += [(0.12 * square(f, i) + 0.10 * tri(f, i)) * envelope(i, n, 0.03, 0.7) for i in range(n)]
    return out


def callout():
    """Momento raro (ex.: chefe vencido): três notas subindo e um brilho."""
    out = []
    for k, f in enumerate([659.25, 880.0, 1318.5]):
        n = int(RATE * (0.08 if k < 2 else 0.35))
        out += [(0.10 * square(f, i) + 0.16 * tri(f, i)) * envelope(i, n, 0.02, 0.6 if k < 2 else 0.85)
                for i in range(n)]
    return out


if __name__ == "__main__":
    write("blip.wav", blip())
    write("sting_rift.wav", sting_rift())
    write("sting_title.wav", sting_title())
    write("shatter.wav", shatter())
    write("miss.wav", miss())
    write("stair_rumble.wav", stair_rumble())
    write("stair_open.wav", stair_open())
    write("ui_move.wav", ui_move())
    write("ui_confirm.wav", ui_confirm())
    write("callout.wav", callout())
