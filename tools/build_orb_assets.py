"""Gera TODOS os sprites ativos do Project ORB em assets/sprites/.

Entrada: a arte oficial em assets/ProjetoFinal_TCC/ (somente leitura).
Saída:   assets/sprites/ — o ÚNICO diretório de arte que o jogo lê — e
         assets/sprites/manifest.json com as medidas usadas pelo código
         (EnemyType e Constants).

O que não existe na pasta oficial é GERADO aqui, na mesma paleta:
- player: idle/walk/jump/dash/attack/hurt/death a partir dos 3 frames, só com
  operações de pixel art (deslocamento, squash/stretch por linha, flash de
  cor, rotação de 90°, inclinação por linha, dissolução por dithering);
- inimigos da pasta (triângulo, losango, círculo, pentágono): o corpo vem do
  frame oficial; núcleo e arcos são redesenhados por frame (pulso, giro,
  flash, dissolução);
- hexágono, quadrado alinhado aos eixos e o boss: desenhados no mesmo
  gabarito medido nos inimigos oficiais;
- efeitos, mira, marcadores, tiros, UI de apoio e peças de mundo.

Reproduzível: sem aleatoriedade não semeada; a mesma entrada gera os mesmos
bytes. Uso:

    python tools/build_orb_assets.py            # gera assets/sprites/
    python tools/build_orb_assets.py --preview  # e grava previews em tmp/asset-preview/

O script só escreve dentro de assets/sprites/ (e tmp/ com --preview) e nunca
apaga arquivos: arquivos antigos que ele não gera mais são apenas listados.
"""

import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "assets" / "ProjetoFinal_TCC"
OUT = ROOT / "assets" / "sprites"
PREVIEW = ROOT / "tmp" / "asset-preview"

# ------------------------------------------------------------------ paleta
# Amostrada dos inimigos oficiais (enemies/*.png).
DARK = (10, 11, 27)
BAND = (24, 24, 50)
MARK_IN = (33, 25, 59)
SPOKE = (62, 33, 91)
OUTLINE = (119, 59, 170)
MAG = (205, 69, 231)
LILAC = (224, 141, 255)
CYAN = (81, 218, 234)
WHITE = (242, 241, 255)
TEAL = (31, 72, 92)
RED = (239, 55, 55)
# Plataformas / painéis oficiais.
NEON_MAG = (225, 18, 255)
NEON_CYAN = (0, 255, 242)
PANEL_DARK = (11, 12, 26)
PANEL_LINE = (60, 56, 80)

# Gabarito medido nos inimigos oficiais (canvas 128, centro 64,64):
TEMPLATE = {
    "vertex_r": 52.0,      # centro dos marcadores de vértice
    "outer_r": 60.0,       # raio externo do contorno do polígono
    "marker_r": 3.5,       # raio do anel do marcador
    "arc_r": 38.8,         # raio dos arcos magenta/ciano
    "arc_w": 1.7,
    "band_out": 0.765,     # faixa clara, em fração do apótema externo
    "band_in": 0.53,
    "core_r": 17.0,        # disco do núcleo redesenhado por frame
}
ENEMY_CANVAS = 144         # 128 + 8 px de folga por lado
ENEMY_SCALE = 2
BOSS_CANVAS = 224
BOSS_SCALE = 2
BOSS_K = 1.75              # boss = gabarito x 1,75
PLAYER_CANVAS = 48
PLAYER_SCALE = 4
BG_SCALE = 4
SS = 4                     # supersampling das formas vetoriais (mesmo AA da arte oficial)

written = []
manifest = {"generator": "tools/build_orb_assets.py", "source": "assets/ProjetoFinal_TCC"}


# ================================================================ utilidades

def save(img, rel):
    path = OUT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=False, compress_level=9)
    written.append(rel.replace("\\", "/"))


def load(rel):
    return Image.open(SRC / rel).convert("RGBA")


def arr(img):
    return np.array(img, dtype=np.uint8)


def img(a):
    return Image.fromarray(np.ascontiguousarray(a, dtype=np.uint8), "RGBA")


def premul_resize(big, size):
    """Reduz com média de caixa em alfa pré-multiplicado (AA sem franja escura)."""
    return big.convert("RGBa").resize(size, Image.BOX).convert("RGBA")


class AA:
    """Canvas supersampled: desenha em coordenadas de pixel final, reduz com BOX."""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.im = Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.im)

    def _p(self, pts):
        return [(x * SS, y * SS) for x, y in pts]

    def polygon(self, pts, fill):
        self.d.polygon(self._p(pts), fill=fill)

    def polyline(self, pts, fill, width):
        self.d.line(self._p(pts), fill=fill, width=max(1, round(width * SS)), joint="curve")

    def line(self, a, b, fill, width):
        self.d.line(self._p([a, b]), fill=fill, width=max(1, round(width * SS)))

    def disc(self, cx, cy, r, fill):
        self.d.ellipse([(cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS], fill=fill)

    def ring(self, cx, cy, r, fill, width):
        # O traço do PIL fica por dentro da caixa: centraliza no raio pedido.
        R = r + width / 2
        self.d.ellipse([(cx - R) * SS, (cy - R) * SS, (cx + R) * SS, (cy + R) * SS],
                       outline=fill, width=max(1, round(width * SS)))

    def arc(self, cx, cy, r, a0, a1, fill, width):
        """Ângulos em graus, sentido anti-horário a partir das 3h (y para cima)."""
        R = r + width / 2
        self.d.arc([(cx - R) * SS, (cy - R) * SS, (cx + R) * SS, (cy + R) * SS],
                   start=-a1, end=-a0, fill=fill, width=max(1, round(width * SS)))

    def result(self):
        return premul_resize(self.im, (self.w, self.h))


def over(base, top):
    out = base.copy()
    out.alpha_composite(top)
    return out


def shift(a, dx, dy):
    out = np.zeros_like(a)
    h, w = a.shape[:2]
    ys0, ys1 = max(0, -dy), min(h, h - dy)
    xs0, xs1 = max(0, -dx), min(w, w - dx)
    out[ys0 + dy:ys1 + dy, xs0 + dx:xs1 + dx] = a[ys0:ys1, xs0:xs1]
    return out


def tint(a, color, amount):
    """Flash de cor: mistura só nos pixels visíveis, alfa preservado."""
    out = a.copy().astype(np.float32)
    m = a[..., 3] > 0
    for c in range(3):
        out[..., c][m] = out[..., c][m] * (1 - amount) + color[c] * amount
    return np.clip(out, 0, 255).astype(np.uint8)


BAYER4 = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], dtype=np.float32) / 16.0


