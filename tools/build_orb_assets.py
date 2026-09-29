"""Gera TODOS os sprites ativos do Project ORB em assets/sprites/.

Fontes (somente leitura, originais intactos como backup fora de assets/):
  1. art-source/projetofinal-29/   (prioridade máxima)
  2. art-source/ProjetoFinal_TCC/
  3. o que não existir nas duas é GERADO aqui.

Regras que o script garante:
- escala única: 1 pixel de arte = 4 pixels de mundo (Constants.PIXEL_SCALE)
  para ORB, inimigos, boss, mundo, efeitos e UI; tudo é pixel art nativa
  (sem antialiasing, alfa 0/255), desenhado em escala inteira no jogo;
- o ORB usa só a paleta do idle canônico (o script falha se sobrar cor fora);
- inimigos/boss seguem o gabarito medido nos inimigos do TCC (marcadores de
  vértice, contorno, faixa interna, núcleo, arcos), redesenhados a 1/4 do
  tamanho original como pixel art (decisão da Fase 0);
- nomes limpos (sem espaços, acentos ou erros de digitação); a tabela
  nome-limpo -> original fica em sprites/manifest.json ("sources").

Reproduzível: a mesma entrada gera os mesmos bytes. Nunca apaga arquivos;
arquivos antigos que não são mais gerados são apenas listados.

Uso:
    python tools/build_orb_assets.py            # gera assets/sprites/
    python tools/build_orb_assets.py --preview  # e folhas de contato em tmp/asset-preview/
"""

import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SRC_29 = ROOT / "art-source" / "projetofinal-29"
SRC_TCC = ROOT / "art-source" / "ProjetoFinal_TCC"
OUT = ROOT / "assets" / "sprites"
PREVIEW = ROOT / "tmp" / "asset-preview"
PIXEL = 4

# ------------------------------------------------------------------ paleta
# Amostrada dos inimigos, plataformas e painéis do TCC. Paleta fechada: todo
# pixel gerado (fora o que vem pronto das pastas oficiais) é uma destas cores.
DARK = (10, 11, 27)
BAND = (24, 24, 50)
MARK_IN = (33, 25, 59)
SPOKE = (62, 33, 91)
OUTLINE = (119, 59, 170)
MAG = (205, 69, 231)
LILAC = (224, 141, 255)
WHITE = (242, 241, 255)
CYAN = (81, 218, 234)
PALE_CYAN = (170, 246, 255)
TEAL = (31, 72, 92)
RED = (239, 55, 55)
PALE_RED = (255, 150, 170)
NEON_MAG = (225, 18, 255)
NEON_MAG2 = (173, 61, 203)
NEON_CYAN = (0, 255, 242)
NEON_CYAN2 = (40, 170, 190)
PANEL_TOP = (27, 28, 54)
PANEL_DARK = (11, 12, 26)
PANEL_LINE = (60, 56, 80)
PANEL_EDGE = (53, 39, 86)
DASH = (84, 180, 215)
VOID = (5, 5, 12)
PORTAL_1 = (120, 96, 250)
PORTAL_2 = (168, 128, 255)
PORTAL_IN = (58, 38, 118)

# Clareamento dentro da paleta (flash de dano, brilho): cada cor sobe um
# degrau. Nunca cria cor nova.
LIGHTER = {
    DARK: BAND, BAND: SPOKE, MARK_IN: SPOKE, SPOKE: OUTLINE, OUTLINE: MAG, MAG: LILAC,
    LILAC: WHITE, WHITE: WHITE, TEAL: CYAN, CYAN: PALE_CYAN, PALE_CYAN: WHITE,
    RED: PALE_RED, PALE_RED: WHITE,
}

written = []
sources = {}
manifest = {"generator": "tools/build_orb_assets.py", "pixel_scale": PIXEL,
            "source_priority": ["art-source/projetofinal-29", "art-source/ProjetoFinal_TCC", "gerado"]}


# ================================================================ utilidades

def save(a, rel, source="gerado"):
    im = a if isinstance(a, Image.Image) else Image.fromarray(np.ascontiguousarray(a, dtype=np.uint8), "RGBA")
    path = OUT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path, optimize=False, compress_level=9)
    written.append(rel)
    sources[rel] = source


def load(root, rel):
    return np.array(Image.open(root / rel).convert("RGBA"), dtype=np.uint8)


def canvas(w, h=None):
    return np.zeros((h or w, w, 4), dtype=np.uint8)


def paint(a, m, col):
    a[m] = (*col, 255)


def dist(w, h, cx, cy):
    yy, xx = np.mgrid[0:h, 0:w]
    return np.hypot(xx + 0.5 - cx, yy + 0.5 - cy)


def m_disc(w, h, cx, cy, r):
    return dist(w, h, cx, cy) <= r


def m_ring(w, h, cx, cy, r, width=1.0):
    d = dist(w, h, cx, cy)
    return (d >= r - width / 2) & (d < r + width / 2)


def m_arc(w, h, cx, cy, r, a0, a1, width=1.0):
    yy, xx = np.mgrid[0:h, 0:w]
    ang = np.degrees(np.arctan2(-(yy + 0.5 - cy), xx + 0.5 - cx)) % 360
    span = (a1 - a0) % 360
    inside = ((ang - a0) % 360) <= span
    return m_ring(w, h, cx, cy, r, width) & inside


def m_poly(w, h, pts, ss=8):
    im = Image.new("L", (w * ss, h * ss), 0)
    ImageDraw.Draw(im).polygon([(x * ss, y * ss) for x, y in pts], fill=255)
    return np.array(im.resize((w, h), Image.BOX)) >= 128


def outline(m):
    """Borda de 1 px (4-vizinhança). Fora do canvas conta como vazio: formas
    que encostam na borda também ganham contorno (np.roll dava a volta)."""
    p = np.pad(m, 1, constant_values=False)
    er = m.copy()
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        er &= p[1 + dy:1 + dy + m.shape[0], 1 + dx:1 + dx + m.shape[1]]
    return m & ~er


def m_line(w, h, x0, y0, x1, y1):
    m = np.zeros((h, w), dtype=bool)
    x0, y0, x1, y1 = int(math.floor(x0)), int(math.floor(y0)), int(math.floor(x1)), int(math.floor(y1))
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        if 0 <= x0 < w and 0 <= y0 < h:
            m[y0, x0] = True
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy
    return m


def shift(a, dx, dy):
    out = np.zeros_like(a)
    h, w = a.shape[:2]
    ys0, ys1 = max(0, -dy), min(h, h - dy)
    xs0, xs1 = max(0, -dx), min(w, w - dx)
    out[ys0 + dy:ys1 + dy, xs0 + dx:xs1 + dx] = a[ys0:ys1, xs0:xs1]
    return out


def lighten(a, steps=1):
    out = a.copy()
    for _ in range(steps):
        cur = out.copy()
        for src, dst in LIGHTER.items():
            m = (cur[..., 3] > 0) & np.all(cur[..., :3] == src, axis=2)
            out[m, :3] = dst
    return out


def recolor(a, mapping):
    out = a.copy()
    for src, dst in mapping.items():
        m = (a[..., 3] > 0) & np.all(a[..., :3] == src, axis=2)
        out[m, :3] = dst
    return out


BAYER4 = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], dtype=np.float32) / 16.0


