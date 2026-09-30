"""Antes/depois + heatmap de diferenças entre duas pastas de capturas.

Uso: python tools/qa_heatmap.py <antes> <depois> <saida> [--regions nome:x0,y0,x1,y1 ...]
Para cada PNG com o mesmo nome nas duas pastas grava em <saida>:
- <nome>_antes_depois.png (lado a lado, metade do tamanho);
- <nome>_heatmap.png (depois em cinza escuro + pixels alterados em vermelho,
  mais forte = diferença maior);
e imprime/grava relatorio.txt com a fração de pixels alterados por faixa de
altura (linhas de 1080) e por região nomeada.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image


def main():
    a_dir, b_dir, out = (Path(p) for p in sys.argv[1:4])
    regions = {}
    args = sys.argv[4:]
    if args and args[0] == "--regions":
        for spec in args[1:]:
            name, box = spec.split(":")
            regions[name] = tuple(int(v) for v in box.split(","))
    out.mkdir(parents=True, exist_ok=True)
    lines = []
    for fa in sorted(a_dir.glob("*.png")):
        fb = b_dir / fa.name
        if not fb.exists():
            continue
        a = np.array(Image.open(fa).convert("RGB")).astype(int)
        b = np.array(Image.open(fb).convert("RGB")).astype(int)
        diff = np.abs(a - b).sum(2)
        changed = diff > 0
        gray = (b.mean(2) * 0.35).astype(np.uint8)
        heat = np.stack([gray, gray, gray], 2)
        inten = np.clip(80 + diff / 3, 0, 255).astype(np.uint8)
        heat[changed] = np.stack([inten, np.zeros_like(inten), np.zeros_like(inten)], 2)[changed]
        stem = fa.stem
        Image.fromarray(heat).save(out / f"{stem}_heatmap.png")
        side = np.concatenate([a, np.full((a.shape[0], 16, 3), 255), b], 1).astype(np.uint8)
        Image.fromarray(side).resize((side.shape[1] // 2, side.shape[0] // 2), Image.NEAREST).save(
            out / f"{stem}_antes_depois.png")
        h = a.shape[0]
        lines.append(f"{fa.name}: {changed.mean() * 100:.2f}% dos pixels mudaram")
        ys = np.nonzero(changed.any(1))[0]
        if len(ys):
            lines.append(f"  linhas alteradas: {ys.min()}..{ys.max()} (de {h})")
        for y0 in range(0, h, h // 8):
            band = changed[y0:y0 + h // 8]
            lines.append(f"  faixa y {y0:4d}-{min(h, y0 + h // 8) - 1:4d}: {band.mean() * 100:6.2f}%")
        for name, (x0, y0, x1, y1) in regions.items():
            lines.append(f"  região {name}: {changed[y0:y1, x0:x1].mean() * 100:6.2f}%")
    text = "\n".join(lines)
    (out / "relatorio.txt").write_text(text + "\n", encoding="utf-8")
    print(text)


if __name__ == "__main__":
    main()
