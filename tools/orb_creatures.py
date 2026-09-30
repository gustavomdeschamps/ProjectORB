"""Criaturas geométricas do Project ORB (Fase 2): inimigos e chefe.

Fonte da verdade:
- tools/sprite_grids/<nome>.txt — corpo de cada forma, grade de texto por
  PAPEL de cor (semeada uma vez por seed_enemy_grids.py e editada à mão);
- as grades de partes deste arquivo (olhos por expressão, bocas, mãos, pés),
  escritas à mão;
- ANCHORS: onde cada parte fica em cada corpo.

Papéis: '.' vazio, K contorno, D sombra, B base, L luz, H brilho, G reflexo,
W branco do olho, P pupila, M boca. Cada criatura troca papel -> cor pela
própria paleta; nada aqui é aleatório, a mesma entrada gera os mesmos bytes.
"""

from pathlib import Path

import numpy as np

GRIDS = Path(__file__).resolve().parent / "sprite_grids"

# ------------------------------------------------------------------ paletas
INK = (20, 14, 30)            # contorno e pupila: o mesmo quase-preto do ORB
EYE = (248, 246, 255)
SHINE = (255, 255, 255)
MOUTH = (110, 26, 58)

# papel -> cor. Nada de amarelo-âmbar (reservado aos alvos).
PALETTES = {
    "triangle": {"D": (28, 120, 98), "B": (70, 196, 150), "L": (140, 232, 188), "H": (214, 255, 234)},  # menta
    "square": {"D": (52, 104, 176), "B": (112, 178, 236), "L": (178, 222, 255), "H": (232, 246, 255)},  # azul-gelo
    "diamond": {"D": (150, 58, 34), "B": (214, 104, 50), "L": (244, 152, 96), "H": (255, 214, 190)},    # laranja-queimado
    "hexagon": {"D": (138, 52, 142), "B": (206, 104, 196), "L": (236, 164, 228), "H": (252, 224, 250)},  # orquídea
    "boss": {"D": (32, 20, 56), "B": (58, 38, 94), "L": (94, 66, 142), "H": (160, 136, 210)},           # arroxeado escuro
}
COMMON = {"K": INK, "W": EYE, "P": INK, "G": SHINE, "M": MOUTH}
# brilhos multicoloridos do chefe (um por cor de inimigo, sem amarelo)
BOSS_GLINTS = {"1": (140, 232, 188), "2": (178, 222, 255), "3": (244, 152, 96), "4": (236, 164, 228)}


def palette(name):
    pal = dict(COMMON)
    pal.update(PALETTES[name])
    if name == "boss":
        pal.update(BOSS_GLINTS)
    return pal


def palette_colors(name):
    return set(palette(name).values())


# ------------------------------------------------------------ partes (à mão)
# Olhos: bloco de 21x12 (olho 9 + vão 3 + olho 9). As pupilas olham para a
# ESQUERDA: o sprite nasce virado para a esquerda e o jogo espelha.
_OPEN = [".KKKKKKK.", "KWWWWWWWK", "KWWWWWWWK", "KWGGPPWWK", "KWGPPPWWK", "KWPPPPWWK",
         "KWPPPPWWK", "KWPPPPWWK", "KWPPPPWWK", "KWWWWWWWK", "KWWWWWWWK", ".KKKKKKK."]
_BLINK = ["........."] * 7 + ["KKKKKKKKK", ".KKKKKKK."] + ["........."] * 3
_ANGRY_L = ["KK.......", "KWKK.....", "KWWWKK...", "KWGGPPKK.", "KWGPPPWWK", "KWPPPPWWK",
            "KWPPPPWWK", "KWPPPPWWK", "KWPPPPWWK", "KWWWWWWWK", "KWWWWWWWK", ".KKKKKKK."]
_ANGRY_R = [".......KK", ".....KKWK", "...KKWWWK", ".KKGPPWWK", "KWGPPPWWK", "KWPPPPWWK",
            "KWPPPPWWK", "KWPPPPWWK", "KWPPPPWWK", "KWWWWWWWK", "KWWWWWWWK", ".KKKKKKK."]
_SURPRISED = [".KKKKKKK.", "KWWWWWWWK", "KWWWWWWWK", "KWWWWWWWK", "KWWWWWWWK", "KWWGPWWWK",
              "KWWPPWWWK", "KWWWWWWWK", "KWWWWWWWK", "KWWWWWWWK", "KWWWWWWWK", ".KKKKKKK."]
_SAD_L = [".........", ".......KK", ".....KKWK", "...KKWWWK", ".KKWWWWWK", "KWWWWWWWK",
          "KWGPPPWWK", "KWPPPPWWK", "KWPPPPWWK", "KWPPPPWWK", "KWWWWWWWK", ".KKKKKKK."]
