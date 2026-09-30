"""Teste de semelhança do Pi com a referência (etapa D). Sai com código 1 se falhar.

Compara, na MESMA escala, a silhueta do idle do Pi SEM rosto e SEM braços
(tools/orb_pi.body_mask_idle, que vem da grade tools/sprite_grids/pi.txt)
com a referência docs/ref/pi_referencia.png reduzida AQUI, de forma
independente do gerador (limiar 128, caixa delimitadora, média de área para
a largura da silhueta, limiar 50%). Exige IoU >= 0,80 e grava
docs/qa/pi/sobreposicao.png (referência em vermelho, sprite em azul,
sobreposição em roxo), ampliada 8x.
Uso: py tools/audit_pi.py
"""

import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import orb_pi  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
MIN_IOU = 0.80


def reference_mask(width, height):
    im = Image.open(ROOT / "docs" / "ref" / "pi_referencia.png").convert("L")
    a = np.array(im) < 128
    ys, xs = np.nonzero(a)
    crop = im.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    return np.array(crop.resize((width, height), Image.BOX)) < 128


def main():
    sprite = orb_pi.body_mask_idle()
    ys, xs = np.nonzero(sprite)
    sprite = sprite[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    h, w = sprite.shape
    ref = reference_mask(w, h)
    inter = (sprite & ref).sum()
    union = (sprite | ref).sum()
    iou = inter / union
    out = np.zeros((h, w, 3), dtype=np.uint8)
    out[ref & ~sprite] = (230, 40, 40)
    out[sprite & ~ref] = (40, 90, 240)
    out[sprite & ref] = (150, 70, 200)
    dest = ROOT / "docs" / "qa" / "rodada3" / "etapa5"
    dest.mkdir(parents=True, exist_ok=True)
    Image.fromarray(out).resize((w * 8, h * 8), Image.NEAREST).save(dest / "sobreposicao.png")
    ok = iou >= MIN_IOU
    print(f"Pi x referência: IoU = {iou:.3f} (mínimo {MIN_IOU}) em {w}x{h} -> {'PASS' if ok else 'FAIL'}")
    # 2) o sprite exportado de verdade (idle_01.png): tira os braços (tudo que
    # fica fora da silhueta do π dilatada 1 px) e compara de novo
    spr = np.array(Image.open(ROOT / "assets/sprites/npc/pi/idle_01.png").convert("RGBA"))[:, :, 3] > 0
    sil = np.zeros_like(spr)
    g = orb_pi.silhouette()
    sil[orb_pi.OY:orb_pi.OY + g.shape[0], orb_pi.OX:orb_pi.OX + g.shape[1]] = g
    pad = np.pad(sil, 1)
    dil = sil | pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:]
    body = spr & dil
    ys, xs = np.nonzero(body)
    body = body[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    ref2 = reference_mask(body.shape[1], body.shape[0])
    iou2 = (body & ref2).sum() / (body | ref2).sum()
    ok2 = iou2 >= MIN_IOU
    print(f"idle_01.png (sem braços) x referência: IoU = {iou2:.3f} -> {'PASS' if ok2 else 'FAIL'}")
    ok = ok and ok2
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
