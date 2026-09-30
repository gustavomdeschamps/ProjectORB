"""Chefe (etapa E): três formas, nenhuma repetida no jogo.

- fase 1: PENTÁGONO regular (gira; simetria de 72° -> 24 quadros de 3°);
- fase 2: ESCUDO / trapézio isósceles (um eixo de simetria; não gira);
- fase 3: ESTRELA de 6 pontas (gira; simetria de 60° -> 20 quadros de 3°).

Fonte da verdade: tools/sprite_grids/boss_{pentagon,shield,star}.txt (uma
grade mestre por fase, semeada da geometria por seed() e editável à mão). Os
quadros de giro saem SÓ da grade mestre, por rotação própria para pixel art
(build_orb_assets.rotate_pixel: Scale2x 3x, gira com vizinho mais próximo,
volta). A carapaça tem a mesma simetria da forma (bisel, facetas, brilhos e
trincas iguais em cada vértice), então um período de 72°/60° basta.

Camada de cima, SEMPRE de pé (não gira): rosto por fase, lâminas flutuando no
lugar das luvas, fragmentos orbitando, núcleos da fase 2. Partes à mão.
"""

import math
from pathlib import Path

import numpy as np

import orb_hostiles as H
from orb_creatures import stamp, mirror

S = 113
C = S / 2
GRIDS = Path(__file__).resolve().parent / "sprite_grids"
STEP_DEG = 3

# ----------------------------------------------------------- geometria (arte)
PENT_R = 50.0
SHIELD = [(-46, 40), (46, 40), (20, -48), (-20, -48)]           # (x, y para cima)
STAR_R = 54.0
STAR_IN = STAR_R / math.sqrt(3)
CORE_R = 22.75                                                   # núcleos (fase 2)

# pontos fracos (raio em pixels de arte; o jogo multiplica por PIXEL_SCALE)
MARK = {"pent_vertex": 45.0, "pent_side": 37.0, "star_tip": 48.0, "star_inner": 28.0, "core": CORE_R}


def polygon(phase):
    """Vértices (x, y para cima) relativos ao centro."""
    if phase == 1:
        return [(PENT_R * math.cos(math.radians(90 + 72 * i)), PENT_R * math.sin(math.radians(90 + 72 * i)))
                for i in range(5)]
    if phase == 2:
        return list(SHIELD)
    pts = []
    for i in range(6):
        a = math.radians(90 + 60 * i)
        pts.append((STAR_R * math.cos(a), STAR_R * math.sin(a)))
        b = math.radians(120 + 60 * i)
        pts.append((STAR_IN * math.cos(b), STAR_IN * math.sin(b)))
    return pts


def cores():
    return [(CORE_R * math.cos(math.radians(a)), CORE_R * math.sin(math.radians(a))) for a in (90, 210, 330)]


# ------------------------------------------------------------ paletas por fase
BASE = {"K": (10, 6, 18), "V": (226, 232, 214), "M": (46, 8, 26), "W": (255, 255, 255),
        "X": (255, 40, 170), "Y": (40, 230, 255), "E": (40, 44, 52),
        "1": (118, 255, 72), "2": (70, 196, 255), "3": (255, 78, 40), "4": (255, 58, 196)}
PHASE_PAL = {
    1: {"D": (24, 20, 44), "B": (42, 34, 78), "L": (76, 64, 128), "H": (130, 118, 190),
        "A": (90, 220, 255), "C": (220, 248, 255), "G": (236, 250, 255)},          # frio
    2: {"D": (34, 16, 48), "B": (60, 28, 86), "L": (104, 56, 140), "H": (170, 120, 210),
        "A": (240, 70, 220), "C": (255, 214, 250), "G": (255, 236, 252)},          # ameaçador
    3: {"D": (70, 12, 30), "B": (120, 24, 56), "L": (180, 54, 96), "H": (232, 132, 162),
        "A": (255, 70, 90), "C": (255, 212, 216), "G": (255, 232, 236)},           # furioso
}


def palette(phase):
    p = dict(BASE)
    p.update(PHASE_PAL[phase])
    return p


