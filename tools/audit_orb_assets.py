"""Audita assets/sprites/ contra o manifest.json gravado por build_orb_assets.py.

Checa:
- todo arquivo listado existe, é RGBA e não está vazio;
- todos os frames de uma animação têm o mesmo canvas (e o canvas do manifesto);
- a contagem de frames no disco bate com o manifesto (o jogo lê por prefixo);
- os pontos fracos que o jogo calcula (raio dos marcadores em EnemyType) caem
  sobre pixels de marcador magenta desenhados no frame idle_01 de cada inimigo;
- a baseline do manifesto bate com a linha mais baixa ocupada em repouso.

Uso: python tools/audit_orb_assets.py   (código de saída 1 se houver erro)
"""

import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SPRITES = ROOT / "assets" / "sprites"
MAG = np.array([205, 69, 231])

m = json.loads((SPRITES / "manifest.json").read_text(encoding="utf-8"))
errors = []


def rgba(rel):
    return np.array(Image.open(SPRITES / rel).convert("RGBA")).astype(int)


for rel in m["files"]:
    path = SPRITES / rel
    if not path.is_file():
        errors.append(f"faltando: {rel}")
        continue
    with Image.open(path) as im:
        if im.mode != "RGBA":
            errors.append(f"não é RGBA: {rel}")
        if im.getchannel("A").getbbox() is None:
            errors.append(f"vazio: {rel}")


def check_anims(prefix, info):
    for anim, count in info["frames"].items():
        on_disk = sorted(SPRITES.glob(f"{prefix}/{anim}_[0-9][0-9].png"))
        if len(on_disk) != count:
            errors.append(f"{prefix}/{anim}: manifesto diz {count} frames, disco tem {len(on_disk)}")
        sizes = {Image.open(p).size for p in on_disk}
        if sizes != {(info["canvas"], info["canvas"])}:
            errors.append(f"{prefix}/{anim}: canvas {sizes} != {info['canvas']}")


def lowest_rest_row(prefix):
    low = 0
    for p in list(SPRITES.glob(f"{prefix}/idle_*.png")) + list(SPRITES.glob(f"{prefix}/move_*.png")):
        a = np.array(Image.open(p).convert("RGBA"))
        ys = np.nonzero(a[..., 3] >= 128)[0]
        low = max(low, int(ys.max()))
    return low


def weak_points(kind, info):
    r = info["weak_r_px"]
    n, start = {"triangle": (3, 90), "square": (4, 0), "diamond": (4, 0), "hexagon": (6, 90),
                "boss": (6, 90)}[kind]
    return [(r * math.cos(math.radians(start + i * 360 / n)), r * math.sin(math.radians(start + i * 360 / n)))
            for i in range(n)]


report = {}
check_anims("player", m["player"])
for kind, info in list(m["enemies"].items()) + [("boss", m["boss"])]:
    prefix = "boss" if kind == "boss" else f"enemies/{kind}"
    check_anims(prefix, info)
    low = lowest_rest_row(prefix)
    if info["canvas"] - 1 - low != info["baseline_px"]:
        errors.append(f"{kind}: baseline {info['baseline_px']} != medida {info['canvas'] - 1 - low}")
    if kind in ("circle", "pentagon"):
        continue  # arte gerada, sem EnemyType no jogo (ainda)
    a = rgba(f"{prefix}/idle_01.png")
    cx, cy = info["center_px"]
    hits = []
    for lx, ly in weak_points(kind, info):
        x, y = cx + lx, cy - ly
        # procura pixel de marcador magenta num raio de 5 px do ponto fraco
        x0, x1 = int(x - 5), int(x + 6)
        y0, y1 = int(y - 5), int(y + 6)
        win = a[max(0, y0):y1, max(0, x0):x1]
        near = np.abs(win[..., :3] - MAG).sum(axis=2) < 90
        hits.append(bool(near.any()))
        if not near.any():
            errors.append(f"{kind}: ponto fraco em ({x:.1f},{y:.1f}) sem marcador desenhado por perto")
    report[kind] = f"{sum(hits)}/{len(hits)} pontos fracos sobre marcadores"

for k, v in report.items():
    print(f"{k:9s} {v}")
print(f"{len(m['files'])} arquivos auditados, {len(errors)} erro(s)")
for e in errors:
    print("  ERRO:", e)
sys.exit(1 if errors else 0)
