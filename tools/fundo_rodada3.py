"""Rodada 3, etapa 1: a parte de baixo da paisagem.

Rode DEPOIS de tools/build_orb_assets.py (que gera as camadas 00-08 a partir
da arte do TCC). Este script:
- guarda as versões anteriores em art-source/descartado_rodada3/fundo/;
- 06_estruturas: a estrutura circular (portal de pedra com anéis de luz) vira
  uma ruína de arco de cristal quebrado na MESMA caixa (x 309-479, y 75-216) e
  com as mesmas cores; a janela em "O" da torre vira uma fresta de cristal;
  torre e ruína ganham base (degraus, pé e escombros) em vez do corte reto;
- 07_lago: só a água (faixas + ondulações quebradas), começando sob a margem;
- 07_margem: margem irregular de pedra (parallax do lago), com linha molhada;
- 05_reflexo / 06_reflexo: reflexo espelhado de montanhas e estruturas em 2
  tons com pontilhado e linhas horizontais quebradas, no MESMO período da
  camada refletida (o jogo desenha cada um no parallax da sua camada, com a
  ondulação por linha feita em código);
- 08_chao: borda de cima do chão irregular (pedras molhadas), sem linha seca;
- 09_nevoa: névoa baixa em faixas pontilhadas (sem degradê) sobre água e chão;
- menu.png: a mesma paisagem composta (tela de início e telas de menu).
As camadas de cima (céu, nuvens, montanhas) não são tocadas.
"""
from __future__ import annotations

import json
import math
import shutil
from pathlib import Path

import numpy as np
from PIL import Image

import pxlib as px

ROOT = Path(__file__).resolve().parent.parent
BG = ROOT / "assets/sprites/background"
SRC = ROOT / "art-source/ProjetoFinal_TCC/fundo"
ARCHIVE = ROOT / "art-source/descartado_rodada3/fundo"

# ---------------------------------------------------------------- paleta
# Pedra das estruturas (tirada da própria estrutura circular, quantizada):
# sombra puxa para o azul-marinho, luz puxa para o lilás.
K = (1, 3, 24)            # contorno / fenda
S1 = (2, 4, 48)
S2 = (5, 8, 72)
S3 = (8, 13, 104)
S4 = (20, 24, 150)
S5 = (60, 44, 205)
S6 = (172, 134, 249)
STONE = [K, S1, S2, S3, S4, S5, S6]
# Cristal (o brilho violeta das estruturas)
C0 = (7, 6, 156)
C1 = (23, 5, 216)
C2 = (66, 5, 244)
C3 = (109, 37, 236)
C4 = (172, 134, 249)
CW = (255, 255, 254)
# Água: tons do lago original do TCC
W0 = (1, 7, 86)
W1 = (2, 8, 107)
W2 = (5, 10, 132)
W3 = (7, 15, 155)
W4 = (36, 14, 208)
W5 = (64, 42, 251)
# Reflexos (2 tons por camada)
RM = [(2, 9, 96), (10, 18, 146)]          # montanhas
RS = [(1, 5, 70), (64, 42, 251)]          # estruturas (pedra escura / luz)
# Névoa (perto do tom da água, um pouco mais clara e lilás)
MA = (24, 22, 158)
MB = (46, 34, 196)

AXIS = 226            # linha da água: o reflexo de y fica em 2*AXIS + 1 - y
WATER_TOP = 225       # primeira linha da água (sempre coberta pela margem)
PERIOD_LAKE = 380
PERIOD_MIST = 443


def tile_noise(period, x, seeds, amps):
    """Ruído suave e TILEÁVEL: soma de senos com períodos que dividem 'period'."""
    v = np.zeros_like(x, dtype=float)
    for k, a, ph in zip(seeds, amps, [0.3, 1.7, 2.9, 4.1, 5.3, 0.9]):
        v += a * np.sin(2 * math.pi * k * x / period + ph)
    return v


def steps_profile(values, lo, hi):
    """Perfil inteiro com degraus consistentes (sem pixel solto)."""
    out = np.clip(np.round(values), lo, hi).astype(int)
    # tira degraus de 1 coluna isolada (escadinha irregular)
    for _ in range(2):
        for i in range(len(out)):
            a, b, c = out[i - 1], out[i], out[(i + 1) % len(out)]
            if a == c and b != a:
                out[i] = a
    return out


# ------------------------------------------------------- 06 estruturas

