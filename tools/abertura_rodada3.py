"""Rodada 3, etapa 4: arte da abertura nova e a imagem-chave (key art).

Gera em assets/sprites/intro/:
- rift_NN.png        : o Rift (rasgo vertical irregular, sem anel) abrindo, 8 quadros;
- crack_NN.png       : a rachadura no céu antes do Rift, 5 quadros;
- boss_sil_NN.png    : silhueta do chefe (pentágono, como na etapa 6) com olhos
                       apagados/acendendo, 4 quadros;
- calm_<forma>.png   : triângulo, quadrado, losango e hexágono calmos (antes da
                       corrupção), com rosto sereno, 2 quadros cada (flutuando);
- egg_NN.png         : o cristal caído que pulsa, racha e se parte, 9 quadros;
- logo_part_*.png    : as peças do logo (PROJECT, ORB, R, B) para a montagem;
- orb_big.png, pi_big.png : ORB e Pi grandes (mesmo tamanho de pixel) da key art;
e assets/sprites/ui/key_art.png (480x270, sem logo: fundo do menu), mais
docs/key_art.png (1x) e docs/key_art_4x.png (com logo).
"""
from __future__ import annotations

import math
from pathlib import Path

import numpy as np
from PIL import Image

import pxlib as px
import ui_rodada3 as ui

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "assets/sprites/intro"
BG = ROOT / "art-source/ProjetoFinal_TCC/fundo"
SPR = ROOT / "assets/sprites"
PROOF = ROOT / "docs/qa/rodada3/etapa4/arte"
GAME_BG = (22, 18, 92)
LIGHT_BG = (225, 222, 240)

# Rift: branco no miolo, magenta/violeta nas bordas, franja ciano (sem amarelo)
RIFT = [(62, 10, 92), (150, 30, 170), (226, 72, 214), (170, 110, 255), (236, 220, 255), (255, 252, 255)]
RIFT_CYAN = (120, 236, 248)
CRACK = [(120, 70, 220), (214, 190, 255)]
# Silhueta do chefe: quase preto violeta, olhos em magenta quente
SIL = [(10, 4, 22), (26, 10, 48), (46, 18, 80)]
EYE = [(150, 20, 90), (255, 90, 170), (255, 220, 240)]
# Formas calmas: tons suaves (antes da corrupção, sem neon)
CALM = {
    "triangle": [(40, 60, 110), (70, 110, 170), (120, 170, 220), (190, 225, 245)],
    "square": [(40, 70, 90), (60, 120, 130), (110, 180, 170), (190, 240, 220)],
    "diamond": [(70, 50, 100), (120, 90, 160), (180, 150, 220), (235, 220, 250)],
    "hexagon": [(60, 50, 110), (100, 90, 170), (150, 150, 225), (215, 215, 250)],
}
FACE = (24, 20, 48)
# Cristal-ovo do ORB: violeta (o ORB é roxo)
EGG = [(28, 10, 64), (60, 20, 130), (110, 50, 220), (160, 110, 250), (220, 200, 255), (255, 250, 255)]
# Pedra do primeiro plano da key art
ROCK = [(8, 6, 20), (18, 14, 40), (32, 24, 66), (52, 40, 100), (80, 64, 140), (120, 100, 190)]
# Pi (marfim + azul-petróleo, sem laranja/amarelo)
PI = {"K": (22, 26, 40), "I": (238, 232, 214), "S": (196, 208, 204), "T": (38, 112, 122),
      "U": (18, 66, 78), "W": (255, 253, 246)}


# ------------------------------------------------------------------ Rift

def rift_mask(w, h, open_t, seed=3):
    """Rasgo vertical: meia-largura em lente, borda serrilhada em degraus de 1-2 px."""
    rng = np.random.default_rng(seed)
    m = np.zeros((h, w), dtype=bool)
    cx = w / 2
    jag = rng.integers(-2, 3, size=h)
    for y in range(1, len(jag) - 1):     # sem pico isolado
        if jag[y - 1] == jag[y + 1]:
            jag[y] = jag[y - 1]
    drift = np.cumsum(rng.integers(-1, 2, size=h)) * 0.25
    for y in range(h):
        t = y / (h - 1)
        half = (w / 2 - 3) * (math.sin(math.pi * t) ** 0.9) * open_t
        if half < 0.6:
            continue
        c = cx + drift[y] - drift.mean()
        hl = max(0.5, half + jag[y] * 0.5 * open_t)
        hr = max(0.5, half - jag[(y * 7) % h] * 0.5 * open_t)
        x0, x1 = int(round(c - hl)), int(round(c + hr))
        m[y, max(0, x0):min(w, x1 + 1)] = True
    return m


