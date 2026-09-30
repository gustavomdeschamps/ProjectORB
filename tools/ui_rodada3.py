"""Rodada 3, etapa 3: arte de interface feita à mão (em código).

Gera em assets/sprites/:
- ui/logo.png        : lettering "PROJECT ORB" (o "O" é o próprio ORB), sem fonte;
- ui/weakpoint*.png  : alvo de ponto fraco em cristal âmbar (sem anel), 3 tamanhos;
- fx/shatter_NN.png  : o alvo estilhaçando (6 quadros);
- fx/miss_NN.png     : tiro errado: faísca que se desfaz (5 quadros).
As versões anteriores vão para art-source/descartado_rodada3/ui/.
Provas (folhas de contato 1x/4x em fundo do jogo e claro, GIFs) em
docs/qa/rodada3/anti_ia/arte/.
"""
from __future__ import annotations

import math
import shutil
from pathlib import Path

import numpy as np

import pxlib as px

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "assets/sprites"
ARCHIVE = ROOT / "art-source/descartado_rodada3/ui"
PROOF = ROOT / "docs/qa/rodada3/anti_ia/arte"
GAME_BG = (22, 18, 92)
LIGHT_BG = (225, 222, 240)

# ---------------------------------------------------------------- paletas
# Letras do logo: lilás, sombra puxando para o roxo-azulado, luz para o branco quente
LET = [(40, 14, 90), (70, 34, 150), (104, 70, 206), (150, 112, 242), (204, 178, 255), (248, 240, 255)]
LET_OUT = (18, 8, 44)
LET_RIM = (92, 62, 178)          # contorno do lado da luz
# ORB (tirado do sprite do ORB, quantizado)
ORB = [(13, 7, 50), (54, 4, 136), (88, 8, 168), (112, 50, 248), (151, 62, 234), (193, 112, 235), (236, 214, 251)]
ORB_FACE = [(1, 1, 7), (11, 4, 40), (29, 7, 73)]
ORB_EYE = [(203, 191, 222), (251, 249, 252)]
# Âmbar: SÓ nos alvos de ponto fraco
AMB = [(74, 30, 12), (140, 66, 18), (214, 128, 30), (250, 186, 64), (255, 226, 150), (255, 248, 222)]


# ------------------------------------------------------------------ logo

def letter_r(w, h, s, c):
    m = np.zeros((h, w), dtype=bool)
    bowl_h = int(h * 0.56)
    m |= px.poly_mask(w, h, [(0, 0), (w - c, 0), (w, c), (w, bowl_h - c), (w - c, bowl_h), (0, bowl_h)])
    m |= px.rect_mask(w, h, 0, 0, s, h)
    hole = px.poly_mask(w, h, [(s, s - 1), (w - s - 2, s - 1), (w - s, s + 1), (w - s, bowl_h - s - 1),
                               (w - s - 2, bowl_h - s + 1), (s, bowl_h - s + 1)])
    m &= ~hole
    # perna: diagonal a 45 graus (degraus 1-1), do bojo até o pé
    top = bowl_h - 2
    m |= px.poly_mask(w, h, [(w - s - 5, top), (w - s + 5, top), (w, h - (w - (w - s + 5)) - 0), (w, h), (w - s + 1, h),
                             (w - s - 5, top + 3)])
    return m


def letter_b(w, h, s, c):
    m = np.zeros((h, w), dtype=bool)
    mid = int(h * 0.47)
    m |= px.poly_mask(w, h, [(0, 0), (w - c - 3, 0), (w - 3, c), (w - 3, mid - 4), (w - 6, mid - 1), (w - 6, mid + 1),
                             (w, mid + 5), (w, h - c), (w - c, h), (0, h)])
    up = px.poly_mask(w, h, [(s, s - 1), (w - s - 4, s - 1), (w - s - 3, s), (w - s - 3, mid - s + 3), (w - s - 5, mid - s + 5),
                             (s, mid - s + 5)])
    lo = px.poly_mask(w, h, [(s, mid + s - 4), (w - s - 2, mid + s - 4), (w - s, mid + s - 2), (w - s, h - s - 1),
                             (w - s - 2, h - s + 1), (s, h - s + 1)])
    return m & ~up & ~lo


SMALL = {
    # 7x9, traço de 2 px, quinas chanfradas: desenhadas à mão
    "P": ["######.", "##...##", "##...##", "##...##", "######.", "##.....", "##.....", "##.....", "##....."],
    "R": ["######.", "##...##", "##...##", "##...##", "######.", "##.##..", "##..##.", "##...##", "##...##"],
    "O": [".#####.", "##...##", "##...##", "##...##", "##...##", "##...##", "##...##", "##...##", ".#####."],
    "J": ["....###", ".....##", ".....##", ".....##", ".....##", ".....##", "##...##", "##...##", ".#####."],
    "E": ["#######", "##.....", "##.....", "##.....", "######.", "##.....", "##.....", "##.....", "#######"],
    "C": [".######", "##.....", "##.....", "##.....", "##.....", "##.....", "##.....", "##.....", ".######"],
    "T": ["#######", "...##..", "...##..", "...##..", "...##..", "...##..", "...##..", "...##..", "...##.."],
}


