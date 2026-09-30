"""Quadrado, Losango e Hexágono hostis (etapa C), no MESMO estilo do Triângulo.

Mesma técnica e mesmas partes escritas à mão de orb_hostiles.py (olhos
estreitos, presas, lâminas, estacas, rachadura, mordida, veias, estilhaços);
aqui só entram: as paletas de cada um, os espinhos de vértice em 8 direções
(derivados de DOIS desenhos à mão por espelhar/transpor), onde cada parte fica
em cada corpo e o jeito de andar/atacar de cada forma.

O Triângulo NÃO passa por aqui (continua em orb_hostiles.triangle_anims, byte a
byte igual ao aprovado).
"""

import math

import orb_hostiles as H
from orb_creatures import stamp, mirror

# Paletas escuras e saturadas com acento que brilha (nunca amarelo-âmbar).
PALETTES = {
    "square": {   # azul-aço
        "D": (20, 36, 60), "B": (40, 70, 108), "L": (78, 118, 162), "H": (150, 192, 226),
        "A": (70, 196, 255), "C": (212, 244, 255), "G": (230, 248, 255),
    },
    "diamond": {  # magma laranja-avermelhado
        "D": (72, 20, 16), "B": (134, 42, 24), "L": (192, 78, 38), "H": (240, 142, 90),
        "A": (255, 78, 40), "C": (255, 198, 184), "G": (255, 222, 210),
    },
    "hexagon": {  # magenta / vinho
        "D": (58, 12, 40), "B": (108, 24, 72), "L": (162, 50, 112), "H": (222, 122, 182),
        "A": (255, 58, 196), "C": (255, 204, 242), "G": (255, 228, 248),
    },
}

# ------------------------------------------------------------ partes (à mão)
# Espinho de cristal apontando para cima (N) e na diagonal para cima-direita
# (NE). As outras 6 direções são estas espelhadas/transpostas.
SPIKE_N = ["...K...",
           "..KAK..",
           "..KAK..",
           ".KACAK.",
           ".KACAK.",
           "KACCCAK",
           "KACCCAK"]
SPIKE_N_BASE = (3, 6)
SPIKE_NE = [".......K",
            ".....KKK",
            "....KACK",
            "...KACK.",
            "..KACK..",
            ".KACCK..",
            "KACCK...",
            "KKKK...."]
SPIKE_NE_BASE = (2, 6)


def _flip_h(g, b):
    return [r[::-1] for r in g], (len(g[0]) - 1 - b[0], b[1])


def _flip_v(g, b):
    return g[::-1], (b[0], len(g) - 1 - b[1])


def _transpose(g, b):
    return ["".join(g[y][x] for y in range(len(g))) for x in range(len(g[0]))], (b[1], b[0])


def spike(direction):
    """(grade, centro da base) do espinho na direção pedida."""
    if direction in ("N", "S", "W", "E"):
        g, b = SPIKE_N, SPIKE_N_BASE
        if direction == "S":
            g, b = _flip_v(g, b)
        elif direction in ("W", "E"):
            g, b = _transpose(g, b)          # ponta para a esquerda
            if direction == "E":
                g, b = _flip_h(g, b)
        return g, b
    g, b = SPIKE_NE, SPIKE_NE_BASE
    if direction in ("NW", "SW"):
        g, b = _flip_h(g, b)
    if direction in ("SE", "SW"):
        g, b = _flip_v(g, b)
    return g, b


# Lâmina curta para corpos que ocupam o canvas (cabe entre o corpo e a borda).
BLADE_S = ["KK......",
           "KAKK....",
           ".KALKK..",
           "..KHLLKK",
           "..KBBBLK",
           ".KBDDKK.",
           "KDDKK...",
           "KK......"]
BLADE_S_UP = ["..K.....",
              "..KK....",
              "..KAKK..",
              "...KALKK",
              "...KHLLK",
              "..KBBBLK",
              ".KBDDKK.",
              "KDKK...."]

# Onde cada parte fica. Coordenadas no canvas 73 (canto superior esquerdo da parte).
C = 36.5


def poly(n, start, r, aspect=1.0):
    return [(C + aspect * r * math.cos(math.radians(start + i * 360 / n)),
             C - r * math.sin(math.radians(start + i * 360 / n))) for i in range(n)]


# Losango de verdade: diagonais diferentes (não é um quadrado girado).
ASPECT = {"diamond": 26 / 33}