def clear_ring(a):
    """Tira a estrutura circular (tudo à direita da torre, x >= 300)."""
    a[:, 300:480] = 0
    return a


def block_wall(a, mask, bw, bh, ramp, rng, ox=0, oy=0, lit_col=None, crack=None):
    """Alvenaria dentro da máscara: blocos com junta, face de cima/esquerda
    clara, baixo/direita escura (luz do alto à esquerda), leve variação de tom
    por bloco (nunca ruído por pixel)."""
    h, w = mask.shape
    ys, xs = np.nonzero(mask)
    if not len(xs):
        return
    y0, y1 = ys.min(), ys.max() + 1
    row = 0
    for by in range(y0 - oy % bh, y1, bh):
        bx = xs.min() - int(rng.integers(0, bw)) - (ox if row % 2 else 0)
        row += 1
        while bx <= xs.max():
            bwi = int(rng.integers(max(4, bw - 4), bw + 6))
            blk = px.rect_mask(w, h, bx, by, bx + bwi, by + bh) & mask
            bx += bwi
            if not blk.any():
                continue
            # corpo escuro (S2) e alguns blocos um tom acima; o lado da luz
            # (esquerda) fica um tom mais claro
            tone = 2 + (1 if rng.random() < 0.3 else 0)
            if lit_col is not None and bx - bwi < lit_col:
                tone += 1
            tone = int(min(tone, 4))
            px.fill(a, blk, ramp[tone])
            # junta escura: linha de baixo e coluna da direita
            joint = (blk & ~px.shift(blk, 0, -1)) | (blk & ~px.shift(blk, -1, 0))
            px.fill(a, joint, ramp[0] if tone <= 2 else ramp[1])
            # aresta de cima acesa (luz de cima); esquerda meio tom
            top = blk & ~px.shift(blk, 0, 1) & ~joint
            px.fill(a, top, ramp[min(5, tone + 1)])


def crystal(a, pts, ridge=None, lit_first=True):
    """Cristal facetado: faces planas (esquerda clara, direita escura), crista
    brilhante e contorno escuro; ponta com brilho."""
    h, w = a.shape[:2]
    m = px.poly_mask(w, h, pts)
    if not m.any():
        return m
    # divide pela crista (linha do topo até a base central)
    if ridge is None:
        top = min(pts, key=lambda p: p[1])
        bot = ((pts[0][0] + pts[-1][0]) / 2, max(p[1] for p in pts))
        ridge = (top, bot)
    (tx, ty), (bx, by) = ridge
    yy, xx = np.mgrid[0:h, 0:w]
    side = (xx + 0.5 - tx) * (by - ty) - (yy + 0.5 - ty) * (bx - tx)
    left = m & (side < 0)
    right = m & ~left
    px.fill(a, left, C3)
    px.fill(a, right, C1)
    # faixa mais escura na base da face direita, mais clara no alto da esquerda
    ys = np.nonzero(m)[0]
    hgt = ys.max() - ys.min() + 1
    px.fill(a, right & (yy > ys.min() + hgt * 0.62), C0)
    px.fill(a, left & (yy < ys.min() + hgt * 0.35), C4)
    px.line(a, tx, ty + 1, bx, by - 1, C4, mask=m)
    px.selective_outline(a, m, K, lit=None)
    # brilho na ponta (2 px, sem pixel solto)
    px.put(a, int(tx), int(ty) + 1, CW)
    px.put(a, int(tx), int(ty) + 2, C4)
    return m


def voussoirs(a, path, thick, rng, broken_end=True):
    """Aduelas (blocos do arco) ao longo de uma polilinha: quadriláteros retos."""
    h, w = a.shape[:2]
    whole = np.zeros((h, w), dtype=bool)
    for i in range(len(path) - 1):
        (x0, y0), (x1, y1) = path[i], path[i + 1]
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy)
        nx, ny = -dy / L, dx / L          # normal (para "fora" = cima/esquerda)
        if ny > 0:
            nx, ny = -nx, -ny
        quad = [(x0, y0), (x1, y1), (x1 - nx * thick, y1 - ny * thick), (x0 - nx * thick, y0 - ny * thick)]
        m = px.poly_mask(w, h, quad)
        tone = 2 + int(rng.integers(0, 2))
        px.fill(a, m, STONE[tone])
        # face externa (de cima) iluminada, interna (de baixo) na sombra
        top = m & ~px.shift(m, 0, 1)
        bot = m & ~px.shift(m, 0, -1)
        px.fill(a, top, S4)
        px.fill(a, bot, S2)
        whole |= m
    # juntas entre aduelas
    for i in range(1, len(path) - 1):
        x0, y0 = path[i]
        x1, y1 = path[i - 1]
        dx, dy = path[i + 1][0] - x1, path[i + 1][1] - y1
        L = math.hypot(dx, dy)
        nx, ny = -dy / L, dx / L
        if ny > 0:
            nx, ny = -nx, -ny
        px.line(a, x0, y0, x0 - nx * (thick - 1), y0 - ny * (thick - 1), S1, mask=whole)
    return whole


