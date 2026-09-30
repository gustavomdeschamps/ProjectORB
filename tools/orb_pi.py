"""NPC Pi (etapa D): o símbolo π de verdade, decalcado da referência.

Fonte da verdade: tools/sprite_grids/pi.txt (silhueta decalcada de
docs/ref/pi_referencia.png por limiar + redução por área). Sobre ela:
- contorno preto POR DENTRO da silhueta (ela não engorda: a forma continua a
  do π), marfim com sombra azul-petróleo, brilho no alto à esquerda;
- rosto na barra (olhos expressivos, boca simples) e braços curtos que saem
  de baixo das pontas da barra — partes escritas à mão aqui;
- animações derivadas: as pernas curvas se movem de verdade (cada perna é
  inclinada linha a linha a partir da barra), o corpo balança, pisca, fala,
  acena, aponta, comemora e se materializa com partículas.
Paleta marfim + azul-petróleo, sem amarelo e sem laranja. Amigável.
"""

from pathlib import Path

import numpy as np

GRID = Path(__file__).resolve().parent / "sprite_grids" / "pi.txt"
CANVAS = 56          # 44 de π + margem para braços e pulo
OX, OY = 6, 8        # onde o π (44x43) fica no canvas
BAR_BOTTOM = 6       # última linha da barra na grade
SPLIT_X = 21         # colunas < 21: perna esquerda; >= 21: perna direita

PALETTE = {
    "K": (22, 26, 40),      # contorno
    "I": (238, 232, 214),   # marfim
    "S": (196, 208, 204),   # marfim sombreado
    "T": (38, 112, 122),    # azul-petróleo
    "U": (18, 66, 78),      # azul-petróleo escuro
    "W": (255, 253, 246),   # brilho
    "P": (22, 26, 40),      # pupila
    "M": (120, 40, 60),     # boca por dentro
    "Z": (140, 232, 236),   # faísca da materialização
}


def silhouette():
    rows = [r for r in GRID.read_text(encoding="utf-8").splitlines() if r and not r.startswith("# ")]
    return np.array([[c == "#" for c in r] for r in rows])


# ------------------------------------------------------------ partes (à mão)
# Olhos: 2 olhos de 4x5 com 2 px de vão (bloco 10x5). "look" desloca a pupila.
EYES = {    # 2 olhos de 5x5 com 2 px de vão (bloco 12x5)
    "open": [".KKK...KKK.", "KWWWK.KWWWK", "KWPPK.KWPPK", "KWPPK.KWPPK", ".KKK...KKK."],
    "right": [".KKK...KKK.", "KWWWK.KWWWK", "KWWPK.KWWPK", "KWWPK.KWWPK", ".KKK...KKK."],
    "blink": ["...........", "...........", "KKKKK.KKKKK", ".KKK...KKK.", "..........."],
    "happy": ["...........", ".KKK...KKK.", "K...K.K...K", "...........", "..........."],
    "wide": [".KKK...KKK.", "KWWWK.KWWWK", "KWPWK.KWPWK", "KWWWK.KWWWK", ".KKK...KKK."],
}
MOUTHS = {    # 5x3, no "queixo" da barra
    "smile": ["K...K", ".KKK.", "....."],
    "talk": [".KKK.", "KMMMK", ".KKK."],
    "open": [".KKK.", "KMMMK", "KMMMK"],
    "o": ["..K..", ".KMK.", "..K.."],
}
FACE_EYES = (17, 1)    # posição na grade do π (a barra vai da linha 0 à 6)
FACE_MOUTH = (20, 6)

# Braço: ombro na parte de baixo da barra; segmentos (dx, dy) escritos à mão
# por pose; mão = luva azul-petróleo 3x3.
ARMS = {
    "rest": [(0, 1), (0, 2), (-1, 3), (-1, 4), (-2, 5), (-2, 6)],
    "rest_r": [(0, 1), (0, 2), (1, 3), (1, 4), (2, 5), (2, 6)],
    "up": [(-1, -1), (-2, -2), (-2, -3), (-3, -4), (-3, -5), (-4, -6)],
    "up_r": [(1, -1), (2, -2), (2, -3), (3, -4), (3, -5), (4, -6)],
    "wave_a": [(1, -1), (2, -2), (3, -3), (4, -4), (4, -5), (5, -6)],
    "wave_b": [(1, -1), (2, -1), (3, -2), (4, -3), (5, -3), (6, -4)],
    "point": [(1, 0), (2, 0), (3, 0), (4, 0), (5, 0), (6, 0), (7, 0)],
}
HAND = ["KKKK", "KIIK", "KISK", "KKKK"]     # luva marfim


