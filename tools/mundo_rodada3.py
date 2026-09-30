"""Rodada 3, etapa 3: plataformas e chão com material (pedra + cristal).

Gera em assets/sprites/world/:
- platform_capl.png / platform_capr.png : pontas da laje (6x16), esquerda e
  direita desenhadas separadas (a luz vem sempre do alto à esquerda: espelhar
  a ponta punha o brilho do lado errado);
- platform_mod_a/b/c.png (16x16) e platform_mod_d/e.png (12x16): miolos
  variados (blocos, rachaduras, cristal incrustado, borda gasta);
- platform_fill.png (1x16): coluna lisa para sobras;
- o mesmo com prefixo platform_alt_ (pedra azulada com cristal ciano) para as
  zonas alternadas;
- ground_a..ground_e.png (32x32): chão em 5 variantes de lajota + camadas de
  pedra embaixo; as bordas esquerda/direita de todas as variantes casam
  (qualquer ordem emenda sem costura).
As versões antigas vão para art-source/descartado_rodada3/mundo/.
"""
from __future__ import annotations

import shutil
from pathlib import Path

import numpy as np

import pxlib as px

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "assets/sprites/world"
ARCHIVE = ROOT / "art-source/descartado_rodada3/mundo"
PROOF = ROOT / "docs/qa/rodada3/anti_ia/arte"
GAME_BG = (22, 18, 92)
LIGHT_BG = (225, 222, 240)

# Pedra lilás-acinzentada (plataformas): sombra -> azul-roxo, luz -> lilás quente
P = [(16, 10, 36), (34, 26, 70), (58, 48, 108), (88, 76, 148), (126, 112, 186), (176, 160, 222), (214, 204, 240)]
# Pedra azulada (plataformas alternadas)
Q = [(8, 12, 36), (20, 32, 72), (36, 56, 108), (58, 88, 146), (92, 128, 186), (150, 184, 226), (206, 226, 246)]
CRY_V = [(70, 22, 160), (126, 60, 230), (188, 150, 255), (240, 230, 255)]
CRY_T = [(14, 70, 96), (30, 140, 160), (96, 214, 220), (210, 250, 250)]
# Chão (mais escuro que as plataformas: não compete com elas)
G = [(10, 6, 26), (22, 16, 48), (38, 30, 76), (60, 50, 112), (94, 82, 152), (140, 126, 194), (180, 168, 222)]


