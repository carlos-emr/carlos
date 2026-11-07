#!/usr/bin/env python3
"""Compare base vs PR screenshots: exact pixel diff count, diff mask and side-by-side image.

usage: compare.py BASE_DIR PR_DIR OUT_DIR name1 [name2 ...]
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFont


def main() -> int:
    base_dir, pr_dir, out_dir = (Path(a) for a in sys.argv[1:4])
    out_dir.mkdir(parents=True, exist_ok=True)
    results = {}
    for name in sys.argv[4:]:
        a = Image.open(base_dir / f"{name}.png").convert("RGB")
        b = Image.open(pr_dir / f"{name}.png").convert("RGB")
        w, hgt = max(a.width, b.width), max(a.height, b.height)
        pa = Image.new("RGB", (w, hgt), "white"); pa.paste(a, (0, 0))
        pb = Image.new("RGB", (w, hgt), "white"); pb.paste(b, (0, 0))
        diff = ImageChops.difference(pa, pb)
        bbox = diff.getbbox()
        mask = diff.convert("L").point(lambda v: 255 if v else 0)
        changed = sum(1 for v in mask.getdata() if v)
        results[name] = {
            "base_size": [a.width, a.height], "pr_size": [b.width, b.height],
            "changed_pixels": changed, "changed_ratio": round(changed / (w * hgt), 6),
            "diff_bbox": list(bbox) if bbox else None,
        }
        # side by side: base | pr | diff (changed pixels in red over a faded base)
        faded = Image.blend(pa, Image.new("RGB", pa.size, "white"), 0.7)
        red = Image.new("RGB", pa.size, (220, 0, 0))
        overlay = Image.composite(red, faded, mask)
        label_h = 28
        sheet = Image.new("RGB", (w * 3 + 20, hgt + label_h), "white")
        draw = ImageDraw.Draw(sheet)
        try:
            font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 18)
        except OSError:
            font = ImageFont.load_default()
        for i, (img, label) in enumerate(((pa, "BEFORE (release/2026.08)"), (pb, "AFTER (PR 5705)"),
                                          (overlay, f"DIFF: {changed} px changed"))):
            x = i * (w + 10)
            sheet.paste(img, (x, label_h))
            draw.text((x + 6, 4), label, fill=(0, 0, 0), font=font)
        sheet.save(out_dir / f"{name}-compare.png", optimize=True)
        print(f"{name}: {changed} changed pixels, bbox={bbox}")
    (out_dir / "compare.json").write_text(json.dumps(results, indent=2) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
