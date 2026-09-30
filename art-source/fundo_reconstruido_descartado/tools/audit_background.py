"""Teste automático do fundo (item 7 / Fase 5). Sai com código 1 se falhar.

Para cada camada de assets/sprites/background/:
- EMENDA: a diferença entre a última e a primeira coluna (onde o jogo emenda
  a repetição) não pode passar do percentil 99 das diferenças entre colunas
  vizinhas da própria camada — ou seja, a emenda é uma coluna como outra qualquer;
- PALETA: só cores de BG_PALETTE (8 tons), alfa 0/255;
- PERÍODO: camadas com estruturas grandes têm período maior que a tela (480),
  então nenhuma estrutura grande aparece duas vezes na mesma tela; períodos
  diferentes entre si;
- grava docs/qa/fundo/emendas/<camada>.png com 3 repetições emendadas (4x).
Uso: py tools/audit_background.py
"""

import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import orb_background as bg  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "assets" / "sprites" / "background"
OUT = ROOT / "docs" / "qa" / "fundo" / "emendas"
BIG = {"02_montanhas", "04_cristais", "05_ruinas", "08_frente"}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    errors, periods = [], {}
    pal = set(bg.BG_PALETTE)
    for name, _ in bg.LAYERS:
        a = np.array(Image.open(SRC / f"{name}.png").convert("RGBA"))
        w = a.shape[1]
        seam, p99, _ = bg.seam_report(a)
        cols = {tuple(int(v) for v in c) for c in a[a[..., 3] > 0][:, :3]}
        alpha = set(np.unique(a[..., 3]).tolist())
        ok = seam <= p99 + 1e-6
        if not ok:
            errors.append(f"{name}: emenda {seam} > p99 vizinhas {p99:.0f}")
        if cols - pal:
            errors.append(f"{name}: {len(cols - pal)} cor(es) fora da paleta")
        if not alpha <= {0, 255}:
            errors.append(f"{name}: alfa parcial")
        if name in BIG and w <= bg.SCREEN_W:
            errors.append(f"{name}: período {w} <= tela {bg.SCREEN_W} (estrutura grande repetiria)")
        if name != "00_ceu":
            periods.setdefault(w, []).append(name)
        tile = Image.fromarray(a, "RGBA")
        strip = Image.new("RGBA", (w * 3, a.shape[0]), (128, 128, 128, 255))
        for k in range(3):
            strip.alpha_composite(tile, (k * w, 0))
        strip.resize((w * 3 * 2, a.shape[0] * 2), Image.NEAREST).save(OUT / f"{name}.png")
        print(f"{'OK ' if ok else 'ERRO'} {name:15s} período {w:4d}  emenda {seam:6d}  p99 vizinhas {p99:8.0f}")
    for w, names in periods.items():
        if len(names) > 1:
            errors.append(f"período {w} repetido em {names}")
    for e in errors:
        print("ERRO:", e)
    print("FUNDO:", "PASS" if not errors else "FAIL")
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