def paint_rift(w, h, open_t, seed=3):
    a = px.canvas(w, h)
    m = rift_mask(w, h, open_t, seed)
    if not m.any():
        return a
    # halo pontilhado em volta (2 px), borda magenta, violeta, miolo branco
    halo = px.outer_edge(m, diag=True)
    halo2 = px.outer_edge(m | halo, diag=True)
    px.fill(a, px.dither(halo2, 0.5), RIFT[0])
    px.fill(a, halo, RIFT[1])
    # faixas pela distância (horizontal) até a borda do rasgo: magenta,
    # violeta, pontilhado violeta/lilás, lilás claro no miolo
    dist = np.zeros((h, w), dtype=int)
    for y in range(h):
        xs = np.nonzero(m[y])[0]
        if len(xs):
            dist[y, xs] = np.minimum(xs - xs.min(), xs.max() - xs)
    px.fill(a, m, RIFT[2])
    px.fill(a, m & (dist >= 1), RIFT[3])
    mid = m & (dist >= 3) & (dist < 6)
    px.fill(a, px.dither(mid, 0.5), RIFT[4])
    core = m & (dist >= 6)
    px.fill(a, core, RIFT[4])
    inner = m & (dist >= 1)
    # linha branca do meio (fio), quebrada
    ys, xs = np.nonzero(core)
    for y in sorted(set(ys.tolist())):
        row = xs[ys == y]
        if len(row) and y % 7 != 3:
            a[y, int(round(row.mean()))] = px.rgba(RIFT[5])
    # franja ciano: poucos pixels colados à borda, lado de cima-esquerda
    edge = halo & px.shift(m, 1, 0)
    px.fill(a, px.dither(edge, 0.35), RIFT_CYAN)
    return a


