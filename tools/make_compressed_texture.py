"""生成「压缩传送粉」的 16x16 物品贴图。

要求（来自需求）：
  - **形状和 Waystones 的传送粉完全一样**，只是颜色不同
  - 颜色是「传送粉 + 末影珍珠」的混合色

做法：形状直接从原版 warp_dust.png 逐像素读取，不做任何改动——
  0  行 ................
  3  行 .......##.......
  4  行 ......####......
  5  行 .....######.....
  6  行 ....########....
  7  行 ...##########...
  8  行 ..############..
  9  行 ..############..
  10 行 ..############..
  11 行 ...##########...
  12 行 ....########....
  13 行 ......####......
  这个菱形逐格照抄，连每行起始位置都不差。

颜色则换成紫色与末影珍珠青绿的混合：保留原版那套「同一色相、七档明度」
的结构（这样在物品栏里的体积感、光影读法和原版一致），但色相往蓝紫偏移，
饱和度略降、亮度略提，读起来像掺了珍珠粉。
"""
from PIL import Image

SRC = r"D:\DSH work\.tmp-ws-tex\warp_dust.png"
DST = r"D:\DSH work\teleport-staff\src\main\resources\assets\teleport_staff\textures\item\compressed_warp_dust.png"

SIZE = 16

# ---------------------------------------------------------------- 形状 ----
# 直接从原版读形状：凡是原图不透明的地方，新图也不透明。
# 这是「形状完全一样」最可靠的实现方式——手写网格迟早会差一格。
src = Image.open(SRC).convert("RGBA")
src_px = src.load()

# ---------------------------------------------------------------- 调色 ----
# 原版的七档紫色，按明度从暗到亮。
ORIGINAL_RAMP = [
    (83, 0, 110),     # 最深，用于轮廓
    (109, 0, 144),
    (128, 0, 169),
    (166, 0, 220),    # 主体
    (208, 59, 255),
    (215, 92, 255),
    (224, 126, 255),  # 最亮高光
]

# 目标色系：紫色为主、掺入珍珠的青绿。
#
# 调这几组参数踩了三次坑，全部记下来免得以后又调回去：
#   ① MIX=0.22            → 和原版几乎看不出区别，白做
#   ② MIX=0.45 + 提亮      → 变成蓝灰色，紫色被冲掉
#   ③ MIX=0.30 + 绿通道提升 → 还是偏灰蓝，饱和度被拉平了
#
# 根因：**在 RGB 空间里做线性混合，必然掉饱和度**。
# 紫色 (166,0,220) 和青绿 (14,91,91) 在 RGB 里做加权平均，
# 得到的中间色三通道都被拉向彼此，结果就是发灰。
# 想"换色相但保住鲜艳度"，正确的地方是 HSV：只转色相、只微调明度，
# 饱和度基本原样保留。下面的实现就改成了这条路。
#
# 具体取法：原版是紫色（色相约 285°）。往末影珍珠的青绿（色相约 180°）
# 方向转一部分，落在蓝紫偏青的区间；同时整体明度抬一点点，
# 体现"压缩后更密实发亮"。
#
# 偏移量试过三档：
#   -28°  → 整块偏蓝，读起来像蓝宝石，青绿味不够
#   -14°  → 紫中透青，最接近"传送粉 + 末影珍珠"的描述
#    -6°  → 又和原版太像了
# 取 -14°：既明确区别于原料，又保住了紫色的底子。
HUE_SHIFT = -14.0   # 色相偏移（度）。负值 = 从紫往青绿方向转
SAT_SCALE = 0.94    # 饱和度略降，因为掺了珍珠的通透感
VAL_SCALE = 1.06    # 明度略升，压缩版应当比原料更亮


def tint(rgb):
    """把一档紫色转成"紫 + 珍珠青"的混合色。

    在 HSV 空间里只转色相、略调饱和与明度，避免 RGB 线性混合导致的褪色。
    """
    import colorsys
    r, g, b = [c / 255.0 for c in rgb]
    h, s, v = colorsys.rgb_to_hsv(r, g, b)

    # 转色相。colorsys 的 h 是 0..1，一圈 = 360°
    h = (h + HUE_SHIFT / 360.0) % 1.0
    s = min(1.0, s * SAT_SCALE)
    v = min(1.0, v * VAL_SCALE)

    r, g, b = colorsys.hsv_to_rgb(h, s, v)
    return (int(round(r * 255)), int(round(g * 255)), int(round(b * 255)), 255)


# 按原图每一档的明度顺序，映射到调色后的对应档位。
# 用最近邻匹配而不是按索引硬套：原图里同一档颜色在轮廓和高光处
# 会重复出现，最近邻能保证映射结果和原图的光影结构一致。
TINTED = [tint(c) for c in ORIGINAL_RAMP]


def nearest_tinted(rgb):
    best = None
    best_d = None
    for i, orig in enumerate(ORIGINAL_RAMP):
        d = sum((rgb[k] - orig[k]) ** 2 for k in range(3))
        if best_d is None or d < best_d:
            best_d = d
            best = i
    return TINTED[best]


# ---------------------------------------------------------------- 绘制 ----
out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
out_px = out.load()

for y in range(SIZE):
    for x in range(SIZE):
        r, g, b, a = src_px[x, y]
        if a == 0:
            continue  # 透明处保持透明 —— 形状因此与原版逐像素一致
        out_px[x, y] = nearest_tinted((r, g, b))

out.save(DST)
out.resize((256, 256), Image.NEAREST).save(r"D:\DSH work\.tmp-ws-tex\compressed_preview.png")

# 并排对比图：左原版，右压缩版
side = Image.new("RGBA", (16 * 2 + 4, 16), (0, 0, 0, 0))
side.paste(src, (0, 0))
side.paste(out, (20, 0))
side.resize(((16 * 2 + 4) * 12, 192), Image.NEAREST).save(r"D:\DSH work\.tmp-ws-tex\compare.png")

# 形状校验：两图的不透明像素集合必须完全相同
diff = 0
for y in range(SIZE):
    for x in range(SIZE):
        if (src_px[x, y][3] == 0) != (out_px[x, y][3] == 0):
            diff += 1
print("形状差异像素数:", diff, "(必须为 0)")
print("wrote compressed_warp_dust.png")
