"""Inimigos HOSTIS do Project ORB (Fase 2, direção v2: corrompidos pelo Rift).

Mesma técnica das criaturas anteriores (orb_creatures.py):
- corpo = tools/sprite_grids/<nome>.txt, grade por PAPEL de cor;
- partes escritas à mão aqui (olhos estreitos, presas, garras, pernas
  pontiagudas, espinhos, rachaduras, pedaço faltando, estilhaços);
- animações DERIVADAS dessas grades (deslocar, trocar parte, recolorir,
  fatiar para o glitch, partir nos cacos da morte). Nada aleatório.

Papéis: '.' vazio, '_' apaga (pedaço faltando), K contorno, D sombra,
B base, L luz, H brilho da borda, A energia (acento que vaza), C núcleo da
energia, V presa/osso, M garganta, W flash branco (dano), X/Y listras do
glitch (magenta/ciano), E olho apagado.
"""

import math

import numpy as np

from orb_creatures import read_grid, stamp, mirror

INK = (12, 8, 18)
FANG = (226, 232, 214)
THROAT = (46, 8, 26)
FLASH = (255, 255, 255)
GLITCH_X = (255, 40, 170)
GLITCH_Y = (40, 230, 255)
EYE_OFF = (40, 44, 52)

# Paletas escuras e saturadas, com acento que brilha. Nada de amarelo-âmbar.
PALETTES = {
    "triangle": {  # verde-tóxico
        "D": (16, 50, 36), "B": (30, 92, 60), "L": (62, 146, 86), "H": (146, 222, 120),
        "A": (118, 255, 72), "C": (222, 255, 188), "G": (236, 255, 220),
    },
}


def palette(name):
    pal = {"K": INK, "V": FANG, "M": THROAT, "W": FLASH, "X": GLITCH_X, "Y": GLITCH_Y, "E": EYE_OFF}
    pal.update(PALETTES[name])
    return pal


def palette_colors(name):
    return set(palette(name).values())


# ------------------------------------------------------------ partes (à mão)
# Olhos: bloco 29x7 (olho 13 + vão 3 + olho 13). Estreitos, inclinados para o
# centro (sobrancelha brava), brilhando na cor de acento, sem pupila.
_EYE_L = ["KK.........",
          "KAKK.......",
          "KACAKK.....",
          ".KACCAKK...",
          "..KKACCAKK.",
          "....KKAAAAK",
          "......KKKK."]
_EYE_L_CHARGE = ["KK.........",
                 "KCKK.......",
                 "KCCCKK.....",
                 ".KCCCCKK...",
                 "..KKCCCCKK.",
                 "....KKCCCAK",
                 "......KKKK."]
_EYE_L_SQUINT = ["KK.........",
                 "KKKK.......",
                 ".KKKKK.....",
                 "..KKKKKK...",
                 "....KKAAKK.",
                 "......KKKKK",
                 "..........."]
_EYE_L_OFF = ["KK.........",
              "KEKK.......",
              "KEEEKK.....",
              ".KEEEEKK...",
              "..KKEEEEKK.",
              "....KKEEEEK",
              "......KKKK."]


def _eyes(left):
    return [a + "..." + b for a, b in zip(left, mirror(left))]


EYES = {
    "glare": _eyes(_EYE_L),
    "charge": _eyes(_EYE_L_CHARGE),
    "squint": _eyes(_EYE_L_SQUINT),
    "off": _eyes(_EYE_L_OFF),
}

# Boca: fenda irregular com presas (19 de largura). Aberta no ataque, com a
# energia brilhando no fundo da garganta.
MOUTHS = {
    "fangs": ["KKKKKKKKKKKKKKKKKKK",
              ".KVVK.KVK.KVK.KVVK.",
              "..KVK..K...K..KVK..",
              "...K...........K...",
              "..................."],
    "open": [".KKKKKKKKKKKKKKKKK.",
             "KVVKVKKKKKKKKKVKVVK",
             "KKVKMMAAAAAAAMMKVKK",
             "KMMAAACCCCCCCAAAMMK",
             "KMMAAACCCCCCCAAAMMK",
             "KKVKMMAAAAAAAMMKVKK",
             "KVVKVKKKKKKKKKVKVVK",
             ".KKKKKKKKKKKKKKKKK."],
    "grimace": ["KKKKKKKKKKKKKKKKKKK",
                "KVKVKVKVKVKVKVKVKVK",
                "KVKVKVKVKVKVKVKVKVK",
                "KKKKKKKKKKKKKKKKKKK",
                "..................."],
}