def small_word(word, gap=2):
    w = sum(len(SMALL[ch][0]) for ch in word) + gap * (len(word) - 1)
    m = np.zeros((9, w), dtype=bool)
    x = 0
    for ch in word:
        g = SMALL[ch]
        for y, row in enumerate(g):
            for i, v in enumerate(row):
                if v == "#":
                    m[y, x + i] = True
        x += len(g[0]) + gap
    return m


def paint_letter(a, mask, ox, oy, extrude=3):
    """Letra com extrusão 3D para baixo-direita, contorno seletivo, bisel e brilho."""
    h, w = mask.shape
    H, W = a.shape[:2]
    full = np.zeros((H, W), dtype=bool)
    full[oy:oy + h, ox:ox + w] = mask
    # extrusão (atrás): tom escuro, borda mais escura
    ext = np.zeros_like(full)
    for k in range(1, extrude + 1):
        ext |= px.shift(full, k, k)
    ext &= ~full
    px.fill(a, ext, LET[1])
    px.fill(a, ext & ~px.shift(ext, -1, -1), LET[0])
    # corpo
    px.fill(a, full, LET[3])
    # bisel: faixa de cima clara, lados direito/baixo na sombra (luz do alto à esquerda)
    top1 = full & ~px.shift(full, 0, 1)
    left1 = full & ~px.shift(full, 1, 0)
    right2 = full & (~px.shift(full, -1, 0) | ~px.shift(full, -2, 0))
    bot2 = full & (~px.shift(full, 0, -1) | ~px.shift(full, 0, -2))
    px.fill(a, right2 | bot2, LET[2])
    px.fill(a, left1 | top1, LET[4])
    # brilho: canto de cima à esquerda de cada contorno (2 px)
    corner = top1 & left1
    ys, xs = np.nonzero(corner)
    for y, x in zip(ys, xs):
        a[y, x] = px.rgba(LET[5])
        if x + 1 < W and full[y, x + 1]:
            a[y, x + 1] = px.rgba(LET[5])
    # contorno: escuro, e mais claro no lado da luz (em cima/à esquerda)
    solid = full | ext
    edge = px.outer_edge(solid)
    px.fill(a, edge, LET_OUT)
    lit = edge & (px.shift(full, 0, -1) | px.shift(full, -1, 0)) & ~(px.shift(ext, 0, -1) | px.shift(ext, -1, 0))
    px.fill(a, lit, LET_RIM)
    return full


def orb_letter(a, cx, cy, r, look=0):
    """O "O" do logo: o ORB (esfera roxa, viseira escura com dois olhos).
    look: desloca os olhos (px) para olhar para um lado."""
    H, W = a.shape[:2]
    yy, xx = np.mgrid[0:H, 0:W]
    d = ((xx + 0.5 - cx) / r) ** 2 + ((yy + 0.5 - cy) / r) ** 2
    body = d <= 1.0
    # extrusão igual à das letras
    ext = (px.shift(body, 1, 1) | px.shift(body, 2, 2) | px.shift(body, 3, 3)) & ~body
    px.fill(a, ext, LET[1])
    px.fill(a, ext & ~px.shift(ext, -1, -1), LET[0])
    # volume: tons por direção da luz (alto à esquerda), sem sombra em volta toda
    nx, ny = (xx + 0.5 - cx) / r, (yy + 0.5 - cy) / r
    nz = np.sqrt(np.clip(1 - nx * nx - ny * ny, 0, 1))
    L = np.array([-0.52, -0.62, 0.59])
    L = L / np.linalg.norm(L)
    lam = nx * L[0] + ny * L[1] + nz * L[2]    # normal 3D da esfera: faixas em crescente
    tone = np.select([lam > 0.93, lam > 0.72, lam > 0.42, lam > 0.12], [5, 4, 3, 2], 1)
    for t in range(1, 6):
        px.fill(a, body & (tone == t), ORB[t])
    # reflexo de luz: mancha oval no alto à esquerda (2 tons)
    spec = ((xx + 0.5 - (cx - r * 0.38)) / (r * 0.26)) ** 2 + ((yy + 0.5 - (cy - r * 0.52)) / (r * 0.16)) ** 2 <= 1
    px.fill(a, body & spec, ORB[6])
    px.fill(a, body & ((xx + 0.5 - (cx - r * 0.42)) ** 2 / (r * 0.10) ** 2 + (yy + 0.5 - (cy - r * 0.55)) ** 2 / (r * 0.07) ** 2 <= 1),
            (251, 249, 252))
    # viseira: retângulo de quinas chanfradas (não é círculo), um pouco abaixo do centro
    vw, vh = r * 0.52, r * 0.36
    vx0, vy0 = cx - vw, cy - vh * 0.35
    vis = px.poly_mask(W, H, [(vx0 + 4, vy0), (cx + vw - 4, vy0), (cx + vw, vy0 + 4), (cx + vw, vy0 + 2 * vh - 4),
                              (cx + vw - 4, vy0 + 2 * vh), (vx0 + 4, vy0 + 2 * vh), (vx0, vy0 + 2 * vh - 4), (vx0, vy0 + 4)])
    px.fill(a, vis, ORB_FACE[0])
    px.fill(a, vis & ~px.shift(vis, 0, 1), ORB_FACE[2])        # borda de cima da viseira
    px.fill(a, vis & ~px.shift(vis, 0, -1), ORB_FACE[1])
    # olhos: barras verticais brancas com 1 px de brilho lilás
    for ex in (cx - 6 + look, cx + 3 + look):
        eye = px.rect_mask(W, H, int(ex), int(vy0 + vh * 0.40), int(ex) + 3, int(vy0 + vh * 1.60))
        px.fill(a, eye, ORB_EYE[1])
        px.fill(a, eye & ~px.shift(eye, 0, -1), ORB_EYE[0])
    # contorno
    edge = px.outer_edge(body | ext)
    px.fill(a, edge, ORB[0])
    lit = edge & (px.shift(body, 0, -1) | px.shift(body, -1, 0)) & ~(px.shift(ext, 0, -1) | px.shift(ext, -1, 0))
    px.fill(a, lit, LET_RIM)
    return body


