"""Folha de contato rotulada para o portão visual das fases de arte.

Cada linha é um rótulo + uma sequência de PNGs de assets/sprites/, ampliados
em escala inteira (nearest) sobre dois fundos: o roxo escuro do jogo e um
fundo claro (para conferir contorno e legibilidade nos dois extremos).

Uso:
    py tools/contact_sheet.py SAIDA.png ZOOM "rótulo=glob" ["rótulo=glob" ...]
    ex.: py tools/contact_sheet.py docs/qa/fase1/coracao.png 8 "cheio=ui/heart/full_*.png"
"""

import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SPRITES = ROOT / "assets" / "sprites"
BACKS = [(28, 22, 64, 255), (214, 210, 226, 255)]   # roxo do jogo, claro
LABEL_W = 170
PAD = 8


def build(out, zoom, rows):
    lines = []
    for label, pattern in rows:
        files = sorted(SPRITES.glob(pattern))
        if not files:
            raise SystemExit(f"nada encontrado para {pattern}")
        lines.append((label, [Image.open(f).convert("RGBA") for f in files]))
    cell_h = max(im.height for _, ims in lines for im in ims) * zoom + PAD * 2
    half_w = max(sum(im.width * zoom + PAD for im in ims) for _, ims in lines) + PAD
    sheet = Image.new("RGBA", (LABEL_W + half_w * 2, cell_h * len(lines)), (16, 14, 24, 255))
    draw = ImageDraw.Draw(sheet)
    for row, (label, ims) in enumerate(lines):
        y = row * cell_h
        draw.text((PAD, y + cell_h // 2 - 6), label, fill=(235, 232, 245, 255))
        for side, back in enumerate(BACKS):
            x0 = LABEL_W + side * half_w
            draw.rectangle([x0, y + 1, x0 + half_w - 2, y + cell_h - 2], fill=back)
            x = x0 + PAD
            for im in ims:
                big = im.resize((im.width * zoom, im.height * zoom), Image.NEAREST)
                sheet.alpha_composite(big, (x, y + (cell_h - big.height) // 2))
                x += big.width + PAD
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    print(f"folha de contato: {out}")


if __name__ == "__main__":
    if len(sys.argv) < 4:
        raise SystemExit(__doc__)
    build(sys.argv[1], int(sys.argv[2]), [a.split("=", 1) for a in sys.argv[3:]])