# Garra-lâmina (sai da lateral esquerda; a direita é o espelho). Fio em energia.
BLADE = ["K...........",
         "KK..........",
         "KAKK........",
         ".KAAKK......",
         ".KHAALKK....",
         "..KHLLLLKK..",
         "..KBLLLLLLKK",
         "...KBBBBBDDK",
         "...KKDDDDDK.",
         ".....KKKKK.."]
BLADE_UP = ["..K.........",
            "..KK........",
            "..KAKK......",
            "..KAAAKK....",
            "...KHAALKK..",
            "...KHLLLLLKK",
            "...KBLLLLLLK",
            "...KBBBBBDDK",
            "....KDDDDDK.",
            ".....KKKKK.."]
# Perna pontiaguda (apoio): a ponta encosta no chão.
LEG = ["KKKKKK.",
       "KHLBDK.",
       "KLLBDK.",
       ".KLBK..",
       ".KLBK..",
       ".KBDK..",
       ".KBDK..",
       "..KAK..",
       "..KAK..",
       "..KK...",
       "..K....",
       "......."]
LEG_UP = [".......", ".......", "......."] + LEG[:9]
# Espinhos de cristal nos vértices (a energia sobe pelo meio).
SPIKE_UP = ["...K...",
            "...K...",
            "..KAK..",
            "..KAK..",
            ".KACAK.",
            ".KACAK.",
            "KACCCAK"]
SPIKE_DL = ["KK........",
            ".KKK......",
            ".KAAKK....",
            "..KACAKK..",
            "...KACCAKK",
            "....KKCCA.",
            "......KK.."]
SPIKE_DR = [r[::-1] for r in SPIKE_DL]
# Rachadura com energia vazando, pedaço faltando e veias.
CRACK = ["KK......",
         ".KK.....",
         ".KCA....",
         "..KCA...",
         "..KKCA..",
         "...KCA..",
         "..KCAK..",
         ".KCAK...",
         ".KCA....",
         "..KCA...",
         "...KCA..",
         "....KK.."]
NOTCH = ["KK______",
         "KAKK____",
         ".KCAKK__",
         "..KCAAKK",
         "...KKKK."]
VEIN = ["A.......",
        ".A......",
        ".AA.....",
        "..A.....",
        "..AA....",
        "....A...",
        "....AA..",
        "......A.",
        ".......A"]
# Estilhaço flutuante (orbita o corpo).
SHARD = ["..K..",
         ".KLK.",
         ".KHK.",
         "KAAAK",
         "KKKKK"]
CHIP = [".K.", "KAK", ".K."]

ANCHORS = {
    "triangle": {
        "eyes": (24, 25), "mouth": (27, 36),
        "blades": ((3, 31), (58, 31)), "legs": ((20, 50), (47, 50)),
        "spikes": [("up", 33, 0), ("dl", 1, 49), ("dr", 62, 49)],
        "crack": (38, 10), "notch": (54, 42), "vein": (17, 38),
        # órbita dos 3 estilhaços, um ponto por frame do idle (desenhado à mão)
        "shards": [[(4, 12), (3, 10), (4, 8), (6, 7), (7, 9), (6, 11)],
                   [(63, 18), (65, 17), (66, 15), (65, 13), (63, 14), (62, 16)],
                   [(55, 2), (57, 3), (58, 5), (57, 7), (55, 6), (54, 4)]],
    },
}


# ---------------------------------------------------------------- utilidades

def erase(g, part, x, y):
    """'_' da parte apaga o pixel da grade (pedaço faltando); resto carimba."""
    for dy, row in enumerate(part):
        for dx, ch in enumerate(row):
            yy, xx = y + dy, x + dx
            if 0 <= yy < len(g) and 0 <= xx < len(g[0]):
                if ch == "_":
                    g[yy][xx] = "."
                elif ch != ".":
                    g[yy][xx] = ch
    return g


def under(g, part, x, y):
    for dy, row in enumerate(part):
        for dx, ch in enumerate(row):
            yy, xx = y + dy, x + dx
            if ch != "." and 0 <= yy < len(g) and 0 <= xx < len(g[0]) and g[yy][xx] == ".":
                g[yy][xx] = ch
    return g


def to_rgba(g, pal):
    h, w = len(g), len(g[0])
    a = np.zeros((h, w, 4), dtype=np.uint8)
    for y in range(h):
        for x in range(w):
            ch = g[y][x]
            if ch != ".":
                a[y, x] = (*pal[ch], 255)
    return a


