#!/usr/bin/env python3
"""Pixel-compare before/after screenshots and build side-by-side composites (before | after | diff)."""
import json
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path('/home/user/build/visual')
OUT = ROOT / 'compare'
OUT.mkdir(exist_ok=True)

PAIRS = [
    ('01-new-form.png', '01-new-form.png', 'New Growth 0-36 form (demographic prefill)'),
    ('02-after-save.png', '02-after-save.png', 'Redisplay after Save (before: legacy JDBC save; after: JPA save of the same values)'),
    ('03-saved-legacy-row.png', '03-saved-legacy-row.png', 'Saved row written by the legacy code (after: read by the new JPA code)'),
    ('03-saved-legacy-row.png', '03b-saved-jpa-row.png', 'Legacy-saved row (before) vs JPA-saved row with the same values (after)'),
    ('04-print-growth-1.png', '04-print-growth-1.png', 'Print Growth PDF (length/weight chart), page 1'),
    ('05-print-headcirc-1.png', '05-print-headcirc-1.png', 'Head Circ(1) PDF, page 1'),
    ('08-null-slot-legacy-row.png', '08-null-slot-legacy-row.png', 'Legacy row with all twenty slots SQL NULL'),
    ('07-echart-leftnav.png', '07-echart-leftnav.png', 'eChart left navigation (Forms module lists Growth 0-36m)'),
    ('06-echart.png', '06-echart.png', 'eChart page'),
]


def label(image, text):
    canvas = Image.new('RGB', (image.width, image.height + 22), 'white')
    canvas.paste(image, (0, 22))
    ImageDraw.Draw(canvas).text((6, 5), text, fill='black')
    return canvas


def compare(before_name, after_name, title):
    before_path, after_path = ROOT / 'before' / before_name, ROOT / 'after' / after_name
    if not before_path.exists() or not after_path.exists():
        return {'title': title, 'missing': [str(p) for p in (before_path, after_path) if not p.exists()]}
    a = Image.open(before_path).convert('RGB')
    b = Image.open(after_path).convert('RGB')
    size = (max(a.width, b.width), max(a.height, b.height))
    pa, pb = Image.new('RGB', size, 'white'), Image.new('RGB', size, 'white')
    pa.paste(a, (0, 0))
    pb.paste(b, (0, 0))
    diff = ImageChops.difference(pa, pb)
    mask = diff.convert('L').point(lambda v: 255 if v > 16 else 0)
    changed = sum(1 for v in mask.getdata() if v)
    total = size[0] * size[1]
    bbox = mask.getbbox()
    overlay = pb.copy()
    red = Image.new('RGB', size, (255, 0, 0))
    overlay = Image.composite(red, Image.blend(overlay, Image.new('RGB', size, 'white'), 0.6), mask)
    gap = 12
    composite = Image.new('RGB', (size[0] * 3 + gap * 2, size[1] + 22), (230, 230, 230))
    composite.paste(label(pa, 'BEFORE (release/2026.08)'), (0, 0))
    composite.paste(label(pb, 'AFTER (PR #5705)'), (size[0] + gap, 0))
    composite.paste(label(overlay, f'DIFF: {changed} px changed'), (2 * (size[0] + gap), 0))
    name = after_name.replace('.png', '') if before_name == after_name else f'{before_name[:-4]}-vs-{after_name[:-4]}'
    target = OUT / f'{name}-compare.png'
    composite.save(target, optimize=True)
    return {'title': title, 'before': before_name, 'after': after_name, 'size_before': a.size, 'size_after': b.size,
            'changed_pixels': changed, 'changed_ratio': round(changed / total, 6), 'diff_bbox': bbox, 'composite': str(target)}


if __name__ == '__main__':
    results = [compare(*pair) for pair in PAIRS]
    (OUT / 'summary.json').write_text(json.dumps(results, indent=2))
    for r in results:
        print(json.dumps(r))
    sys.exit(0)
