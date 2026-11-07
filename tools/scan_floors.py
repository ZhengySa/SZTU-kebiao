# -*- coding: utf-8 -*-
"""探测楼层图里的蓝色标题带位置（纯 PIL，无需 numpy）。"""
from PIL import Image

FLOORS = r"D:\no game\weixinshuju\xwechat_files\wxid_hhkth9n20d9h22_0026\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\f7bed0fc42ea79fac806249004eb85e7.jpg"

im = Image.open(FLOORS).convert("RGB")
W, H = im.size
small = im.resize((48, H), Image.BOX)
px = small.load()

def blue_row(y):
    hit = 0
    for x in range(48):
        r, g, b = px[x, y]
        if b > 90 and b < 215 and b - r > 45 and b - g > 25:
            hit += 1
    return hit / 48.0

rows = [blue_row(y) > 0.55 for y in range(H)]

bands = []
start = None
for y in range(H):
    if rows[y] and start is None:
        start = y
    elif not rows[y] and start is not None:
        if y - start >= 20:
            bands.append((start, y - 1))
        start = None
if start is not None and H - start >= 20:
    bands.append((start, H - 1))

print("size", W, H)
print("blue bands:", len(bands))
for s, e in bands:
    print(f"  y={s}..{e} h={e - s + 1}")
