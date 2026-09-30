"""Fundo do Project ORB (Fase 5 / item 7): paisagem roxa em camadas de parallax.

Substitui as camadas do TCC (o lago em blocos de 9 px virava mosaico a 4x, e
a mistura pontilhada das emendas de lago/chão virava uma coluna pontilhada).

Regras:
- paleta curta de 8 tons roxo/azul (BG_PALETTE), sem gradiente liso: as
  transições do céu e da bruma são pontilhado ordenado Bayer 4x4;
- nada de ruído por pixel, mosaico, espelhamento ou anéis;
- formas geométricas facetadas com luz do alto à esquerda (face esquerda
  clara, direita escura, aresta de luz de 1 px);
- cada camada é tileável: toda forma é desenhada em x, x-período e
  x+período, então a emenda é invisível por construção; períodos diferentes
  entre si e, nas camadas com estruturas grandes, maiores que a tela (480);
- baixo contraste e pouca saturação: o jogo (ORB, inimigos, alvos) se destaca.
Determinístico: posições em tabelas escritas aqui ou num gerador congruente
com semente fixa (poucas estrelas e fragmentos), nunca ruído por pixel.
"""

import numpy as np
from PIL import Image, ImageDraw

H = 270
SCREEN_W = 480
# 8 tons, do mais escuro ao mais claro
BG_PALETTE = [
    (20, 15, 44),    # 0 fundo profundo / silhuetas
    (30, 23, 66),    # 1
    (42, 33, 90),    # 2
    (56, 45, 114),   # 3
    (72, 58, 136),   # 4
    (92, 76, 158),   # 5
    (122, 106, 186), # 6 arestas de luz
    (170, 158, 214), # 7 estrelas
]
EMPTY = 255
BAYER4 = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0


class Layer:
    def __init__(self, width, fill=EMPTY):
        self.w = width
        self.a = np.full((H, width), fill, dtype=np.uint8)
        self.im = Image.fromarray(self.a, "L")
        self.d = ImageDraw.Draw(self.im)

    def poly(self, pts, idx):
        """Polígono sem antialiasing, repetido em -w, 0, +w (emenda invisível)."""
        for off in (-self.w, 0, self.w):
            self.d.polygon([(x + off, y) for x, y in pts], fill=idx)

    def line(self, p0, p1, idx):
        for off in (-self.w, 0, self.w):
            self.d.line([(p0[0] + off, p0[1]), (p1[0] + off, p1[1])], fill=idx)

    def rect(self, x0, y0, x1, y1, idx):
        self.poly([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], idx)

    def dither_band(self, y0, y1, idx, level):
        """Faixa horizontal em pontilhado ordenado (densidade level 0..1). A largura
        da camada deve ser múltipla de 4 para o padrão Bayer emendar."""
        a = np.array(self.im)
        for y in range(max(0, y0), min(H, y1)):
            m = BAYER4[y % 4, np.arange(self.w) % 4] < level
            a[y, m] = idx
        self.im = Image.fromarray(a, "L")
        self.d = ImageDraw.Draw(self.im)

    def rgba(self):
        a = np.array(self.im)
        out = np.zeros((H, self.w, 4), dtype=np.uint8)
        for i, c in enumerate(BG_PALETTE):
            out[a == i] = (*c, 255)
        return out


def lcg(seed):
    """Gerador congruente com semente fixa (só para espalhar poucas estrelas)."""
    s = seed
    while True:
        s = (s * 1103515245 + 12345) & 0x7FFFFFFF
        yield s


def sky():
    L = Layer(SCREEN_W, 0)
    bands = [(0, 0), (58, 1), (118, 2), (172, 3), (214, 4)]   # (início, tom)
    for (y0, idx) in bands[1:]:
        L.rect(-1, y0, SCREEN_W + 1, H, idx)
    a = np.array(L.im)
    for (y0, idx), (_, prev) in zip(bands[1:], bands[:-1]):  # transição de 8 linhas
        for k in range(8):
            y = y0 - 8 + k
            m = BAYER4[y % 4, np.arange(SCREEN_W) % 4] < (k + 0.5) / 8
            a[y, m] = idx
    L.im = Image.fromarray(a, "L")
    return L


def stars(width=503, seed=29, twinkle=False):
    L = Layer(width)
    g = lcg(seed)
    a = np.array(L.im)
    for k in range(34):
        x = next(g) % width
        y = 6 + next(g) % 140
        bright = next(g) % 5 == 0
        idx = 7 if bright else 6
        if twinkle and k % 3 == 0:
            idx = 5 if bright else EMPTY          # no 2º quadro algumas apagam/escurecem
        a[y, x] = idx
    L.im = Image.fromarray(a, "L")
    return L


def facet(L, x, top, base, half, lit, shade, ridge, lean=0):
    """Pico facetado: face esquerda clara, direita escura, aresta de luz."""
    apex = (x + lean, top)
    L.poly([(x - half, base), apex, (x + lean, base)], lit)
    L.poly([(x + lean, base), apex, (x + half, base)], shade)
    L.line((x - half, base), apex, ridge)


def mountains():
    W = 613
    L = Layer(W)
    L.rect(-1, 196, W + 1, H, 1)
    for x, top, half in [(20, 120, 70), (95, 142, 55), (170, 104, 80), (262, 150, 60), (330, 118, 75),
                         (412, 136, 58), (480, 110, 72), (560, 146, 62)]:
        facet(L, x, top, 197, half, 2, 1, 3)
    return L


