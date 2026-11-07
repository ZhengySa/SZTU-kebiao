# -*- coding: utf-8 -*-
"""把教学楼楼层图切成 1F..5F 五张独立图片，并复制校园导览图到 res/drawable。

蓝带（每层底部的「C-5 公共教学楼 N 楼（教室分布图）」条）由 scan_floors.py 探得：
    y=2188..2327 / 3352..3491 / 4516..4655 / 5680..5819 / 6844..6983
间距恒为 1164，所以每层的裁切区间就是「上一带尾 .. 本带尾」。
"""
import os
from PIL import Image

CAMPUS = r"C:\Users\ZhengY\.workbuddy\clipboard-images\clipboard-2026-10-27T05-22-34-631Z-673294ea.jpg"
FLOORS = r"D:\no game\weixinshuju\xwechat_files\wxid_hhkth9n20d9h22_0026\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\f7bed0fc42ea79fac806249004eb85e7.jpg"
OUT = r"D:\xiangmu\app\src\main\res\drawable"

os.makedirs(OUT, exist_ok=True)

# ---- 校园导览图 ----
campus = Image.open(CAMPUS).convert("RGB")
campus.save(os.path.join(OUT, "map_campus.jpg"), "JPEG", quality=92, optimize=True)
print("map_campus.jpg", campus.size)

# ---- 教学楼按层切图 ----
im = Image.open(FLOORS).convert("RGB")
W, H = im.size

BAND_TOPS = [2188, 3352, 4516, 5680, 6844]
BAND_H = 140

cuts = []  # (start, end) 含端点
prev_end = 1164  # 跳过顶部深蓝海报头（0..1163）
for top in BAND_TOPS:
    cuts.append((prev_end, top + BAND_H - 1))
    prev_end = top + BAND_H

for i, (start, end) in enumerate(cuts, start=1):
    box = (0, start, W, min(end + 1, H))
    piece = im.crop(box)
    name = f"map_floor_{i}.jpg"
    piece.save(os.path.join(OUT, name), "JPEG", quality=90, optimize=True)
    print(name, piece.size, f"y={start}..{end}")