def pose(name, eyes="glare", mouth="fangs", body_dy=0, legs=(0, 0), blades=("", ""), shards=0,
         vein_hot=False, spikes_hot=False, show_shards=True):
    """Grade de papéis de uma pose. body_dy desloca corpo/rosto (respirar)."""
    an = ANCHORS[name]
    body = read_grid(name)
    h, w = len(body), len(body[0])
    g = [["."] * w for _ in range(h)]
    for (lx, ly), lift in zip(an["legs"], legs):                     # pernas atrás
        under(g, LEG_UP if lift else LEG, lx, ly - lift)
    for y in range(h):                                               # corpo
        sy = y - body_dy
        if 0 <= sy < h:
            for x in range(w):
                if body[sy][x] != ".":
                    g[y][x] = body[sy][x]
    for kind, sx, sy in an["spikes"]:
        part = {"up": SPIKE_UP, "dl": SPIKE_DL, "dr": SPIKE_DR}[kind]
        if spikes_hot:
            part = [r.replace("A", "C") for r in part]
        stamp(g, part, sx, sy + body_dy)
    nx, ny = an["notch"]
    erase(g, NOTCH, nx, ny + body_dy)
    cx, cy = an["crack"]
    stamp(g, CRACK, cx, cy + body_dy)
    vx, vy = an["vein"]
    stamp(g, [r.replace("A", "C") for r in VEIN] if vein_hot else VEIN, vx, vy + body_dy)
    ex, ey = an["eyes"]
    stamp(g, EYES[eyes], ex, ey + body_dy)
    mx, my = an["mouth"]
    stamp(g, MOUTHS[mouth], mx, my + body_dy)
    (blx, bly), (brx, bry) = an["blades"]
    stamp(g, BLADE_UP if blades[0] == "up" else BLADE, blx, bly + body_dy)
    stamp(g, mirror(BLADE_UP if blades[1] == "up" else BLADE), brx, bry + body_dy)
    if show_shards:
        for orbit in an["shards"]:
            px, py = orbit[shards % len(orbit)]
            under(g, SHARD, px, py)
    return g


def recolor_roles(g, mapping):
    return [[mapping.get(ch, ch) for ch in row] for row in g]


def flash(g):
    """Dano: tudo que não é contorno vira branco (os olhos continuam acesos)."""
    return recolor_roles(g, {r: "W" for r in "DBLHVM"})


def glitch(g, rows, shift, stripe="X"):
    """Fatias horizontais deslocadas + listra de cor (derivado da pose)."""
    out = [row[:] for row in g]
    w = len(g[0])
    for y in rows:
        src = g[y]
        out[y] = ["."] * w
        for x in range(w):
            if src[x] != ".":
                nx = x + shift
                if 0 <= nx < w:
                    out[y][nx] = stripe if src[x] in "DBLH" and (x % 3 == 0) else src[x]
    return out


# pontilhado ordenado 4x4 (limiares 0..15/16): some de forma controlada
BAYER4 = [[0 / 16, 8 / 16, 2 / 16, 10 / 16], [12 / 16, 4 / 16, 14 / 16, 6 / 16],
          [3 / 16, 11 / 16, 1 / 16, 9 / 16], [15 / 16, 7 / 16, 13 / 16, 5 / 16]]


def split_pieces(g, cut_x_top, cut_x_bottom, gap, drop, keep=1.0):
    """Morte: parte o corpo em duas metades pela rachadura e as afasta."""
    h, w = len(g), len(g[0])
    out = [["."] * w for _ in range(h)]
    for y in range(h):
        cut = cut_x_top + (cut_x_bottom - cut_x_top) * y / h
        for x in range(w):
            ch = g[y][x]
            if ch == ".":
                continue
            left = x < cut
            nx = x - gap if left else x + gap
            ny = y + (drop if left else drop + 1)
            if abs(x - cut) < 1:
                ch = "A"                                           # energia na fratura
            if 0 <= nx < w and 0 <= ny < h and BAYER4[ny % 4][nx % 4] < keep:
                out[ny][nx] = ch
    return out


def rays(g, cx, cy, r0, r1, char="C"):
    """Explosão de energia em 8 raios (sem anéis)."""
    out = [row[:] for row in g]
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        for r in range(r0, r1):
            x, y = int(round(cx + r * math.cos(a))), int(round(cy - r * math.sin(a)))
            if 0 <= y < len(g) and 0 <= x < len(g[0]):
                out[y][x] = char if r < (r0 + r1) // 2 else "A"
    return out


