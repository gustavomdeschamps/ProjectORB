"""Audita assets/sprites/ contra o manifest.json gravado por build_orb_assets.py.

Checa:
- todo arquivo listado existe, é RGBA, não está vazio e tem alfa binário
  (0/255: pixel art de verdade, sem franja antialiasada);
- a contagem de frames no disco bate com o manifesto (o jogo lê por prefixo)
  e todos os frames de uma animação têm o canvas do manifesto;
- o ORB só usa cores da paleta do idle canônico;
- os pontos fracos que o jogo calcula (EnemyType: raio dos marcadores)
  caem sobre pixels de marcador magenta desenhados em idle_01;
- os números de EnemyType.java (canvas, baseline, raios) batem com o manifesto.

Uso: python tools/audit_orb_assets.py   (código de saída 1 se houver erro)
"""

import json
import math
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SPRITES = ROOT / "assets" / "sprites"
ENEMY_JAVA = ROOT / "core/src/main/java/com/delmartec/projectorb/entities/EnemyType.java"
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
    a = rgba(rel)
    if a[..., 3].max() == 0:
        errors.append(f"vazio: {rel}")
    if rel.startswith("background/"):
        continue  # camadas oficiais do TCC, desenhadas com meios-tons próprios
    if not np.isin(a[..., 3], (0, 255)).all():
        errors.append(f"alfa não binário: {rel}")


def check_anims(prefix, info):
    for anim, count in info["frames"].items():
        on_disk = sorted(SPRITES.glob(f"{prefix}/{anim}_[0-9][0-9].png"))
        if len(on_disk) != count:
            errors.append(f"{prefix}/{anim}: manifesto diz {count} frames, disco tem {len(on_disk)}")
        sizes = {Image.open(p).size for p in on_disk}
        if sizes != {(info["canvas"], info["canvas"])}:
            errors.append(f"{prefix}/{anim}: canvas {sizes} != {info['canvas']}")


check_anims("player", m["player"])
pal = {tuple(c) for c in m["orb_palette"]}
for p in sorted(SPRITES.glob("player/*.png")):
    a = rgba(p.relative_to(SPRITES).as_posix())
    extra = {tuple(int(v) for v in c) for c in a[a[..., 3] > 0][:, :3]} - pal
    if extra:
        errors.append(f"{p.name}: {len(extra)} cor(es) fora da paleta do ORB")

java = ENEMY_JAVA.read_text(encoding="utf-8")
rows = {name: [float(v) for v in vals.split(",")] for name, vals in
        re.findall(r"^\s*(TRIANGLE|SQUARE|DIAMOND|HEXAGON|BOSS)\s*\(([\d.,\s f]+?),\s*\"", java, re.M)
        for vals in [vals.replace("f", "")]}
names = {"triangle": "TRIANGLE", "square": "SQUARE", "diamond": "DIAMOND", "hexagon": "HEXAGON", "boss": "BOSS"}

report = {}
for kind, info in list(m["enemies"].items()) + [("boss", m["boss"])]:
    prefix = "boss" if kind == "boss" else f"enemies/{kind}"
    check_anims(prefix, info)
    if kind not in names:
        continue  # círculo e pentágono: arte pronta, sem EnemyType (decisão da Fase 0)
    canvas_, baseline, sides, start, marker_r, outer_r = rows[names[kind]]
    expect = (info["canvas"], info["baseline_px"], info["sides"], info["start_deg"], info["marker_r_px"],
              info["outer_r_px"])
    got = (canvas_, baseline, sides, start, marker_r, outer_r)
    if any(abs(g - e) > 0.01 for g, e in zip(got, expect)):
        errors.append(f"EnemyType.{names[kind]} {got} != manifesto {expect}")
    a = rgba(f"{prefix}/idle_01.png")
    c = info["canvas"] / 2
    n, st = {"triangle": (3, 90), "square": (4, 0), "diamond": (4, 0), "hexagon": (6, 90), "boss": (6, 90)}[kind]
    hits = 0
    for i in range(n):
        ang = math.radians(st + i * 360 / n)
        x, y = c + marker_r * math.cos(ang), c - marker_r * math.sin(ang)
        win = a[int(y - 3):int(y + 4), int(x - 3):int(x + 4)]
        if (np.abs(win[..., :3] - MAG).sum(axis=2) < 30).any():
            hits += 1
        else:
            errors.append(f"{kind}: ponto fraco em ({x:.1f},{y:.1f}) sem marcador desenhado")
    report[kind] = f"{hits}/{n} pontos fracos sobre marcadores"

for k, v in report.items():
    print(f"{k:9s} {v}")
print(f"{len(m['files'])} arquivos auditados, {len(errors)} erro(s)")
for e in errors:
    print("  ERRO:", e)
sys.exit(1 if errors else 0)