def dither_dissolve(a, level):
    """Dissolução por dithering ordenado (compatível com pixel art)."""
    h, w = a.shape[:2]
    th = np.tile(BAYER4, (h // 4 + 1, w // 4 + 1))[:h, :w]
    out = a.copy()
    out[..., 3][th < level] = 0
    return out


def noise_dissolve(a, level, seed):
    """Dissolução por ruído semeado (para as formas com AA)."""
    rng = np.random.default_rng(seed)
    h, w = a.shape[:2]
    # ruído em blocos de 2 px: lê como "pixels se soltando", não como chuvisco
    small = rng.random((h // 2 + 1, w // 2 + 1)).astype(np.float32)
    th = np.kron(small, np.ones((2, 2), dtype=np.float32))[:h, :w]
    out = a.copy()
    out[..., 3][th < level] = 0
    return out


def opaque_bbox(a, thr=128):
    ys, xs = np.nonzero(a[..., 3] >= thr)
    if len(xs) == 0:
        return None
    return int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())


def poly_pts(cx, cy, r, n, start_deg):
    return [(cx + r * math.cos(math.radians(start_deg + i * 360 / n)),
             cy - r * math.sin(math.radians(start_deg + i * 360 / n))) for i in range(n)]


# ============================================================ inimigos: núcleo

def core_profile(src, cx, cy, rmax=18.0, step=0.25):
    """Perfil radial médio (RGBA pré-multiplicado) do núcleo oficial."""
    a = arr(src).astype(np.float32)
    h, w = a.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.hypot(xx + 0.5 - cx, yy + 0.5 - cy)
    pm = a.copy()
    pm[..., :3] *= pm[..., 3:4] / 255.0
    bins = np.arange(0, rmax + step, step)
    prof = []
    for r in bins:
        m = np.abs(d - r) <= step
        prof.append(pm[m].mean(axis=0) if m.any() else None)
    # Bins sem pixel (r=0 cai entre os centros de pixel) herdam o vizinho
    # mais próximo; antes viravam um ponto escuro no meio do núcleo.
    filled = [i for i, v in enumerate(prof) if v is not None]
    prof = [v if v is not None else prof[min(filled, key=lambda j: abs(j - i))]
            for i, v in enumerate(prof)]
    return bins, np.array(prof)


def draw_core(a, cx, cy, profile, scale=1.0, bright=0.0, hue=None, clear_r=None):
    """Redesenha o núcleo (disco) a partir do perfil oficial, com escala/brilho."""
    bins, prof = profile
    h, w = a.shape[:2]
    rmax = bins[-1] - 1.0
    reach = rmax * scale
    clear_r = reach if clear_r is None else max(clear_r, reach)
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.hypot(xx + 0.5 - cx, yy + 0.5 - cy)
    out = a.astype(np.float32)
    m = d <= clear_r + 0.5
    rr = np.clip(d[m] / scale, 0, bins[-1])
    idx = np.clip(np.round(rr / (bins[1] - bins[0])).astype(int), 0, len(bins) - 1)
    col = prof[idx].copy()
    # Fora do alcance do perfil (anel ciano já passou): fundo escuro do corpo.
    far = d[m] > reach
    col[far] = np.array([*DARK, 255], dtype=np.float32)
    # Borda do disco limpo: mistura com o que já estava (sem degrau duro).
    edge = np.clip(clear_r + 0.5 - d[m], 0, 1)[:, None]
    rgb = col[:, :3] / np.maximum(col[:, 3:4] / 255.0, 1e-3)
    if hue is not None:
        sat = (rgb.max(axis=1) - rgb.min(axis=1)) > 60
        rgb[sat] = rgb[sat] * 0.35 + np.array(hue, dtype=np.float32) * 0.65
    if bright:
        lum = rgb.max(axis=1, keepdims=True) > 70
        rgb = np.where(lum, rgb + (255 - rgb) * bright, rgb)
    newc = np.concatenate([rgb, col[:, 3:4]], axis=1)
    out[m] = out[m] * (1 - edge) + newc * edge
    return np.clip(out, 0, 255).astype(np.uint8)


# ============================================================ inimigos: arcos

def arc_mask(a, cx, cy, r0, r1):
    rgb = a[..., :3].astype(np.int32)
    h, w = a.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.hypot(xx + 0.5 - cx, yy + 0.5 - cy)
    ann = (d >= r0) & (d <= r1)
    strong = ann & (a[..., 3] > 20) & ((rgb[..., 0] > 150) | (rgb[..., 1] > 100))
    # dilata 1 px dentro do anel para levar a franja antialiasada junto
    grow = strong.copy()
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            grow |= np.roll(np.roll(strong, dy, 0), dx, 1)
    return grow & ann


def inpaint(a, mask, iters=12):
    """Preenche a máscara de fora para dentro com a média dos vizinhos conhecidos."""
    pm = a.astype(np.float32)
    pm[..., :3] *= pm[..., 3:4] / 255.0
    known = ~mask
    for _ in range(iters):
        if known.all():
            break
        acc = np.zeros_like(pm)
        cnt = np.zeros(mask.shape, dtype=np.float32)
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if dx == 0 and dy == 0:
                    continue
                k = np.roll(np.roll(known, dy, 0), dx, 1)
                v = np.roll(np.roll(pm, dy, 0), dx, 1)
                acc += v * k[..., None]
                cnt += k
        fill = (~known) & (cnt > 0)
        pm[fill] = acc[fill] / cnt[fill][:, None]
        known = known | fill
    rgb = pm[..., :3] / np.maximum(pm[..., 3:4] / 255.0, 1e-3)
    out = np.concatenate([rgb, pm[..., 3:4]], axis=2)
    return np.clip(out, 0, 255).astype(np.uint8)


ARC_SET = [(60, 180, MAG), (240, 360, CYAN)]  # medido nos inimigos oficiais


def arcs_layer(size, cx, cy, r, width, theta, colors=None, extra=()):
    c = AA(size, size)
    cols = colors or [col for _, _, col in ARC_SET]
    for (a0, a1, _), col in zip(ARC_SET, cols):
        c.arc(cx, cy, r, a0 + theta, a1 + theta, col, width)
    for (rr, a0, a1, col, wd) in extra:
        c.arc(cx, cy, rr, a0 + theta, a1 + theta, col, wd)
    return c.result()


def markers_flash_layer(size, pts, r, amount, color=WHITE):
    if amount <= 0:
        return None
    c = AA(size, size)
    for x, y in pts:
        c.disc(x, y, r + 0.6, (*color, int(255 * amount)))
    return c.result()


# ======================================================= corpo procedural

def draw_body(size, cx, cy, n, start, k=1.0, markers=None, marker_r=None, spokes_to=None,
              extra=None, outline=OUTLINE, outer_r=None):
    """Corpo no gabarito oficial: polígono escuro, faixa, raios e marcadores."""
    T = TEMPLATE
    ro = (outer_r or T["outer_r"]) * k
    c = AA(size, size)
    outer = poly_pts(cx, cy, ro, n, start) if n else None
    if n:
        ap = ro * math.cos(math.pi / n)
        c.polygon(outer, (*DARK, 252))
        c.polygon(poly_pts(cx, cy, ro * T["band_out"], n, start), (*BAND, 255))
        c.polyline(poly_pts(cx, cy, ro * T["band_out"], n, start) + [poly_pts(cx, cy, ro * T["band_out"], n, start)[0]],
                   (*TEAL, 170), 1.0)
        c.polygon(poly_pts(cx, cy, ro * T["band_in"], n, start), (*DARK, 255))
    else:
        c.disc(cx, cy, ro, (*DARK, 252))
        c.disc(cx, cy, ro * T["band_out"], (*BAND, 255))
        c.ring(cx, cy, ro * T["band_out"] - 0.5, (*TEAL, 170), 1.0)
        c.disc(cx, cy, ro * T["band_in"], (*DARK, 255))
    for (x, y) in (spokes_to or []):
        c.line((cx, cy), (x, y), (*SPOKE, 255), 1.0 * max(1.0, k * 0.8))
    if extra:
        extra(c)
    if n:
        c.polyline(outer + [outer[0]], (*outline, 255), 1.3 * max(1.0, k * 0.8))
    else:
        c.ring(cx, cy, ro - 0.6, (*outline, 255), 1.3 * max(1.0, k * 0.8))
    mr = (marker_r or T["marker_r"]) * k
    for (x, y) in (markers or []):
        c.disc(x, y, mr, (*MARK_IN, 255))
        # 1,5 px: com traço mais fino o AA diluía o magenta e o marcador ficava
        # mais apagado que os da arte oficial.
        c.ring(x, y, mr - 0.5, (*MAG, 255), 1.5 * max(1.0, k * 0.7))
    return c.result()


# ============================================================ inimigos

def build_enemy_frames(body, cx, cy, profile, marker_pts, size, arc_r, arc_w, core_k=1.0,
                       enraged=False):
    """Retorna dict anim -> lista de frames (numpy RGBA) para um inimigo comum."""
    base = arr(body)

    def frame(theta=0.0, s=1.0, b=0.0, flash=0.0, dx=0, dy=0, tint_c=None, tint_a=0.0,
              dissolve=0.0, seed=0, hue=None, arc_cols=None):
        a = draw_core(base, cx, cy, profile, scale=s * core_k, bright=b, hue=hue,
                      clear_r=TEMPLATE["core_r"] * core_k)
        im = img(a)
        im = over(im, arcs_layer(size, cx, cy, arc_r, arc_w, theta, arc_cols))
        fl = markers_flash_layer(size, marker_pts, TEMPLATE["marker_r"] * core_k, flash)
        if fl is not None:
            im = over(im, fl)
        a = arr(im)
        if tint_c is not None and tint_a > 0:
            a = tint(a, tint_c, tint_a)
        if dissolve > 0:
            a = noise_dissolve(a, dissolve, seed)
        if dx or dy:
            a = shift(a, dx, dy)
        return a

    anims = {}
    anims["idle"] = [frame(theta=6 * math.sin(2 * math.pi * i / 6),
                           s=1 + 0.06 * math.sin(2 * math.pi * i / 6),
                           b=0.10 + 0.10 * math.sin(2 * math.pi * i / 6)) for i in range(6)]
    bob = [0, -1, -1, 0, 0, 1, 1, 0]
    anims["move"] = [frame(theta=-45 * i, dy=bob[i], s=1.0 + 0.04 * (i % 2)) for i in range(8)]
    anims["attack"] = [frame(theta=t, s=s, b=b, flash=f) for t, s, b, f in zip(
        [0, 20, 50, 95, 130, 150], [1.0, 1.15, 1.30, 1.45, 1.22, 1.05],
        [0.10, 0.30, 0.50, 0.85, 0.40, 0.15], [0.0, 0.30, 0.60, 1.0, 0.45, 0.15])]
    anims["hurt"] = [frame(tint_c=WHITE, tint_a=ta, dx=dx) for ta, dx in zip(
        [0.75, 0.40, 0.18, 0.0], [2, -2, 1, 0])]
    anims["hurt"][1] = tint(anims["hurt"][1], MAG, 0.25)
    anims["death"] = [frame(theta=20 * i, s=1.2 + 0.25 * i, b=min(1.0, 0.3 + 0.18 * i),
                            tint_c=LILAC, tint_a=0.12 * i, dissolve=d, seed=1000 + i)
                      for i, d in enumerate([0.0, 0.18, 0.38, 0.58, 0.78, 0.94])]
    return anims


def process_official_enemy(filename):
    """Corpo oficial sem os arcos; núcleo e arcos são redesenhados por frame."""
    src = load(f"enemies/{filename}")
    pad = (ENEMY_CANVAS - 128) // 2
    canvas = Image.new("RGBA", (ENEMY_CANVAS, ENEMY_CANVAS), (0, 0, 0, 0))
    canvas.paste(src, (pad, pad))
    cx = cy = ENEMY_CANVAS / 2
    profile = core_profile(canvas, cx, cy)
    a = arr(canvas)
    mask = arc_mask(a, cx, cy, TEMPLATE["arc_r"] - 3.3, TEMPLATE["arc_r"] + 3.3)
    body = inpaint(a, mask)
    # Onde o arco passava FORA do corpo o preenchimento mistura corpo e vazio e
    # deixa uma mancha translúcida: ali o certo é transparente.
    body[..., 3][mask & (body[..., 3] < 200)] = 0
    return img(body), profile


def vertex_points(cx, cy, r, n, start):
    return poly_pts(cx, cy, r, n, start)


def write_anims(prefix, anims):
    counts = {}
    for name, frames in anims.items():
        for i, f in enumerate(frames, 1):
            save(img(f), f"{prefix}/{name}_{i:02d}.png")
        counts[name] = len(frames)
    return counts


def lowest_row(frames_list):
    low = 0
    for f in frames_list:
        bb = opaque_bbox(f)
        if bb:
            low = max(low, bb[3])
    return low


def build_enemies(official_profile_src):
    T = TEMPLATE
    S = ENEMY_CANVAS
    c = S / 2
    enemies = {}

    # ---- oficiais: triângulo, losango (square.png), círculo, pentágono
    official = {
        "triangle": ("triangle.png", 3, 90.0),
        "diamond": ("square.png", 4, 0.0),     # square.png é o quadrado girado 45°
        "pentagon": ("pentagonon.png", 5, 90.0),
        "circle": ("cricle.png", 0, 90.0),
    }
    for key, (fname, n, start) in official.items():
        body, profile = process_official_enemy(fname)
        pts = vertex_points(c, c, T["vertex_r"], n if n else 32, start)
        anims = build_enemy_frames(body, c, c, profile, pts, S, T["arc_r"], T["arc_w"])
        enemies[key] = {"anims": anims, "sides": n, "start": start, "weak_r": T["vertex_r"],
                        "outer_r": T["outer_r"], "source": f"enemies/{fname}"}

    profile = core_profile(official_profile_src, c, c)

    # ---- hexágono (desenhado): pontudo em cima, marcadores nos 6 vértices
    pts = vertex_points(c, c, T["vertex_r"], 6, 90)
    body = draw_body(S, c, c, 6, 90, markers=pts, spokes_to=pts)
    enemies["hexagon"] = {"anims": build_enemy_frames(body, c, c, profile, pts, S, T["arc_r"], T["arc_w"]),
                          "sides": 6, "start": 90.0, "weak_r": T["vertex_r"], "outer_r": T["outer_r"],
                          "source": "gerado"}

    # ---- quadrado (desenhado, alinhado aos eixos): marcadores no MEIO dos lados,
    # que é onde ficam os pontos fracos do QUADRADO no jogo.
    # Apótema 50: com o raio externo das outras formas (60) o quadrado ficava
    # visivelmente menor; 50 é o maior que cabe no canvas de 144 com as quinas.
    ap = 50.0
    sq_r = ap / math.cos(math.pi / 4)
    side_r = round(ap - 2.4, 1)
    pts = vertex_points(c, c, side_r, 4, 0)
    corners = vertex_points(c, c, sq_r * 0.86, 4, 45)
    body = draw_body(S, c, c, 4, 45, markers=pts, spokes_to=corners, outer_r=sq_r)
    enemies["square"] = {"anims": build_enemy_frames(body, c, c, profile, pts, S, T["arc_r"], T["arc_w"]),
                         "sides": 4, "start": 45.0, "weak_r": side_r, "outer_r": round(sq_r, 2),
                         "source": "gerado"}

    out = {}
    for key, e in enemies.items():
        counts = write_anims(f"enemies/{key}", e["anims"])
        rest = e["anims"]["idle"] + e["anims"]["move"]
        low = lowest_row(rest)
        out[key] = {
            "canvas": S, "scale": ENEMY_SCALE, "frames": counts,
            "center_px": [c, c],
            # linha mais baixa ocupada em repouso/movimento (inclui arcos):
            # é ela que encosta no chão.
            "baseline_px": S - 1 - low,
            "sides": e["sides"], "start_deg": e["start"],
            "weak_r_px": e["weak_r"], "outer_r_px": e["outer_r"],
            "source": e["source"],
        }
    return out


def build_boss(profile_src):
    T = TEMPLATE
    k = BOSS_K
    S = BOSS_CANVAS
    c = S / 2
    vr = T["vertex_r"] * k
    pts = vertex_points(c, c, vr, 6, 90)
    sub = vertex_points(c, c, vr * 0.5, 3, 90)          # 3 núcleos da fase do espelho
    sides = vertex_points(c, c, vr * math.cos(math.pi / 6), 6, 0)

    def extra(cv):
        # triângulo interno ligando os núcleos + hexágono girado de apoio
        cv.polyline(sub + [sub[0]], (*SPOKE, 255), 1.6)
        inner = poly_pts(c, c, T["outer_r"] * k * 0.36, 6, 0)
        cv.polyline(inner + [inner[0]], (*TEAL, 200), 1.2)
        for x, y in sub:
            cv.disc(x, y, 7.5, (*DARK, 255))
            cv.ring(x, y, 6.2, (*CYAN, 255), 1.6)
            cv.disc(x, y, 3.0, (*MAG, 255))
            cv.disc(x, y, 1.4, (*WHITE, 255))

    body = draw_body(S, c, c, 6, 90, k=k, markers=pts, spokes_to=pts, extra=extra)
    body_red = draw_body(S, c, c, 6, 90, k=k, markers=pts, spokes_to=pts, extra=extra, outline=RED)
    # núcleo: perfil oficial ampliado (mesmo desenho, escala 1,75)
    small_c = ENEMY_CANVAS / 2
    profile = core_profile(profile_src, small_c, small_c)
    arc_r = T["arc_r"] * k
    arc_w = T["arc_w"] * 1.4
    extra_arcs = [(T["arc_r"] * k * 0.78, 200, 290, MAG, 1.6), (T["arc_r"] * k * 0.78, 20, 110, CYAN, 1.6)]

    def frame(b_img=body, theta=0.0, s=1.0, br=0.0, flash=0.0, sub_glow=0.0, hue=None,
              arc_cols=None, tint_c=None, tint_a=0.0, dissolve=0.0, seed=0, beam=0.0, orbs=0.0):
        a = draw_core(arr(b_img), c, c, profile, scale=s * k, bright=br, hue=hue,
                      clear_r=T["core_r"] * k)
        im = img(a)
        im = over(im, arcs_layer(S, c, c, arc_r, arc_w, theta, arc_cols, extra_arcs))
        if sub_glow > 0:
            g = AA(S, S)
            for x, y in sub:
                g.disc(x, y, 5 + 5 * sub_glow, (*LILAC, int(120 * sub_glow)))
                g.disc(x, y, 3 + 2 * sub_glow, (*WHITE, int(255 * sub_glow)))
            im = over(im, g.result())
        if orbs > 0:
            g = AA(S, S)
            for i in range(6):
                ang = math.radians(90 + 60 * i + theta)
                rr = vr * (0.55 + 0.5 * orbs)
                x, y = c + rr * math.cos(ang), c - rr * math.sin(ang)
                g.disc(x, y, 5.5, (*MAG, 255))
                g.disc(x, y, 2.6, (*WHITE, 255))
            im = over(im, g.result())
        if beam > 0:
            g = AA(S, S)
            hw = 2 + 6 * beam
            g.polygon([(c, c - hw), (S, c - hw * 0.6), (S, c + hw * 0.6), (c, c + hw)], (*LILAC, 220))
            g.polygon([(c, c - hw), (0, c - hw * 0.6), (0, c + hw * 0.6), (c, c + hw)], (*LILAC, 220))
            g.polygon([(c, c - hw * 0.4), (S, c - hw * 0.2), (S, c + hw * 0.2), (c, c + hw * 0.4)], (*WHITE, 255))
            g.polygon([(c, c - hw * 0.4), (0, c - hw * 0.2), (0, c + hw * 0.2), (c, c + hw * 0.4)], (*WHITE, 255))
            im = over(im, g.result())
        fl = markers_flash_layer(S, pts, T["marker_r"] * k, flash)
        if fl is not None:
            im = over(im, fl)
        a = arr(im)
        if tint_c is not None and tint_a > 0:
            a = tint(a, tint_c, tint_a)
        if dissolve > 0:
            a = noise_dissolve(a, dissolve, seed)
        return a

    anims = {}
    anims["idle"] = [frame(theta=5 * math.sin(2 * math.pi * i / 6), s=1 + 0.05 * math.sin(2 * math.pi * i / 6),
                           br=0.1 + 0.1 * math.sin(2 * math.pi * i / 6)) for i in range(6)]
    anims["powerup"] = [frame(theta=30 * i, s=1 + 0.12 * i, br=0.15 * i, flash=f, sub_glow=0.2 * i)
                        for i, f in enumerate([0, 0.2, 0.4, 0.7, 1.0, 0.6])]
    anims["orbs"] = [frame(theta=12 * i, s=1 + 0.08 * i, br=0.12 * i, sub_glow=g, orbs=o)
                     for i, (g, o) in enumerate([(0.3, 0), (0.6, 0), (1.0, 0.1), (0.7, 0.55), (0.3, 1.0)])]
    anims["beam"] = [frame(s=1 + 0.1 * i, br=0.15 + 0.15 * i, beam=bm)
                     for i, bm in enumerate([0.0, 0.25, 0.7, 1.0, 0.45])]
    red_arcs = [RED, MAG]
    anims["enraged"] = [frame(b_img=body_red, theta=-40 * i, s=1.08 + 0.06 * (i % 2), br=0.2 + 0.1 * i,
                              hue=RED, arc_cols=red_arcs) for i in range(4)]
    anims["death"] = [frame(theta=25 * i, s=1.2 + 0.3 * i, br=min(1.0, 0.3 + 0.15 * i), flash=max(0, 1 - 0.3 * i),
                            tint_c=LILAC, tint_a=0.1 * i, dissolve=d, seed=2000 + i)
                      for i, d in enumerate([0.0, 0.15, 0.35, 0.55, 0.75, 0.93])]
    counts = write_anims("boss", anims)
    low = lowest_row(anims["idle"])
    return {
        "canvas": S, "scale": BOSS_SCALE, "frames": counts, "center_px": [c, c],
        "baseline_px": S - 1 - low, "sides": 6, "start_deg": 90.0,
        "weak_r_px": vr, "side_r_px": vr * math.cos(math.pi / 6), "core_r_px": vr * 0.5,
        "outer_r_px": T["outer_r"] * k, "source": "gerado",
    }


# ================================================================ player

def build_player():
    C = PLAYER_CANVAS
    raw = {
        "idle": load("player/Personagem principal.png"),
        "run": load("player/correndo.png"),
        "jump": load("player/principal pulando.png"),
    }
    FEET = C - 5  # linha dos pés no canvas 48 (4 px livres embaixo)
    base = {}
    for k, im in raw.items():
        a = arr(im)
        bb = opaque_bbox(a)
        cxs = (bb[0] + bb[2] + 1) / 2
        dx = int(round(C / 2 - cxs))
        dy = FEET - bb[3]
        canvas = np.zeros((C, C, 4), dtype=np.uint8)
        canvas[dy:dy + 32, dx:dx + 32] = a
        base[k] = canvas

    def body_rows(a):
        bb = opaque_bbox(a)
        return bb[1], bb[3]

    def squash(a, n=1):
        """Remove n linhas do meio do corpo e desce a parte de cima (pés fixos)."""
        top, bot = body_rows(a)
        out = a.copy()
        for _ in range(n):
            mid = (top + bot) // 2
            out = np.concatenate([np.zeros_like(out[:1]), out[:mid], out[mid + 1:]], axis=0)
        return out

    def stretch(a, n=1):
        top, bot = body_rows(a)
        out = a.copy()
        for _ in range(n):
            mid = (top + bot) // 2
            out = np.concatenate([out[1:mid + 1], out[mid:mid + 1], out[mid + 1:]], axis=0)
        return out

    def widen(a, n=1):
        bb = opaque_bbox(a)
        mid = (bb[0] + bb[2]) // 2
        out = a.copy()
        for _ in range(n):
            out = np.concatenate([out[:, 1:mid + 1], out[:, mid:mid + 1], out[:, mid + 1:]], axis=1)
        return out

    def lean(a, amount):
        """Inclinação por linha: a cabeça desloca 'amount' px, os pés ficam."""
        top, bot = body_rows(a)
        out = np.zeros_like(a)
        for y in range(C):
            if y > bot or a[y, :, 3].max() == 0:
                out[y] = a[y]
                continue
            s = int(round(amount * (bot - y) / max(1, bot - top)))
            out[y] = np.roll(a[y], s, axis=0)
        return out

    def legs_shear(a, amount):
        top, bot = body_rows(a)
        legs = bot - 6
        out = a.copy()
        for y in range(legs, bot + 1):
            s = int(round(amount * (y - legs) / 6))
            out[y] = np.roll(a[y], s, axis=0)
        return out

    def blink(a):
        top, _ = body_rows(a)
        out = a.copy()
        region = out[top + 5:top + 15, 8:40]
        rgb = region[..., :3].astype(int)
        eyes = (region[..., 3] > 0) & (rgb.min(axis=2) > 170)
        # olho fechado: a cor mais escura do visor
        region[eyes] = [16, 10, 42, 255]
        return out

    def rot90_grounded(a, k):
        r = np.rot90(a, k=k).copy()
        bb = opaque_bbox(r)
        cx = (bb[0] + bb[2] + 1) / 2
        return shift(r, int(round(C / 2 - cx)), FEET - bb[3])

    I, R, J = base["idle"], base["run"], base["jump"]
    anims = {
        "idle": [I, I, squash(I), squash(I), I, blink(I)],
        "walk": [R, shift(R, 0, -1), legs_shear(R, 1), R, shift(R, 0, -1), legs_shear(R, -1)],
        "jump": [J, stretch(J)],
        "dash": [lean(R, 1), widen(lean(R, 2)), widen(lean(R, 3), 2), widen(lean(R, 2)), lean(R, 1)],
        "attack": [tint(I, CYAN, 0.35), shift(squash(I), -1, 0), shift(I, -1, 0), I],
        "hurt": [tint(I, WHITE, 0.8), shift(tint(I, MAG, 0.5), -1, 0), shift(tint(I, MAG, 0.25), 1, 0), I],
        "death": [tint(I, WHITE, 0.6), squash(I, 2), rot90_grounded(I, -1),
                  dither_dissolve(rot90_grounded(I, -1), 0.4), dither_dissolve(rot90_grounded(I, -1), 0.8)],
    }
    counts = write_anims("player", anims)
    # Linha do pé medida no frame de idle depois de montado.
    low = opaque_bbox(anims["idle"][0])[3]
    return {"canvas": C, "scale": PLAYER_SCALE, "frames": counts, "baseline_px": C - 1 - low,
            "sources": ["player/Personagem principal.png", "player/correndo.png", "player/principal pulando.png"]}


# ============================================================== efeitos/UI

def pixel_disc(size, rings):
    """Pixel art sem AA: rings = [(raio, cor RGBA)] do maior para o menor."""
    a = np.zeros((size, size, 4), dtype=np.uint8)
    c = (size - 1) / 2
    yy, xx = np.mgrid[0:size, 0:size]
    d = np.hypot(xx - c, yy - c)
    for r, col in rings:
        a[d <= r + 0.35] = col
    return a


def pixel_ring(a, r, col, w=1.0):
    size = a.shape[0]
    c = (size - 1) / 2
    yy, xx = np.mgrid[0:size, 0:size]
    d = np.hypot(xx - c, yy - c)
    a[(d >= r - w / 2) & (d <= r + w / 2 + 0.2)] = col
    return a


def build_fx():
    info = {}
    save(img(pixel_disc(11, [(5, (*CYAN, 255)), (3.2, (160, 245, 255, 255)), (1.6, (*WHITE, 255))])),
         "fx/player_shot.png")
    save(img(pixel_disc(11, [(5, (*MAG, 255)), (3.2, (*LILAC, 255)), (1.6, (*WHITE, 255))])),
         "fx/enemy_shot.png")
    bs = pixel_disc(18, [(8.4, (*RED, 255)), (6.6, (*MAG, 255)), (4.2, (*LILAC, 255)), (2.2, (*WHITE, 255))])
    for i in range(8):  # espinhos
        ang = math.radians(i * 45)
        x, y = int(round(8.5 + 8.4 * math.cos(ang))), int(round(8.5 - 8.4 * math.sin(ang)))
        if 0 <= x < 18 and 0 <= y < 18:
            bs[y, x] = (*WHITE, 255)
    save(img(bs), "fx/boss_shot.png")

    def burst(name, main, accent, n=6, size=32, shards=8):
        frames = []
        for i in range(n):
            t = i / (n - 1)
            a = np.zeros((size, size, 4), dtype=np.uint8)
            r = 3 + t * 12
            pixel_ring(a, r, (*main, 255), 2.0 if t < 0.6 else 1.0)
            if t < 0.4:
                a = np.maximum(a, pixel_disc(size, [(4 - 6 * t, (*WHITE, 255))]))
            for s in range(shards):
                ang = math.radians(s * 360 / shards + 22.5 * (s % 2))
                rr = r + 2 + 3 * t
                x = int(round((size - 1) / 2 + rr * math.cos(ang)))
                y = int(round((size - 1) / 2 - rr * math.sin(ang)))
                if 0 <= x < size and 0 <= y < size:
                    a[y, x] = (*accent, 255)
                    if t < 0.5 and 0 <= y + 1 < size:
                        a[y + 1, x] = (*accent, 255)
            a[..., 3] = (a[..., 3] > 0) * 255
            frames.append(dither_dissolve(a, max(0.0, t - 0.35) * 1.4))
        for i, f in enumerate(frames, 1):
            save(img(f), f"fx/{name}_{i:02d}.png")
        return n

    info["hit_burst"] = burst("hit_burst", CYAN, WHITE)
    info["void_burst"] = burst("void_burst", MAG, LILAC)
    # rastro do dash: faixas horizontais que encurtam
    n = 4
    for i in range(n):
        a = np.zeros((32, 32, 4), dtype=np.uint8)
        for row, col, ln in [(12, LILAC, 26), (15, CYAN, 30), (18, MAG, 24), (21, LILAC, 18)]:
            L = int(ln * (1 - i / n))
            a[row, 32 - L:32] = (*col, 255)
        save(img(dither_dissolve(a, i * 0.2)), f"fx/dash_trail_{i + 1:02d}.png")
    info["dash_trail"] = n

    # marcador de ponto fraco (3 tamanhos nativos, desenhados a 2x)
    for size in (15, 17, 22):
        a = np.zeros((size, size, 4), dtype=np.uint8)
        r = (size - 1) / 2
        pixel_ring(a, r - 0.6, (*MAG, 255), 1.4)
        pixel_ring(a, r - 3.2, (*CYAN, 255), 1.0)
        a = np.maximum(a, pixel_disc(size, [(r * 0.28, (*WHITE, 255))]))
        m = size // 2
        a[m, 0:2] = a[m, size - 2:] = a[0:2, m] = a[size - 2:, m] = (*WHITE, 255)
        save(img(a), f"ui/weakpoint_{size}.png")
    # mira
    a = np.zeros((17, 17, 4), dtype=np.uint8)
    pixel_ring(a, 6.2, (*WHITE, 230), 1.0)
    for i in list(range(0, 5)) + list(range(12, 17)):
        a[8, i] = a[i, 8] = (*WHITE, 255)
    a[8, 8] = (*CYAN, 255)
    save(img(a), "ui/crosshair.png")
    # seta guia (aponta para cima; o jogo espelha)
    a = np.zeros((16, 12, 4), dtype=np.uint8)
    for y in range(8):  # ponta triangular
        half = y // 2 + 1
        a[y, 6 - half:6 + half] = (*CYAN, 255)
    a[8:16, 4:8] = (*CYAN, 255)  # haste
    save(img(a), "ui/guide_arrow.png")
    # orbe de vida pequena (14 px, desenhada a 2x) no desenho da life_orb oficial
    a = pixel_disc(14, [(6.3, (255, 72, 196, 255)), (5.0, (0, 0, 0, 0)), (4.2, (100, 250, 255, 255)),
                        (3.0, (0, 0, 0, 0)), (2.2, (*WHITE, 255))])
    save(img(a), "ui/life_orb_small.png")
    save(Image.new("RGBA", (1, 1), (255, 255, 255, 255)), "ui/pixel.png")
    return info


def build_ui_and_world():
    # ---- UI oficial (cópia 1:1; o jogo usa NinePatch com bordas nativas)
    for src, dst in [("ui/hud_panel.png", "ui/hud_panel.png"), ("ui/enemy_painel.png", "ui/enemy_panel.png"),
                     ("ui/diolog_painel.png", "ui/dialog_panel.png"), ("ui/life_orb.png", "ui/life_orb.png")]:
        save(load(src), dst)

    # Cortes de NinePatch: até onde a borda deixa de ser igual à linha/coluna
    # do meio (inclui os detalhes de canto, que assim nunca esticam).
    patches = {}
    for name in ("hud_panel", "enemy_panel", "dialog_panel"):
        pa = arr(Image.open(OUT / f"ui/{name}.png").convert("RGBA")).astype(int)
        h, w = pa.shape[:2]
        cols = [x for x in range(w) if np.abs(pa[:, x] - pa[:, w // 2]).sum() > 0]
        rows = [y for y in range(h) if np.abs(pa[y] - pa[h // 2]).sum() > 0]
        patches[name] = {
            "left": max(x for x in cols if x < w // 2) + 1, "right": w - min(x for x in cols if x > w // 2),
            "top": max(y for y in rows if y < h // 2) + 1, "bottom": h - min(y for y in rows if y > h // 2)}
    manifest["ninepatch"] = patches

    # ---- fundo: 9 camadas 480x270 (desenhadas a 4x) + composição para menus
    layers = ["ceu", "nuvem1", "nuvem2", "nuvem3", "nuvem4", "montanhas", "estruturas-fundo", "lago", "chao"]
    comp = Image.new("RGBA", (480, 270))
    for i, name in enumerate(layers):
        im = load(f"fundo/{name}.png")
        save(im, f"background/{i:02d}_{name}.png")
        comp.alpha_composite(im)
    save(comp, "background/menu.png")

    # ---- mundo
    for name in ("gate", "portal", "wall"):
        save(load(f"word/{name}.png"), f"world/{name}.png")
    crystal = load("word/crystal.png")
    crystal = crystal.crop(crystal.getchannel("A").getbbox())
    save(crystal, "world/crystal.png")

    platform_info = {}
    for name in ("platform", "platform_alt"):
        p = load(f"word/{name}.png")
        top = p.getchannel("A").getbbox()[1]
        p = p.crop((0, top, 256, 64))
        # ponta (6 px), módulo caixa+vão (54 px) e uma coluna lisa de preenchimento
        save(p.crop((0, 0, 6, p.height)), f"world/{name}_cap.png")
        save(p.crop((6, 0, 60, p.height)), f"world/{name}_module.png")
        save(p.crop((6, 0, 7, p.height)), f"world/{name}_fill.png")
        platform_info[name] = {"height": p.height, "cap": 6, "module": 54}

    # ---- piso do mundo: tile 64 x 126 (FLOOR_Y) no estilo das plataformas
    def ground(top_color, top2):
        a = np.zeros((126, 64, 4), dtype=np.uint8)
        a[:, :] = (*PANEL_DARK, 255)
        a[0:2] = (*top_color, 255)
        a[2] = (*top2, 255)
        a[3:5] = (27, 28, 54, 255)
        for y in range(34, 126, 32):
            a[y] = (*PANEL_LINE, 255)
        for band, y0 in enumerate(range(5, 126, 32)):
            x = 0 if band % 2 == 0 else 32
            a[y0:min(126, y0 + 29), x] = (*PANEL_LINE, 255)
        a[12:14, 20:34] = (*CYAN, 200)  # detalhe ciano como nas plataformas
        a[12:14, 20:34, :3] = (84, 180, 215)
        return img(a)
    save(ground(NEON_MAG, (173, 61, 203)), "world/ground.png")
    save(ground(NEON_CYAN, (40, 170, 190)), "world/ground_alt.png")

    # ---- checkpoint: cristal oficial sobre base escura com filete ciano
    base_w, base_h = 64, 20
    cp = Image.new("RGBA", (64, crystal.height + base_h))
    cp.alpha_composite(crystal, ((64 - crystal.width) // 2, 0))
    b = np.zeros((base_h, base_w, 4), dtype=np.uint8)
    b[:, 6:58] = (*PANEL_DARK, 255)
    b[0:2, 6:58] = (*NEON_CYAN, 255)
    b[:, 6] = b[:, 57] = (*PANEL_LINE, 255)
    cp.alpha_composite(img(b), (0, crystal.height))
    save(cp, "world/checkpoint.png")

    # ---- cacho de cristais (decoração)
    cl = Image.new("RGBA", (128, crystal.height + 8))
    cl.alpha_composite(crystal, (0, 8))
    cl.alpha_composite(crystal, (64, 8))
    cl.alpha_composite(crystal, (32, 0))
    save(cl, "world/crystal_cluster.png")

    # ---- arco em ruína: duas colunas (wall) + verga feita do módulo da plataforma
    wall = load("word/wall.png")
    module = load("word/platform.png")
    module = module.crop((0, module.getchannel("A").getbbox()[1], 128, 64))
    arch = Image.new("RGBA", (200, 256))
    arch.alpha_composite(wall, (0, 0))
    arch.alpha_composite(wall.crop((0, 60, 64, 256)), (136, 60))
    arch.alpha_composite(module.crop((0, 0, 128, 40)), (40, 0))
    save(arch, "world/arch.png")
    save(wall.crop((0, 96, 64, 256)), "world/wall_broken.png")
    return platform_info


# ================================================================ preview

def contact_sheet():
    PREVIEW.mkdir(parents=True, exist_ok=True)
    groups = {}
    for rel in written:
        groups.setdefault(rel.rsplit("/", 1)[0], []).append(rel)
    for group, rels in groups.items():
        ims = [Image.open(OUT / r).convert("RGBA") for r in sorted(rels)]
        scale = max(1, min(8, 128 // max(max(i.size) for i in ims)))
        w = sum(i.width * scale + 6 for i in ims)
        h = max(i.height * scale for i in ims)
        cols_w = min(w, 1800)
        sheet = Image.new("RGBA", (cols_w, 20 + (h + 6) * (w // cols_w + 1)), (40, 40, 48, 255))
        x = y = 0
        for im in ims:
            big = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
            if x + big.width > cols_w:
                x, y = 0, y + h + 6
            bg = Image.new("RGBA", big.size, (70, 70, 80, 255))
            bg.alpha_composite(big)
            sheet.paste(bg, (x, y))
            x += big.width + 6
        sheet.save(PREVIEW / (group.replace("/", "_") + ".png"))


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--preview", action="store_true", help="grava contact sheets em tmp/asset-preview/")
    args = ap.parse_args()
    if not SRC.is_dir():
        raise SystemExit(f"Pasta oficial não encontrada: {SRC}")
    before = {p.relative_to(OUT).as_posix() for p in OUT.rglob("*") if p.is_file()} if OUT.is_dir() else set()

    # núcleo de referência para as formas desenhadas: o do triângulo oficial
    ref = Image.new("RGBA", (ENEMY_CANVAS, ENEMY_CANVAS))
    ref.paste(load("enemies/triangle.png"), ((ENEMY_CANVAS - 128) // 2,) * 2)

    manifest["player"] = build_player()
    manifest["enemies"] = build_enemies(ref)
    manifest["boss"] = build_boss(ref)
    manifest["fx"] = build_fx()
    manifest["platform"] = build_ui_and_world()
    manifest["scales"] = {"background": BG_SCALE, "player": PLAYER_SCALE, "enemy": ENEMY_SCALE,
                          "boss": BOSS_SCALE, "world": 1, "portal": 2}
    manifest["files"] = sorted(written)
    (OUT / "manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    stale = sorted(before - set(written) - {"manifest.json"})
    print(f"{len(written)} arquivos gerados em {OUT.relative_to(ROOT)}")
    if stale:
        print("Arquivos antigos que não são mais gerados (não apagados):")
        for s in stale:
            print("  ", s)
    if args.preview:
        contact_sheet()
        print(f"previews em {PREVIEW.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
