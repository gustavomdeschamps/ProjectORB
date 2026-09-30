"""Rodada 3, etapa 5: o Pi do jogo passa a ser o π decalcado da referência.

Fonte: tools/sprite_grids/pi.txt (silhueta decalcada de docs/ref/pi_referencia.png)
e tools/orb_pi.py (pintura, rosto, braços, poses). Este script:
- estende as animações ao mínimo pedido (idle 6, walk 8, appear 8, talk 4,
  wave 6, point 4, cheer 6) com as pernas curvas se movendo;
- grava assets/sprites/npc/pi/*.png + portrait.png (o Pi antigo, "mesa laranja",
  vai para art-source/descartado_rodada3/pi_antigo/);
- grava as provas em docs/qa/rodada3/etapa5/ (folhas 1x/4x, GIFs, grade em texto).
"""
from __future__ import annotations

import shutil
from pathlib import Path

import numpy as np

import orb_pi
import pxlib as px

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "assets/sprites/npc/pi"
ARCHIVE = ROOT / "art-source/descartado_rodada3/pi_antigo"
PROOF = ROOT / "docs/qa/rodada3/etapa5"
GAME_BG = (22, 18, 92)
LIGHT_BG = (225, 222, 240)


def anims():
    F = orb_pi.frame
    a = orb_pi.anims()
    # idle 6: respira (sobe 1 px), pernas balançam de leve, pisca uma vez
    a["idle"] = [F(), F(legs=(0, 1, 0, 0)), F(dy=1, legs=(1, 1, 0, 0)), F(dy=1, legs=(1, 0, 0, 0)),
                 F(legs=(0, 0, 0, 0)), F(eyes="blink")]
    # walk 8: passo com as pernas curvas (uma pisa, a outra levanta), quique no contato
    walk = [(0, 0, 0, 0), (2, -1, 2, 0), (3, -1, 1, 0), (1, 0, 0, 0),
            (0, 0, 0, 0), (-1, 2, 0, 2), (-1, 3, 0, 1), (0, 1, 0, 0)]
    a["walk"] = [F(dy=(1 if i in (0, 4) else 0), legs=walk[i],
                   arms=("rest", "rest_r") if i % 4 else ("up", "rest_r")) for i in range(8)]
    return a


def to_rgba_all(a):
    return {k: [orb_pi.to_rgba(g) for g in v] for k, v in a.items()}


def main():
    ARCHIVE.mkdir(parents=True, exist_ok=True)
    for f in OUT.glob("*.png"):
        dst = ARCHIVE / f.name
        if not dst.exists():
            shutil.copy2(f, dst)
        f.unlink()
    rgba = to_rgba_all(anims())
    for name, frames in rgba.items():
        for i, f in enumerate(frames, 1):
            px.save(f, OUT / f"{name}_{i:02d}.png")
    portrait = orb_pi.to_rgba(orb_pi.portrait(orb_pi.frame()))
    px.save(portrait, OUT / "portrait.png")

    PROOF.mkdir(parents=True, exist_ok=True)
    durations = {"idle": [220, 180, 160, 180, 220, 90], "walk": [90, 80, 100, 80, 90, 80, 100, 80],
                 "talk": 110, "wave": [90, 80, 120, 80, 120, 100], "point": [100, 90, 140, 120],
                 "cheer": [80, 90, 140, 90, 110, 160], "appear": [70, 70, 80, 80, 90, 90, 110, 160]}
    for name, frames in rgba.items():
        for bgname, bg in [("fundo_jogo", GAME_BG), ("fundo_claro", LIGHT_BG)]:
            for k in (1, 4):
                px.save(px.contact_sheet(frames, bg, scale=k), PROOF / f"pi_{name}_{k}x_{bgname}.png")
        px.save_gif(frames, durations[name], PROOF / f"pi_{name}.gif", GAME_BG, scale=4)
    for bgname, bg in [("fundo_jogo", GAME_BG), ("fundo_claro", LIGHT_BG)]:
        px.save(px.contact_sheet([portrait], bg, scale=4), PROOF / f"pi_retrato_4x_{bgname}.png")
    grid = "\n".join("".join("#" if v else "." for v in row) for row in orb_pi.silhouette())
    (PROOF / "grade_pi.txt").write_text(grid + "\n", encoding="utf-8")
    print({k: len(v) for k, v in rgba.items()})


if __name__ == "__main__":
    main()