def build_logo():
    W, H = 150, 78
    a = px.canvas(W, H)
    # "PROJECT" pequeno, em cima e à esquerda, alinhado com o ORB
    pw = small_word("PROJECT")
    sm = np.zeros((H, W), dtype=bool)
    sm[2:2 + pw.shape[0], 4:4 + pw.shape[1]] = pw
    ext = (px.shift(sm, 1, 1)) & ~sm
    px.fill(a, ext, LET[1])
    px.fill(a, sm, LET[4])
    px.fill(a, sm & ~px.shift(sm, 0, 1), LET[5])
    px.fill(a, px.outer_edge(sm | ext), LET_OUT)
    # ORB (o "O"), R e B: base comum
    base = 74
    r = 26
    orb_letter(a, 4 + r, base - r, r)
    rw, bh = 36, 46
    xr = 4 + 2 * r + 4
    paint_letter(a, letter_r(rw, bh, 11, 6), xr, base - bh)
    paint_letter(a, letter_b(rw, bh, 11, 6), xr + rw + 5, base - bh)
    return a


# --------------------------------------------------------------- alvos

def target(size):
    """Alvo de ponto fraco: cristal âmbar facetado (losango de 4 faces), sem anel."""
    a = px.canvas(size, size)
    c = size / 2
    r = size / 2 - 1
    shape = [(c, c - r), (c + r, c), (c, c + r), (c - r, c)]
    m = px.poly_mask(size, size, shape)
    yy, xx = np.mgrid[0:size, 0:size]
    up = (yy + 0.5) < c
    left = (xx + 0.5) < c
    px.fill(a, m & up & left, AMB[4])
    px.fill(a, m & up & ~left, AMB[3])
    px.fill(a, m & ~up & left, AMB[2])
    px.fill(a, m & ~up & ~left, AMB[1])
    # núcleo claro no centro (2x2) e aresta de cima acesa
    core = px.rect_mask(size, size, int(c) - 1, int(c) - 1, int(c) + 1, int(c) + 1) & m
    px.fill(a, core, AMB[5])
    px.fill(a, m & ~px.shift(m, 0, 1) & up, AMB[5])
    px.fill(a, px.outer_edge(m), AMB[0])
    return a