SHAPE = {
    #            lados início raio  direções dos espinhos (na ordem dos vértices)
    "square": (4, 45.0, 28 / math.cos(math.pi / 4), ["NE", "NW", "SW", "SE"]),
    "diamond": (4, 0.0, 33.0, ["E", "N", "W", "S"]),
    "hexagon": (6, 90.0, 32.0, ["N", "NW", "SW", "S", "SE", "NE"]),
}

ANCHORS = {
    "square": {
        "eyes": (24, 22), "mouth": (27, 36), "legs": ((18, 62), (46, 62)),
        "blades": ((0, 38), (65, 38)), "crack": (46, 10), "notch": (58, 48), "vein": (15, 42),
        "spike_in": 3, "shards": [(4, 4), (62, 2), (2, 60)],
        "rays": (36, 36, 34, 40), "cut": (40, 32),
        "chips": [(2, 30), (68, 22), (38, 1)],
    },
    "diamond": {
        "eyes": (24, 27), "mouth": (27, 40), "legs": None,
        "blades": ((6, 44), (59, 44)), "crack": (40, 12), "notch": (48, 46), "vein": (22, 36),
        "spike_in": 6, "shards": [(8, 8), (60, 10), (10, 60)],
        "rays": (11, 36, 1, 8), "cut": (42, 32),
        "chips": [(4, 20), (66, 30), (40, 1)],
    },
    "hexagon": {
        "eyes": (24, 23), "mouth": (27, 36), "legs": ((19, 57), (47, 57)),
        "blades": ((0, 30), (65, 30)), "crack": (43, 9), "notch": (56, 26), "vein": (16, 40),
        "spike_in": 3, "shards": [(4, 4), (64, 6), (2, 64)],
        "rays": (36, 36, 34, 40), "cut": (42, 32),
        "chips": [(2, 32), (68, 24), (40, 1)],
    },
}

# órbita dos estilhaços: o mesmo passeio (à mão) somado a cada centro
ORBIT = [(0, 0), (1, -1), (2, -2), (1, -3), (0, -2), (-1, -1), (-1, 0), (0, 1)]


def pose(name, eyes="glare", mouth="fangs", body_dy=0, legs=(0, 0), blades=("", ""), shards=0,
         vein_hot=False, spikes_hot=False, show_shards=True):
    an = ANCHORS[name]
    n, start, ro, dirs = SHAPE[name]
    body = H.read_grid(name)
    h, w = len(body), len(body[0])
    g = [["."] * w for _ in range(h)]
    if an["legs"]:
        for (lx, ly), lift in zip(an["legs"], legs):
            H.under(g, H.LEG_UP if lift else H.LEG, lx, ly - lift)
    for y in range(h):
        sy = y - body_dy
        if 0 <= sy < h:
            for x in range(w):
                if body[sy][x] != ".":
                    g[y][x] = body[sy][x]
    # espinhos nos vértices: a base entra 'spike_in' px no corpo, a ponta sai
    for (vx, vy), d in zip(poly(n, start, ro, ASPECT.get(name, 1.0)), dirs):
        part, (bx, by) = spike(d)
        if spikes_hot:
            part = [r.replace("A", "C") for r in part]
        ix, iy = C - vx, C - vy
        ln = math.hypot(ix, iy)
        px = vx + ix / ln * an["spike_in"] - bx
        py = vy + iy / ln * an["spike_in"] - by + body_dy
        stamp(g, part, int(round(px)), int(round(py)))
    nx, ny = an["notch"]
    H.erase(g, H.NOTCH, nx, ny + body_dy)
    cx, cy = an["crack"]
    stamp(g, H.CRACK, cx, cy + body_dy)
    vx, vy = an["vein"]
    stamp(g, [r.replace("A", "C") for r in H.VEIN] if vein_hot else H.VEIN, vx, vy + body_dy)
    ex, ey = an["eyes"]
    stamp(g, H.EYES[eyes], ex, ey + body_dy)
    mx, my = an["mouth"]
    stamp(g, H.MOUTHS[mouth], mx, my + body_dy)
    (blx, bly), (brx, bry) = an["blades"]
    stamp(g, BLADE_S_UP if blades[0] == "up" else BLADE_S, blx, bly + body_dy)
    stamp(g, mirror(BLADE_S_UP if blades[1] == "up" else BLADE_S), brx, bry + body_dy)
    if show_shards:
        for k, (sx, sy) in enumerate(an["shards"]):
            ox, oy = ORBIT[(shards + 3 * k) % len(ORBIT)]
            H.under(g, H.SHARD, sx + ox, sy + oy)
    return g


