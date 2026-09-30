"""Sobrepõe as medidas da escadinha nas capturas (docs/qa/rodada3/etapa2/escadas).

Para cada escada: caixa da massa (fechada) ou da escada (aberta) em ciano,
faixas de folga de 3 larguras do ORB (174 px) em vermelho e as plataformas em
verde com a distância horizontal escrita. Lê escadas.txt (StairLayoutCapture)
e as plataformas de LevelDemo.java.
"""
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
D = ROOT / "docs/qa/rodada3/etapa2/escadas"
CLEAR = 174
FLOOR = 126


def platforms():
    src = (ROOT / "core/src/main/java/com/delmartec/projectorb/level/LevelDemo.java").read_text(encoding="utf-8")
    out = []
    for m in re.finditer(r"new Platform\((\d+), (\d+), (\d+), (\d+)\)", src):
        x, y, w, h = (int(v) for v in m.groups())
        if w < 5000:
            out.append((x, y, w, h))
    return out


def main():
    plats = platforms()
    rows = []
    for line in (D / "escadas.txt").read_text(encoding="utf-8").splitlines():
        g, *kv = line.split()
        vals = dict(k.split("=") for k in kv)
        cam = float(vals["camera"])
        mx, my, mw, mh = (float(v) for v in vals["massa"].split(","))
        el, er, top = (float(v) for v in vals["escada"].split(","))
        for state, (l, r, t) in [("fechada", (mx, mx + mw, my + mh)), ("aberta", (el, er, top))]:
            im = Image.open(D / f"escada{g}_{state}.png").convert("RGB")
            dr = ImageDraw.Draw(im, "RGBA")

            def sx(x):
                return x - cam + 960

            def sy(y):
                return 1080 - y

            dr.rectangle([sx(l - CLEAR), 0, sx(l), 1080], fill=(255, 40, 40, 38))
            dr.rectangle([sx(r), 0, sx(r + CLEAR), 1080], fill=(255, 40, 40, 38))
            dr.rectangle([sx(l), sy(t), sx(r), sy(FLOOR)], outline=(0, 255, 255, 255), width=3)
            for (x, y, w, h) in plats:
                if x + w < cam - 1100 or x > cam + 1100:
                    continue
                dr.rectangle([sx(x), sy(y + h), sx(x + w), sy(y)], outline=(60, 255, 90, 255), width=3)
                dx = max(0, max(x - r, l - (x + w)))
                dr.text((sx(x) + 6, sy(y + h) - 18), f"{dx:.0f} px", fill=(60, 255, 90, 255))
            dr.text((20, 20), f"escada {g} {state}: faixa vermelha = 3 larguras do ORB (174 px); "
                              f"verde = plataformas", fill=(255, 255, 255, 255))
            im.save(D / f"escada{g}_{state}_medidas.png")
            rows.append(im.resize((640, 360)))
    sheet = Image.new("RGB", (1280, 360 * 5))
    for i, im in enumerate(rows):
        sheet.paste(im, ((i % 2) * 640, (i // 2) * 360))
    sheet.save(D / "todas_as_escadas.png")
    print("ok", len(rows))


if __name__ == "__main__":
    sys.exit(main())