def lean_legs(mask, left, right, lift_l=0, lift_r=0):
    """Inclina cada perna linha a linha a partir da barra (o pé anda 'left'/'right'
    pixels) e levanta o pé ('lift' px). Derivado da silhueta decalcada."""
    h, w = mask.shape
    out = np.zeros_like(mask)
    out[:BAR_BOTTOM + 1] = mask[:BAR_BOTTOM + 1]
    span = h - 1 - BAR_BOTTOM
    for y in range(BAR_BOTTOM + 1, h):
        t = (y - BAR_BOTTOM) / span
        for side, dx, lift in ((0, left, lift_l), (1, right, lift_r)):
            cols = range(0, SPLIT_X) if side == 0 else range(SPLIT_X, w)
            shift = int(round(dx * t))
            ny = y - int(round(lift * t * t))
            for x in cols:
                if mask[y, x] and 0 <= x + shift < w and 0 <= ny < h:
                    out[ny, x + shift] = True
    return out


def paint(mask):
    """Silhueta -> papéis: contorno por dentro, marfim, sombra à direita/embaixo,
    azul-petróleo na base de cada traço, brilho no alto à esquerda."""
    h, w = mask.shape
    g = np.full((h, w), ".", dtype="<U1")
    g[mask] = "I"
    pad = np.pad(mask, 1)
    edge = mask & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
    # sombra: pixel cujo vizinho da direita ou de baixo é borda
    right_edge = mask & ~pad[1:-1, 2:]
    below_edge = mask & ~pad[2:, 1:-1]
    shade = (np.roll(right_edge, -1, 1) | np.roll(below_edge, -1, 0)) & mask & ~edge
    g[shade] = "S"
    # pés/base das pernas e parte de baixo da barra em azul-petróleo
    for y in range(h):
        for x in range(w):
            if mask[y, x] and not edge[y, x] and (y >= h - 6 or y == BAR_BOTTOM - 1):
                g[y, x] = "T" if g[y, x] == "I" else "U"
    # brilho: diagonal no alto à esquerda da barra
    for (x, y) in [(10, 1), (11, 1), (12, 1), (8, 2), (9, 2), (10, 2), (6, 3), (7, 3)]:
        if mask[y, x] and not edge[y, x]:
            g[y, x] = "W"
    g[edge] = "K"
    return g


def stamp(g, part, x, y):
    for dy, row in enumerate(part):
        for dx, ch in enumerate(row):
            if ch != "." and 0 <= y + dy < g.shape[0] and 0 <= x + dx < g.shape[1]:
                g[y + dy, x + dx] = ch


def arm(g, sx, sy, segs):
    """Braço marfim com contorno preto (visível no fundo escuro) + luva."""
    core = [(sx + dx, sy + dy) for dx, dy in segs]
    for (x, y) in core:
        if 0 <= y < g.shape[0] and 0 <= x < g.shape[1] and g[y, x] == ".":
            g[y, x] = "I"
    for (x, y) in core:
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= ny < g.shape[0] and 0 <= nx < g.shape[1] and g[ny, nx] == ".":
                g[ny, nx] = "K"
    ex, ey = core[-1]
    stamp(g, HAND, ex - 1, ey - 1)


# Ombros: braços em repouso saem de baixo da barra; erguidos e apontando saem
# das pontas da barra (senão a barra os cobre).
SHOULDER = {"rest": (7, BAR_BOTTOM + 1), "rest_r": (36, BAR_BOTTOM + 1),
            "up": (2, 3), "up_r": (44, 3), "wave_a": (44, 3), "wave_b": (44, 3), "point": (44, 4)}