def mist(width, y0, y1, idx, level):
    L = Layer(width)
    L.dither_band(y0, y0 + 4, idx, level * 0.5)
    L.dither_band(y0 + 4, y1 - 4, idx, level)
    L.dither_band(y1 - 4, y1, idx, level * 0.5)
    return L


def crystals():
    W = 701
    L = Layer(W)
    base = 222
    # (x, altura, meia-largura, inclinação): aglomerados de cristais
    for x, h, half, lean in [(30, 70, 7, -2), (44, 92, 9, 1), (58, 60, 6, 3), (190, 84, 8, 0), (204, 58, 6, 4),
                             (176, 50, 5, -3), (330, 100, 10, -1), (348, 72, 7, 3), (312, 64, 6, -4),
                             (500, 78, 8, 2), (486, 54, 6, -3), (620, 88, 9, -2), (638, 60, 6, 2)]:
        top = base - h
        tip = top + half * 2
        apex = (x + lean, top)
        L.poly([(x - half, base), (x - half, tip), apex, (x, tip + 2), (x, base)], 3)
        L.poly([(x, base), (x, tip + 2), apex, (x + half, tip), (x + half, base)], 2)
        L.line((x - half, tip), apex, 4)
    return L


def ruins():
    W = 829
    L = Layer(W)
    base = 226

    def pillar(x, h, w, broken):
        top = base - h
        L.rect(x, top + 4, x + w, base, 2)
        L.rect(x + w - 2, top + 4, x + w, base, 1)           # sombra à direita
        L.line((x, top + 4), (x, base), 3)                   # aresta de luz
        L.rect(x - 2, top, x + w + 2, top + 4, 3)             # capitel
        L.line((x - 2, top), (x + w + 2, top), 4)
        if broken:                                           # topo quebrado em degraus
            L.rect(x + w // 2, top - 1, x + w + 3, top + 3, EMPTY)
            L.rect(x + w - 2, top + 3, x + w + 3, top + 7, EMPTY)

    for x, h, w, broken in [(40, 70, 12, False), (96, 70, 12, True), (250, 96, 14, True), (300, 58, 10, False),
                            (470, 84, 12, False), (522, 84, 12, False), (690, 64, 12, True)]:
        pillar(x, h, w, broken)
    # vigas retas entre pares de pilares (sem arcos)
    for x0, x1, y in [(38, 110, 152), (468, 536, 138)]:
        L.rect(x0, y, x1, y + 5, 3)
        L.line((x0, y), (x1, y), 4)
        L.rect(x0, y + 5, x1, y + 6, 1)
    return L


def fragments():
    W = 389
    L = Layer(W)
    g = lcg(314)
    for k in range(14):
        x = next(g) % W
        y = 50 + next(g) % 130
        s = 3 + next(g) % 5
        kind = k % 3
        if kind == 0:     # triângulo
            L.poly([(x, y - s), (x - s, y + s), (x, y + s)], 5)
            L.poly([(x, y - s), (x, y + s), (x + s, y + s)], 3)
        elif kind == 1:   # losango
            L.poly([(x, y - s), (x - s, y), (x, y + s)], 5)
            L.poly([(x, y - s), (x, y + s), (x + s, y)], 3)
        else:             # lasca inclinada
            L.poly([(x - s, y), (x, y - s), (x + 1, y - s + 2), (x - s + 2, y + 1)], 4)
    return L


def foreground():
    W = 757
    L = Layer(W)
    L.rect(-1, 240, W + 1, H, 0)
    for x, h, half, lean in [(40, 26, 20, 4), (120, 40, 12, -3), (138, 22, 10, 2), (300, 34, 26, 0),
                             (455, 44, 10, 3), (472, 26, 14, -2), (610, 30, 24, -4), (700, 20, 16, 2)]:
        top = 241 - h
        L.poly([(x - half, 241), (x + lean, top), (x + half, 241)], 0)
        L.line((x - half, 241), (x + lean, top), 1)
    return L


LAYERS = [  # (nome, função) — ordem de trás para a frente
    ("00_ceu", sky),
    ("01_estrelas", stars),
    ("02_montanhas", mountains),
    ("03_bruma_alta", lambda: mist(420, 178, 196, 3, 0.5)),
    ("04_cristais", crystals),
    ("05_ruinas", ruins),
    ("06_bruma_baixa", lambda: mist(444, 214, 232, 4, 0.25)),
    ("07_fragmentos", fragments),
    ("08_frente", foreground),
]


def build():
    """{nome: RGBA} de cada camada, + o 2º quadro das estrelas e a composição do menu."""
    out = {name: fn().rgba() for name, fn in LAYERS}
    out["01_estrelas_b"] = stars(twinkle=True).rgba()
    comp = Image.new("RGBA", (SCREEN_W, H))
    for name, _ in LAYERS:
        a = out[name]
        tile = Image.fromarray(a, "RGBA")
        for x in range(0, SCREEN_W, tile.width):
            comp.alpha_composite(tile, (x, 0))
    out["menu"] = np.array(comp)
    return out


def seam_report(rgba):
    """Diferença da emenda (última->primeira coluna) comparada às colunas vizinhas."""
    a = rgba.astype(int)
    diffs = np.abs(a[:, 1:] - a[:, :-1]).sum(axis=(0, 2))
    seam = int(np.abs(a[:, 0] - a[:, -1]).sum())
    return seam, float(np.percentile(diffs, 99)), int(diffs.max())
