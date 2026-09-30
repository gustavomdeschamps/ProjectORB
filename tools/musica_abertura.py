"""Música da abertura (rodada 3, etapa 4): 40 s, sintetizada, com os impactos
nos cortes do roteiro. Gera assets/audio/intro_music.wav (44,1 kHz, mono, 16 bits).

Linha do tempo (a mesma de IntroScreen.CUES):
  0-6    plano 1: mundo em paz (pad suave + arpejo em Lá lídio)
  6      rachadura (sino desafinado)
  6-10   plano 2: a música falha (gagueja, o tom cai), chiado subindo
  10     o Rift abre: estrondo
  12-20  plano 3: ostinato tenso em Ré menor; impacto em 12, 14, 16, 18 (cortes)
  20-27  plano 4: drone grave do chefe; impacto em 23,5 (olhos acendem)
  27-34  plano 5: caixinha de música (esperança); cristal racha em 29,5,
         ORB nasce em 30,5, Pi aparece em 32
  34-40  plano 6: tambores crescendo; 4 impactos na montagem do logo
         (36, 36,5, 37, 37,5) e acorde final em 38 até 40
"""
from __future__ import annotations

import wave
from pathlib import Path

import numpy as np

RATE = 44100
DUR = 40.0
OUT = Path(__file__).resolve().parents[1] / "assets/audio/intro_music.wav"
rng = np.random.default_rng(2026)
N = int(RATE * DUR)
t = np.arange(N) / RATE
mix = np.zeros(N)


def note(freq):
    return freq


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def env(n, a=0.01, r=0.3, sustain=1.0):
    e = np.ones(n) * sustain
    na, nr = max(1, int(a * RATE)), max(1, int(r * RATE))
    e[:na] = np.linspace(0, sustain, na)
    if nr < n:
        e[-nr:] = np.linspace(sustain, 0, nr)
    return e


def osc(kind, f, n, phase=0.0):
    x = (np.arange(n) / RATE) * f + phase
    if kind == "sin":
        return np.sin(2 * np.pi * x)
    if kind == "tri":
        return 2 * np.abs(2 * (x - np.floor(x + 0.5))) - 1
    if kind == "sq":
        return np.where((x % 1) < 0.5, 1.0, -1.0)
    if kind == "pulse":
        return np.where((x % 1) < 0.25, 1.0, -1.0)
    raise ValueError(kind)


def add(start, sig, gain=1.0):
    i = int(start * RATE)
    j = min(N, i + len(sig))
    if i < N:
        mix[i:j] += sig[:j - i] * gain


def tone(start, dur, midi, kind="tri", gain=0.2, a=0.01, r=0.2, vib=0.0):
    n = int(dur * RATE)
    f = hz(midi)
    if vib:
        x = np.arange(n) / RATE
        ph = np.cumsum(f * (1 + vib * np.sin(2 * np.pi * 5 * x))) / RATE
        sig = 2 * np.abs(2 * (ph - np.floor(ph + 0.5))) - 1 if kind == "tri" else np.sin(2 * np.pi * ph)
    else:
        sig = osc(kind, f, n)
    add(start, sig * env(n, a, r), gain)


def noise(start, dur, gain=0.2, a=0.005, r=0.2, lp=0.0):
    n = int(dur * RATE)
    s = rng.uniform(-1, 1, n)
    if lp > 0:
        y = np.zeros(n)
        acc = 0.0
        for k in range(n):
            acc += lp * (s[k] - acc)
            y[k] = acc
        s = y / max(1e-6, np.abs(y).max())
    add(start, s * env(n, a, r), gain)


def kick(start, gain=0.9):
    n = int(0.35 * RATE)
    x = np.arange(n) / RATE
    f = 120 * np.exp(-x * 18) + 40
    ph = np.cumsum(f) / RATE
    add(start, np.sin(2 * np.pi * ph) * np.exp(-x * 8), gain)


def impact(start, gain=1.0):
    """Impacto de corte: bumbo + chiado + acorde curto."""
    kick(start, 0.9 * gain)
    noise(start, 0.25, 0.35 * gain, 0.002, 0.2, lp=0.25)
    for m in (38, 45, 50):
        tone(start, 0.35, m, "sq", 0.06 * gain, 0.002, 0.3)


def boom(start):
    n = int(2.2 * RATE)
    x = np.arange(n) / RATE
    f = 70 * np.exp(-x * 1.2) + 28
    ph = np.cumsum(f) / RATE
    add(start, np.sin(2 * np.pi * ph) * np.exp(-x * 1.4), 0.9)
    noise(start, 1.6, 0.4, 0.002, 1.3, lp=0.08)


# ---- plano 1 (0-6): Lá lídio, pad + arpejo suave
pad = [(57, 61, 64, 68), (59, 63, 66, 69)]
for k in range(3):
    chord = pad[k % 2]
    for m in chord:
        tone(k * 2.0, 2.2, m, "tri", 0.045, 0.6, 0.8, vib=0.004)