_SAD_R = [".........", "KK.......", "KWKK.....", "KWWWKK...", "KWWWWWKK.", "KWWWWWWWK",
          "KWGPPPWWK", "KWPPPPWWK", "KWPPPPWWK", "KWPPPPWWK", "KWWWWWWWK", ".KKKKKKK."]
_DIZZY = [".........", ".........", ".KK...KK.", "..KK.KK..", "...KKK...", "..KK.KK..",
          ".KK...KK.", ".........", ".........", ".........", ".........", "........."]


def _pair(left, right):
    return [a + "..." + b for a, b in zip(left, right)]


EYES = {
    "open": _pair(_OPEN, _OPEN),
    "blink": _pair(_BLINK, _BLINK),
    "angry": _pair(_ANGRY_L, _ANGRY_R),
    "surprised": _pair(_SURPRISED, _SURPRISED),
    "sad": _pair(_SAD_L, _SAD_R),
    "dizzy": _pair(_DIZZY, _DIZZY),
}

# Bocas: bloco de 9x4, centrado sob os olhos.
MOUTHS = {
    "flat": [".........", "..KKKKK..", ".........", "........."],
    "smirk": [".........", "......KK.", "..KKKKK..", "........."],
    "open": ["..KKKKK..", ".KMMMMMK.", ".KMMMMMK.", "..KKKKK.."],
    "grit": [".KKKKKKK.", ".KWKWKWK.", ".KKKKKKK.", "........."],
    "o": ["...KKK...", "..KMMMK..", "..KMMMK..", "...KKK..."],
    "frown": [".........", "...KKK...", "..K...K..", "........."],
}

# Mão (luva redonda) e pé (sapato com bico para a esquerda) em tons claros,
# como os do ORB: contorno preto some no fundo escuro, a luva clara não.
HAND = [".KKKK.", "KHLLLK", "KLLLLK", "KLLLBK", "KLBBBK", ".KKKK."]
FOOT = ["..KKKKKK.", ".KLLLLLLK", "KLBBBBBBK", "KDDDDDDDK", ".KKKKKKK."]
# Brilho grande no alto à esquerda (a "calota" clara do ORB).
SHINE = ["..GGH..", ".GHHHH.", "GHHHH..", "HHH....", "HH....."]

# Onde vão as partes (canto superior esquerdo de cada bloco), por corpo. As
# mãos encostam 2 px na borda do corpo (ficam presas a ele, por cima).
ANCHORS = {
    "triangle": {"eyes": (26, 32), "mouth": (32, 45), "shine": (31, 17),
                 "hands": ((13, 37), (54, 37)), "feet": ((22, 52), (42, 52))},
    "square": {"eyes": (26, 22), "mouth": (32, 37), "shine": (14, 14),
               "hands": ((7, 44), (60, 44)), "feet": ((18, 62), (46, 62))},
    "diamond": {"eyes": (26, 26), "mouth": (32, 40), "shine": (23, 18),
                "hands": ((12, 44), (55, 44)), "feet": None},
    "hexagon": {"eyes": (26, 24), "mouth": (32, 38), "shine": (15, 18),
                "hands": ((6, 33), (61, 33)), "feet": ((21, 62), (43, 62))},
}


# ---------------------------------------------------------------- utilidades

def read_grid(name):
    rows = [r for r in (GRIDS / f"{name}.txt").read_text(encoding="utf-8").splitlines()
            if r and not r.startswith("#")]
    w = len(rows[0])
    for i, r in enumerate(rows):
        if len(r) != w:
            raise SystemExit(f"{name}.txt: linha {i} com {len(r)} colunas (esperado {w})")
    return [list(r) for r in rows]


def stamp(g, part, x, y):
    """Carimba 'part' na grade g ('.' da parte é transparente)."""
    for dy, row in enumerate(part):
        for dx, ch in enumerate(row):
            if ch != "." and 0 <= y + dy < len(g) and 0 <= x + dx < len(g[0]):
                g[y + dy][x + dx] = ch
    return g


def under(g, part, x, y):
    """Carimba só onde a grade está vazia (pés e mãos ficam ATRÁS do corpo)."""
    for dy, row in enumerate(part):
        for dx, ch in enumerate(row):
            if ch != "." and 0 <= y + dy < len(g) and 0 <= x + dx < len(g[0]) and g[y + dy][x + dx] == ".":
                g[y + dy][x + dx] = ch
    return g


def mirror(part):
    return [r[::-1] for r in part]


def to_rgba(g, pal):
    h, w = len(g), len(g[0])
    a = np.zeros((h, w, 4), dtype=np.uint8)
    for y in range(h):
        for x in range(w):
            ch = g[y][x]
            if ch != ".":
                if ch not in pal:
                    raise SystemExit(f"papel '{ch}' sem cor na paleta")
                a[y, x] = (*pal[ch], 255)
    return a