def palette_colors():
    return {c for ph in (1, 2, 3) for c in palette(ph).values()}


# ---------------------------------------------------------- semeadura da grade
def _mask(pts):
    from build_orb_assets import m_poly
    return m_poly(S, S, [(C + x, C - y) for x, y in pts])


def _erode(m, n):
    out = m.copy()
    for _ in range(n):
        p = np.pad(out, 1, constant_values=False)
        e = out.copy()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            e &= p[1 + dy:1 + dy + S, 1 + dx:1 + dx + S]
        out = e
    return out


def _line(g, x0, y0, x1, y1, ch, where):
    n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
    for t in range(n + 1):
        x, y = int(x0 + (x1 - x0) * t / n), int(y0 + (y1 - y0) * t / n)
        if 0 <= y < S and 0 <= x < S and where[y, x]:
            g[y, x] = ch


def seed(phase):
    """Grade mestre: contorno 3 px, bisel claro igual em todas as arestas,
    facetas do centro aos vértices, brilhos multicoloridos iguais em cada
    aresta, e (fase 3) uma trinca com energia igual em cada ponta."""
    pts = polygon(phase)
    body = _mask(pts)
    inner = _erode(body, 3)
    g = np.full((S, S), ".", dtype="<U1")
    g[body] = "K"
    g[inner] = "B"
    band = inner & ~_erode(inner, 2)
    g[band] = "L"
    g[_erode(inner, 2) & ~_erode(inner, 3)] = "D"
    outer = [p for i, p in enumerate(pts) if phase != 3 or i % 2 == 0]
    for (x, y) in outer:
        _line(g, C, C, C + x * 0.86, C - y * 0.86, "D", inner & (g == "B"))
    n = len(pts)
    for i in range(n):
        (x0, y0), (x1, y1) = pts[i], pts[(i + 1) % n]
        for t, ch in ((0.3, "1"), (0.5, "2"), (0.7, "4")) if phase != 3 else ((0.5, "3"),):
            px, py = C + (x0 + (x1 - x0) * t) * 0.82, C - (y0 + (y1 - y0) * t) * 0.82
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                xx, yy = int(px) + dx - 1, int(py) + dy - 1
                if inner[yy, xx]:
                    g[yy, xx] = ch
    if phase == 3:
        zig = [(0.95, 0.0), (0.84, 2.5), (0.72, -2.0), (0.62, 2.5), (0.52, 0.0)]
        for (x, y) in outer:
            ln = math.hypot(x, y)
            ux, uy = x / ln, -y / ln
            px_, py_ = -uy, ux
            cells = [(C + ux * ln * f + px_ * o, C + uy * ln * f + py_ * o) for f, o in zig]
            for (a0, b0), (a1, b1) in zip(cells, cells[1:]):
                _line(g, a0, b0, a1, b1, "K", inner)
                _line(g, a0 + 1, b0, a1 + 1, b1, "A", inner & (g != "K"))
    if phase == 2:
        # gemas facetadas dos 3 núcleos (não são anéis)
        gem = ["...K...", "..KAK..", ".KACAK.", "KACGCAK", ".KACAK.", "..KAK..", "...K..."]
        for (x, y) in cores():
            stamp_np(g, gem, int(round(C + x)) - 3, int(round(C - y)) - 3)
    return "\n".join("".join(r) for r in g)


def stamp_np(g, part, x, y):
    for dy, row in enumerate(part):
        for dx, ch in enumerate(row):
            if ch != "." and 0 <= y + dy < S and 0 <= x + dx < S:
                g[y + dy, x + dx] = ch


GRID_NAME = {1: "boss_pentagon", 2: "boss_shield", 3: "boss_star"}


def ensure_grids(force=False):
    for ph, name in GRID_NAME.items():
        path = GRIDS / f"{name}.txt"
        if force or not path.exists():
            path.write_text(seed(ph) + "\n", encoding="utf-8")


def master(phase):
    rows = [r for r in (GRIDS / f"{GRID_NAME[phase]}.txt").read_text(encoding="utf-8").splitlines() if r]
    return [list(r) for r in rows]


