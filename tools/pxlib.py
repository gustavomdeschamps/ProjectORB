"""Ferramentas de pixel art da rodada 3 (sem dependências além de numpy/PIL).

Convenções:
- imagens são arrays numpy uint8 (altura, largura, 4) RGBA; alfa só 0 ou 255;
- coordenadas (x, y) com y para BAIXO (como no PNG);
- polígonos são testados no CENTRO de cada pixel (x + 0.5, y + 0.5), então
  bordas retas saem em degraus regulares;
- luz sempre do alto à esquerda (LIGHT = (-1, -1)).
"""
from __future__ import annotations

import math
from pathlib import Path

import numpy as np
from PIL import Image

LIGHT = (-1, -1)
BAYER2 = np.array([[0, 2], [3, 1]]) / 4.0
BAYER4 = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0


# ------------------------------------------------------------------ básico

def canvas(w, h):
    return np.zeros((h, w, 4), dtype=np.uint8)


def rgba(c):
    return (int(c[0]), int(c[1]), int(c[2]), 255)


def fill(a, mask, color):
    a[mask] = rgba(color)
    return a


def opaque(a):
    return a[:, :, 3] > 0


def put(a, x, y, color):
    h, w = a.shape[:2]
    if 0 <= x < w and 0 <= y < h:
        a[y, x] = rgba(color)


def poly_mask(w, h, pts, ox=0.0, oy=0.0):
    """Máscara dos pixels cujo centro cai dentro do polígono (par-ímpar)."""
    yy, xx = np.mgrid[0:h, 0:w]
    px = xx + 0.5 - ox
    py = yy + 0.5 - oy
    inside = np.zeros((h, w), dtype=bool)
    n = len(pts)
    for i in range(n):
        x1, y1 = pts[i]
        x2, y2 = pts[(i + 1) % n]
        if y1 == y2:
            continue
        cond = (py >= min(y1, y2)) & (py < max(y1, y2))
        xint = x1 + (py - y1) * (x2 - x1) / (y2 - y1)
        inside ^= cond & (px < xint)
    return inside


def rect_mask(w, h, x0, y0, x1, y1):
    m = np.zeros((h, w), dtype=bool)
    m[max(0, y0):max(0, y1), max(0, x0):max(0, x1)] = True
    return m


def shift(mask, dx, dy):
    """Máscara deslocada (dx, dy), sem dar a volta."""
    out = np.zeros_like(mask)
    h, w = mask.shape
    xs0, xs1 = max(0, -dx), min(w, w - dx)
    ys0, ys1 = max(0, -dy), min(h, h - dy)
    out[ys0 + dy:ys1 + dy, xs0 + dx:xs1 + dx] = mask[ys0:ys1, xs0:xs1]
    return out


def outer_edge(mask, diag=False):
    """Pixels FORA da máscara que encostam nela (contorno externo de 1 px)."""
    n = shift(mask, 1, 0) | shift(mask, -1, 0) | shift(mask, 0, 1) | shift(mask, 0, -1)
    if diag:
        n |= shift(mask, 1, 1) | shift(mask, -1, -1) | shift(mask, 1, -1) | shift(mask, -1, 1)
    return n & ~mask


def inner_edge(mask):
    """Pixels DENTRO da máscara com algum vizinho (4) fora."""
    return mask & ~(shift(mask, 1, 0) & shift(mask, -1, 0) & shift(mask, 0, 1) & shift(mask, 0, -1))


def facing(mask, dx, dy):
    """Pixels da borda interna cujo vizinho na direção (dx, dy) está fora."""
    return mask & ~shift(mask, -dx, -dy)


def line(a, x0, y0, x1, y1, color, mask=None):
    """Bresenham; com 'mask', só pinta onde a máscara é verdadeira."""
    x0, y0, x1, y1 = int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1))
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    h, w = a.shape[:2]
    while True:
        if 0 <= x0 < w and 0 <= y0 < h and (mask is None or mask[y0, x0]):
            a[y0, x0] = rgba(color)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def line_mask(w, h, x0, y0, x1, y1):
    m = np.zeros((h, w, 4), dtype=np.uint8)
    line(m, x0, y0, x1, y1, (1, 1, 1))
    return m[:, :, 3] > 0


def selective_outline(a, mask, dark, lit=None, light=LIGHT):
    """Contorno externo: escuro onde encosta no fundo; 'lit' no lado da luz."""
    edge = outer_edge(mask)
    fill(a, edge, dark)
    if lit is not None:
        # o lado de onde vem a luz: pixel de contorno cujo vizinho na direção
        # oposta à luz é a forma (ou seja, fica acima/à esquerda da forma)
        lx, ly = light
        side = edge & (shift(mask, lx, 0) | shift(mask, 0, ly)) & ~shift(mask, -lx, 0) & ~shift(mask, 0, -ly)
        fill(a, side, lit)
    return a