arp = [69, 73, 76, 80, 76, 73]
for i in range(24):
    tone(0.25 * i, 0.22, arp[i % len(arp)], "sq", 0.025, 0.005, 0.15)

# ---- 6: rachadura (sino desafinado) e 6-10: a música falha
tone(6.0, 1.4, 88, "sin", 0.12, 0.002, 1.2)
tone(6.0, 1.4, 88.4, "sin", 0.08, 0.002, 1.2)
for i in range(16):
    st = 6.0 + 0.25 * i
    detune = -i * 0.35                        # o tom cai
    if i % 4 == 3:
        continue                               # buracos: a música falha
    dur = 0.22 if i % 3 else 0.06              # gagueja
    tone(st, dur, arp[i % len(arp)] + detune, "sq", 0.022, 0.005, 0.05)
noise(7.0, 3.0, 0.08, 2.5, 0.2)
# gagueira: repete um pedaço curto do pad (8.0-9.5)
seg = mix[int(1.0 * RATE):int(1.12 * RATE)].copy()
for k in range(10):
    add(8.0 + k * 0.14, seg * (1 - k / 12), 1.0)

# ---- 10: o Rift abre
boom(10.0)
for m in (26, 33, 38):
    tone(10.0, 2.0, m, "sq", 0.05, 0.01, 1.5)

# ---- plano 3 (12-20): ostinato em Ré menor + impactos nos cortes
bass = [38, 38, 41, 38, 43, 38, 41, 36]
for i in range(64):
    st = 12.0 + 0.125 * i
    tone(st, 0.11, bass[i % len(bass)], "pulse", 0.07, 0.003, 0.05)
for k, cut in enumerate([12.0, 14.0, 16.0, 18.0]):
    impact(cut, 1.0)
    # "estala" da corrupção 1,2 s depois de cada corte
    noise(cut + 1.2, 0.15, 0.25, 0.001, 0.12, lp=0.5)
    tone(cut + 1.2, 0.5, 74 - k, "sq", 0.05, 0.002, 0.4)

# ---- plano 4 (20-27): drone grave do chefe
for m in (26, 33):
    tone(20.0, 7.0, m, "tri", 0.10, 1.5, 1.5, vib=0.01)
noise(20.0, 7.0, 0.05, 2.0, 2.0, lp=0.03)
impact(23.5, 1.3)
tone(23.5, 2.5, 62, "sq", 0.05, 0.01, 2.0)
tone(23.5, 2.5, 63, "sq", 0.05, 0.01, 2.0)

# ---- plano 5 (27-34): caixinha de música
box = [76, 79, 83, 88, 83, 79, 81, 84, 88, 93, 88, 84]
for i in range(26):
    tone(27.0 + 0.26 * i, 0.4, box[i % len(box)], "sin", 0.07, 0.003, 0.35)
for m in (52, 59, 64):
    tone(27.0, 7.0, m, "tri", 0.035, 1.0, 1.5)
noise(29.5, 0.2, 0.25, 0.001, 0.18, lp=0.6)           # cristal racha
tone(29.5, 0.3, 96, "sin", 0.08, 0.001, 0.25)
for k, m in enumerate([84, 88, 91, 96]):              # ORB nasce
    tone(30.5 + 0.06 * k, 0.6, m, "sin", 0.08, 0.002, 0.5)
for k, m in enumerate([79, 83, 86]):                  # Pi aparece
    tone(32.0 + 0.09 * k, 0.4, m, "tri", 0.07, 0.002, 0.3)

# ---- plano 6 (34-40): tambores crescendo, logo, acorde final
for i in range(16):
    st = 34.0 + 0.125 * i
    kick(st, 0.25 + 0.04 * i)
    noise(st + 0.0625, 0.05, 0.05 + 0.01 * i, 0.001, 0.04)
for k, st in enumerate([36.0, 36.5, 37.0, 37.5]):
    impact(st, 0.9 + 0.1 * k)
    tone(st, 0.4, [62, 65, 69, 74][k], "sq", 0.07, 0.002, 0.3)
for m in (50, 57, 62, 66, 69, 74):
    tone(38.0, 2.0, m, "tri", 0.06, 0.01, 1.2)
    tone(38.0, 2.0, m + 12, "sq", 0.015, 0.01, 1.2)
kick(38.0, 1.0)

# ---- master: normaliza e grava
peak = np.abs(mix).max()
mix = np.tanh(mix / peak * 1.6) * 0.85
pcm = (mix * 32767).astype("<i2")
OUT.parent.mkdir(parents=True, exist_ok=True)
with wave.open(str(OUT), "wb") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(RATE)
    w.writeframes(pcm.tobytes())
print("gerado", OUT.name, f"{len(pcm) / RATE:.1f}s")