def crack_frames(w=120, h=90, n=5, seed=9):
    """Rachadura no céu: linha serrilhada que cresce a partir do centro e se ramifica."""
    rng = np.random.default_rng(seed)
    main = [(w // 2, h // 2)]
    for _ in range(18):
        x, y = main[-1]
        main.append((x + int(rng.integers(-3, 4)), y - int(rng.integers(2, 4))))
    down = [(w // 2, h // 2)]
    for _ in range(14):
        x, y = down[-1]
        down.append((x + int(rng.integers(-3, 4)), y + int(rng.integers(2, 4))))
    branches = []
    for base in (main[6], main[12], down[5], down[10]):
        b = [base]
        dx = int(rng.choice([-1, 1]))
        for _ in range(6):
            x, y = b[-1]
            b.append((x + dx * int(rng.integers(2, 4)), y + int(rng.integers(-2, 3))))
        branches.append(b)
    frames = []
    for f in range(n):
        t = (f + 1) / n
        a = px.canvas(w, h)
        for path, lim in [(main, t), (down, t)] + [(b, max(0.0, t * 1.6 - 0.6)) for b in branches]:
            k = int(round(lim * (len(path) - 1)))
            for i in range(k):
                (x0, y0), (x1, y1) = path[i], path[i + 1]
                px.line(a, x0, y0, x1, y1, CRACK[1] if f >= 2 else CRACK[0])
        m = px.opaque(a)
        px.fill(a, px.outer_edge(m) & px.dither(px.outer_edge(m), 0.5), CRACK[0])
        frames.append(a)
    return frames


# ---------------------------------------------------------- chefe (silhueta)

def pentagon(w, h, cx, cy, r, rot=-90):
    pts = [(cx + r * math.cos(math.radians(rot + 72 * i)), cy + r * math.sin(math.radians(rot + 72 * i))) for i in range(5)]
    return px.poly_mask(w, h, pts)


def boss_silhouette(eyes=1.0, size=72):
    a = px.canvas(size, size)
    c = size / 2
    body = pentagon(size, size, c, c + 2, c - 3)
    px.fill(a, body, SIL[1])
    # borda interna mais escura e aresta de cima levemente acesa pelo Rift
    px.fill(a, body & ~px.shift(body, 0, -1), SIL[0])
    px.fill(a, body & ~px.shift(body, 0, 1), SIL[2])
    # espinhos nos vértices (triângulos curtos), como os inimigos corrompidos
    for i in range(5):
        ang = math.radians(-90 + 72 * i)
        vx, vy = c + (c - 3) * math.cos(ang), c + 2 + (c - 3) * math.sin(ang)
        tip = (c + (c + 1) * math.cos(ang), c + 2 + (c + 1) * math.sin(ang))
        pa = (vx + 3 * math.cos(ang + 1.6), vy + 3 * math.sin(ang + 1.6))
        pb = (vx + 3 * math.cos(ang - 1.6), vy + 3 * math.sin(ang - 1.6))
        spike = px.poly_mask(size, size, [pa, tip, pb])
        px.fill(a, spike, SIL[0])
    # olhos: fendas inclinadas para o meio (raiva), 2 px de altura, acendendo
    if eyes > 0:
        col = EYE[1] if eyes >= 1 else EYE[0]
        ey = int(c + 4)
        for side in (-1, 1):
            for k in range(6):
                x = int(c + side * (4 + k))
                y = ey - (k // 2)                  # sobe para fora: degraus 2-2-2
                a[y, x] = px.rgba(col)
                a[y + 1, x] = px.rgba(col)
            if eyes >= 1:
                a[ey, int(c + side * 5)] = px.rgba(EYE[2])
    px.fill(a, px.outer_edge(px.opaque(a)), SIL[0])
    return a


# ---------------------------------------------------------- formas calmas

def calm_shape(kind, bob=0):
    size = 40
    a = px.canvas(size, size + 2)
    c = size / 2
    ramp = CALM[kind]
    if kind == "triangle":
        m = px.poly_mask(size, size + 2, [(c, 4 + bob), (size - 4, size - 6 + bob), (4, size - 6 + bob)])
    elif kind == "square":
        m = px.rect_mask(size, size + 2, 7, 7 + bob, size - 7, size - 7 + bob)
    elif kind == "diamond":
        m = px.poly_mask(size, size + 2, [(c, 3 + bob), (size - 8, c + bob), (c, size - 3 + bob), (8, c + bob)])
    else:
        pts = [(c + 16 * math.cos(math.radians(90 + 60 * i)), c + bob + 16 * math.sin(math.radians(90 + 60 * i))) for i in range(6)]
        m = px.poly_mask(size, size + 2, pts)
    px.fill(a, m, ramp[1])
    yy, xx = np.mgrid[0:size + 2, 0:size]
    ys, xs = np.nonzero(m)
    cyy = ys.mean()
    px.fill(a, m & (yy < cyy - 2) & (xx < xs.mean() + 2), ramp[2])
    px.fill(a, m & ~px.shift(m, 0, 1), ramp[3])
    px.fill(a, m & ~px.shift(m, 1, 0), ramp[3])
    px.fill(a, m & (~px.shift(m, -1, 0) | ~px.shift(m, 0, -1)), ramp[0])
    # rosto sereno: olhos fechados (arcos de 3 px) e sorriso pequeno
    fy = int(cyy)
    for ex in (int(xs.mean()) - 5, int(xs.mean()) + 3):
        a[fy - 1, ex:ex + 3] = px.rgba(FACE)
        a[fy - 2, ex + 1] = px.rgba(FACE)
    a[fy + 3, int(xs.mean()) - 1:int(xs.mean()) + 2] = px.rgba(FACE)
    a[fy + 2, int(xs.mean()) - 2] = px.rgba(FACE)
    a[fy + 2, int(xs.mean()) + 2] = px.rgba(FACE)
    px.fill(a, px.outer_edge(m), ramp[0])
    return a


# ---------------------------------------------------------- cristal-ovo

def egg_frames():
    """Cristal caído no chão: pulsa (3), racha (3) e se parte em cacos (3)."""
    w, h = 44, 40
    frames = []
    base_pts = [(6, 34), (14, 10), (24, 4), (33, 12), (38, 34)]
    body = px.poly_mask(w, h, base_pts)
    ridge = ((24, 4), (22, 34))
    cracks = [[(22, 20), (19, 16), (20, 12)], [(22, 20), (26, 25), (29, 23)], [(22, 20), (17, 26), (13, 25)],
              [(22, 20), (25, 14), (28, 11)]]
    for f in range(9):
        a = px.canvas(w, h)
        # chão (pedras) sob o cristal
        px.fill(a, px.rect_mask(w, h, 2, 34, 42, 37), ROCK[2])
        px.fill(a, px.rect_mask(w, h, 2, 34, 42, 35), ROCK[3])
        if f < 7:
            glow = [0, 1, 2, 1, 2, 2, 2][f]
            yy, xx = np.mgrid[0:h, 0:w]
            side = (xx + 0.5 - 24) * 30 - (yy + 0.5 - 4) * (-2)
            left = body & (side < 0)
            px.fill(a, body, EGG[1])
            px.fill(a, left, EGG[2 + (1 if glow == 2 else 0)])
            px.fill(a, body & (yy > 26) & ~left, EGG[0])
            px.line(a, 24, 5, 22, 33, EGG[3 + (1 if glow else 0)], mask=body)
            # luz roxa pulsando no miolo
            core = px.poly_mask(w, h, [(22, 14), (26, 18), (22, 26), (18, 20)])
            px.fill(a, core & body, [EGG[3], EGG[4], EGG[5]][glow])
            if f >= 3:
                for path in cracks[:f - 2]:
                    for (x0, y0), (x1, y1) in zip(path, path[1:]):
                        px.line(a, x0, y0, x1, y1, EGG[5])
            px.fill(a, px.outer_edge(body), EGG[0])
        else:
            # cacos voando e luz no chão
            spread = 4 if f == 7 else 9
            for (dx, dy, pts) in [(-1, -1, [(10, 20), (16, 12), (20, 22)]), (1, -1, [(28, 12), (34, 20), (26, 22)]),
                                  (-1, 0, [(8, 32), (14, 24), (18, 32)]), (1, 0, [(30, 32), (32, 24), (38, 32)])]:
                shard = px.poly_mask(w, h, [(x + dx * spread, y + dy * spread - (f - 6)) for x, y in pts])
                px.fill(a, shard, EGG[3])
                px.fill(a, shard & ~px.shift(shard, 0, 1), EGG[4])
                px.fill(a, px.outer_edge(shard), EGG[0])
            px.fill(a, px.rect_mask(w, h, 12, 33, 34, 34), EGG[4])
        frames.append(a)
    return frames


# ---------------------------------------------------------- ORB e Pi grandes

def orb_big(facing=-1):
    """ORB em pose heroica (mesma pixel art do logo, sem extrusão), olhando para
    a esquerda (o Rift). Mãos e pés como no sprite do jogo."""
    W, H = 64, 64
    a = px.canvas(W, H)
    cx, cy, r = 32, 28, 20
    # pés (sapatos roxos) e mãos (luvas lilás-claro) atrás/abaixo do corpo
    for fx in (22, 38):
        foot = px.poly_mask(W, H, [(fx - 5, 60), (fx - 4, 53), (fx + 3, 53), (fx + 5, 60)])
        px.fill(a, foot, ui.ORB[3])
        px.fill(a, foot & ~px.shift(foot, 0, 1), ui.ORB[5])
        px.fill(a, foot & ~px.shift(foot, 0, -1), ui.ORB[1])
        leg = px.rect_mask(W, H, fx - 2, 46, fx + 2, 54)
        px.fill(a, leg, ui.ORB_EYE[0])
    tmp = px.canvas(W, H)
    ui.orb_letter(tmp, cx, cy, r, look=-2)
    # tira a extrusão do logo (tons LET) e o contorno de fora dela
    ext = np.zeros((H, W), dtype=bool)
    for c in (ui.LET[0], ui.LET[1]):
        ext |= (tmp[:, :, :3] == c).all(2)
    tmp[ext] = 0
    body = np.zeros((H, W), dtype=bool)
    yy, xx = np.mgrid[0:H, 0:W]
    body = ((xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2) <= r * r
    edge = px.outer_edge(body) & px.opaque(tmp)
    tmp[px.opaque(tmp) & ~body & ~edge] = 0
    px.composite(a, tmp, 0, 0)
    # mãos: luvas lilás-claro de quinas chanfradas (como no sprite), contorno
    # escuro; a da frente erguida para o Rift com uma faísca de energia, a de
    # trás em guarda perto do corpo
    GLOVE = [(203, 191, 222), (236, 214, 251), (251, 249, 252)]
    for (hx, hy, raised) in [(cx - r, cy - 4, True), (cx + r - 2, cy + 11, False)]:
        hand = px.poly_mask(W, H, [(hx - 2, hy - 3), (hx + 2, hy - 3), (hx + 3, hy - 2), (hx + 3, hy + 2),
                                   (hx + 2, hy + 3), (hx - 2, hy + 3), (hx - 3, hy + 2), (hx - 3, hy - 2)])
        px.fill(a, hand, GLOVE[1])
        px.fill(a, hand & (~px.shift(hand, -1, 0) | ~px.shift(hand, 0, -1)), GLOVE[0])
        px.fill(a, hand & ~px.shift(hand, 0, 1) & ~px.shift(hand, -1, 0), GLOVE[2])
        px.fill(a, px.outer_edge(hand) & ~body, ui.ORB[0])
        if raised:
            # faísca de 4 pontas (sem anel), acima e à frente da mão
            sx, sy = hx - 4, hy - 7
            for (dx, dy, col) in [(0, 0, (255, 250, 255)), (1, 0, (214, 190, 255)), (-1, 0, (214, 190, 255)),
                                  (0, 1, (214, 190, 255)), (0, -1, (214, 190, 255)), (0, -2, (150, 110, 240)),
                                  (0, 2, (150, 110, 240)), (2, 0, (150, 110, 240)), (-2, 0, (150, 110, 240))]:
                px.put(a, sx + dx, sy + dy, col)
    if facing > 0:
        a = a[:, ::-1].copy()
    return a


def pi_trace(width):
    """Silhueta do π decalcada de docs/ref/pi_referencia.png na largura pedida
    (limiar -> caixa -> média por área -> limiar 50%), proporção da referência."""
    ref = np.array(Image.open(ROOT / "docs/ref/pi_referencia.png").convert("L")).astype(float)
    ink = ref < 128
    ys, xs = np.nonzero(ink)
    crop = ink[ys.min():ys.max() + 1, xs.min():xs.max() + 1].astype(float)
    h = int(round(width * crop.shape[0] / crop.shape[1]))
    im = Image.fromarray((crop * 255).astype(np.uint8)).resize((width, h), Image.BOX)
    return np.array(im) >= 128


def pi_big(width=58):
    m = pi_trace(width)
    h, w = m.shape
    W, H = w + 8, h + 8
    a = px.canvas(W, H)
    mask = np.zeros((H, W), dtype=bool)
    mask[4:4 + h, 4:4 + w] = m
    yy, xx = np.mgrid[0:H, 0:W]
    px.fill(a, mask, PI["I"])
    # sombra azul-petróleo embaixo das pernas e à direita; brilho no alto à esquerda
    shade = mask & (~px.shift(mask, -1, 0) | ~px.shift(mask, -2, 0))
    px.fill(a, shade, PI["S"])
    px.fill(a, mask & (yy > 4 + h - 7), PI["T"])
    px.fill(a, mask & (yy > 4 + h - 7) & ~px.shift(mask, -1, 0), PI["U"])
    top = mask & ~px.shift(mask, 0, 1)
    px.fill(a, top & (xx < 4 + w * 0.5), PI["W"])
    px.fill(a, px.outer_edge(mask), PI["K"])
    # rosto na barra: olhos (3x4, contorno por dentro da barra) olhando para a
    # esquerda (o Rift) e sorriso logo abaixo; tudo dentro da altura da barra
    bar_rows = [y for y in range(H) if mask[y].sum() > w * 0.6]
    b0, b1 = bar_rows[0], bar_rows[-1]
    cxm = 4 + int(w * 0.56)
    ey = b0 + 1
    for ex in (cxm - 6, cxm + 2):
        eye = px.rect_mask(W, H, ex, ey, ex + 3, ey + 4)
        px.fill(a, eye, PI["W"])
        px.fill(a, px.rect_mask(W, H, ex, ey + 1, ex + 2, ey + 4), PI["K"])    # pupila à esquerda
        px.put(a, ex, ey + 1, PI["W"])                                           # brilho no olho
    my = min(b1 + 2, ey + 6)
    for x, y in [(cxm - 3, my), (cxm - 2, my + 1), (cxm - 1, my + 1), (cxm, my + 1), (cxm + 1, my)]:
        px.put(a, x, y, PI["K"])
    return a


# ------------------------------------------------------------------ key art

def layer(name):
    return np.array(Image.open(BG / f"{name}.png").convert("RGBA"))


def darken(a, k, tint=(1.0, 1.0, 1.0)):
    out = a.copy()
    out[:, :, :3] = np.clip(out[:, :, :3].astype(float) * k * np.array(tint), 0, 255).astype(np.uint8)
    return out


def rock_mass(W, H, top_fn, x0, x1):
    m = np.zeros((H, W), dtype=bool)
    for x in range(max(0, x0), min(W, x1)):
        m[max(0, int(top_fn(x))):, x] = True
    return m


def shade_rock(art, m, ramp, lit_from_rift=None):
    """Rocha: corpo escuro, aresta de cima acesa, faces com blocos grandes; se
    lit_from_rift=(cx, cy), a aresta voltada para o Rift ganha o tom mais claro."""
    H, W = m.shape
    px.fill(art, m, ramp[1])
    top = m & ~px.shift(m, 0, 1)
    top2 = m & ~px.shift(m, 0, 2) & ~top
    px.fill(art, top2, ramp[3])
    px.fill(art, top, ramp[4])
    # fendas verticais curtas (blocos), sem ruído
    ys, xs = np.nonzero(top)
    for x, y in zip(xs[::13], ys[::13]):
        for k in range(3, 9):
            if y + k < H and m[y + k, x]:
                art[y + k, x] = px.rgba(ramp[0])
    if lit_from_rift is not None:
        cx, cy = lit_from_rift
        yy, xx = np.mgrid[0:H, 0:W]
        facing = top & (np.abs(xx - cx) < 120)
        px.fill(art, px.dither(facing, 0.6), ramp[5])


def rays(art, cx, cy, targets, col):
    """Feixes de luz do Rift: linhas pontilhadas (50%) em direção aos alvos."""
    H, W = art.shape[:2]
    for (tx, ty, width) in targets:
        m = px.poly_mask(W, H, [(cx - 3, cy), (cx + 3, cy), (tx + width, ty), (tx - width, ty)])
        dm = px.dither(m, 0.18) & (art[:, :, 3] > 0)
        lum = art[:, :, :3].astype(int).sum(2)
        dm &= lum < 360
        px.fill(art, dm, col)


def key_art(with_logo):
    """Capa: o Rift rasga o céu no alto (luz vem de cima, levemente da esquerda
    para quem está embaixo à direita); o chefe é uma silhueta na luz; os 4
    corrompidos em rochas no plano do meio; ORB (pose heroica) e Pi no primeiro
    plano. O terço esquerdo fica mais calmo para o logo e os botões do menu."""
    W, H = 480, 270
    art = px.canvas(W, H)
    for n, k in [("ceu", 0.9), ("nuvem1", 0.85), ("nuvem2", 0.82), ("nuvem3", 0.78), ("nuvem4", 0.72), ("montanhas", 0.55)]:
        px.composite(art, darken(layer(n), k), 0, 0)
    estr = np.array(Image.open(SPR / "background/06_estruturas.png").convert("RGBA"))[:, :480]
    px.composite(art, darken(estr, 0.5), -10, 0)
    rcx, rcy = 262, 84
    yy, xx = np.mgrid[0:H, 0:W]
    # luz do Rift nas nuvens (pontilhado lilás nas faixas médias, perto dele)
    near = ((xx - rcx) ** 2 / 120 ** 2 + (yy - rcy) ** 2 / 120 ** 2) < 1
    lum = art[:, :, :3].astype(int).sum(2)
    px.fill(art, px.dither(near & (lum > 250) & (lum < 520), 0.5), (150, 88, 240))
    # rachaduras pelo céu, saindo do Rift
    cr = crack_frames(220, 150, 5, seed=4)[-1]
    px.composite(art, cr, rcx - 110, rcy - 80)
    # Rift largo; o chefe como silhueta contra a luz de dentro; bordas por cima
    rift = paint_rift(92, 160, 1.0, seed=5)
    rx, ry = rcx - 46, rcy - 78
    px.composite(art, rift, rx, ry)
    sil = boss_silhouette(1.0, 64)
    px.composite(art, sil, rcx - 32, rcy - 26)
    m = px.opaque(rift)
    edge = m & (~px.shift(m, 2, 0) | ~px.shift(m, -2, 0))
    edge_only = rift.copy()
    edge_only[~edge] = 0
    px.composite(art, edge_only, rx, ry)
    # plano do meio: rochas flutuantes/cristas com os corrompidos escalonados
    mids = [("triangle", 132, 196), ("square", 190, 168), ("diamond", 318, 164), ("hexagon", 376, 192)]
    for kind, ex, top in mids:
        spr = np.array(Image.open(SPR / f"enemies/{kind}/idle_01.png").convert("RGBA"))
        mm = spr[:, :, 3] > 0
        ys, xs = np.nonzero(mm)
        spr = spr[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
        sh, sw = spr.shape[:2]
        # rocha sob o inimigo (plataforma de pedra flutuante)
        rw = sw + 16
        rock = px.poly_mask(W, H, [(ex - 8, top), (ex + rw - 8, top), (ex + rw - 12, top + 7), (ex + rw // 2, top + 16),
                                   (ex, top + 8)])
        shade_rock(art, rock, ROCK, (rcx, rcy))
        px.fill(art, px.outer_edge(rock), ROCK[0])
        dark = darken(spr, 0.42, (1.0, 0.92, 1.08))
        smk = dark[:, :, 3] > 0
        # borda acesa pelo Rift (lado de cima e o lado voltado para o Rift)
        toward = 1 if ex + sw / 2 < rcx else -1
        rim = smk & (~px.shift(smk, 0, 1) | ~px.shift(smk, -toward, 0))
        dark[rim] = px.rgba((188, 140, 255))
        bright = (spr[:, :, :3].astype(int).max(2) > 200) & smk
        dark[bright] = spr[bright]
        px.composite(art, dark, ex, top - sh + 2)
    # feixes de luz do Rift até o ORB
    rays(art, rcx, rcy + 70, [(252, 228, 18)], (120, 70, 210))
    # primeiro plano: laje onde o ORB e o Pi estão (do meio para a direita)
    ledge = rock_mass(W, H, lambda x: 236 + 4 * math.sin(x / 17.0) + (0 if x > 200 else (200 - x) * 0.25), 150, W)
    shade_rock(art, ledge, ROCK, (rcx, rcy))
    # pedras escuras à esquerda (moldura) e cristais no primeiro plano
    left = rock_mass(W, H, lambda x: 250 + 6 * math.sin(x / 9.0) + (x - 120) * 0.1 if x < 150 else 300, 0, 150)
    shade_rock(art, left, ROCK)
    for (cx, base, hgt, wid) in [(470, 270, 84, 16), (448, 270, 50, 10), (160, 270, 30, 8), (12, 270, 60, 12)]:
        mm = px.poly_mask(W, H, [(cx - wid / 2, base), (cx - wid / 2 + 2, base - hgt * 0.7), (cx, base - hgt),
                                 (cx + wid / 2, base - hgt * 0.6), (cx + wid / 2, base)])
        px.fill(art, mm, ROCK[1])
        px.fill(art, mm & (xx < cx), ROCK[2])
        px.line(art, cx, base - hgt + 1, cx - 1, base - 2, (150, 110, 230), mask=mm)
        px.fill(art, px.outer_edge(mm), ROCK[0])
    orb = orb_big(facing=-1)
    ox, oy = 230, 236 - 60
    px.composite(art, orb, ox, oy)
    pi = pi_big(54)
    px.composite(art, pi, 300, 236 - pi.shape[0] + 6)
    if with_logo:
        logo = ui.build_logo()
        px.composite(art, logo, 14, 10)
    return art


def logo_parts():
    """Peças do logo com a posição de cada uma no logo inteiro."""
    logo = ui.build_logo()
    m = px.opaque(logo)
    parts = {"project": (0, 0, 72, 14), "o": (0, 14, 60, 78), "r": (60, 14, 101, 78), "b": (101, 14, 150, 78)}
    out = {}
    for name, (x0, y0, x1, y1) in parts.items():
        p = px.canvas(logo.shape[1], logo.shape[0])
        p[y0:y1, x0:x1] = logo[y0:y1, x0:x1]
        ys, xs = np.nonzero(px.opaque(p))
        out[name] = (p[ys.min():ys.max() + 1, xs.min():xs.max() + 1], int(xs.min()), int(ys.min()))
    return out, logo.shape


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    PROOF.mkdir(parents=True, exist_ok=True)
    opens = [0.08, 0.18, 0.32, 0.48, 0.64, 0.80, 0.92, 1.0]
    rifts = [paint_rift(40, 150, t) for t in opens]
    for i, f in enumerate(rifts, 1):
        px.save(f, OUT / f"rift_{i:02d}.png")
    cracks = crack_frames()
    for i, f in enumerate(cracks, 1):
        px.save(f, OUT / f"crack_{i:02d}.png")
    sils = [boss_silhouette(e) for e in (0.0, 0.5, 1.0, 1.0)]
    for i, f in enumerate(sils, 1):
        px.save(f, OUT / f"boss_sil_{i:02d}.png")
    calm = {}
    for kind in CALM:
        calm[kind] = [calm_shape(kind, 0), calm_shape(kind, 1)]
        for i, f in enumerate(calm[kind], 1):
            px.save(f, OUT / f"calm_{kind}_{i:02d}.png")
    eggs = egg_frames()
    for i, f in enumerate(eggs, 1):
        px.save(f, OUT / f"egg_{i:02d}.png")
    parts, lshape = logo_parts()
    offsets = []
    for name, (img, x, y) in parts.items():
        px.save(img, OUT / f"logo_part_{name}.png")
        offsets.append(f"{name} {x} {y}")
    (OUT / "logo_parts.txt").write_text("\n".join(offsets) + "\n", encoding="utf-8")
    orb = orb_big(-1)
    px.save(orb, OUT / "orb_big.png")
    pi = pi_big(54)
    px.save(pi, OUT / "pi_big.png")

    ka = key_art(False)
    px.save(ka, SPR / "ui/key_art.png")
    kl = key_art(True)
    px.save(kl, ROOT / "docs/key_art.png")
    px.save(px.upscale(kl, 4), ROOT / "docs/key_art_4x.png")

    # provas
    for bgname, bg in [("fundo_jogo", GAME_BG), ("fundo_claro", LIGHT_BG)]:
        for k in (1, 4):
            px.save(px.contact_sheet(rifts, bg, scale=k), PROOF / f"rift_{k}x_{bgname}.png")
            px.save(px.contact_sheet(cracks, bg, scale=k), PROOF / f"rachadura_{k}x_{bgname}.png")
            px.save(px.contact_sheet(sils, bg, scale=k), PROOF / f"chefe_silhueta_{k}x_{bgname}.png")
            px.save(px.contact_sheet([f for v in calm.values() for f in v], bg, scale=k), PROOF / f"formas_calmas_{k}x_{bgname}.png")
            px.save(px.contact_sheet(eggs, bg, scale=k), PROOF / f"cristal_ovo_{k}x_{bgname}.png")
            px.save(px.contact_sheet([orb, pi], bg, scale=k), PROOF / f"orb_pi_grandes_{k}x_{bgname}.png")
    px.save_gif(rifts, [120] * 7 + [300], PROOF / "rift.gif", GAME_BG, scale=4)
    px.save_gif(eggs, [160, 160, 160, 120, 120, 120, 90, 90, 200], PROOF / "cristal_ovo.gif", GAME_BG, scale=4)
    px.save_gif(sils, [200, 120, 120, 300], PROOF / "chefe_olhos.gif", GAME_BG, scale=4)
    print("abertura: arte gerada")


if __name__ == "__main__":
    main()