def compose(name, eyes="open", mouth="flat", body_dy=0, feet_lift=(0, 0), hands_dy=(0, 0)):
    """Uma pose: corpo (deslocado body_dy) + partes nas âncoras."""
    body = read_grid(name)
    h, w = len(body), len(body[0])
    g = [["."] * w for _ in range(h)]
    an = ANCHORS[name]
    if an["feet"]:
        for (fx, fy), lift in zip(an["feet"], feet_lift):
            under(g, FOOT, fx, fy - lift)
    for y in range(h):                      # corpo por cima dos pés
        sy = y - body_dy
        if 0 <= sy < h:
            for x in range(w):
                if body[sy][x] != ".":
                    g[y][x] = body[sy][x]
    sx, sy = an["shine"]
    stamp(g, SHINE, sx, sy + body_dy)
    ex, ey = an["eyes"]
    stamp(g, EYES[eyes], ex, ey + body_dy)
    mx, my = an["mouth"]
    stamp(g, MOUTHS[mouth], mx, my + body_dy)
    (lx, ly), (rx, ry) = an["hands"]
    stamp(g, HAND, lx, ly + body_dy + hands_dy[0])
    stamp(g, mirror(HAND), rx, ry + body_dy + hands_dy[1])
    return to_rgba(g, palette(name))


# ===================================================================== chefe
# Duas camadas: a CARAPAÇA (sprite_grids/boss_shell*.txt, simétrica a cada 60°)
# gira; o ROSTO (olhos, boca, mãos, brilho, núcleos) fica sempre de pé.
BOSS_C = 113
BOSS_ENRAGED = {"D": (74, 16, 44), "B": (128, 30, 72), "L": (186, 62, 112), "H": (236, 150, 180)}

# Olhos do chefe: 15x18. As linhas de baixo (pupila e base) são comuns.
_B_LOW = ["KWPPPPPPPPWWWWK", "KWPPPPPPPPWWWWK", "KWPPPPPPPPWWWWK", "KWPPPPPPPPWWWWK",
          "KWPPPPPPPPWWWWK", "KWWWWWWWWWWWWWK", "KWWWWWWWWWWWWWK", ".KWWWWWWWWWWWK.",
          "..KWWWWWWWWWK..", "...KKKKKKKKK..."]
_B_OPEN = ["...KKKKKKKKK...", "..KWWWWWWWWWK..", ".KWWWWWWWWWWWK.", "KWWWWWWWWWWWWWK",
           "KWGGGGPPPPWWWWK", "KWGGGPPPPPWWWWK", "KWGGPPPPPPWWWWK", "KWGPPPPPPPWWWWK"] + _B_LOW
# confiante (fase 1): pálpebra reta e grossa sobre o alto do olho
_B_STERN = ["...............", "...............", "KKKKKKKKKKKKKKK", "KKKKKKKKKKKKKKK",
            "KWGGGGPPPPWWWWK", "KWGGGPPPPPWWWWK", "KWGGPPPPPPWWWWK", "KWGPPPPPPPWWWWK"] + _B_LOW
# furioso (fase 3): pálpebra em diagonal descendo para o centro do rosto
_B_FURY_L = ["KK.............", "KWKK...........", "KWWWKK.........", "KWWWWWKK.......",
             "KWGGGGPKKK.....", "KWGGGPPPPKKK...", "KWGGPPPPPPWKKK.", "KWGPPPPPPPWWWWK"] + _B_LOW
_B_FURY_R = [r[::-1] for r in _B_FURY_L[:8]] + _B_LOW
# esperto (fase 2, espelho): sobrancelha erguida num olho só
_B_SMUG_R = ["KKKKKK.........", "......KKKKKK...", "...KKKKKKKKK...", ".KWWWWWWWWWWWK.",
             "KWGGGGPPPPWWWWK", "KWGGGPPPPPWWWWK", "KWGGPPPPPPWWWWK", "KWGPPPPPPPWWWWK"] + _B_LOW
_B_SURPRISED = ["...KKKKKKKKK...", "..KWWWWWWWWWK..", ".KWWWWWWWWWWWK.", "KWWWWWWWWWWWWWK",
                "KWWWWWWWWWWWWWK", "KWWWWWWWWWWWWWK", "KWWWWGPPWWWWWWK", "KWWWWPPPWWWWWWK",
                "KWWWWPPPWWWWWWK", "KWWWWWWWWWWWWWK", "KWWWWWWWWWWWWWK", "KWWWWWWWWWWWWWK",
                "KWWWWWWWWWWWWWK", "KWWWWWWWWWWWWWK", "KWWWWWWWWWWWWWK", ".KWWWWWWWWWWWK.",
                "..KWWWWWWWWWK..", "...KKKKKKKKK..."]
