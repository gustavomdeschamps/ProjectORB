"""NPC Octógono (etapa E): octógono regular AMIGÁVEL, estilo criatura.

Corpo: tools/sprite_grids/octagon.txt (octógono regular de raio 30, topo reto),
semeado da geometria e editável à mão. Partes escritas à mão aqui: olhos
grandes com pupila e brilho, sorriso, mãos e pés pequenos claros, reticências
de pensar, gota de "ops". Azul-royal com detalhes claros; sem anéis, sem
amarelo. Animações derivadas (piscar, balançar, pular, trocar expressão).
"""

import numpy as np

from orb_creatures import read_grid, stamp, under, mirror

PALETTE = {
    "K": (14, 16, 40),      # contorno
    "D": (26, 42, 128),     # sombra
    "B": (46, 76, 196),     # azul-royal
    "L": (98, 130, 236),    # luz
    "H": (178, 200, 255),   # brilho da borda
    "G": (236, 244, 255),   # reflexo
    "W": (250, 252, 255),   # branco dos olhos
    "P": (14, 16, 40),      # pupila
    "M": (120, 36, 70),     # boca por dentro
    "T": (150, 230, 255),   # gota / faísca
}

EYE = ["..KKKKK..", ".KWWWWWK.", "KWWWWWWWK", "KWGPPPWWK", "KWPPPPWWK", "KWPPPPWWK", "KWWWWWWWK",
       ".KWWWWWK.", "..KKKKK.."]
EYE_UP = ["..KKKKK..", ".KWGPPWK.", "KWWPPPWWK", "KWWPPPWWK", "KWWWWWWWK", "KWWWWWWWK", "KWWWWWWWK",
          ".KWWWWWK.", "..KKKKK.."]
EYE_BLINK = [".........", ".........", ".........", ".........", "KKKKKKKKK", ".KKKKKKK.", ".........",
             ".........", "........."]
EYE_HAPPY = [".........", ".........", "..KKKKK..", ".KK...KK.", "KK.....KK", "K.......K", ".........",
             ".........", "........."]
EYE_WIDE = ["..KKKKK..", ".KWWWWWK.", "KWWWWWWWK", "KWWWWWWWK", "KWWWPWWWK", "KWWWWWWWK", "KWWWWWWWK",
            ".KWWWWWK.", "..KKKKK.."]


def pair(e):
    return [a + "...." + a for a in e]       # 9 + 4 + 9 = 22


EYES = {"open": pair(EYE), "up": pair(EYE_UP), "blink": pair(EYE_BLINK),
        "happy": pair(EYE_HAPPY), "wide": pair(EYE_WIDE)}
MOUTHS = {    # 11x4
    "smile": ["K.........K", ".KK.....KK.", "...KKKKK...", "..........."],
    "talk": ["...KKKKK...", "..KMMMMMK..", "...KKKKK...", "..........."],
    "open": [".KKKKKKKKK.", "KMMMMMMMMMK", ".KMMMMMMMK.", "..KKKKKKK.."],
    "flat": ["...........", "..KKKKKKK..", "...........", "..........."],
    "o": ["....KKK....", "...KMMMK...", "...KMMMK...", "....KKK...."],
}
HAND = [".KKKK.", "KHLLLK", "KLLLLK", "KLLLBK", "KLLBBK", ".KKKK."]
HAND_UP = HAND
FOOT = ["..KKKKKK.", ".KHLLLLLK", "KLLLLLLLK", ".KKKKKKK."]
DOTS = ["H.H.H"]
SWEAT = ["..K..", ".KTK.", "KTTTK", ".KKK."]

EYES_AT = (25, 22)
MOUTH_AT = (31, 38)
HANDS = ((4, 38), (63, 38))
HANDS_UP = ((4, 20), (63, 20))
FEET = ((19, 62), (45, 62))


def frame(eyes="open", mouth="smile", dy=0, hands_up=(False, False), feet_lift=(0, 0), dots=0, sweat=False):
    body = read_grid("octagon")
    h, w = len(body), len(body[0])
    g = [["."] * w for _ in range(h)]
    for (fx, fy), lift in zip(FEET, feet_lift):
        under(g, FOOT, fx, fy - lift + min(0, dy))
    for y in range(h):
        sy = y - dy
        if 0 <= sy < h:
            for x in range(w):
                if body[sy][x] != ".":
                    g[y][x] = body[sy][x]
    stamp(g, EYES[eyes], EYES_AT[0], EYES_AT[1] + dy)
    stamp(g, MOUTHS[mouth], MOUTH_AT[0], MOUTH_AT[1] + dy)
    for side in (0, 1):
        (hx, hy) = (HANDS_UP if hands_up[side] else HANDS)[side]
        part = HAND_UP if hands_up[side] else HAND
        stamp(g, part if side == 0 else mirror(part), hx, hy + dy)
    if dots:
        stamp(g, [DOTS[0][:2 * dots - 1]], 62, 4 + dy)
    if sweat:
        stamp(g, SWEAT, 63, 6 + dy)
    return g


BAYER4 = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0


def anims():
    a = {
        "idle": [frame(), frame(), frame(dy=1), frame(dy=1), frame(), frame(eyes="blink")],
        "talk": [frame(mouth=m) for m in ("smile", "talk", "open", "talk")],
        "think": [frame(eyes="up", mouth="flat", dots=1 + i % 3) for i in range(6)],
        "happy": [frame(eyes="happy", mouth="open", dy=d) for d in (0, -2, -3, -2, 0, 0)],
        "oops": [frame(eyes="wide", mouth="o", sweat=True, dy=d) for d in (0, 1, 0, 1)],
        "cheer": [frame(eyes="happy", mouth="open", hands_up=(True, True), dy=d, feet_lift=(l, l))
                  for d, l in ((0, 0), (-3, 2), (-5, 3), (-3, 2), (0, 0), (-1, 0))],
    }
    base = frame()
    S = len(base)
    sparks = [(4, 8), (66, 6), (2, 60), (68, 62), (36, 2), (36, 70), (10, 36), (64, 36)]
    appear = []
    for i in range(8):
        t = i / 7
        g = [["."] * S for _ in range(S)]
        if t > 0.3:
            lvl = (t - 0.3) / 0.7
            for y in range(S):
                for x in range(S):
                    if base[y][x] != "." and BAYER4[y % 4, x % 4] < lvl:
                        g[y][x] = base[y][x]
        for (x, y) in sparks:
            px, py = round(x + (36 - x) * t), round(y + (36 - y) * t)
            g[py][px] = "T" if i % 2 == 0 else "H"
        appear.append(g)
    a["appear"] = appear
    return a


def portrait(g):
    """Retrato do diálogo: rosto (recorte sem reamostrar)."""
    return [row[16:57] for row in g[14:50]]


def to_rgba(g):
    h, w = len(g), len(g[0])
    out = np.zeros((h, w, 4), dtype=np.uint8)
    for y in range(h):
        for x in range(w):
            if g[y][x] != ".":
                out[y, x] = (*PALETTE[g[y][x]], 255)
    return out