def dither(a, level):
    h, w = a.shape[:2]
    th = np.tile(BAYER4, (h // 4 + 1, w // 4 + 1))[:h, :w]
    out = a.copy()
    out[..., 3][th < level] = 0
    return out


def bbox(a, thr=1):
    ys, xs = np.nonzero(a[..., 3] >= thr)
    if len(xs) == 0:
        return None
    return int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())


def snap(c, r, deg):
    """Offset inteiro a partir do centro: pontos simétricos ficam simétricos."""
    return c + round(r * math.cos(math.radians(deg))), c - round(r * math.sin(math.radians(deg)))


def palette_of(a):
    px = a[a[..., 3] > 0][:, :3]
    return {tuple(int(v) for v in p) for p in px}


def quantize(a, pal):
    """Cada pixel visível vira a cor mais próxima da paleta (distância RGB)."""
    pal_arr = np.array(sorted(pal), dtype=np.int32)
    out = a.copy()
    m = a[..., 3] > 0
    px = a[m][:, :3].astype(np.int32)
    d = ((px[:, None, :] - pal_arr[None, :, :]) ** 2).sum(axis=2)
    out[m, :3] = pal_arr[d.argmin(axis=1)]
    out[..., 3] = np.where(a[..., 3] > 0, 255, 0)
    return out


def write_anims(prefix, anims, source="gerado"):
    counts = {}
    for name, frames in anims.items():
        for i, f in enumerate(frames, 1):
            save(f, f"{prefix}/{name}_{i:02d}.png", source)
        counts[name] = len(frames)
    return counts


# ============================================================ inimigos

# Gabarito medido no TCC (canvas 128), aqui a 1/2: 128 px a 2x viram 64 px a 4x.
# Canvas ímpar (73): o centro é o pixel 36, pontos simétricos ficam simétricos.
ENEMY_C = 73
T = {"arc_r": 19.5, "band_out": 0.765, "band_in": 0.53, "core_ring": 7.5, "core_mag": 3.6, "core_white": 1.6}

SHAPES = {
    #            lados início  raio-externo marcadores (raio, ângulos)                      fonte
    "triangle": (3, 90.0, 30.0, (26, [90, 210, 330]), "ProjetoFinal_TCC/enemies/triangle.png"),
    "diamond": (4, 0.0, 30.0, (26, [0, 90, 180, 270]), "ProjetoFinal_TCC/enemies/square.png"),
    "pentagon": (5, 90.0, 30.0, (26, [90 + 72 * i for i in range(5)]), "ProjetoFinal_TCC/enemies/pentagonon.png"),
    "circle": (0, 90.0, 30.0, (26, [22.5 * i for i in range(16)]), "ProjetoFinal_TCC/enemies/cricle.png"),
    "hexagon": (6, 90.0, 30.0, (26, [90 + 60 * i for i in range(6)]), "gerado"),
    # quadrado alinhado aos eixos, marcadores no MEIO dos lados (onde ficam os
    # pontos fracos do QUADRADO): apótema 25 -> raio externo 25/cos45.
    "square": (4, 45.0, 25 / math.cos(math.pi / 4), (24, [0, 90, 180, 270]), "gerado"),
}


def poly(c, r, n, start):
    return [(c + r * math.cos(math.radians(start + i * 360 / n)),
             c - r * math.sin(math.radians(start + i * 360 / n))) for i in range(n)]


def draw_body(S, n, start, ro, markers, k=1.0, outline_col=OUTLINE, spoke_to=None, extra=None):
    c = S / 2
    a = canvas(S)
    if n:
        outer = m_poly(S, S, poly(c, ro, n, start))
        band_o = m_poly(S, S, poly(c, ro * T["band_out"], n, start))
        band_i = m_poly(S, S, poly(c, ro * T["band_in"], n, start))
    else:
        outer = m_disc(S, S, c, c, ro)
        band_o = m_disc(S, S, c, c, ro * T["band_out"])
        band_i = m_disc(S, S, c, c, ro * T["band_in"])
    paint(a, outer, DARK)
    paint(a, band_o & ~band_i, BAND)
    paint(a, outline(band_o), TEAL)
    core_clear = m_disc(S, S, c, c, (T["core_ring"] + 1.5) * k)
    for (x, y) in (spoke_to or markers):
        paint(a, m_line(S, S, c, c, x + 0.5, y + 0.5) & ~core_clear, SPOKE)
    if extra:
        extra(a)
    paint(a, outline(outer), outline_col)
    mr = 2 if k < 1.5 else 3
    for (x, y) in markers:
        paint(a, m_disc(S, S, x + 0.5, y + 0.5, mr + 0.5), MARK_IN)
        paint(a, m_ring(S, S, x + 0.5, y + 0.5, mr, 1.0), MAG)
    return a


def draw_core(a, k=1.0, s=1.0, bright=0, hue=False):
    S = a.shape[0]
    c = S / 2
    ring_w = 1.0 if k < 1.5 else 2.0
    paint(a, m_disc(S, S, c, c, T["core_ring"] * k + ring_w / 2), DARK)
    paint(a, m_ring(S, S, c, c, T["core_ring"] * k, ring_w), PALE_CYAN if bright >= 2 else CYAN)
    core = RED if hue else MAG
    paint(a, m_disc(S, S, c, c, T["core_mag"] * k * s), core)
    if bright >= 1:
        paint(a, m_ring(S, S, c, c, T["core_mag"] * k * s - 0.5, 1.0), PALE_RED if hue else LILAC)
    paint(a, m_disc(S, S, c, c, T["core_white"] * k * s + (0.6 if bright >= 2 else 0)), WHITE)
    return a


def draw_arcs(a, theta, r, width=1.0, cols=(MAG, CYAN), extra=()):
    S = a.shape[0]
    c = S / 2
    for (a0, a1), col in zip(((60, 180), (240, 360)), cols):
        paint(a, m_arc(S, S, c, c, r, a0 + theta, a1 + theta, width), col)
    for (rr, a0, a1, col, wd) in extra:
        paint(a, m_arc(S, S, c, c, rr, a0 + theta, a1 + theta, wd), col)
    return a


def flash_markers(a, markers, amount):
    if amount <= 0:
        return a
    S = a.shape[0]
    col = WHITE if amount >= 0.66 else LILAC
    r = 2 if S < 100 else 3
    for (x, y) in markers:
        paint(a, m_disc(S, S, x + 0.5, y + 0.5, r + 0.5), col)
    return a


def enemy_frames(key):
    n, start, ro, (mr, angles), src = SHAPES[key]
    S = ENEMY_C
    c = S // 2  # pixel central (inteiro)
    markers = [snap(c, mr, ang) for ang in angles]
    spoke = [snap(c, ro * 0.86, start + i * 360 / n) for i in range(n)] if key == "square" else None
    body = draw_body(S, n, start, ro, markers, spoke_to=spoke)

    def frame(theta=0.0, s=1.0, bright=0, flash=0.0, light=0, dx=0, dy=0, dis=0.0):
        a = draw_core(body.copy(), s=s, bright=bright)
        a = draw_arcs(a, theta, T["arc_r"])
        a = flash_markers(a, markers, flash)
        if light:
            a = lighten(a, light)
        if dis:
            a = dither(a, dis)
        return shift(a, dx, dy)

    sway = [0, 6, 10, 6, 0, -6]
    anims = {
        "idle": [frame(theta=sway[i], s=[1.0, 1.0, 1.2, 1.2, 1.0, 1.0][i], bright=[0, 0, 1, 1, 0, 0][i])
                 for i in range(6)],
        "move": [frame(theta=-45 * i, dy=[0, -1, -1, 0, 0, 1, 1, 0][i]) for i in range(8)],
        "attack": [frame(theta=t, s=s, bright=b, flash=f) for t, s, b, f in zip(
            [0, 20, 50, 95, 130, 150], [1.0, 1.2, 1.4, 1.7, 1.3, 1.1], [0, 1, 1, 2, 1, 0],
            [0, 0.4, 0.7, 1.0, 0.5, 0])],
        "hurt": [frame(light=3, dx=1), frame(light=2, dx=-1), frame(light=1, dx=1), frame()],
        "death": [frame(theta=20 * i, s=1.3 + 0.3 * i, bright=2, light=min(3, i), dis=d)
                  for i, d in enumerate([0.0, 0.2, 0.4, 0.6, 0.8, 0.9])],
    }
    return anims, markers, src


def build_enemies():
    out = {}
    for key in SHAPES:
        anims, markers, src = enemy_frames(key)
        source = f"redesenho de art-source/{src}" if src != "gerado" else "gerado"
        counts = write_anims(f"enemies/{key}", anims, source)
        low = max(bbox(f)[3] for f in anims["idle"] + anims["move"])
        n, start, ro, (mr, angles), _ = SHAPES[key]
        out[key] = {"canvas": ENEMY_C, "frames": counts, "center_px": ENEMY_C / 2,
                    "baseline_px": ENEMY_C - 1 - low, "sides": n, "start_deg": start,
                    "marker_r_px": mr, "outer_r_px": round(ro, 3), "markers_px": markers, "source": src}
    return out


BOSS_C = 113
BOSS_K = 1.75


def build_boss():
    S = BOSS_C
    c = S // 2
    k = BOSS_K
    ro = 30.0 * k
    mr = 26 * k                       # 45.5
    markers = [snap(c, mr, 90 + 60 * i) for i in range(6)]
    subs = [snap(c, mr * 0.5, 90 + 120 * i) for i in range(3)]

    def extra(a):
        for i in range(3):
            x0, y0 = subs[i]
            x1, y1 = subs[(i + 1) % 3]
            paint(a, m_line(S, S, x0 + 0.5, y0 + 0.5, x1 + 0.5, y1 + 0.5), SPOKE)
        paint(a, outline(m_poly(S, S, poly(S / 2, ro * 0.36, 6, 0))), TEAL)

    def sub_cores(a, glow=0):
        for (x, y) in subs:
            paint(a, m_disc(S, S, x + 0.5, y + 0.5, 4.5), DARK)
            paint(a, m_ring(S, S, x + 0.5, y + 0.5, 3.5, 1.0), PALE_CYAN if glow >= 2 else CYAN)
            paint(a, m_disc(S, S, x + 0.5, y + 0.5, 1.6 + (1 if glow else 0)), LILAC if glow else MAG)
            if glow >= 2:
                paint(a, m_disc(S, S, x + 0.5, y + 0.5, 1.0), WHITE)
        return a

    body = draw_body(S, 6, 90, ro, markers, k=k, extra=extra)
    body_red = draw_body(S, 6, 90, ro, markers, k=k, extra=extra, outline_col=RED)
    arc_r = 19.5 * k
    inner = [(arc_r * 0.78, 200, 290, MAG, 1.0), (arc_r * 0.78, 20, 110, CYAN, 1.0)]

    def frame(b=body, theta=0.0, s=1.0, bright=0, flash=0.0, glow=0, hue=False, cols=(MAG, CYAN),
              light=0, dis=0.0, beam=0, orbs=0.0):
        a = draw_core(b.copy(), k=k, s=s, bright=bright, hue=hue)
        a = sub_cores(a, glow)
        a = draw_arcs(a, theta, arc_r, 2.0, cols, inner)
        if orbs:
            for i in range(6):
                x, y = snap(c, mr * (0.55 + 0.5 * orbs), 90 + 60 * i + theta)
                paint(a, m_disc(S, S, x + 0.5, y + 0.5, 3.5), RED)
                paint(a, m_disc(S, S, x + 0.5, y + 0.5, 1.6), WHITE)
        if beam:
            half = beam
            a[c - half - 1:c + half + 2, :] = (*LILAC, 255)
            a[c - max(0, half - 1):c + max(0, half - 1) + 1, :] = (*WHITE, 255)
        a = flash_markers(a, markers, flash)
        if light:
            a = lighten(a, light)
        if dis:
            a = dither(a, dis)
        return a

    anims = {
        "idle": [frame(theta=[0, 5, 8, 5, 0, -5][i], s=[1, 1, 1.15, 1.15, 1, 1][i], bright=[0, 0, 1, 1, 0, 0][i])
                 for i in range(6)],
        "powerup": [frame(theta=30 * i, s=1 + 0.12 * i, bright=min(2, i // 2), flash=f, glow=min(2, i // 2))
                    for i, f in enumerate([0, 0.2, 0.4, 0.7, 1.0, 0.6])],
        "orbs": [frame(theta=12 * i, s=1 + 0.08 * i, bright=min(2, i), glow=g, orbs=o)
                 for i, (g, o) in enumerate([(1, 0), (2, 0), (2, 0.1), (1, 0.55), (1, 1.0)])],
        "beam": [frame(s=1 + 0.1 * i, bright=min(2, i), beam=bm) for i, bm in enumerate([0, 1, 3, 4, 2])],
        "enraged": [frame(b=body_red, theta=-40 * i, s=1.1 + 0.1 * (i % 2), bright=1, hue=True, cols=(RED, MAG))
                    for i in range(4)],
        "death": [frame(theta=25 * i, s=1.2 + 0.3 * i, bright=2, flash=max(0, 1 - 0.3 * i), light=min(3, i), dis=d)
                  for i, d in enumerate([0.0, 0.15, 0.35, 0.55, 0.75, 0.9])],
    }
    counts = write_anims("boss", anims)
    low = max(bbox(f)[3] for f in anims["idle"])
    return {"canvas": S, "frames": counts, "center_px": S / 2, "baseline_px": S - 1 - low,
            "sides": 6, "start_deg": 90.0, "marker_r_px": mr, "side_r_px": mr * math.cos(math.pi / 6),
            "core_r_px": mr * 0.5, "outer_r_px": ro, "markers_px": markers, "subcores_px": subs,
            "source": "gerado"}


# ================================================================ player

PLAYER_C = 48


def lighten_orb(a, pal, steps):
    """Flash do ORB dentro da própria paleta: cada cor vira a cor mais clara da
    paleta mais próxima dela, 'steps' vezes."""
    pal_sorted = sorted(pal, key=sum)
    out = a.copy()
    for _ in range(steps):
        m = out[..., 3] > 0
        px = out[m][:, :3].astype(int)
        new = []
        for p in px:
            lum = int(p.sum())
            brighter = [c for c in pal_sorted if sum(c) > lum + 30]
            if not brighter:
                new.append(tuple(p))
                continue
            new.append(min(brighter, key=lambda c: sum((c[i] - p[i]) ** 2 for i in range(3))))
        out[m, :3] = np.array(new, dtype=np.uint8)
    return out


def build_player():
    """ORB: frames oficiais de projetofinal-29 ajustados à paleta do idle.

    A animação completa é montada a partir deles; a tarefa A2 redesenha cada
    estado. Aqui garantimos a paleta travada (o script falha se sobrar cor).
    """
    d = "personagem principal/"
    idle_src = load(SRC_29, d + "Personagem principal.png")
    pal = palette_of(idle_src)
    manifest["orb_palette"] = sorted(list(p) for p in pal)
    raw = {
        "idle": idle_src,
        "blink": quantize(load(SRC_29, d + "principal piscando.png"), pal),
        "run": quantize(load(SRC_29, d + "principal correndo.png"), pal),
        "jump": quantize(load(SRC_29, d + "principal pulando.png"), pal),
        "shoot": quantize(load(SRC_29, d + "principal atirando.png"), pal),
    }
    FEET = PLAYER_C - 5
    base = {}
    for k, a in raw.items():
        bb = bbox(a)
        dx = int(round(PLAYER_C / 2 - (bb[0] + bb[2] + 1) / 2))
        dy = FEET - bb[3]
        cv = canvas(PLAYER_C)
        cv[dy:dy + 32, dx:dx + 32] = a
        base[k] = cv

    def rows(a):
        bb = bbox(a)
        return bb[1], bb[3]

    def squash(a, n=1):
        top, bot = rows(a)
        out = a.copy()
        for _ in range(n):
            mid = (top + bot) // 2
            out = np.concatenate([np.zeros_like(out[:1]), out[:mid], out[mid + 1:]], axis=0)
        return out

    def stretch(a, n=1):
        top, bot = rows(a)
        out = a.copy()
        for _ in range(n):
            mid = (top + bot) // 2
            out = np.concatenate([out[1:mid + 1], out[mid:mid + 1], out[mid + 1:]], axis=0)
        return out

    def lean(a, amount):
        top, bot = rows(a)
        out = a.copy()
        for y in range(top, bot + 1):
            out[y] = np.roll(a[y], int(round(amount * (bot - y) / max(1, bot - top))), axis=0)
        return out

    def rot_grounded(a):
        r = np.rot90(a, k=-1).copy()
        bb = bbox(r)
        return shift(r, int(round(PLAYER_C / 2 - (bb[0] + bb[2] + 1) / 2)), FEET - bb[3])

    I, B, R, J, S = base["idle"], base["blink"], base["run"], base["jump"], base["shoot"]
    top, bot = rows(I)
    # Idle canônico (32x32) no canvas: linhas 3..22 = corpo+braços; 23..29 =
    # pernas e pés. Deslocamento do canvas em relação ao arquivo 32x32:
    oy = bot - 29
    ox = int(round(PLAYER_C / 2 - 16))
    LEG_TOP = 23 + oy
    left_leg = np.zeros(I.shape[:2], dtype=bool)
    right_leg = np.zeros(I.shape[:2], dtype=bool)
    left_leg[LEG_TOP:, :PLAYER_C // 2] = True
    right_leg[LEG_TOP:, PLAYER_C // 2:] = True

    def walk_frame(body_dy, lift_l, lift_r):
        """Corpo/olhos idênticos ao idle; só o quique e os pés mudam."""
        out = canvas(PLAYER_C)
        for mask, lift in ((left_leg, lift_l), (right_leg, lift_r)):
            leg = np.where(mask[..., None], I, 0).astype(np.uint8)
            if body_dy < 0:
                # corpo subiu: a coxa cresce 1 px para não abrir fresta
                leg[LEG_TOP - 1] = np.where(mask[LEG_TOP][:, None], I[LEG_TOP], 0)
            leg = shift(leg, 1 if lift else 0, -lift)   # pé levantado sobe e avança
            out = np.where(leg[..., 3:4] > 0, leg, out)
        body = I.copy()
        body[LEG_TOP:] = 0
        body = shift(body, 0, body_dy)
        return np.where(body[..., 3:4] > 0, body, out).astype(np.uint8)

    def extend_legs(a, n):
        """Pernas estendidas (descida do pulo): repete a linha da canela."""
        out = a.copy()
        shin = LEG_TOP + 1
        for _ in range(n):
            out = np.concatenate([out[1:shin + 1], out[shin:shin + 1], out[shin + 1:]], axis=0)
        return out

    def tuck(a, n):
        """Pés recolhidos: some n linhas da canela, os pés sobem (corpo parado)."""
        shin = LEG_TOP + 1
        out = np.concatenate([a[:shin], a[shin + n:], np.zeros_like(a[:n])], axis=0)
        return out

    def stretch_head(a, n=1):
        """Estica a cabeça duplicando uma linha ACIMA do visor (olhos iguais)."""
        row = top + 3
        out = a.copy()
        for _ in range(n):
            out = np.concatenate([out[1:row + 1], out[row:row + 1], out[row + 1:]], axis=0)
        return out

    def trail(a, steps):
        """Rastro do dash: cópias atrás, pontilhadas, na própria paleta."""
        out = canvas(PLAYER_C)
        for k, lvl in zip(range(steps, 0, -1), (0.75, 0.5, 0.3)):
            ghost = dither(lighten_orb(shift(a, -4 * k, 0), pal, 1), lvl)
            out = np.where(ghost[..., 3:4] > 0, ghost, out)
        return np.where(a[..., 3:4] > 0, a, out).astype(np.uint8)

    anims = {
        # idle exatamente como a referência; o piscar oficial 1x a cada 12 frames
        "idle": [I] * 11 + [B],
        "walk": [walk_frame(1, 0, 0), walk_frame(0, 2, 0), walk_frame(-1, 1, 0),
                 walk_frame(1, 0, 0), walk_frame(0, 0, 2), walk_frame(-1, 0, 1)],
        # subida / ápice / descida (o jogo escolhe pela velocidade vertical)
        # O "pulando" oficial tem o ORB ~15% menor que o idle (esfera de 17-18
        # px contra 20-21): não é coerente, então o pulo é redesenhado do idle.
        "jump": [stretch_head(tuck(I, 3), 1), tuck(I, 2), extend_legs(I, 2)],
        "dash": [trail(lean(R, 1), 1), trail(lean(R, 2), 2), trail(lean(R, 3), 3), trail(lean(R, 2), 2),
                 trail(lean(R, 1), 1)],
        "attack": [lighten_orb(S, pal, 1), shift(S, -1, 0), S, I],
        "hurt": [lighten_orb(I, pal, 2), shift(lighten_orb(I, pal, 1), -1, 0), shift(I, 1, 0), I],
        "death": [lighten_orb(I, pal, 2), squash(I, 2), rot_grounded(I), dither(rot_grounded(I), 0.4),
                  dither(rot_grounded(I), 0.8)],
    }
    for name, frames in anims.items():
        for f in frames:
            extra = palette_of(f) - pal
            if extra:
                raise SystemExit(f"ORB/{name}: {len(extra)} cor(es) fora da paleta do idle: {sorted(extra)[:5]}")
    counts = write_anims("player", anims, "art-source/projetofinal-29/personagem principal (paleta do idle)")
    low = bbox(anims["idle"][0])[3]
    return {"canvas": PLAYER_C, "frames": counts, "baseline_px": PLAYER_C - 1 - low}


# ================================================================ tiros

def scale2x(a):
    """EPX/Scale2x: amplia 2x preservando bordas de pixel art."""
    h, w = a.shape[:2]
    p = np.pad(a, ((1, 1), (1, 1), (0, 0)), mode="edge")
    P = p[1:-1, 1:-1]
    A, B, C, D = p[:-2, 1:-1], p[1:-1, 2:], p[1:-1, :-2], p[2:, 1:-1]

    def eq(x, y):
        return np.all(x == y, axis=2)
    out = np.zeros((h * 2, w * 2, 4), dtype=np.uint8)
    out[0::2, 0::2] = np.where((eq(C, A) & ~eq(C, D) & ~eq(A, B))[..., None], A, P)
    out[0::2, 1::2] = np.where((eq(A, B) & ~eq(A, C) & ~eq(B, D))[..., None], B, P)
    out[1::2, 0::2] = np.where((eq(D, C) & ~eq(D, B) & ~eq(C, A))[..., None], C, P)
    out[1::2, 1::2] = np.where((eq(B, D) & ~eq(B, A) & ~eq(D, C))[..., None], D, P)
    return out


def rotate_pixel(a, deg):
    """Rotação estilo RotSprite: Scale2x 3x, gira com nearest, amostra 1/8."""
    if deg % 90 == 0:
        return np.rot90(a, k=int(deg // 90) % 4).copy()
    big = scale2x(scale2x(scale2x(a)))
    im = Image.fromarray(big, "RGBA").rotate(deg, resample=Image.NEAREST, expand=False)
    return np.array(im)[4::8, 4::8].copy()


SHOT_RAMPS = {
    "player": ("tiros/tiro1.png", None),
    "enemy": ("tiros/tiro2.png", [MARK_IN, SPOKE, MAG, LILAC, WHITE]),
    "boss_orb": ("tiros/tiro3.png", [SPOKE, RED, MAG, PALE_RED, WHITE]),
    "boss_volley": ("tiros/tiro4.png", [SPOKE, RED, MAG, PALE_RED, WHITE]),
}


def ramp_recolor(a, ramp):
    out = a.copy()
    m = a[..., 3] > 0
    lum = a[m][:, :3].astype(float) @ np.array([0.3, 0.5, 0.2])
    lo, hi = lum.min(), lum.max()
    idx = np.clip(((lum - lo) / max(1, hi - lo) * len(ramp)).astype(int), 0, len(ramp) - 1)
    out[m, :3] = np.array(ramp, dtype=np.uint8)[idx]
    return out


def build_shots():
    info = {}
    for role, (rel, ramp) in SHOT_RAMPS.items():
        a = load(SRC_29, rel)
        a[..., 3] = np.where(a[..., 3] >= 128, 255, 0)  # tiro2..4 têm meio-tons de alfa
        if ramp:
            a = ramp_recolor(a, ramp)
        # âncora = centro da cabeça (pixels mais claros), não o centro do canvas
        lum = a[..., :3].astype(float) @ np.array([0.3, 0.5, 0.2]) * (a[..., 3] > 0)
        ys, xs = np.nonzero(lum >= np.percentile(lum[lum > 0], 85))
        head = (float(xs.mean() + 0.5), float(ys.mean() + 0.5))
        for d in range(8):
            save(rotate_pixel(a, 45 * d), f"fx/shot_{role}_{d}.png", f"art-source/projetofinal-29/{rel}")
        info[role] = {"canvas": a.shape[0], "head_px": [round(head[0], 2), round(head[1], 2)]}
    return info


# ============================================================== efeitos/UI

def burst_frames(size, main, accent, n=6):
    frames = []
    c = size / 2
    for i in range(n):
        t = i / (n - 1)
        a = canvas(size)
        r = 2 + t * (size / 2 - 4)
        paint(a, m_ring(size, size, c, c, r, 2.0 if t < 0.6 else 1.0), main)
        if t < 0.4:
            paint(a, m_disc(size, size, c, c, max(1.0, 3 - 5 * t)), WHITE)
        for s in range(8):
            x, y = snap(size // 2, r + 2 + 2 * t, s * 45 + 22.5 * (s % 2))
            if 0 <= x < size and 0 <= y < size:
                a[y, x] = (*accent, 255)
        frames.append(dither(a, max(0.0, t - 0.35) * 1.4))
    return frames


def pixel_marker(size, outer, inner):
    a = canvas(size)
    c = size / 2
    paint(a, m_ring(size, size, c, c, c - 1.0, 1.0), outer)
    paint(a, m_ring(size, size, c, c, c - 3.0, 1.0), inner)
    paint(a, m_disc(size, size, c, c, 0.8), WHITE)
    return a


def build_fx_ui():
    info = {}
    for name, size, main, acc in [("hit_burst", 24, CYAN, WHITE), ("void_burst", 28, MAG, LILAC),
                                  ("void_burst_mid", 44, MAG, LILAC), ("void_burst_big", 80, MAG, LILAC)]:
        frames = burst_frames(size, main, acc)
        for i, f in enumerate(frames, 1):
            save(f, f"fx/{name}_{i:02d}.png")
        info[name] = len(frames)
    for i in range(4):
        a = canvas(32, 12)
        for row, col, ln in [(2, LILAC, 26), (5, CYAN, 30), (7, MAG, 24), (9, LILAC, 18)]:
            a[row, 32 - int(ln * (1 - i / 4)):32] = (*col, 255)
        save(dither(a, i * 0.2), f"fx/dash_trail_{i + 1:02d}.png")
    info["dash_trail"] = 4

    save(pixel_marker(9, MAG, CYAN), "ui/weakpoint.png")
    save(pixel_marker(7, MAG, CYAN), "ui/weakpoint_small.png")
    save(pixel_marker(11, MAG, CYAN), "ui/weakpoint_boss.png")
    cr = canvas(9)
    cr[4, 0:3] = cr[4, 6:9] = cr[0:3, 4] = cr[6:9, 4] = (*WHITE, 255)
    cr[4, 4] = (*CYAN, 255)
    save(cr, "ui/crosshair.png")
    arrow = canvas(7, 9)
    for y in range(4):
        arrow[y, 3 - y:4 + y] = (*CYAN, 255)
    arrow[4:9, 2:5] = (*CYAN, 255)
    save(arrow, "ui/guide_arrow.png")
    orb = canvas(14)
    paint(orb, m_ring(14, 14, 7, 7, 6.0, 1.5), (255, 72, 196))
    paint(orb, m_ring(14, 14, 7, 7, 3.8, 1.5), CYAN)
    paint(orb, m_disc(14, 14, 7, 7, 1.8), WHITE)
    save(orb, "ui/life_orb.png", "redesenho de art-source/ProjetoFinal_TCC/ui/life_orb.png")
    save(Image.new("RGBA", (1, 1), (255, 255, 255, 255)), "ui/pixel.png")

    # Painéis: o hud_panel do TCC é pixel art em blocos de 8 px; amostrado a
    # cada 4 px (2 por bloco) ele fica na escala única sem perda. Os painéis
    # ciano e vermelho são o mesmo desenho recolorido.
    hud = load(SRC_TCC, "ui/hud_panel.png")[2::4, 2::4].copy()
    save(hud, "ui/panel.png", "art-source/ProjetoFinal_TCC/ui/hud_panel.png (reduzido 4x sem perda)")
    lil = sorted({c for c in palette_of(hud) if sum(c) > 400}, key=sum)
    save(recolor(hud, {c: (NEON_CYAN2 if i == 0 else NEON_CYAN) for i, c in enumerate(lil)}), "ui/panel_cyan.png",
         "art-source/ProjetoFinal_TCC/ui/hud_panel.png (recolorido)")
    save(recolor(hud, {c: (RED if i == 0 else PALE_RED) for i, c in enumerate(lil)}), "ui/panel_red.png",
         "art-source/ProjetoFinal_TCC/ui/hud_panel.png (recolorido)")
    h, w = hud.shape[:2]
    cols = [x for x in range(w) if np.abs(hud[:, x].astype(int) - hud[:, w // 2]).sum() > 0]
    rws = [y for y in range(h) if np.abs(hud[y].astype(int) - hud[h // 2]).sum() > 0]
    manifest["ninepatch_px"] = {"left": max(x for x in cols if x < w // 2) + 1,
                                "right": w - min(x for x in cols if x > w // 2),
                                "top": max(y for y in rws if y < h // 2) + 1,
                                "bottom": h - min(y for y in rws if y > h // 2)}
    return info


def build_world():
    # ---- fundo (já está na escala 4: 480x270 -> 1920x1080)
    # O jogo repete cada camada em wrap simples (sem espelhar). Para isso cada
    # camada precisa ser tileável e ter um período próprio: recortamos na
    # largura em que a coluna seguinte do original mais se parece com a
    # primeira (a emenda some), escolhendo larguras distintas entre 380 e 478
    # — nenhuma é múltipla de outra, então duas camadas nunca "alinham".
    # A camada de estruturas ganha um vão: com período maior que a tela mais
    # uma estrutura, cada estrutura aparece no máximo uma vez por tela.
    layers = ["ceu", "nuvem1", "nuvem2", "nuvem3", "nuvem4", "montanhas", "estruturas-fundo", "lago", "chao"]
    comp = Image.new("RGBA", (480, 270))
    used = set()
    info = []
    for i, name in enumerate(layers):
        im = Image.open(SRC_TCC / f"fundo/{name}.png").convert("RGBA")
        comp.alpha_composite(im)
        a = np.array(im).astype(np.int32)
        if name == "estruturas-fundo":
            period = 760
            out = canvas(period, 270)
            out[:, :480] = a
            seam = 0.0
        else:
            # janela de 12 colunas: pega também texturas em blocos (lago) e
            # montes que cruzariam a emenda (chão)
            def cost(w):
                return float(sum(np.abs(a[:, w + k] - a[:, k]).sum() for k in range(12)))
            L = 24
            cands = [w for w in range(380, 480 - L) if w not in used]
            period = min(cands, key=lambda w: (cost(w), -w))
            seam = cost(period) / (12 * 270)
            out = a[:, :period].copy()
            # Transição em dithering ordenado nas L primeiras colunas: começa
            # na continuação natural da última coluna (a[period + x]) e chega
            # ao conteúdo original (a[x]). Some a emenda sem espelhar e sem
            # meio-tom (pixel art pura).
            # só onde a emenda é grande (lago, chão): nas silhuetas de cor única
            # o pontilhado aparecia mais que o degrau de 1-3 px do corte seco
            for x in (range(L) if seam > 20 else ()):
                t = (x + 0.5) / L
                take_orig = BAYER4[np.arange(270) % 4, x % 4] < t
                out[take_orig, x] = a[take_orig, x]
                out[~take_orig, x] = a[~take_orig, period + x]
            out = out.astype(np.uint8)
        used.add(period)
        clean = name.replace("estruturas-fundo", "estruturas")
        save(out, f"background/{i:02d}_{clean}.png", f"art-source/ProjetoFinal_TCC/fundo/{name}.png")
        info.append({"layer": clean, "period_px": period, "seam_mean_diff": round(seam, 2)})
    manifest["background_layers"] = info
    save(comp, "background/menu.png", "composição de art-source/ProjetoFinal_TCC/fundo/*")

    # ---- plataformas (16 px = 64 de mundo), redesenho de word/platform*.png
    for name, top1, top2 in [("platform", NEON_MAG, NEON_MAG2), ("platform_alt", NEON_CYAN, NEON_CYAN2)]:
        col = np.zeros((16, 1, 4), dtype=np.uint8)
        col[:] = (*PANEL_DARK, 255)
        col[0], col[1], col[2], col[15] = (*top1, 255), (*top2, 255), (*PANEL_TOP, 255), (*PANEL_EDGE, 255)
        module = np.repeat(col, 12, axis=1)
        module[4, 2:10] = module[11, 2:10] = (*PANEL_LINE, 255)       # caixa com cantos cortados
        module[5:11, 1] = module[5:11, 10] = (*PANEL_LINE, 255)
        module[8, 4:8] = (*TEAL, 255)                                   # traço no meio da caixa
        module[14, 3:8] = (*DASH, 255)                                  # traço ciano de baixo
        cap = np.repeat(col, 2, axis=1)
        cap[1:15, 0] = (*PANEL_EDGE, 255)
        cap[15, 0] = (0, 0, 0, 0)
        src = f"redesenho de art-source/ProjetoFinal_TCC/word/{name}.png"
        save(cap, f"world/{name}_cap.png", src)
        save(module, f"world/{name}_module.png", src)
        save(col, f"world/{name}_fill.png", src)

    # ---- piso: tile 16x32 (64x128 de mundo)
    g = np.zeros((32, 16, 4), dtype=np.uint8)
    g[:] = (*PANEL_DARK, 255)
    g[0], g[1], g[2] = (*NEON_MAG, 255), (*NEON_MAG2, 255), (*PANEL_TOP, 255)
    for y in (11, 21, 31):
        g[y] = (*PANEL_LINE, 255)
    g[3:11, 0] = g[22:31, 0] = (*PANEL_LINE, 255)
    g[12:21, 8] = (*PANEL_LINE, 255)
    g[5, 5:11] = (*DASH, 255)
    save(g, "world/ground.png", "gerado no estilo de art-source/ProjetoFinal_TCC/word/platform.png")

    # ---- portão: 22x75 (88x300 de mundo = a colisão), redesenho de word/gate.png
    gate = canvas(22, 75)
    body = np.zeros((75, 22), dtype=bool)
    body[1:74, 2:20] = True
    body[1, 2] = body[1, 19] = body[73, 2] = body[73, 19] = False
    paint(gate, body, VOID)
    paint(gate, outline(body), NEON_MAG)
    gate[4:71, 10:12] = (*LILAC, 255)
    for y in range(6, 71, 8):
        gate[y, 6:16] = (*CYAN, 255)
    save(gate, "world/gate.png", "redesenho de art-source/ProjetoFinal_TCC/word/gate.png")

    # ---- portal: 64x79 (256x316 de mundo), redesenho de word/portal.png
    W, H = 64, 79
    yy, xx = np.mgrid[0:H, 0:W]
    e = ((xx + 0.5 - W / 2) / (W / 2 - 1)) ** 2 + ((yy + 0.5 - H / 2) / (H / 2 - 1)) ** 2
    portal = canvas(W, H)
    for lim, col in [(1.0, PORTAL_1), (0.82, PORTAL_2), (0.68, CYAN), (0.60, PORTAL_IN)]:
        paint(portal, e <= lim, col)
    rng = np.random.default_rng(29)
    inner = np.argwhere(e <= 0.45)
    for y, x in inner[rng.choice(len(inner), 14, replace=False)]:
        portal[y, x] = (*CYAN, 255)
    portal[H // 2 - 4:H // 2 + 4, 6:8] = (*MAG, 255)
    portal[H // 2 - 4:H // 2 + 4, W - 8:W - 6] = (*MAG, 255)
    save(portal, "world/portal.png", "redesenho de art-source/ProjetoFinal_TCC/word/portal.png")

    # ---- cristal (projetofinal-29 tem prioridade sobre word/crystal.png do TCC)
    cr = load(SRC_29, "cristal.png")
    cr[..., 3] = np.where(cr[..., 3] >= 128, 255, 0)
    save(cr, "world/crystal.png", "art-source/projetofinal-29/cristal.png")


# ================================================================ NPC Pi (C1)

# Paleta própria (dourado + ciano) para se destacar do roxo do ORB.
PI_LINE = (26, 22, 44)
PI_GOLD_D = (168, 104, 30)
PI_GOLD = (230, 170, 52)
PI_GOLD_L = (255, 220, 118)
PI_CYAN = (60, 200, 220)
PI_CYAN_L = (170, 246, 255)
PI_EYE = (250, 248, 236)
PI_PUPIL = (26, 22, 44)
PI_MOUTH = (110, 36, 52)
PI_PALETTE = {PI_LINE, PI_GOLD_D, PI_GOLD, PI_GOLD_L, PI_CYAN, PI_CYAN_L, PI_EYE, PI_PUPIL, PI_MOUTH}


def pi_frame(dy=0, bar_squash=0, legs=(0, 0), leg_dx=(0, 0), arm_l=(-100, 8), arm_r=(-80, 8),
             eyes="open", look=0, mouth="closed", finger=False):
    """Um frame do Pi, desenhado por partes direto no canvas 48 (a grade de
    32 do corpo fica deslocada de OX, OY: braços e dedo podem sair dela).
    Ângulos dos braços em graus (0 = direita, -90 = baixo, 90 = cima)."""
    C = PLAYER_C
    OX, OY = 8, (C - 5) - 29          # linha 29 da grade = linha dos pés
    a = canvas(C)
    top = 8 + dy + bar_squash
    bottom = 15 + dy                  # no squash a barra encolhe por cima

    def rect(x0, y0, x1, y1, fill):
        a[max(0, y0 + OY):y1 + OY + 1, max(0, x0 + OX):x1 + OX + 1] = (*fill, 255)

    def px(x, y, fill):
        if 0 <= y + OY < C and 0 <= x + OX < C:
            a[y + OY, x + OX] = (*fill, 255)

    # ---- pernas (atrás da barra): esquerda reta, direita com o gancho do π
    for k, (x0, x1) in enumerate(((9, 12), (19, 22))):
        lift, dx = legs[k], leg_dx[k]
        y0, y1 = bottom + 1, 28 - lift
        rect(x0 + dx, y0, x1 + dx, y1, PI_LINE)
        rect(x0 + 1 + dx, y0, x1 - 1 + dx, y1 - 1, PI_GOLD_D if k == 0 else PI_GOLD)
        rect(x0 + 1 + dx, y0, x0 + 1 + dx, y1 - 1, PI_GOLD_L if k == 1 else PI_GOLD)
        if k == 1:                    # gancho para a direita
            rect(x1 + dx, y1 - 2, x1 + 3 + dx, y1, PI_LINE)
            rect(x1 + dx, y1 - 1, x1 + 2 + dx, y1 - 1, PI_GOLD)
        rect(x0 - 1 + dx, y1, x1 + dx, y1 + 1, PI_LINE)          # sola
        rect(x0 + dx, y1, x1 - 1 + dx, y1, PI_CYAN)

    # ---- braços (atrás da barra), 3 px: contorno + núcleo de 1 px + mão
    for (sx, sy), (ang, ln), is_right in (((1, top + 3), arm_l, False), ((30, top + 3), arm_r, True)):
        ex_ = sx + round(ln * math.cos(math.radians(ang)))
        ey_ = sy - round(ln * math.sin(math.radians(ang)))
        core = m_line(C, C, sx + OX + 0.5, sy + OY + 0.5, ex_ + OX + 0.5, ey_ + OY + 0.5)
        grow = core.copy()
        for dy_, dx_ in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            grow |= np.roll(np.roll(core, dy_, 0), dx_, 1)
        paint(a, grow, PI_LINE)
        paint(a, core, PI_GOLD_D)
        hx, hy = ex_ + OX + 0.5, ey_ + OY + 0.5
        paint(a, m_disc(C, C, hx, hy, 2.3), PI_LINE)
        paint(a, m_disc(C, C, hx, hy, 1.5), PI_CYAN_L)
        if finger and is_right:       # dedo apontando para a direita
            rect(ex_ + 2, ey_, ex_ + 4, ey_, PI_CYAN_L)
            px(ex_ + 5, ey_, PI_LINE)
            px(ex_ + 3, ey_ - 1, PI_LINE)
            px(ex_ + 3, ey_ + 1, PI_LINE)

    # ---- barra (corpo/cabeça) com as pontas passando das pernas
    rect(2, top, 29, bottom, PI_LINE)
    rect(3, top + 1, 28, bottom - 1, PI_GOLD)
    rect(3, top + 1, 28, top + 1, PI_GOLD_L)                   # brilho em cima
    rect(3, bottom - 1, 28, bottom - 1, PI_GOLD_D)             # sombra embaixo
    rect(4, top - 1, 27, top - 1, PI_CYAN)                     # filete ciano ("serifa")
    px(3, top - 1, PI_LINE)
    px(28, top - 1, PI_LINE)

    # ---- rosto
    ey = top + 2
    for ex in (11, 18):
        if eyes == "open":
            rect(ex, ey, ex + 2, ey + 2, PI_EYE)
            rect(ex + 1 + look, ey + 1, ex + 1 + look, ey + 2, PI_PUPIL)
        elif eyes == "happy":         # ^ ^
            px(ex, ey + 2, PI_LINE)
            px(ex + 1, ey + 1, PI_LINE)
            px(ex + 2, ey + 2, PI_LINE)
        elif eyes == "closed":
            rect(ex, ey + 2, ex + 2, ey + 2, PI_LINE)
    my = ey + 4
    if mouth == "closed":
        rect(14, my, 17, my, PI_LINE)
    elif mouth == "small":
        rect(14, my, 17, my + 1, PI_LINE)
        rect(15, my, 16, my, PI_MOUTH)
    elif mouth == "open":
        rect(13, my - 1, 18, my + 1, PI_LINE)
        rect(14, my - 1, 17, my, PI_MOUTH)
    elif mouth == "smile":
        px(13, my - 1, PI_LINE)
        px(18, my - 1, PI_LINE)
        rect(14, my, 17, my, PI_LINE)
    return a


def build_pi():
    C = PLAYER_C
    FEET = C - 5

    def place(f):
        return f                                  # pi_frame já desenha no canvas 48

    def sparkle(frame, pts, col):
        out = frame.copy()
        for x, y in pts:
            if 0 <= x < C and 0 <= y < C:
                out[y, x] = (*col, 255)
        return out

    anims = {
        "idle": [place(pi_frame(arm_l=(-100, 8), arm_r=(-80, 8))),
                 place(pi_frame(arm_l=(-104, 8), arm_r=(-76, 8))),
                 place(pi_frame(dy=1, arm_l=(-104, 8), arm_r=(-76, 8))),
                 place(pi_frame(arm_l=(-100, 8), arm_r=(-80, 8), eyes="closed"))],
        "walk": [place(pi_frame(dy=1, arm_l=(-120, 8), arm_r=(-60, 8))),
                 place(pi_frame(legs=(2, 0), leg_dx=(1, 0), arm_l=(-110, 8), arm_r=(-70, 8))),
                 place(pi_frame(dy=-1, legs=(1, 0), arm_l=(-95, 8), arm_r=(-85, 8))),
                 place(pi_frame(dy=1, arm_l=(-60, 8), arm_r=(-120, 8))),
                 place(pi_frame(legs=(0, 2), leg_dx=(0, 1), arm_l=(-70, 8), arm_r=(-110, 8))),
                 place(pi_frame(dy=-1, legs=(0, 1), arm_l=(-85, 8), arm_r=(-95, 8)))],
        "talk": [place(pi_frame(mouth="closed")),
                 place(pi_frame(mouth="small", arm_r=(-60, 8))),
                 place(pi_frame(mouth="open", bar_squash=1, arm_r=(-40, 8))),
                 place(pi_frame(mouth="small", arm_r=(-60, 8)))],
        "wave": [place(pi_frame(arm_r=(ang, 8), mouth="smile")) for ang in (-30, 30, 70, 50, 80, 50)],
        "point": [place(pi_frame(arm_r=(-20, 5), look=1)),
                  place(pi_frame(arm_r=(0, 4), look=1, finger=True)),
                  place(pi_frame(arm_r=(0, 5), look=1, finger=True, mouth="small")),
                  place(pi_frame(arm_r=(0, 4), look=1, finger=True))],
        "cheer": [place(pi_frame(arm_l=(150, 8), arm_r=(30, 8), eyes="happy", mouth="open")),
                  place(pi_frame(dy=-2, legs=(1, 1), arm_l=(120, 8), arm_r=(60, 8), eyes="happy", mouth="open")),
                  place(pi_frame(dy=-3, legs=(2, 2), arm_l=(110, 8), arm_r=(70, 8), eyes="happy", mouth="open")),
                  place(pi_frame(dy=-2, legs=(1, 1), arm_l=(120, 8), arm_r=(60, 8), eyes="happy", mouth="open")),
                  place(pi_frame(arm_l=(150, 8), arm_r=(30, 8), eyes="happy", mouth="smile")),
                  place(pi_frame(dy=1, arm_l=(160, 8), arm_r=(20, 8), eyes="happy", mouth="smile"))],
    }
    # appear: faíscas convergem e o Pi se materializa (dithering 1 -> 0)
    base = anims["idle"][0]
    rng = np.random.default_rng(314)
    sparks = [(int(x), int(y)) for x, y in rng.integers(4, C - 4, size=(10, 2))]
    cx, cy = C // 2, FEET - 14
    appear = []
    for i in range(8):
        t = i / 7
        pts = [(round(x + (cx - x) * t), round(y + (cy - y) * t)) for x, y in sparks]
        f = dither(base, 1.0 - t) if t > 0.3 else canvas(C)
        appear.append(sparkle(f, pts, PI_CYAN_L if i % 2 == 0 else PI_GOLD_L))
    anims["appear"] = appear
    for name, frames in anims.items():
        for f in frames:
            extra = palette_of(f) - PI_PALETTE
            if extra:
                raise SystemExit(f"Pi/{name}: cor fora da paleta do Pi: {sorted(extra)[:4]}")
    counts = write_anims("npc/pi", anims)
    # retrato: barra + rosto do idle (recorte, sem reamostrar)
    idle = anims["idle"][0]
    bb = bbox(idle)
    save(idle[bb[1] - 1:bb[1] + 14, 8:40].copy(), "npc/pi/portrait.png")
    return {"canvas": C, "frames": counts, "baseline_px": C - 1 - bbox(idle)[3],
            "palette": sorted(list(c) for c in PI_PALETTE)}


# ================================================================ HUD (A3)

def build_hud():
    """Ícones do HUD em pixel art, na paleta do jogo e na escala única.

    Nada de caixas, degradês ou brilho: formas de 1 px, cor sólida.
    """
    # rosto do ORB: recorte do idle canônico (sem reamostrar)
    idle = load(SRC_29, "personagem principal/Personagem principal.png")
    face = idle[3:23, 5:27].copy()
    face[~m_disc(22, 20, 11.5, 10.0, 10.6)] = 0   # só a esfera, sem os braços
    save(face, "ui/hud_orb.png", "recorte de art-source/projetofinal-29/personagem principal/Personagem principal.png")

    # cristal pequeno, redesenhado com a paleta do cristal oficial
    cr = load(SRC_29, "cristal.png")
    cpal = sorted(palette_of(cr), key=sum)
    dark, mid, light, hi = cpal[0], cpal[len(cpal) // 3], cpal[2 * len(cpal) // 3], cpal[-1]
    icon = canvas(7, 12)
    shape = ["..ddd..", ".dmmmd.", "dmlhlmd", "dmlhlmd", "dmlhlmd", "dmllmmd", "dmlhmmd", "dmlhmmd",
             "dmllmmd", ".dmmmd.", "..dmd..", "...d..."]
    cmap = {"d": dark, "m": mid, "l": light, "h": hi}
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch in cmap:
                icon[y, x] = (*cmap[ch], 255)
    save(icon, "ui/hud_crystal.png", "redesenho pequeno de art-source/projetofinal-29/cristal.png")

    # dash: duas setas ">>" (7 linhas, ponta 3 px à frente)
    dash = canvas(9, 9)
    for k, col in ((0, CYAN), (4, PALE_CYAN)):
        for i, dx in enumerate((0, 1, 2, 3, 2, 1, 0)):
            dash[i + 1, k + dx] = (*col, 255)
            if k + dx + 1 < 9:
                dash[i + 1, k + dx + 1] = (*col, 255)
    save(dash, "ui/hud_dash.png")

    # ícones de propriedade (15x15): forma em contorno + o que é pedido em destaque
    S = 15
    c = S / 2

    def base(n, start, r=6.0):
        a = canvas(S)
        pts = poly(c, r, n, start)
        paint(a, outline(m_poly(S, S, pts)), OUTLINE)
        return a, pts

    def dots(a, pts, col=WHITE, r=1.0):
        for x, y in pts:
            paint(a, m_disc(S, S, math.floor(x) + 0.5, math.floor(y) + 0.5, r), col)
        return a

    icons = {}
    a, pts = base(3, 90)
    icons["tri_vertices"] = dots(a, pts)
    a, pts = base(4, 0)
    icons["diamond_vertices"] = dots(a, pts)
    a, pts = base(4, 45, 7.0)
    icons["square_sides"] = dots(a, poly(c, 7.0 * math.cos(math.pi / 4), 4, 0), MAG)
    a, pts = base(6, 90)
    icons["hex_vertices"] = dots(a, pts)
    a, pts = base(6, 90)
    for x, y in pts:  # ângulo: um "L" de 2 px apontando para dentro em cada vértice
        paint(a, m_disc(S, S, math.floor(x) + 0.5 + (c - x) * 0.25, math.floor(y) + 0.5 + (c - y) * 0.25, 0.8), CYAN)
    icons["hex_angles"] = a
    a, pts = base(6, 90)
    icons["hex_sides"] = dots(a, poly(c, 6 * math.cos(math.pi / 6), 6, 0), MAG)
    a, pts = base(6, 90)
    icons["hex_cores"] = dots(a, poly(c, 3.2, 3, 90), CYAN)
    a, pts = base(6, 90)
    a[1:14:2, 7] = (*CYAN, 255)  # eixo de simetria tracejado
    icons["hex_symmetry"] = dots(a, [(c - 6, c), (c + 5, c)], MAG)
    for name, a in icons.items():
        save(a, f"ui/prop_{name}.png")

    # ---- teclas e mouse da tela "Como jogar" (A4)
    def keycap(face, edge, depth):
        k = canvas(11, 12)
        body = np.zeros((12, 11), dtype=bool)
        body[0:12, 0:11] = True
        body[0, 0] = body[0, 10] = body[11, 0] = body[11, 10] = False
        paint(k, body, depth)                         # lateral/profundidade
        top = body.copy()
        top[9:, :] = False                            # tampa: 3 px mais alta
        paint(k, top, face)
        paint(k, outline(body), edge)
        return k
    save(keycap(BAND, OUTLINE, MARK_IN), "ui/key.png")
    save(keycap(SPOKE, MAG, OUTLINE), "ui/key_lit.png")
    for name, rows in (("arrow_left", ["..w....", ".ww....", "wwwwwww", ".ww....", "..w...."]),
                       ("arrow_right", ["....w..", "....ww.", "wwwwwww", "....ww.", "....w.."])):
        ar = canvas(7, 5)
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == "w":
                    ar[y, x] = (*WHITE, 255)
        save(ar, f"ui/{name}.png")
    for name, left in (("mouse", BAND), ("mouse_lit", MAG)):
        m = canvas(12, 17)
        shape = np.zeros((17, 12), dtype=bool)
        shape[:, :] = m_poly(12, 17, [(1.5, 3), (3, 0.8), (9, 0.8), (10.5, 3), (10.5, 13), (8.5, 16.2), (3.5, 16.2), (1.5, 13)])
        paint(m, shape, BAND)
        lb = shape.copy()
        lb[7:, :] = False
        lb[:, 6:] = False
        paint(m, lb, left)
        paint(m, outline(shape), OUTLINE)
        m[0:7, 6] = (*OUTLINE, 255)                    # divisão dos botões
        m[7, 1:11] = (*OUTLINE, 255)
        save(m, f"ui/{name}.png")

    # polígono do chefe: hexágono com 6 lâmpadas (o código acende/apaga)
    B = 29
    hexa = canvas(B)
    paint(hexa, outline(m_poly(B, B, poly(B / 2, 13, 6, 90))), OUTLINE)
    paint(hexa, outline(m_poly(B, B, poly(B / 2, 5, 6, 90))), TEAL)
    save(hexa, "ui/hud_boss_hex.png")
    for name, col, ring in (("hud_lamp_on", WHITE, MAG), ("hud_lamp_off", SPOKE, MARK_IN)):
        lamp = canvas(5)
        paint(lamp, m_disc(5, 5, 2.5, 2.5, 2.5), ring)
        paint(lamp, m_disc(5, 5, 2.5, 2.5, 1.2), col)
        save(lamp, f"ui/{name}.png")


# ================================================================ preview

def contact_sheet():
    PREVIEW.mkdir(parents=True, exist_ok=True)
    groups = {}
    for rel in written:
        groups.setdefault(rel.rsplit("/", 1)[0], []).append(rel)
    for group, rels in groups.items():
        ims = [Image.open(OUT / r).convert("RGBA") for r in sorted(rels)]
        big = [im.resize((im.width * PIXEL, im.height * PIXEL), Image.NEAREST) for im in ims]
        width = 1800
        x = y = rowh = 0
        places = []
        for b in big:
            if x + b.width > width and x > 0:
                x, y, rowh = 0, y + rowh + 6, 0
            places.append((x, y))
            x += b.width + 6
            rowh = max(rowh, b.height)
        sheet = Image.new("RGBA", (width, y + rowh + 6), (40, 40, 48, 255))
        for b, (px, py) in zip(big, places):
            bg = Image.new("RGBA", b.size, (70, 70, 82, 255))
            bg.alpha_composite(b)
            sheet.paste(bg, (px, py))
        sheet.save(PREVIEW / (group.replace("/", "_") + ".png"))


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--preview", action="store_true")
    args = ap.parse_args()
    for d in (SRC_29, SRC_TCC):
        if not d.is_dir():
            raise SystemExit(f"Pasta de arte não encontrada: {d}")
    before = {p.relative_to(OUT).as_posix() for p in OUT.rglob("*") if p.is_file()} if OUT.is_dir() else set()

    manifest["player"] = build_player()
    manifest["enemies"] = build_enemies()
    manifest["boss"] = build_boss()
    manifest["shots"] = build_shots()
    manifest["fx"] = build_fx_ui()
    build_world()
    build_hud()
    manifest["npc_pi"] = build_pi()
    manifest["sources"] = dict(sorted(sources.items()))
    manifest["files"] = sorted(written)
    (OUT / "manifest.json").write_text(json.dumps(manifest, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")

    stale = sorted(before - set(written) - {"manifest.json"})
    print(f"{len(written)} arquivos gerados em {OUT.relative_to(ROOT).as_posix()}")
    if stale:
        print(f"{len(stale)} arquivo(s) antigo(s) não são mais gerados (não apagados); ex.: {stale[:3]}")
    if args.preview:
        contact_sheet()
        print(f"folhas de contato em {PREVIEW.relative_to(ROOT).as_posix()}")


if __name__ == "__main__":
    main()