_B_DIZZY = ["..............."] * 3 + ["..KK.......KK..", "...KK.....KK...", "....KK...KK....",
            ".....KK.KK.....", "......KKK......", ".....KK.KK.....", "....KK...KK....",
            "...KK.....KK...", "..KK.......KK.."] + ["..............."] * 6


def _bpair(left, right):
    return [a + "....." + b for a, b in zip(left, right)]   # 15 + 5 + 15


BOSS_EYES = {
    "open": _bpair(_B_OPEN, _B_OPEN),
    "stern": _bpair(_B_STERN, _B_STERN),
    "smug": _bpair(_B_STERN, _B_SMUG_R),
    "fury": _bpair(_B_FURY_L, _B_FURY_R),
    "surprised": _bpair(_B_SURPRISED, _B_SURPRISED),
    "dizzy": _bpair(_B_DIZZY, _B_DIZZY),
}
BOSS_MOUTHS = {   # 17x6
    "flat": [".................", "....KKKKKKKKK....", "....KKKKKKKKK....", ".................",
             ".................", "................."],
    "smirk": [".................", "............KKK..", "...KKKKKKKKKKK...", "...KKKKKKKKK.....",
              ".................", "................."],
    "grit": [".KKKKKKKKKKKKKKK.", "KWWKWWKWWKWWKWWK.", "KWWKWWKWWKWWKWWK.", "KKKKKKKKKKKKKKKK.",
             ".................", "................."],
    "roar": ["...KKKKKKKKKKK...", "..KMMMMMMMMMMMK..", ".KMWWMMMMMMMWWMK.", ".KMMMMMMMMMMMMMK.",
             "..KMMMMMMMMMMMK..", "...KKKKKKKKKKK..."],
    "o": [".......KKK.......", "......KMMMK......", ".....KMMMMMK.....", ".....KMMMMMK.....",
          "......KMMMK......", ".......KKK......."],
}
# luva clara (no fundo escuro a luva escura sumia), presa na lateral
BOSS_HAND = ["...KKKKK...", "..KGHHHHK..", ".KGHHHHHHK.", "KHHHHHHHHLK", "KHHHHHHHLLK",
             "KHHHHHHLLLK", "KHHHHLLLLLK", ".KHLLLLLLK.", "..KLLLLLK..", "...KKKKK..."]
BOSS_SHINE = ["...GGHH..", "..GHHHHH.", ".GHHHH...", "GHHH.....", "HHH......", "HH......."]
# núcleo da fase do espelho: gema facetada (não é anel), cores dos brilhos
BOSS_CORE = ["...K...", "..K4K..", ".K424K.", "K42G24K", ".K424K.", "..K4K..", "...K..."]
BOSS_ANCHORS = {"eyes": (39, 34), "mouth": (48, 60), "shine": (27, 27),
                "hands": ((3, 58), (99, 58))}


def boss_cores_px(c=BOSS_C // 2, r=26 * 1.75):
    """Centros dos 3 núcleos (os mesmos da lógica: r/2 em 90°, 210°, 330°)."""
    import math
    return [(c + round(r * 0.5 * math.cos(math.radians(a))), c - round(r * 0.5 * math.sin(math.radians(a))))
            for a in (90, 210, 330)]


def boss_face(eyes="stern", mouth="flat", cores=False, hands_dy=(0, 0), enraged=False):
    """Camada de cima do chefe (não gira): 113x113 RGBA."""
    g = [["."] * BOSS_C for _ in range(BOSS_C)]
    if cores:
        for (x, y) in boss_cores_px():
            stamp(g, BOSS_CORE, x - 3, y - 3)
    stamp(g, BOSS_SHINE, *BOSS_ANCHORS["shine"])
    ex, ey = BOSS_ANCHORS["eyes"]
    stamp(g, BOSS_EYES[eyes], ex, ey)
    mx, my = BOSS_ANCHORS["mouth"]
    stamp(g, BOSS_MOUTHS[mouth], mx, my)
    (lx, ly), (rx, ry) = BOSS_ANCHORS["hands"]
    stamp(g, BOSS_HAND, lx, ly + hands_dy[0])
    stamp(g, mirror(BOSS_HAND), rx, ry + hands_dy[1])
    return to_rgba(g, boss_palette(enraged))


def boss_palette(enraged=False):
    pal = palette("boss")
    if enraged:
        pal.update(BOSS_ENRAGED)
    return pal


def boss_shell(cracked=False, enraged=False):
    return to_rgba(read_grid("boss_shell_cracked" if cracked else "boss_shell"), boss_palette(enraged))