def rubble(a, x0, x1, top, bottom, rng, ramp=STONE, density=1.0):
    """Escombros: pedras (quadriláteros irregulares) empilhadas entre top e bottom,
    base irregular, topo iluminado pela esquerda."""
    h, w = a.shape[:2]
    chunks = []
    x = x0
    while x < x1:
        sw = int(rng.integers(8, 17))
        sh = int(rng.integers(4, 8))
        by = int(rng.integers(bottom - 3, bottom + 1))
        ty = max(top, by - sh)
        cut = int(rng.integers(1, 4))
        # pedra facetada: base larga, topo chanfrado de um lado
        pts = [(x, by), (x + sw, by), (x + sw, ty + cut), (x + sw - cut - 1, ty), (x + cut, ty), (x, ty + cut)]
        chunks.append((ty, pts))
        x += max(4, int(sw * (0.5 + 0.35 * rng.random()) / density))
    whole = np.zeros((h, w), dtype=bool)
    # de trás (mais alta) para a frente (mais baixa): a de baixo cobre a de cima
    for ty, pts in sorted(chunks, key=lambda c: c[0]):
        m = px.poly_mask(w, h, pts)
        if m.sum() < 8:
            continue
        tone = 2 if rng.random() < 0.6 else 3
        px.fill(a, m, ramp[tone])
        topf = m & ~px.shift(m, 0, 2)
        px.fill(a, topf, ramp[tone + 1])
        px.fill(a, m & ~px.shift(m, 0, 1), ramp[tone + 2])            # aresta de cima
        px.fill(a, m & ~px.shift(m, -1, 0), ramp[1])                   # lado da sombra
        px.fill(a, m & ~px.shift(m, 0, -1), ramp[1])
        px.fill(a, px.outer_edge(m) & (whole | (a[:, :, 3] == 0)), K)
        whole |= m
    return whole