# ------------------------------------------------------------ camada de cima
# Olhos: bloco 37x7 (olho 16 + vão 5 + olho 16). A cor vem de A/C da fase.
_SLIT_L = ["................",                     # fase 1: fenda fria
           "KKKKK...........",
           "KAAAKKKKKK......",
           ".KKCCAAAAAKKKKK.",
           "...KKKKAAAAAAAK.",
           "........KKKKKK..",
           "................"]
_THREAT_L = ["KKK.............",                   # fase 2: sobrancelha brava
             "KAAKKKK.........",
             "KACCAAAKKKK.....",
             ".KACCCCAAAAKKK..",
             "..KKKACCCAAAAAK.",
             ".....KKKKKKKKKK.",
             "................"]
_FURY_L = ["KK..............",                     # fase 3: furioso, maior
           "KAKKK...........",
           "KACAAKKK........",
           "KACCCCAAKKK.....",
           ".KACCCCCCAAKKKK.",
           "..KKACCCCCCCAAK.",
           "....KKKKKKKKKKK."]
_SQUINT_L = ["................", "KKKKK...........", "KKKKKKKKKK......", ".KKKKKAAAAKKKKK.",
             "...KKKKKKKKKKKK.", "................", "................"]
_OFF_L = [r.replace("A", "E").replace("C", "E") for r in _FURY_L]


def _eyes(left):
    return [a + "....." + b for a, b in zip(left, mirror(left))]


EYES = {1: _eyes(_SLIT_L), 2: _eyes(_THREAT_L), 3: _eyes(_FURY_L), "squint": _eyes(_SQUINT_L), "off": _eyes(_OFF_L)}
EYES_CHARGE = {k: [r.replace("A", "C") for r in v] for k, v in EYES.items() if k in (1, 2, 3)}
MOUTHS = {
    1: ["KKKKKKKKKKKKKKKKKKKKK", ".KVK...........KVK...", "..K.............K...."],           # fenda fria
    2: ["KKKKKKKKKKKKKKKKKKKKK", ".KVVK.KVK.KVK.KVK.KVVK"[:21], "..KVK..K...K...K..KVK"[:21],
        "...K.............K..."],                                                             # presas
    3: [".KKKKKKKKKKKKKKKKKKK.", "KVKVKVKKKKKKKKKVKVKVK", "KKVKMMAAAAAAAAMMKVKK.",
        "KMMAAACCCCCCCCAAAMMK.", "KKVKVKKKKKKKKKKVKVKK.", ".KKKKKKKKKKKKKKKKKKK."],          # rugido
    "open": [".KKKKKKKKKKKKKKKKKKK.", "KVKVKKKKKKKKKKKKVKVK.", "KKMMAAAAAAAAAAAAMMKK.",
             "KMAACCCCCCCCCCCCAAMK.", "KKMMAAAAAAAAAAAAMMKK.", "KVKVKKKKKKKKKKKKVKVK.",
             ".KKKKKKKKKKKKKKKKKKK."],
}
SHINE = ["...GGHH..", "..GHHHHH.", ".GHHHH...", "GHHH.....", "HHH......"]
EYES_AT = (38, 42)
MOUTH_AT = (46, 56)
SHINE_AT = (26, 26)
BLADES_AT = ((0, 52), (102, 52))
SHARDS = [(10, 14), (96, 12), (8, 92), (98, 94)]
ORBIT = [(0, 0), (1, -1), (2, -2), (1, -3), (0, -2), (-1, -1), (-1, 0), (0, 1)]


def face(phase, eyes=None, mouth=None, t=0, blades_up=False, shards=True, dy=0):
    g = [["."] * S for _ in range(S)]
    stamp(g, SHINE, SHINE_AT[0], SHINE_AT[1] + dy)
    e = EYES[phase] if eyes is None else eyes
    stamp(g, e, EYES_AT[0], EYES_AT[1] + dy)
    stamp(g, MOUTHS[phase] if mouth is None else mouth, MOUTH_AT[0], MOUTH_AT[1] + dy)
    blade = H.BLADE_UP if blades_up else H.BLADE
    (lx, ly), (rx, ry) = BLADES_AT
    stamp(g, blade, lx, ly + dy)
    stamp(g, mirror(blade), rx, ry + dy)
    if shards and phase == 1:
        for k, (sx, sy) in enumerate(SHARDS):
            ox, oy = ORBIT[(t + 2 * k) % len(ORBIT)]
            stamp(g, H.SHARD, sx + ox * 2, sy + oy * 2)
    return g


