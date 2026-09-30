"""Ponto de partida das grades dos inimigos (Fase 2). NÃO é chamado pelo gerador.

Rasteriza a silhueta de cada forma a partir da MESMA geometria usada nos
acertos (EnemyType: lados, ângulo inicial, raio externo), com contorno preto
de 2-3 px, faixa clara nas arestas voltadas para cima/esquerda, faixa escura
nas voltadas para baixo/direita e um brilho no canto superior esquerdo.

O resultado vai para tools/sprite_grids/<nome>.txt como grade de texto por
papel de cor. A partir daí as grades são editadas à mão e o gerador
(build_orb_assets.py) lê SÓ as grades. Rodar de novo sobrescreve edições:
    py tools/seed_enemy_grids.py --force
"""

import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_orb_assets import m_poly, poly  # noqa: E402

OUT = Path(__file__).resolve().parent / "sprite_grids"

#          canvas lados início raio-ext contorno
SHAPES = {
    "triangle": (73, 3, 90.0, 30.0, 2),
    "square": (73, 4, 45.0, 25 / math.cos(math.pi / 4), 2),
    "diamond": (73, 4, 0.0, 30.0, 2),
    "hexagon": (73, 6, 90.0, 30.0, 2),
}
BOSS = (113, 6, 90.0, 52.5, 3)


def erode(m, n):
    out = m.copy()
    for _ in range(n):
        p = np.pad(out, 1, constant_values=False)
        e = out.copy()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            e &= p[1 + dy:1 + dy + m.shape[0], 1 + dx:1 + dx + m.shape[1]]
        out = e
    return out


def seed(name, S, n, start, ro, thick):
    c = S / 2
    pts = poly(c, ro, n, start)
    body = m_poly(S, S, pts)
    inner = erode(body, thick)
    g = np.full((S, S), ".", dtype="<U1")
    g[body] = "K"
    g[inner] = "B"
    # faixas por aresta: normal para fora de cada aresta decide clara/escura
    yy, xx = np.mgrid[0:S, 0:S] + 0.5
    for i in range(n):
        (x0, y0), (x1, y1) = pts[i], pts[(i + 1) % n]
        ex, ey = x1 - x0, y1 - y0
        ln = math.hypot(ex, ey)
        nx, ny = ey / ln, -ex / ln                       # normal (y para baixo)
        if (c - x0) * nx + (c - y0) * ny > 0:
            nx, ny = -nx, -ny                            # aponta para fora
        d = -((xx - x0) * nx + (yy - y0) * ny)           # distância para dentro
        light = -(nx + ny) / math.sqrt(2)                # para cima/esquerda
        band = inner & (d < thick + 3.5)
        if light > 0.3:
            g[band & (g == "B")] = "L"
        elif light < -0.3:
            g[band & (g == "B")] = "D"
    # brilho: bloco junto ao ponto mais "superior esquerdo" do miolo (fora
    # da faixa clara): a luz vem do canto superior esquerdo
    ys, xs = np.nonzero(inner & (g == "B"))
    k = int(np.argmin(xs + ys))
    hx, hy = int(xs[k]), int(ys[k])
    for dy, row in enumerate(["GHH.", "HHHH", ".HH."]):
        for dx, ch in enumerate(row):
            if ch != "." and hy + dy < S and hx + dx < S and inner[hy + dy, hx + dx]:
                g[hy + dy, hx + dx] = ch
    return "\n".join("".join(r) for r in g)


def line_cells(x0, y0, x1, y1):
    n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
    return {(int(x0 + (x1 - x0) * t / n), int(y0 + (y1 - y0) * t / n)) for t in range(n + 1)}


def seed_boss(cracked):
    """Carapaça do chefe com simetria de 60° (o giro reaproveita os quadros):
    bisel claro igual em todas as arestas, 6 facetas escuras do centro aos
    vértices, e em cada aresta os mesmos brilhos multicoloridos (1 menta,
    2 gelo, 4 orquídea). A luz direcional fica na camada do rosto, que não gira.
    Variante rachada (fase furiosa): de cada vértice sai a mesma trinca."""
    S, n, start, ro, thick = BOSS
    c = S / 2
    pts = poly(c, ro, n, start)
    body = m_poly(S, S, pts)
    inner = erode(body, thick)
    g = np.full((S, S), ".", dtype="<U1")
    g[body] = "K"
    g[inner] = "B"
    band = inner & ~erode(inner, 3)
    g[band] = "L"
    bevel = erode(inner, 3) & ~erode(inner, 4)
    g[bevel] = "D"
    for (vx, vy) in pts:                                 # facetas
        for (x, y) in line_cells(c, c, c + (vx - c) * 0.86, c + (vy - c) * 0.86):
            if inner[y, x] and g[y, x] == "B":
                g[y, x] = "D"
    for i in range(n):                                   # brilhos por aresta
        (x0, y0), (x1, y1) = pts[i], pts[(i + 1) % n]
        for t, ch in ((0.3, "1"), (0.5, "2"), (0.7, "4")):
            px = c + (x0 + (x1 - x0) * t - c) * 0.80
            py = c + (y0 + (y1 - y0) * t - c) * 0.80
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                g[int(py) + dy - 1, int(px) + dx - 1] = ch
    if cracked:
        # trinca em zigue-zague do vértice para dentro (mesma em todos)
        zig = [(0.95, 0.0), (0.85, 3.0), (0.74, -2.0), (0.64, 3.5), (0.55, 0.0)]
        for k in range(n):
            ang = math.radians(start + k * 360 / n)
            ux, uy = math.cos(ang), -math.sin(ang)
            px_, py_ = -uy, ux
            cells = [(c + ux * ro * f + px_ * o, c + uy * ro * f + py_ * o) for f, o in zig]
            for (a0, b0), (a1, b1) in zip(cells, cells[1:]):
                for (x, y) in line_cells(a0, b0, a1, b1):
                    if inner[y, x]:
                        g[y, x] = "K"
    return "\n".join("".join(r) for r in g)


def main():
    OUT.mkdir(exist_ok=True)
    force = "--force" in sys.argv
    for name, (S, n, start, ro, thick) in SHAPES.items():
        path = OUT / f"{name}.txt"
        if path.exists() and not force:
            print(f"{path.name}: já existe (use --force para sobrescrever)")
            continue
        path.write_text(seed(name, S, n, start, ro, thick) + "\n", encoding="utf-8")
        print(f"{path.name}: semeada")
    for name, cracked in (("boss_shell", False), ("boss_shell_cracked", True)):
        path = OUT / f"{name}.txt"
        if path.exists() and not force:
            print(f"{path.name}: já existe (use --force para sobrescrever)")
            continue
        path.write_text(seed_boss(cracked) + "\n", encoding="utf-8")
        print(f"{path.name}: semeada")


if __name__ == "__main__":
    main()
