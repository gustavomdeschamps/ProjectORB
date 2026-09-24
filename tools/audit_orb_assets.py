"""Validate every exported animation frame and the manifest used by LibGDX."""

import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "assets"
parser = argparse.ArgumentParser()
parser.add_argument("--remade", action="store_true")
args = parser.parse_args()
OUTPUT_NAME = "processed_remade" if args.remade else "processed"
manifest = json.loads((ASSETS / OUTPUT_NAME / "manifest.json").read_text(encoding="utf-8"))
errors = []
report = {}
frame_count = 0
world_count = 0

for actor in ("player", "triangle", "square", "diamond", "hexagon", "boss"):
    report[actor] = {}
    for animation, spec in manifest[actor].items():
        metrics = []
        expected_size = tuple(spec["canvas"])
        for frame_path in spec["frames"]:
            path = ASSETS / frame_path
            if not path.is_file():
                errors.append(f"Missing: {frame_path}")
                continue
            with Image.open(path) as image:
                if image.mode != "RGBA" or image.size != expected_size:
                    errors.append(f"Wrong format or size: {frame_path}")
                alpha = np.asarray(image.getchannel("A"))
                count = int(np.count_nonzero(alpha))
                ys, xs = np.nonzero(alpha)
                bounds = [int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())] if count else None
                if not count:
                    errors.append(f"Empty frame: {frame_path}")
                elif animation != "death" and count < (1500 if actor == "boss" else 500):
                    errors.append(f"Likely missing character: {frame_path} ({count} pixels)")
                if bounds and (bounds[0] < 2 or bounds[1] < 2 or bounds[2] >= image.width - 2 or bounds[3] >= image.height - 2):
                    errors.append(f"Touches canvas edge: {frame_path} {bounds}")
                metrics.append({"frame": frame_path, "opaque_pixels": count, "bounds": bounds})
                frame_count += 1
        report[actor][animation] = metrics

for name, frame_path in manifest["world"].items():
    path = ASSETS / frame_path
    if not path.is_file():
        errors.append(f"Missing world asset: {name}: {frame_path}")
        continue
    with Image.open(path) as image:
        if image.mode != "RGBA" or image.getchannel("A").getbbox() is None:
            errors.append(f"Invalid world asset: {name}: {frame_path}")
        world_count += 1

output = {
    "frames_checked": frame_count,
    "world_assets_checked": world_count,
    "errors": errors,
    "animations": report,
}
(ASSETS / OUTPUT_NAME / "audit.json").write_text(json.dumps(output, indent=2), encoding="utf-8")

# A compact contact sheet makes adjacent-cell contamination visible to a human.
world_items = list(manifest["world"].items())
cell_w, cell_h, columns = 220, 200, 5
contact = Image.new("RGB", (cell_w * columns, cell_h * ((len(world_items) + columns - 1) // columns)), (15, 18, 32))
draw = ImageDraw.Draw(contact)
for index, (name, relative) in enumerate(world_items):
    with Image.open(ASSETS / relative) as image:
        rgba = image.convert("RGBA")
        rgba.thumbnail((196, 158), Image.Resampling.NEAREST)
        x = (index % columns) * cell_w
        y = (index // columns) * cell_h
        contact.paste(rgba, (x + (cell_w - rgba.width) // 2, y + 8), rgba)
        draw.text((x + 8, y + 174), name, fill=(220, 232, 255))
contact.save(ASSETS / OUTPUT_NAME / "world_contact.png")
print(f"Checked {frame_count} animation frames and {world_count} world assets; {len(errors)} errors")
for error in errors:
    print(error)
raise SystemExit(1 if errors else 0)