def face_anims(phase):
    idle = [face(phase, t=i, dy=(1 if i in (2, 3) else 0)) for i in range(6)]
    charge = [face(phase, eyes=EYES_CHARGE[phase], t=i, blades_up=i % 2 == 0) for i in range(4)]
    attack = [face(phase, eyes=EYES_CHARGE[phase], mouth=MOUTHS[phase], blades_up=True, dy=2),
              face(phase, eyes=EYES_CHARGE[phase], mouth=MOUTHS["open"], blades_up=True, dy=1),
              H.rays(face(phase, eyes=EYES_CHARGE[phase], mouth=MOUTHS["open"], blades_up=True, dy=-2), 56, 56, 58, 64),
              face(phase, eyes=EYES_CHARGE[phase], mouth=MOUTHS["open"], dy=-1),
              face(phase, mouth=MOUTHS["open"]),
              face(phase)]
    hurt = [H.glitch(face(phase, eyes=EYES["squint"], mouth=MOUTHS[2]), range(40, 50), 3),
            H.chips(face(phase, eyes=EYES["squint"], mouth=MOUTHS[2]), [(14, 40), (96, 30), (56, 8)]),
            H.glitch(face(phase, eyes=EYES["squint"], mouth=MOUTHS[2]), range(52, 60), -3, "Y"),
            face(phase)]
    return {"idle": idle, "charge": charge, "attack": attack, "hurt": hurt}


# ------------------------------------------------------------ quadros completos
def composite(phase, face_grid):
    g = [row[:] for row in master(phase)]
    for y in range(S):
        for x in range(S):
            if face_grid[y][x] != ".":
                g[y][x] = face_grid[y][x]
    return g


def death_frames():
    dead = composite(3, face(3, eyes=EYES["off"], mouth=MOUTHS[3], shards=False))
    return [
        H.flash(dead),
        H.rays(dead, 56, 56, 56, 64),
        H.split_pieces(dead, 62, 48, 1, 0),
        H.rays(H.split_pieces(dead, 62, 48, 4, 3, 0.85), 56, 56, 58, 66, "A"),
        H.chips(H.split_pieces(dead, 62, 48, 8, 6, 0.6), [(4, 30), (104, 20), (50, 2), (80, 100)]),
        H.chips(H.split_pieces(dead, 62, 48, 12, 10, 0.4), [(2, 20), (108, 12), (40, 104), (90, 106)]),
        H.split_pieces(dead, 62, 48, 16, 14, 0.2),
        [["."] * S for _ in range(S)],
    ]


def transition_frames(new_phase):
    """Troca de fase: glitch na forma antiga, explosão, glitch na forma nova."""
    old = composite(new_phase - 1, face(new_phase - 1))
    new = composite(new_phase, face(new_phase))
    return [
        H.glitch(old, range(20, 60, 3), 4),
        H.rays(H.flash(old), 56, 56, 40, 60),
        H.rays([["." if (y % 3) else c for c in row] for y, row in enumerate(new)], 56, 56, 52, 64, "A"),
        H.glitch(new, range(30, 90, 4), -4, "Y"),
        H.glitch(new, range(60, 70), 2),
        new,
    ]


def appear_frames():
    full = composite(1, face(1))
    return [
        H.glitch([["." if (y % 4) else c for c in row] for y, row in enumerate(full)], range(10, 100, 2), 5, "Y"),
        H.glitch([["." if (y % 3 == 0) else c for c in row] for y, row in enumerate(full)], range(0, 110, 3), -4),
        H.glitch(full, range(30, 44), 4, "Y"),
        H.glitch(full, range(70, 76), -3),
        full,
    ]


def to_rgba(g, phase):
    return H.to_rgba(g, palette(phase))