def chips(g, pts):
    out = [row[:] for row in g]
    for (x, y) in pts:
        stamp(out, CHIP, x, y)
    return out


# ----------------------------------------------------------------- animações

def triangle_anims():
    n = "triangle"
    idle = [pose(n, body_dy=dy, shards=i, vein_hot=hot)
            for i, (dy, hot) in enumerate([(0, False), (0, False), (1, True), (1, True), (0, False), (0, False)])]
    move = [pose(n, body_dy=dy, legs=lg, shards=i % 6, blades=bl)
            for i, (dy, lg, bl) in enumerate([
                (0, (0, 0), ("", "")), (-1, (2, 0), ("up", "")), (0, (1, 0), ("", "")), (1, (0, 0), ("", "")),
                (0, (0, 0), ("", "")), (-1, (0, 2), ("", "up")), (0, (0, 1), ("", "")), (1, (0, 0), ("", ""))])]
    # carga (telegrafia): olhos acendem, corpo contrai e treme, espinhos esquentam
    charge = [pose(n, eyes="charge", body_dy=1, shards=0, vein_hot=True, spikes_hot=(i % 2 == 0))
              for i in range(4)]
    charge[1] = [r[1:] + ["."] for r in charge[1]]                   # tremor de 1 px
    charge[3] = [["."] + r[:-1] for r in charge[3]]
    # ataque (0,48 s; disparo aos 55% = frame 3): antecipação, carga, disparo, recuperação
    attack = [
        pose(n, eyes="charge", mouth="grimace", body_dy=2, blades=("up", "up"), vein_hot=True),
        pose(n, eyes="charge", mouth="grimace", body_dy=2, blades=("up", "up"), vein_hot=True, spikes_hot=True),
        pose(n, eyes="charge", mouth="open", body_dy=1, blades=("up", "up"), vein_hot=True, spikes_hot=True),
        rays(pose(n, eyes="charge", mouth="open", body_dy=-2, vein_hot=True, spikes_hot=True), 36, 2, 4, 9),
        pose(n, eyes="glare", mouth="open", body_dy=-1, vein_hot=True),
        pose(n, eyes="glare", mouth="fangs", body_dy=0),
    ]
    hurt_base = pose(n, eyes="squint", mouth="grimace", shards=2)
    hurt = [
        flash(hurt_base),
        chips(glitch(hurt_base, range(18, 30), 2), [(4, 30), (66, 26), (40, 2)]),
        chips(glitch(hurt_base, range(34, 44), -2, "Y"), [(1, 34), (69, 22), (44, 0)]),
        pose(n, eyes="glare", mouth="grimace"),
    ]
    dead = pose(n, eyes="off", mouth="grimace", show_shards=False)
    death = [
        flash(pose(n, eyes="squint", mouth="grimace", show_shards=False)),
        rays(dead, 36, 30, 20, 26),
        split_pieces(dead, 44, 34, 1, 0),
        rays(split_pieces(dead, 44, 34, 3, 2, 0.85), 36, 30, 24, 32, "A"),
        chips(split_pieces(dead, 44, 34, 6, 5, 0.6), [(2, 20), (66, 12), (30, 0), (50, 60)]),
        chips(split_pieces(dead, 44, 34, 9, 9, 0.3), [(0, 14), (69, 6), (26, 62), (56, 64)]),
    ]
    full = pose(n)
    appear = [
        glitch([["." if (y % 4) else ch for ch in row] for y, row in enumerate(full)], range(10, 50, 2), 4, "Y"),
        glitch([["." if (y % 3 == 0) else ch for ch in row] for y, row in enumerate(full)], range(0, 60, 3), -3),
        glitch(full, range(20, 30), 3, "Y"),
        glitch(full, range(40, 44), -2),
        full,
    ]
    # glitch ocasional (o jogo toca de vez em quando; desligado em movimento reduzido)
    glitch_anim = [glitch(full, range(14, 22), 3), glitch(full, range(30, 36), -3, "Y"), full]
    return {"idle": idle, "move": move, "charge": charge, "attack": attack, "hurt": hurt,
            "death": death, "appear": appear, "glitch": glitch_anim}


BUILDERS = {"triangle": triangle_anims}


def build(name):
    """{animação: [RGBA]} já na paleta; o chamador valida e grava."""
    pal = palette(name)
    return {k: [to_rgba(g, pal) for g in frames] for k, frames in BUILDERS[name]().items()}