def anims(name):
    an = ANCHORS[name]
    hover = an["legs"] is None
    if hover:
        # losango: flutua (sobe e desce) e bate as lâminas
        bob = [0, -1, -2, -1, 0, 1, 2, 1]
        idle = [pose(name, body_dy=bob[i], shards=i, vein_hot=i in (2, 3)) for i in range(6)]
        move = [pose(name, body_dy=bob[i], shards=i, blades=("up", "") if i % 4 < 2 else ("", "up"))
                for i in range(8)]
    else:
        idle = [pose(name, body_dy=dy, shards=i, vein_hot=hot)
                for i, (dy, hot) in enumerate([(0, False), (0, False), (1, True), (1, True), (0, False), (0, False)])]
        # passo pesado: uma estaca sobe, o corpo afunda no apoio
        steps = [(0, (0, 0)), (-1, (2, 0)), (0, (1, 0)), (1, (0, 0)), (0, (0, 0)), (-1, (0, 2)), (0, (0, 1)), (1, (0, 0))]
        move = [pose(name, body_dy=dy, legs=lg, shards=i) for i, (dy, lg) in enumerate(steps)]
    charge = [pose(name, eyes="charge", body_dy=1, vein_hot=True, spikes_hot=(i % 2 == 0)) for i in range(4)]
    charge[1] = [r[1:] + ["."] for r in charge[1]]
    charge[3] = [["."] + r[:-1] for r in charge[3]]
    rx, ry, r0, r1 = an["rays"]
    attack = [
        pose(name, eyes="charge", mouth="grimace", body_dy=2, blades=("up", "up"), vein_hot=True),
        pose(name, eyes="charge", mouth="grimace", body_dy=2, blades=("up", "up"), vein_hot=True, spikes_hot=True),
        pose(name, eyes="charge", mouth="open", body_dy=1, blades=("up", "up"), vein_hot=True, spikes_hot=True),
        H.rays(pose(name, eyes="charge", mouth="open", body_dy=-2, vein_hot=True, spikes_hot=True), rx, ry, r0, r1),
        pose(name, eyes="glare", mouth="open", body_dy=-1, vein_hot=True),
        pose(name, eyes="glare", mouth="fangs", body_dy=0),
    ]
    base = pose(name, eyes="squint", mouth="grimace", shards=2)
    ch = an["chips"]
    hurt = [
        H.flash(base),
        H.chips(H.glitch(base, range(18, 30), 2), ch),
        H.chips(H.glitch(base, range(36, 46), -2, "Y"), [(x + 2, y + 3) for x, y in ch]),
        pose(name, eyes="glare", mouth="grimace"),
    ]
    dead = pose(name, eyes="off", mouth="grimace", show_shards=False)
    ct, cb = an["cut"]
    death = [
        H.flash(pose(name, eyes="squint", mouth="grimace", show_shards=False)),
        H.rays(dead, 36, 36, 30, 36),
        H.split_pieces(dead, ct, cb, 1, 0),
        H.rays(H.split_pieces(dead, ct, cb, 3, 2, 0.85), 36, 36, 32, 38, "A"),
        H.chips(H.split_pieces(dead, ct, cb, 6, 5, 0.6), [(2, 20), (66, 12), (30, 0), (50, 62)]),
        H.chips(H.split_pieces(dead, ct, cb, 9, 9, 0.3), [(0, 14), (69, 6), (26, 64), (56, 66)]),
    ]
    full = pose(name)
    appear = [
        H.glitch([["." if (y % 4) else c for c in row] for y, row in enumerate(full)], range(10, 60, 2), 4, "Y"),
        H.glitch([["." if (y % 3 == 0) else c for c in row] for y, row in enumerate(full)], range(0, 70, 3), -3),
        H.glitch(full, range(20, 30), 3, "Y"),
        H.glitch(full, range(40, 44), -2),
        full,
    ]
    glitch_anim = [H.glitch(full, range(14, 22), 3), H.glitch(full, range(34, 40), -3, "Y"), full]
    return {"idle": idle, "move": move, "charge": charge, "attack": attack, "hurt": hurt,
            "death": death, "appear": appear, "glitch": glitch_anim}


BUILDERS = {name: (lambda n=name: anims(n)) for name in ANCHORS}