def dither(mask, level, matrix=BAYER4, ox=0, oy=0):
    """Subconjunto da máscara com densidade 'level' (0..1), Bayer ordenado."""
    h, w = mask.shape
    mh, mw = matrix.shape
    yy, xx = np.mgrid[0:h, 0:w]
    thr = matrix[(yy + oy) % mh, (xx + ox) % mw]
    return mask & (thr < level)


def shade_by_normal(mask, ramp, light=(-0.6, -0.8), bias=0.0, band=None):
    """Tons de uma rampa pela direção da borda mais próxima (sombreamento de volume
    sem 'pillow shading': o lado da luz clareia, o oposto escurece, o meio fica no
    tom base). Devolve um array de índices da rampa (-1 fora da máscara)."""
    h, w = mask.shape
    idx = np.full((h, w), -1, dtype=int)
    ys, xs = np.nonzero(mask)
    if len(xs) == 0:
        return idx
    cx, cy = xs.mean(), ys.mean()
    rx = max(1.0, (xs.max() - xs.min() + 1) / 2)
    ry = max(1.0, (ys.max() - ys.min() + 1) / 2)
    nx = (xs + 0.5 - cx) / rx
    ny = (ys + 0.5 - cy) / ry
    lx, ly = light
    d = -(nx * lx + ny * ly) + bias          # >0 = voltado para a luz
    n = len(ramp)
    base = n // 2
    band = band or [-0.55, -0.15, 0.35, 0.7]
    k = np.searchsorted(np.array(band), d)
    k = np.clip(k - 2 + base, 0, n - 1)
    idx[ys, xs] = k
    return idx


def paint_idx(a, idx, ramp):
    for i, c in enumerate(ramp):
        fill(a, idx == i, c)
    return a


# ------------------------------------------------------------- utilidades

def save(a, path):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(a, "RGBA").save(path)


def load(path):
    return np.array(Image.open(path).convert("RGBA"))


def upscale(a, k):
    return np.repeat(np.repeat(a, k, axis=0), k, axis=1)


def composite(bg, fg, x=0, y=0):
    """Cola fg (alfa 0/255) sobre bg em (x, y); devolve bg."""
    h, w = fg.shape[:2]
    H, W = bg.shape[:2]
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(W, x + w), min(H, y + h)
    if x1 <= x0 or y1 <= y0:
        return bg
    sub = fg[y0 - y:y1 - y, x0 - x:x1 - x]
    m = sub[:, :, 3] > 0
    bg[y0:y1, x0:x1][m] = sub[m]
    return bg


def colors_of(a):
    m = a[:, :, 3] > 0
    return {tuple(int(v) for v in c) for c in a[m][:, :3]}


def contact_sheet(frames, bg, scale=1, pad=2, cols=None):
    """Tira de quadros lado a lado sobre a cor 'bg' (ou imagem de fundo)."""
    fw = max(f.shape[1] for f in frames)
    fh = max(f.shape[0] for f in frames)
    cols = cols or len(frames)
    rows = math.ceil(len(frames) / cols)
    W, H = cols * (fw + pad) + pad, rows * (fh + pad) + pad
    sheet = np.zeros((H, W, 4), dtype=np.uint8)
    sheet[:] = rgba(bg)
    for i, f in enumerate(frames):
        r, c = divmod(i, cols)
        composite(sheet, f, pad + c * (fw + pad) + (fw - f.shape[1]) // 2, pad + r * (fh + pad) + (fh - f.shape[0]))
    return upscale(sheet, scale) if scale > 1 else sheet


def save_gif(frames, durations_ms, path, bg, scale=4):
    """GIF da animação (fundo sólido 'bg', escala inteira, tempos por quadro)."""
    ims = []
    fw = max(f.shape[1] for f in frames)
    fh = max(f.shape[0] for f in frames)
    for f in frames:
        c = np.zeros((fh, fw, 4), dtype=np.uint8)
        c[:] = rgba(bg)
        composite(c, f, (fw - f.shape[1]) // 2, fh - f.shape[0])
        ims.append(Image.fromarray(upscale(c, scale)[:, :, :3], "RGB"))
    if isinstance(durations_ms, (int, float)):
        durations_ms = [int(durations_ms)] * len(frames)
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    ims[0].save(path, save_all=True, append_images=ims[1:], duration=list(durations_ms), loop=0, disposal=2)


def hsv(c):
    r, g, b = (v / 255.0 for v in c[:3])
    mx, mn = max(r, g, b), min(r, g, b)
    d = mx - mn
    if d == 0:
        h = 0.0
    elif mx == r:
        h = (60 * ((g - b) / d) + 360) % 360
    elif mx == g:
        h = 60 * ((b - r) / d) + 120
    else:
        h = 60 * ((r - g) / d) + 240
    s = 0.0 if mx == 0 else d / mx
    return h, s, mx


def is_amber(c):
    """Amarelo-âmbar: matiz 28..62°, saturado e claro."""
    h, s, v = hsv(c)
    return 28 <= h <= 62 and s >= 0.45 and v >= 0.55