def draw_arch_ruin(a, rng):
    """Ruína de arco de cristal quebrado na caixa da antiga estrutura circular."""
    h, w = a.shape[:2]
    # ---- plataforma de pedra (degrau largo + degrau de cima), como a base antiga
    step_lo = px.rect_mask(w, h, 309, 207, 480, 217)
    step_hi = px.poly_mask(w, h, [(315, 207), (473, 207), (470, 199), (318, 199)])
    block_wall(a, step_lo, 14, 5, STONE, rng, lit_col=360)
    block_wall(a, step_hi, 12, 4, STONE, rng, ox=5, lit_col=360)
    # ---- pilar esquerdo (alto, inteiro) com capitel
    pil_l = px.rect_mask(w, h, 323, 106, 349, 199)
    block_wall(a, pil_l, 13, 8, STONE, rng, ox=3, lit_col=331)
    cap_l = px.poly_mask(w, h, [(318, 106), (354, 106), (352, 98), (320, 98)])
    block_wall(a, cap_l, 12, 4, STONE, rng, lit_col=334)
    # face esquerda do pilar (luz) e direita (sombra) em colunas inteiras
    px.fill(a, px.rect_mask(w, h, 323, 106, 324, 199), S4)
    px.fill(a, px.rect_mask(w, h, 347, 106, 349, 199), S2)
    # veio de cristal descendo pelo pilar (fenda com luz)
    for y in range(128, 186):
        x = 336 + int(round(2 * math.sin(y / 9.0)))
        px.put(a, x, y, C3 if y % 7 else C4)
    # ---- arco: metade esquerda de aduelas retas, quebrada perto do topo
    path = [(351, 104), (360, 93), (370, 85), (381, 79), (392, 76), (400, 76)]
    arch = voussoirs(a, path, 11, rng)
    # borda quebrada: morde a ponta direita em degraus
    for (x, y0, y1) in [(399, 64, 76), (398, 64, 69), (400, 70, 76), (397, 64, 66)]:
        a[y0:y1, x:401] = 0
    # ---- pedras do arco que ficaram flutuando (a ruína é flutuante)
    for (cx, cy, s) in [(410, 84, 7), (421, 95, 6), (430, 108, 5)]:
        pts = [(cx - s, cy - 2), (cx + s - 1, cy - s + 1), (cx + s, cy + 2), (cx - s + 2, cy + s - 1)]
        m = px.poly_mask(w, h, pts)
        px.fill(a, m, S3)
        px.fill(a, m & ~px.shift(m, 0, 1), S5)
        px.fill(a, m & ~px.shift(m, 0, -1), S2)
        px.selective_outline(a, m, K)
    # ---- pilar direito, quebrado no alto (topo em dentes), com cristais
    top_prof = [138, 136, 136, 133, 133, 131, 131, 134, 134, 137, 137, 135, 135, 139, 139, 141, 141,
                143, 143, 140, 140, 142, 142, 145, 145, 146]
    pil_r = np.zeros((h, w), dtype=bool)
    for i, t in enumerate(top_prof):
        pil_r[t:199, 438 + i] = True
    block_wall(a, pil_r, 13, 8, STONE, rng, ox=7, lit_col=446)
    px.fill(a, pil_r & px.rect_mask(w, h, 438, 0, 439, h), S4)
    px.fill(a, pil_r & px.rect_mask(w, h, 461, 0, 464, h), S2)
    # topo quebrado: aresta clara
    px.fill(a, pil_r & ~px.shift(pil_r, 0, 1), S5)
    crystal(a, [(446, 134), (449, 118), (452, 133)])
    crystal(a, [(451, 136), (457, 124), (458, 138)], ridge=((457, 124), (455, 137)))
    # ---- aglomerado de cristal no meio, sobre a plataforma (sem círculo)
    crystal(a, [(378, 199), (377, 150), (389, 120), (401, 150), (400, 199)], ridge=((389, 120), (389, 198)))
    crystal(a, [(364, 199), (357, 168), (364, 158), (374, 196)], ridge=((364, 158), (368, 198)))
    crystal(a, [(402, 199), (412, 166), (420, 172), (410, 199)], ridge=((416, 169), (406, 199)))
    crystal(a, [(418, 199), (423, 185), (428, 199)])
    crystal(a, [(352, 199), (355, 188), (359, 199)])
    # contorno final: escuro por fora, lilás no lado da luz onde há luz direta
    solid = px.opaque(a) & px.rect_mask(w, h, 300, 0, 480, h)
    edge = px.outer_edge(solid) & px.rect_mask(w, h, 300, 0, 480, h)
    px.fill(a, edge, K)
    # escombros e pé da ruína (mais baixo que o antigo corte reto)
    rubble(a, 304, 482, 214, 228, rng)
    return a


def fix_tower_window(a):
    """Troca a janela em "O" da torre por uma fresta de cristal (losango alto)."""
    h, w = a.shape[:2]
    # fundo da janela: a cor do vão logo acima/abaixo do anel
    niche = [tuple(int(v) for v in a[y, x, :3]) for (x, y) in [(126, 104), (126, 127)]]
    base = niche[0]
    for y in range(106, 127):
        for x in range(119, 135):
            c = a[y, x, :3].astype(int)
            if c.sum() > 180:          # só os pixels do anel de luz e do halo
                a[y, x] = (*base, 255)
    shard = px.poly_mask(w, h, [(126.5, 107), (130, 116), (126.5, 125), (123, 116)])
    px.fill(a, shard, C2)
    px.fill(a, shard & px.rect_mask(w, h, 0, 0, 127, h), C3)
    px.line(a, 126, 109, 126, 122, C4)
    px.put(a, 126, 111, CW)
    px.put(a, 126, 112, CW)
    return a


def tower_base(a, rng):
    """Pé da torre: dois degraus mais largos e escombros até a margem."""
    h, w = a.shape[:2]
    s1 = px.rect_mask(w, h, 44, 221, 200, 225)
    s2 = px.rect_mask(w, h, 40, 225, 204, 228)
    block_wall(a, s1, 12, 4, STONE, rng, lit_col=100)
    block_wall(a, s2, 14, 3, STONE, rng, ox=6, lit_col=100)
    # a linha de luz da porta desce até o degrau
    for y in range(221, 226):
        px.put(a, 120, y, C4 if y < 223 else C3)
    rubble(a, 26, 48, 219, 229, rng)
    rubble(a, 196, 232, 221, 229, rng, density=0.8)
    edge = px.outer_edge(px.opaque(a)) & px.rect_mask(w, h, 20, 214, 240, 232)
    px.fill(a, edge, K)
    return a


