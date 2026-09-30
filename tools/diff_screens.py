"""Diff de imagem das telas aprovadas: antes da rodada x agora.

Telas de menu (fundo do menu) são comparadas inteiras; telas de jogo são
comparadas só na região do HUD (canto superior esquerdo 420x160) e, na pausa/
códex, no painel central — o fundo do jogo muda de propósito na etapa A.
Uso: py tools/diff_screens.py PASTA_ANTES PASTA_DEPOIS
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HUD = (0, 0, 420, 160)
REGIONS = {
    "01-menu-selecionado": None, "02-menu-outro-selecionado": None, "03-menu-pressionado": None,
    "04-como-jogar": None, "05-como-jogar-teclas-acesas": None, "06-opcoes": None,
    "10-vitoria": None, "11-derrota": None,
    "07-pausa": (610, 170, 1310, 910), "08-pausa-pressionado": (610, 170, 1310, 910),
    "09-codex": (310, 120, 1610, 960),
    "01-hud-cheio": HUD, "03-hud-2-vidas": HUD, "02-quebra-1": HUD, "02-quebra-2": HUD,
    "02-quebra-3": HUD, "02-quebra-4": HUD,
    "05-vitoria": None, "06-derrota": None, "07-menu": None, "04-pausa": (610, 170, 1310, 910),
    "hud": None,
}


def main(a_dir, b_dir):
    bad = 0
    for pa in sorted(Path(a_dir).rglob("*.png")):
        name = pa.stem
        if name not in REGIONS:
            continue
        pb = Path(b_dir) / pa.relative_to(a_dir)
        a = Image.open(pa).convert("RGB")
        b = Image.open(pb).convert("RGB")
        box = REGIONS[name]
        if box:
            a, b = a.crop(box), b.crop(box)
        d = int((np.abs(np.array(a).astype(int) - np.array(b).astype(int)).sum(2) > 0).sum())
        print(f"{'IGUAL ' if d == 0 else 'DIFERE'} {pa.relative_to(a_dir)}  ({'região ' + str(box) if box else 'tela inteira'}; {d} px)")
        bad += d > 0
    print("TELAS APROVADAS:", "IDÊNTICAS" if bad == 0 else f"{bad} com diferença")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