def slab_module(w, ramp, rng, crystal=None, crack=False, chips=(), under_seed=0):
    """Um pedaço da laje (w x 16): topo gasto e aceso, corpo em blocos, base
    irregular de rocha flutuante (termina em pontas)."""
    h = 16
    a = px.canvas(w, h)
    # base irregular: cada coluna termina entre as linhas 11 e 15
    r2 = np.random.default_rng(under_seed)
    bottom = np.full(w, 15)
    for x in range(w):
        bottom[x] = 15 - int(r2.integers(0, 4)) if (x // 3) % 2 else 15 - int(r2.integers(0, 2))
    # sem degraus de 1 coluna soltos
    for x in range(1, w - 1):
        if bottom[x - 1] == bottom[x + 1] != bottom[x]:
            bottom[x] = bottom[x - 1]
    body = np.zeros((h, w), dtype=bool)
    for x in range(w):
        body[0:bottom[x] + 1, x] = True
    px.fill(a, body, ramp[2])
    # faixa de rocha de baixo (mais escura) e contorno da base
    under = body & (np.mgrid[0:h, 0:w][0] >= 10)
    px.fill(a, under, ramp[1])
    base_edge = body & ~px.shift(body, 0, -1)
    px.fill(a, base_edge, ramp[0])
    # blocos do corpo (linhas 3-9): juntas verticais em posições próprias
    joints = sorted(set(int(v) for v in r2.integers(3, max(4, w - 3), size=max(1, w // 8)))) if w >= 8 else []
    for j in joints:
        a[3:10, j] = px.rgba(ramp[1])
        a[3:10, j + 1] = px.rgba(ramp[3])          # aresta acesa depois da junta
    a[9, :] = px.rgba(ramp[1])                     # junta de baixo dos blocos
    # topo: 3 linhas (aresta, face de cima, sombra da face)
    a[0, :] = px.rgba(ramp[5])
    a[1, :] = px.rgba(ramp[4])
    a[2, :] = px.rgba(ramp[3])
    # desgaste: lascas no topo (um vão de 2-3 px que mostra a pedra de baixo)
    for (x0, ln) in chips:
        a[0, x0:x0 + ln] = px.rgba(ramp[3])
        a[1, x0 + 1:x0 + ln - 1] = px.rgba(ramp[2])
    # textura de pedra: marcas horizontais curtas (2-3 px) em tom vizinho,
    # uma por bloco mais ou menos, nunca pixel solto
    for _ in range(w // 6):
        mx, my = int(r2.integers(1, max(2, w - 3))), int(r2.integers(4, 8))
        if a[my, mx, 0] == ramp[2][0] and a[my, mx + 1, 0] == ramp[2][0]:
            a[my, mx:mx + 2] = px.rgba(ramp[3] if (mx + my) % 2 else ramp[1])
    # pontas de rocha pendentes (estalactites de 2 px de largura)
    if w >= 12:
        sx = int(r2.integers(2, w - 4))
        for y in range(11, 16):
            a[y, sx:sx + 2] = px.rgba(ramp[1])
        a[15, sx:sx + 2] = px.rgba(ramp[0])
        a[11:15, sx + 1] = px.rgba(ramp[0])
    if crack:
        cx = w // 2 + int(r2.integers(-3, 3))
        for y, dx in zip(range(3, 10), [0, 0, 1, 1, 1, 2, 2]):
            a[y, cx + dx] = px.rgba(ramp[0])
    if crystal is not None:
        # cristal incrustado (losango alto 3x5) com brilho na ponta
        cx = int(r2.integers(3, w - 4))
        m = px.poly_mask(w, h, [(cx + 1.5, 3), (cx + 3, 6), (cx + 1.5, 9), (cx, 6)])
        px.fill(a, m, crystal[1])
        px.fill(a, m & (np.mgrid[0:h, 0:w][1] <= cx + 1), crystal[2])
        a[4, cx + 1] = px.rgba(crystal[3])
        px.fill(a, px.outer_edge(m) & body & (np.mgrid[0:h, 0:w][0] > 2), crystal[0])
    return a


def cap(ramp, left=True):
    """Ponta da laje (6x16): quina de cima chanfrada, base afinando."""
    w, h = 6, 16
    a = px.canvas(w, h)
    rows = [(2, 6), (1, 6), (0, 6), (0, 6), (0, 6), (0, 6), (0, 6), (0, 6), (0, 6), (0, 6),
            (1, 6), (1, 6), (2, 6), (3, 6), (4, 6)]
    m = np.zeros((h, w), dtype=bool)
    for y, (x0, x1) in enumerate(rows):
        m[y, x0:x1] = True
    if not left:
        m = m[:, ::-1]
    px.fill(a, m, ramp[2])
    yy = np.mgrid[0:h, 0:w][0]
    px.fill(a, m & (yy >= 10), ramp[1])
    px.fill(a, m & (yy == 9), ramp[1])
    top = m & ~px.shift(m, 0, 1)
    px.fill(a, m & (yy == 1), ramp[4])
    px.fill(a, m & (yy == 2), ramp[3])
    px.fill(a, top, ramp[5])
    side = m & ~px.shift(m, 1 if left else -1, 0) & (yy > 0)
    # lado de fora: aceso na esquerda (luz), sombra na direita
    px.fill(a, side & (yy < 10), ramp[4] if left else ramp[1])
    px.fill(a, m & ~px.shift(m, 0, -1), ramp[0])
    px.fill(a, side & (yy >= 10), ramp[0])
    if not left:
        px.fill(a, side & (yy < 10), ramp[1])
    return a


def platform_set(prefix, ramp, crystal, seed):
    rng = np.random.default_rng(seed)
    mods = {
        "mod_a": slab_module(16, ramp, rng, chips=[(5, 3)], under_seed=seed + 1),
        "mod_b": slab_module(16, ramp, rng, crystal=crystal, under_seed=seed + 2),
        "mod_c": slab_module(16, ramp, rng, crack=True, chips=[(11, 2)], under_seed=seed + 3),
        "mod_d": slab_module(12, ramp, rng, chips=[(2, 3)], under_seed=seed + 4),
        "mod_e": slab_module(12, ramp, rng, crystal=crystal, crack=False, under_seed=seed + 5),
    }
    out = {}
    for k, v in mods.items():
        out[f"{prefix}_{k}"] = v
    out[f"{prefix}_capl"] = cap(ramp, True)
    out[f"{prefix}_capr"] = cap(ramp, False)
    fill = slab_module(1, ramp, rng, under_seed=seed + 9)
    fill[11:, 0] = px.rgba(ramp[1])
    fill[15, 0] = px.rgba(ramp[0])
    out[f"{prefix}_fill"] = fill
    return out


def ground_tile(variant, seed):
    """Chão 32x32: lajotas gastas em cima (luz de cima), duas fiadas de pedra
    embaixo. Colunas 0 e 31 iguais em todas as variantes (emenda livre)."""
    w, h = 32, 32
    a = px.canvas(w, h)
    rng = np.random.default_rng(seed)
    a[:, :] = px.rgba(G[2])
    # lajotas: linhas 0-8; juntas: x=0 sempre (emenda) + 1 ou 2 internas
    inner = {0: [11, 22], 1: [14], 2: [9, 20], 3: [17], 4: [7, 19, 26]}[variant]
    joints = [0] + inner
    for j in joints:
        a[0:9, j] = px.rgba(G[0])
        if j + 1 < w:
            a[1:8, j + 1] = px.rgba(G[3])
    a[8, :] = px.rgba(G[0])
    a[0, :] = px.rgba(G[5])
    a[1, :] = px.rgba(G[4])
    for j in joints:
        a[0, j] = px.rgba(G[1])
        a[1, j] = px.rgba(G[1])
    a[2:8, :][a[2:8, :, 0] == G[2][0]] = px.rgba(G[3])
    a[7, :][a[7, :, 0] == G[3][0]] = px.rgba(G[2])   # sombra de baixo da lajota
    # desgaste: lascas no topo, variando por variante
    for (x0, ln) in {0: [(4, 3), (26, 2)], 1: [(20, 4)], 2: [(13, 2), (28, 2)], 3: [(6, 3)], 4: [(10, 2), (22, 3)]}[variant]:
        a[0, x0:x0 + ln] = px.rgba(G[3])
        a[1, x0 + 1:x0 + ln - 1] = px.rgba(G[2])
    # fiada 1 (linhas 9-19): blocos grandes; junta vertical nas bordas em x=31
    for j in [31] + [{0: 15, 1: 8, 2: 21, 3: 12, 4: 16}[variant]]:
        a[9:20, j] = px.rgba(G[0])
    a[9, :] = px.rgba(G[3])
    a[19, :] = px.rgba(G[0])
    a[10:19, 0] = px.rgba(G[3])
    # fiada 2 (linhas 20-31): mais escura; junta no meio e em x=0
    a[20:32, :][a[20:32, :, 0] == G[2][0]] = px.rgba(G[1])
    for j in [{0: 6, 1: 24, 2: 11, 3: 27, 4: 4}[variant]]:
        a[20:32, j] = px.rgba(G[0])
    a[20, :] = px.rgba(G[2])
    a[31, :] = px.rgba(G[0])
    # detalhes por variante: rachadura, cristal, pedrinhas
    if variant in (1, 4):
        cx = {1: 22, 4: 27}[variant]
        for y, dx in zip(range(2, 8), [0, 1, 1, 2, 2, 3]):
            if cx + dx < w - 1:
                a[y, cx + dx] = px.rgba(G[1])
    if variant in (2, 3):
        cx, cy = {2: (5, 12), 3: (22, 13)}[variant]
        m = px.poly_mask(w, h, [(cx + 1.5, cy), (cx + 3, cy + 3), (cx + 1.5, cy + 6), (cx, cy + 3)])
        px.fill(a, m, CRY_V[1])
        px.fill(a, m & (np.mgrid[0:h, 0:w][1] <= cx + 1), CRY_V[2])
        a[cy + 1, cx + 1] = px.rgba(CRY_V[3])
        px.fill(a, px.outer_edge(m), CRY_V[0])
    if variant == 0:
        for (x, y) in [(20, 14), (8, 25)]:
            a[y, x:x + 2] = px.rgba(G[3])
            a[y + 1, x:x + 2] = px.rgba(G[1])
    # textura: marcas curtas nas lajotas e nas fiadas (tom vizinho, 2-3 px)
    for _ in range(6):
        mx, my = int(rng.integers(2, 29)), int(rng.choice([3, 4, 5, 12, 14, 16, 23, 26, 28]))
        base = tuple(int(v) for v in a[my, mx, :3])
        if base in (G[3], G[2], G[1]) and tuple(int(v) for v in a[my, mx + 1, :3]) == base:
            k = G.index(base)
            a[my, mx:mx + 2] = px.rgba(G[k + 1] if my < 9 else G[k - 1])
    return a


def main():
    ARCHIVE.mkdir(parents=True, exist_ok=True)
    old = ["platform_cap", "platform_module", "platform_fill", "platform_alt_cap", "platform_alt_module",
           "platform_alt_fill", "ground"]
    for name in old:
        f, dst = OUT / f"{name}.png", ARCHIVE / f"{name}.png"
        if f.exists() and not dst.exists():
            shutil.copy2(f, dst)
    sets = {}
    sets.update(platform_set("platform", P, CRY_V, 40))
    sets.update(platform_set("platform_alt", Q, CRY_T, 80))
    for name, img in sets.items():
        px.save(img, OUT / f"{name}.png")
    grounds = [ground_tile(v, 100 + v) for v in range(5)]
    # as colunas das bordas têm de ser iguais em todas as variantes
    for g in grounds[1:]:
        assert (g[:, 0] == grounds[0][:, 0]).all() and (g[:, 31] == grounds[0][:, 31]).all(), "borda do chão diverge"
    for v, g in enumerate(grounds):
        px.save(g, OUT / f"ground_{'abcde'[v]}.png")
    # prova: folhas e uma faixa de chão montada na ordem que o jogo usa
    PROOF.mkdir(parents=True, exist_ok=True)
    order = [ground_variant(i) for i in range(12)]
    strip = np.concatenate([grounds[o] for o in order], axis=1)
    plat = np.concatenate([sets["platform_capl"], sets["platform_mod_a"], sets["platform_mod_b"],
                           sets["platform_mod_c"], sets["platform_mod_d"], sets["platform_capr"]], axis=1)
    plat_alt = np.concatenate([sets["platform_alt_capl"], sets["platform_alt_mod_b"], sets["platform_alt_mod_e"],
                               sets["platform_alt_mod_c"], sets["platform_alt_capr"]], axis=1)
    for bgname, bg in [("fundo_jogo", GAME_BG), ("fundo_claro", LIGHT_BG)]:
        for k in (1, 4):
            px.save(px.contact_sheet([plat, plat_alt], bg, scale=k, cols=1), PROOF / f"plataformas_{k}x_{bgname}.png")
            px.save(px.contact_sheet(grounds, bg, scale=k), PROOF / f"chao_variantes_{k}x_{bgname}.png")
            px.save(px.contact_sheet([strip], bg, scale=k), PROOF / f"chao_faixa_{k}x_{bgname}.png")
    print("mundo rodada 3 gerado")


def ground_variant(i):
    """Mesma escolha do jogo (GameScreen.groundVariant): hash do índice do ladrilho."""
    h = (i * 0x9E3779B1) & 0xFFFFFFFF
    h ^= h >> 15
    return h % 5


if __name__ == "__main__":
    main()