# ------------------------------------------------------------ água

def lake_water():
    P = PERIOD_LAKE
    a = px.canvas(P, 270)
    x = np.arange(P)
    bands = [(WATER_TOP, W3), (236, W2), (247, W1), (258, W0)]
    for i, (y0, col) in enumerate(bands):
        y1 = bands[i + 1][0] if i + 1 < len(bands) else 270
        a[y0:y1] = px.rgba(col)
    # transições pontilhadas de 2 linhas entre faixas (sem degradê)
    for (y0, upper), (_, lower) in zip(bands[1:], bands[:-1]):
        for dy, lvl in [(-2, 0.25), (-1, 0.5)]:
            m = np.zeros((270, P), dtype=bool)
            m[y0 + dy] = True
            px.fill(a, px.dither(m, lvl), upper)
    # ondulações: traços horizontais quebrados, mais curtos e raros ao longe
    rng = np.random.default_rng(71)
    for y in range(WATER_TOP + 4, 270, 3):
        depth = (y - WATER_TOP) / (270 - WATER_TOP)
        n = int(2 + 3 * depth)
        for _ in range(n):
            x0 = int(rng.integers(0, P))
            ln = int(rng.integers(4, 10 + int(26 * depth)))
            base = tuple(int(v) for v in a[y, x0, :3])
            tone = {W3: W4, W2: W3, W1: W2, W0: W1}.get(base, W2)
            for k in range(ln):
                if (k + y) % 11 == 10:
                    continue           # quebra dentro do traço
                a[y, (x0 + k) % P] = px.rgba(tone)
    return a