def shatter_frames(size=15):
    """Cristal âmbar se partindo: estala, racha em 4 cacos que voam e somem."""
    frames = []
    base = target(size)
    c = size // 2
    quads = [(-1, -1), (1, -1), (-1, 1), (1, 1)]
    for f in range(6):
        a = px.canvas(size + 28, size + 28)
        off = 14
        if f == 0:
            px.composite(a, base, off, off)
            # flash branco no núcleo e rachadura
            a[off + c - 2:off + c + 2, off + c] = px.rgba(AMB[5])
            a[off + c, off + c - 2:off + c + 2] = px.rgba(AMB[5])
            frames.append(a)
            continue
        spread = [0, 2, 4, 6, 8, 9][f]
        for (sx, sy) in quads:
            piece = base.copy()
            yy, xx = np.mgrid[0:size, 0:size]
            keep = ((xx - c) * sx >= 0) & ((yy - c) * sy >= 0)
            piece[~keep] = 0
            if f >= 4:
                # cacos diminuem: tira a borda de fora
                pm = px.opaque(piece)
                piece[pm & ~(px.shift(pm, sx, 0) & px.shift(pm, 0, sy))] = 0
            if f == 5:
                pm = px.opaque(piece)
                piece[pm & ~(px.shift(pm, sx, 0) & px.shift(pm, 0, sy))] = 0
            px.composite(a, piece, off + sx * spread, off + sy * spread + (f - 1))
        if f in (1, 2):
            # estilhaços pequenos (2 px) saindo em diagonal
            for (sx, sy) in quads:
                x = off + c + sx * (spread + 5)
                y = off + c + sy * (spread + 5)
                a[y, x:x + 2] = px.rgba(AMB[4])
                a[y + 1, x:x + 2] = px.rgba(AMB[2])
        frames.append(a)
    return frames


def miss_frames():
    """Tiro no lugar errado: faísca rosa em X que se desfaz (sem anel)."""
    ROSE = [(80, 14, 44), (170, 34, 86), (240, 92, 140), (255, 190, 214)]
    frames = []
    for f in range(5):
        s = 13
        a = px.canvas(s, s)
        c = s // 2
        ln = [2, 4, 5, 5, 4][f]
        gap = [0, 0, 1, 2, 3][f]
        for (dx, dy) in [(1, 1), (-1, 1), (1, -1), (-1, -1)]:
            for k in range(gap, ln + 1):
                x, y = c + dx * k, c + dy * k
                col = ROSE[3] if k <= gap + 1 and f < 3 else ROSE[2]
                if f == 4:
                    col = ROSE[1]
                a[y, x] = px.rgba(col)
        if f < 2:
            a[c, c] = px.rgba(ROSE[3])
        m = px.opaque(a)
        edge = px.outer_edge(m) & px.rect_mask(s, s, 0, 0, s, s)
        if f < 3:
            px.fill(a, edge & (px.dither(edge, 0.5)), ROSE[0])
        frames.append(a)
    return frames


def main():
    ARCHIVE.mkdir(parents=True, exist_ok=True)
    for name in ["ui/weakpoint.png", "ui/weakpoint_small.png", "ui/weakpoint_boss.png"]:
        src = OUT / name
        dst = ARCHIVE / name.replace("/", "_")
        if src.exists() and not dst.exists():
            shutil.copy2(src, dst)
    logo = build_logo()
    px.save(logo, OUT / "ui/logo.png")
    for name, size in [("weakpoint_small", 7), ("weakpoint", 9), ("weakpoint_boss", 11)]:
        px.save(target(size), OUT / f"ui/{name}.png")
    sh = shatter_frames()
    for i, f in enumerate(sh, 1):
        px.save(f, OUT / f"fx/shatter_{i:02d}.png")
    ms = miss_frames()
    for i, f in enumerate(ms, 1):
        px.save(f, OUT / f"fx/miss_{i:02d}.png")
    # borda da faixa escura dos menus: 3 degraus de pontilhado (75/50/25%),
    # 4 colunas cada, repetida na vertical e tingida em código
    edge = px.canvas(12, 4)
    for x in range(12):
        lvl = [0.75, 0.5, 0.25][x // 4]
        for y in range(4):
            if px.BAYER4[y % 4, x % 4] < lvl:
                edge[y, x] = (255, 255, 255, 255)
    px.save(edge, OUT / "ui/shade_edge.png")

    PROOF.mkdir(parents=True, exist_ok=True)
    for bgname, bg in [("fundo_jogo", GAME_BG), ("fundo_claro", LIGHT_BG)]:
        for k in (1, 4):
            px.save(px.contact_sheet([logo], bg, scale=k), PROOF / f"logo_{k}x_{bgname}.png")
            px.save(px.contact_sheet([target(7), target(9), target(11)], bg, scale=k), PROOF / f"alvos_{k}x_{bgname}.png")
            px.save(px.contact_sheet(sh, bg, scale=k), PROOF / f"estilhaco_{k}x_{bgname}.png")
            px.save(px.contact_sheet(ms, bg, scale=k), PROOF / f"erro_{k}x_{bgname}.png")
    px.save_gif(sh, [60, 50, 50, 60, 70, 90], PROOF / "estilhaco.gif", GAME_BG, scale=8)
    px.save_gif(ms, [50, 50, 60, 70, 90], PROOF / "erro.gif", GAME_BG, scale=8)
    print("ui rodada 3 gerada")


if __name__ == "__main__":
    main()
