"""Teste de silhuetas únicas (regra global de formas). Sai com código 1 se falhar.

Para cada personagem/fase com corpo geométrico (grades em tools/sprite_grids/),
mede a partir da própria silhueta (pixels do corpo, sem partes):
- CANTOS: máximos locais do perfil radial r(θ) a partir do centro de massa;
- ROTAÇÃO: maior n em 1..12 com r(θ + 360/n) ≈ r(θ);
- ESPELHO: simetria em relação ao eixo vertical;
- CONVEXA: área / área do fecho convexo > 0,9 (estrela é côncava).
Falha se duas silhuetas tiverem a mesma assinatura (cantos, rotação, espelho,
convexa), ou se alguma for um círculo (sem cantos).
Uso: py tools/audit_shapes.py
"""

import math
import sys
from pathlib import Path

import numpy as np

GRIDS = Path(__file__).resolve().parent / "sprite_grids"
# nome da grade -> nome no jogo
BODIES = {
    "triangle": "Triângulo", "square": "Quadrado", "diamond": "Losango", "hexagon": "Hexágono",
    "octagon": "Octógono (NPC)", "boss_pentagon": "Chefe fase 1 (pentágono)",
    "boss_shield": "Chefe fase 2 (escudo)", "boss_star": "Chefe fase 3 (estrela)",
}


def mask_of(name):
    rows = [r for r in (GRIDS / f"{name}.txt").read_text(encoding="utf-8").splitlines()
            if r and not r.startswith("# ")]
    return np.array([[c not in ". " for c in r] for r in rows])


def profile(m, samples=720):
    ys, xs = np.nonzero(m)
    cy, cx = ys.mean() + 0.5, xs.mean() + 0.5
    r = np.zeros(samples)
    for i in range(samples):
        a = 2 * math.pi * i / samples
        dx, dy = math.cos(a), -math.sin(a)
        t, last = 0.0, 0.0
        while t < max(m.shape):
            x, y = int(cx + dx * t), int(cy + dy * t)
            if 0 <= y < m.shape[0] and 0 <= x < m.shape[1] and m[y, x]:
                last = t
            t += 0.25
        r[i] = last
    return r


def smooth(r, w=5):
    """Média circular de w amostras (tira o serrilhado dos pixels)."""
    return np.convolve(np.concatenate([r[-w:], r, r[:w]]), np.ones(w) / w, mode="same")[w:-w]


def corners(r):
    r = smooth(r)
    n = len(r)
    k = n // 24                                   # janela de 15°
    peaks = 0
    for i in range(n):
        win = [r[(i + d) % n] for d in range(-k, k + 1)]
        if r[i] == max(win) and r[i] > min(win) + 1.2 and r[i] > r[(i - 1) % n]:
            peaks += 1
    return peaks


def rotation_order(r):
    n = len(r)
    best = 1
    for order in range(2, 13):
        shift = n // order
        if n % order:
            continue
        if np.abs(np.roll(r, shift) - r).mean() < 0.8:
            best = order
    return best


def mirror_v(m):
    ys, xs = np.nonzero(m)
    crop = m[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    return (crop == crop[:, ::-1]).mean() > 0.97


def convex(m):
    ys, xs = np.nonzero(m)
    pts = sorted(set(zip(xs.tolist(), ys.tolist())))

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    lower, upper = [], []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)
    hull = lower[:-1] + upper[:-1]
    area = 0.0
    for i in range(len(hull)):
        x0, y0 = hull[i]
        x1, y1 = hull[(i + 1) % len(hull)]
        area += x0 * y1 - x1 * y0
    return m.sum() / max(1.0, abs(area) / 2 + len(hull) / 2) > 0.9


def main():
    seen, errors = {}, []
    for grid, label in BODIES.items():
        if not (GRIDS / f"{grid}.txt").exists():
            errors.append(f"{label}: grade {grid}.txt não existe")
            continue
        m = mask_of(grid)
        r = profile(m)
        sig = (corners(r), rotation_order(r), mirror_v(m), convex(m))
        print(f"  {label:28s} cantos={sig[0]:2d} rotação={sig[1]:2d} espelho={'sim' if sig[2] else 'não'} "
              f"{'convexa' if sig[3] else 'côncava'}")
        if sig[0] == 0:
            errors.append(f"{label}: sem cantos (círculo não é permitido)")
        if sig in seen:
            errors.append(f"{label} tem a mesma silhueta que {seen[sig]} {sig}")
        seen[sig] = label
    for e in errors:
        print("  ERRO:", e)
    print("SILHUETAS ÚNICAS:", "PASS" if not errors else "FAIL")
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