def frame(eyes="open", mouth="smile", dy=0, legs=(0, 0, 0, 0), arms=("rest", "rest_r"), show_arms=True):
    """Um quadro 56x56 (papéis). legs = (inclinação esq., dir., levantar esq., dir.)."""
    mask = lean_legs(silhouette(), *legs)
    body = paint(mask)
    stamp(body, EYES[eyes], *FACE_EYES)
    stamp(body, MOUTHS[mouth], *FACE_MOUTH)
    g = np.full((CANVAS, CANVAS), ".", dtype="<U1")
    h, w = body.shape
    for y in range(h):
        for x in range(w):
            if body[y, x] != "." and 0 <= y + OY + dy < CANVAS:
                g[y + OY + dy, x + OX] = body[y, x]
    if show_arms:
        for name in arms:
            sx, sy = SHOULDER[name]
            arm(g, sx + OX, sy + OY + dy, ARMS[name])
    return g


BAYER4 = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0


def anims():
    walk_legs = [(0, 0, 0, 0), (2, -1, 2, 0), (1, -1, 1, 0), (0, 0, 0, 0), (-1, 2, 0, 2), (-1, 1, 0, 1)]
    a = {
        "idle": [frame(), frame(dy=0, legs=(0, 1, 0, 0)), frame(dy=1, legs=(1, 1, 0, 0)),
                 frame(eyes="blink", legs=(0, 0, 0, 0))],
        "walk": [frame(dy=(1 if i % 3 == 0 else 0), legs=walk_legs[i],
                       arms=("rest", "rest_r") if i % 3 else ("up", "rest_r")) for i in range(6)],
        "talk": [frame(mouth=m) for m in ("smile", "talk", "open", "talk")],
        "wave": [frame(mouth="smile", arms=("rest", arm_)) for arm_ in ("up_r", "wave_a", "wave_b", "wave_a", "wave_b", "up_r")],
        "point": [frame(eyes="right", arms=("rest", "point")), frame(eyes="right", mouth="talk", arms=("rest", "point")),
                  frame(eyes="right", mouth="open", arms=("rest", "point")), frame(eyes="right", arms=("rest", "point"))],
        "cheer": [frame(eyes="happy", mouth="open", arms=("up", "up_r")),
                  frame(eyes="happy", mouth="open", dy=-2, legs=(1, -1, 2, 2), arms=("up", "up_r")),
                  frame(eyes="happy", mouth="open", dy=-3, legs=(1, -1, 3, 3), arms=("up", "up_r")),
                  frame(eyes="happy", mouth="open", dy=-2, legs=(1, -1, 2, 2), arms=("up", "up_r")),
                  frame(eyes="happy", mouth="smile", arms=("up", "up_r")),
                  frame(eyes="happy", mouth="smile", dy=1, arms=("rest", "rest_r"))],
    }
    # materializa: partículas convergem e o corpo entra em pontilhado ordenado
    base = frame()
    sparks = [(4, 10), (50, 6), (2, 40), (52, 44), (26, 2), (30, 54), (10, 50), (46, 24)]
    cx, cy = CANVAS // 2, CANVAS // 2
    appear = []
    for i in range(8):
        t = i / 7
        g = np.full((CANVAS, CANVAS), ".", dtype="<U1")
        if t > 0.3:
            keep = BAYER4[np.arange(CANVAS)[:, None] % 4, np.arange(CANVAS)[None, :] % 4] < (t - 0.3) / 0.7
            g[keep & (base != ".")] = base[keep & (base != ".")]
        for (x, y) in sparks:
            px, py = round(x + (cx - x) * t), round(y + (cy - y) * t)
            g[py, px] = "Z" if i % 2 == 0 else "T"
        appear.append(g)
    a["appear"] = appear
    return a


def portrait(base):
    """Retrato do diálogo: barra + rosto (recorte sem reamostrar)."""
    return base[OY - 1:OY + 13, OX + 6:OX + 40]


def to_rgba(g):
    h, w = g.shape
    out = np.zeros((h, w, 4), dtype=np.uint8)
    for ch, col in PALETTE.items():
        out[g == ch] = (*col, 255)
    return out


def body_mask_idle():
    """Silhueta do idle SEM rosto e SEM braços (para o teste de semelhança)."""
    return silhouette()