def lake_bank(rng):
    """Margem irregular: faixa de pedras escuras que esconde a borda de cima da
    água e o pé das montanhas; linha molhada clara e quebrada embaixo."""
    P = PERIOD_LAKE
    a = px.canvas(P, 270)
    x = np.arange(P)
    bot = steps_profile(228 + tile_noise(P, x, [4, 9, 17], [1.3, 1.0, 0.5]), 226, 230)
    # pedras da margem: cada pedra tem altura própria (topo 2 a 5 px acima da
    # linha d'água), topo chanfrado, face de cima acesa e lado direito na sombra
    top = bot.copy()
    mask = np.zeros((270, P), dtype=bool)
    xx = 0
    while xx < P:
        sw = int(rng.integers(5, 16))
        hgt = int(rng.integers(2, 6))
        cut = min(2, sw // 3)
        tone = [S2, S3, S2, S1][int(rng.integers(0, 4))]
        for k, i in enumerate(range(xx, min(P, xx + sw))):
            dh = hgt - (1 if k < cut or k >= sw - cut else 0)
            # nunca abaixo da 1ª linha da água: a borda reta dela fica sempre coberta
            t = min(bot[i] - dh, WATER_TOP)
            top[i] = t
            mask[t:bot[i] + 1, i] = True
            a[t + 1:bot[i], i] = px.rgba(tone)
            a[t, i] = px.rgba(S4 if k < sw * 0.6 else S3)
            if k >= sw - 1:
                a[t:bot[i], i] = px.rgba(S1)             # lado da sombra
        xx += sw
    # sombra e linha molhada na água, logo abaixo da margem
    for i in range(P):
        a[bot[i], i] = px.rgba(K)
        if (i // 7 + i // 23) % 3 != 0:
            a[bot[i] + 1, i] = px.rgba(W4)
    # contorno de cima: escuro contra o fundo
    for i in range(P):
        a[top[i] - 1, i] = px.rgba(K)
    # lascas de cristal na margem (poucas)
    for cx in [37, 151, 262, 331]:
        t = top[cx] - 1
        for dy, wid in enumerate([0, 1, 1, 2]):
            y = t - 3 + dy
            a[y, cx:cx + wid + 1] = px.rgba(C3)
        a[t - 3, cx] = px.rgba(C4)
        a[t - 4, cx] = px.rgba(K)
        a[t - 3:t + 1, cx - 1] = px.rgba(K)
        a[t - 2:t + 1, cx + 3] = px.rgba(K)
    return a, top, bot


def reflection(src, tones, glow_tone=None, fade_depth=26, seed=1, light_bias=0.0):
    """Reflexo espelhado em 2 tons com pontilhado e linhas quebradas.
    Linha r da água mostra a linha 2*AXIS+1-r da camada."""
    h, P = src.shape[:2]
    out = px.canvas(P, h)
    lum = src[:, :, :3].astype(float) @ np.array([0.30, 0.45, 0.25])
    alpha = src[:, :, 3] > 0
    lo, hi = np.percentile(lum[alpha], [10, 97]) if alpha.any() else (0, 1)
    rng = np.random.default_rng(seed)
    for r in range(WATER_TOP, h):
        s = 2 * AXIS + 1 - r
        if s < 0:
            break
        depth = r - WATER_TOP
        v = np.clip((lum[s] - lo) / max(1.0, hi - lo), 0, 1)
        # 2 tons: escuro / claro, pontilhado só na faixa de meio-tom
        thr = 0.55 + light_bias + (px.BAYER2[r % 2, np.arange(P) % 2] - 0.375) * 0.12
        light = v > thr
        keep = alpha[s].copy()
        # linhas horizontais quebradas: lacunas por linha, mais longe = mais
        gap_p = 0.05 + 0.018 * depth
        xx = 0
        while xx < P:
            ln = int(rng.integers(3, 14))
            if rng.random() < gap_p:
                keep[xx:xx + ln] = False
            xx += ln + int(rng.integers(4, 30))
        if depth % 3 == 2:
            keep &= (np.arange(P) + r * 5) % 7 < 3
        if depth > fade_depth:
            keep &= light
        row = out[r]
        row[keep & ~light] = px.rgba(tones[0])
        row[keep & light] = px.rgba(tones[1])
        if glow_tone is not None:
            glow = keep & (v > 0.82)
            row[glow] = px.rgba(glow_tone)
    # sem pixel solto
    m = px.opaque(out)
    lone = m & ~(px.shift(m, 1, 0) | px.shift(m, -1, 0) | px.shift(m, 0, 1) | px.shift(m, 0, -1)
                 | px.shift(m, 1, 1) | px.shift(m, -1, -1) | px.shift(m, 1, -1) | px.shift(m, -1, 1))
    out[lone] = 0
    return out


def mist():
    """Faixas de névoa: miolo sólido de 1-2 linhas, bordas em pontilhado de 50%
    (uma linha acima e uma abaixo) e pontas afinando em degraus."""
    P = PERIOD_MIST
    a = px.canvas(P, 270)
    rng = np.random.default_rng(5)
    # (linha do miolo, altura do miolo, nº de faixas, comprimento)
    for yc, core_h, count, (lmin, lmax) in [(234, 1, 4, (30, 80)), (240, 1, 5, (50, 130)),
                                              (248, 2, 5, (70, 170)), (257, 2, 4, (90, 190))]:
        for _ in range(count):
            x0 = int(rng.integers(0, P))
            ln = int(rng.integers(lmin, lmax))
            core = np.zeros((270, P), dtype=bool)
            upper = np.zeros((270, P), dtype=bool)
            for k in range(ln):
                xk = (x0 + k) % P
                t = min(k, ln - 1 - k)
                # a linha de cima é mais curta que a de baixo: ponta afinando
                # em degraus de 6 px, sem pontilhado nem degradê
                core[yc + core_h - 1, xk] = True
                if core_h > 1 and t >= 6:
                    upper[yc, xk] = True
            # quebras na faixa (a névoa não é uma régua)
            gaps = np.zeros((270, P), dtype=bool)
            for k in range(0, ln, int(rng.integers(18, 34))):
                g = int(rng.integers(2, 5))
                for j in range(g):
                    gaps[:, (x0 + k + j) % P] = True
            px.fill(a, core & ~gaps, MA)
            px.fill(a, upper & ~gaps, MB)
    m = px.opaque(a)
    lone = m & ~(px.shift(m, 1, 0) | px.shift(m, -1, 0) | px.shift(m, 0, 1) | px.shift(m, 0, -1)
                 | px.shift(m, 1, 1) | px.shift(m, -1, -1) | px.shift(m, 1, -1) | px.shift(m, -1, 1))
    a[lone] = 0
    return a


def ground_edge(chao, rng):
    """08_chao: a faixa de chão (linha 258 em diante) ganha borda irregular de
    pedras molhadas e linha d'água, em vez da borda reta."""
    a = chao.copy()
    P = a.shape[1]
    x = np.arange(P)
    solid_top = min(y for y in range(270) if a[y, :, 3].min() > 0)
    prof = steps_profile(solid_top - 2 + tile_noise(P, x, [5, 11, 19], [1.2, 0.8, 0.5]), solid_top - 4, solid_top)
    dark = [(1, 1, 18), (5, 8, 62), (21, 20, 126), (38, 30, 153), (76, 49, 187)]
    for i in range(P):
        t = prof[i]
        for y in range(t, solid_top):
            if a[y, i, 3] == 0:
                a[y, i] = px.rgba(dark[1])
        if a[t - 1, i, 3] == 0:
            a[t, i] = px.rgba(dark[3] if (i // 5) % 3 else dark[4])
            a[t - 1, i] = px.rgba(dark[0])
            if (i // 9) % 4 != 0:
                a[t - 2, i] = px.rgba(W4)       # brilho da água encostando
    return a


# --------------------------------------------------------------- main

def main():
    ARCHIVE.mkdir(parents=True, exist_ok=True)
    for name in ["06_estruturas.png", "07_lago.png", "08_chao.png", "menu.png"]:
        dst = ARCHIVE / name
        if not dst.exists():
            shutil.copy2(BG / name, dst)
    rng = np.random.default_rng(3)

    # 06 estruturas: parte da arte do TCC, como o gerador original (período 760)
    src = np.array(Image.open(SRC / "estruturas-fundo.png").convert("RGBA"))
    s = px.canvas(760, 270)
    s[:, :480] = src
    clear_ring(s)
    draw_arch_ruin(s, np.random.default_rng(11))
    fix_tower_window(s)
    tower_base(s, np.random.default_rng(12))
    px.save(s, BG / "06_estruturas.png")

    water = lake_water()
    px.save(water, BG / "07_lago.png")
    bank, top, bot = lake_bank(np.random.default_rng(21))
    px.save(bank, BG / "07_margem.png")

    mont = np.array(Image.open(BG / "05_montanhas.png").convert("RGBA"))
    px.save(reflection(mont, RM, seed=31, light_bias=0.3), BG / "05_reflexo.png")
    px.save(reflection(s, RS, glow_tone=(172, 134, 249), seed=32), BG / "06_reflexo.png")

    chao_src = np.array(Image.open(ARCHIVE / "08_chao.png").convert("RGBA"))
    chao = ground_edge(chao_src, rng)
    px.save(chao, BG / "08_chao.png")
    px.save(mist(), BG / "09_nevoa.png")

    # menu: a mesma paisagem, composta a partir da origem (x=0) de cada camada
    menu = px.canvas(480, 270)

    def lay(img, x0=0):
        P = img.shape[1]
        x = -x0 % P - P if x0 else 0
        while x < 480:
            px.composite(menu, img, x, 0)
            x += P

    for name in ["ceu", "nuvem1", "nuvem2", "nuvem3", "nuvem4", "montanhas"]:
        lay(np.array(Image.open(SRC / f"{name}.png").convert("RGBA")))
    lay(s)
    lay(water)
    lay(np.array(Image.open(BG / "05_reflexo.png").convert("RGBA")))
    lay(np.array(Image.open(BG / "06_reflexo.png").convert("RGBA")))
    lay(bank)
    lay(chao)
    lay(mist())
    px.save(menu, BG / "menu.png")

    manifest = ROOT / "assets/sprites/manifest.json"
    data = json.loads(manifest.read_text(encoding="utf-8"))
    data["background_rodada3"] = {
        "axis_row": AXIS, "water_top_row": WATER_TOP,
        "layers": ["07_lago (água)", "05_reflexo", "06_reflexo", "07_margem", "08_chao", "09_nevoa"],
        "periods_px": {"07_lago": PERIOD_LAKE, "07_margem": PERIOD_LAKE, "05_reflexo": mont.shape[1],
                       "06_reflexo": 760, "09_nevoa": PERIOD_MIST},
        "gerador": "tools/fundo_rodada3.py (depois de tools/build_orb_assets.py)",
    }
    manifest.write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print("fundo rodada 3 gerado")


if __name__ == "__main__":
    main()
