"""生成传送法杖的 16x16 物品贴图。

像素画的坐标系统：左上角是 (0,0)，和 Minecraft 贴图一致。
调色板刻意压得很窄（木杆 4 色 + 宝珠 4 色 + 金箍 2 色），
这样在 16px 下轮廓仍然清楚，不会糊成一团。
"""
from PIL import Image, ImageDraw

SIZE = 16
TRANSPARENT = (0, 0, 0, 0)

WOOD_DARK = (58, 40, 26, 255)
WOOD_MID = (86, 60, 38, 255)
WOOD_LIGHT = (120, 86, 54, 255)
GOLD = (201, 162, 39, 255)
GOLD_LIGHT = (240, 214, 120, 255)
ORB_DARK = (94, 30, 140, 255)
ORB_MID = (168, 70, 224, 255)
ORB_LIGHT = (216, 150, 250, 255)
ORB_CORE = (245, 225, 255, 255)

img = Image.new("RGBA", (SIZE, SIZE), TRANSPARENT)
d = ImageDraw.Draw(img)


def px(x, y, color):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        d.point((x, y), fill=color)


# ---------------------------------------------------------------- 木杆 ----
# 从右下往左上的一根杆，走「阶梯」而不是直线，视觉上更像细杆。
# 每往上一格就往右一格，杆身两像素宽：左列是暗面，右列是亮面。
shaft = [
    (3, 14), (4, 13), (5, 12), (6, 11), (7, 10),
    (7, 9), (8, 8), (8, 7), (9, 6), (9, 5),
]
for (x, y) in shaft:
    px(x, y, WOOD_MID)
    px(x + 1, y, WOOD_LIGHT)
    # 杆的下侧再压一道暗边，让它有圆柱感
    if y + 1 < SIZE:
        px(x, y + 1, WOOD_DARK)

# 杆底加一个包头，免得末端像是被截断的
px(3, 15, WOOD_DARK)
px(4, 15, WOOD_MID)
px(2, 14, WOOD_DARK)
px(4, 14, WOOD_LIGHT)

# ---------------------------------------------------------------- 金箍 ----
# 杆与宝珠的接头处缠一圈金属，也把两种材质在视觉上分开。
for x in range(8, 11):
    px(x, 6, GOLD)
px(8, 5, GOLD_LIGHT)
px(9, 7, GOLD_LIGHT)
px(10, 6, GOLD_LIGHT)

# ---------------------------------------------------------------- 宝珠 ----
# 圆心大约在 (11, 4)，半径 3：往下挪一格，宝珠才不会被贴图上边缘切掉。
cx, cy, r = 11.0, 4.0, 3.0
for y in range(SIZE):
    for x in range(SIZE):
        dist = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
        if dist <= r - 1.6:
            px(x, y, ORB_CORE)
        elif dist <= r - 0.8:
            # 高光偏左上，符合 Minecraft 物品默认的左上来光
            px(x, y, ORB_LIGHT if (x - cx) + (y - cy) < 0 else ORB_MID)
        elif dist <= r:
            px(x, y, ORB_MID if (x - cx) + (y - cy) < 0.5 else ORB_DARK)
        elif dist <= r + 0.35:
            px(x, y, ORB_DARK)

# 宝珠左下角点一颗亮色，避免整个球太闷
px(9, 5, ORB_LIGHT)
px(10, 5, ORB_LIGHT)

# ---------------------------------------------------------------- 星芒 ----
# 只在宝珠斜上方各甩一个像素出去。之前用两个像素连成一条横线，
# 放大看像是宝珠长出了一根刺，改成单点才像在闪烁。
px(13, 1, ORB_MID)
px(9, 1, ORB_LIGHT)

img.save(r"D:\DSH work\teleport-staff\src\main\resources\assets\teleport_staff\textures\item\teleport_staff.png")

# 放大一份预览，方便肉眼检查像素对不对（不打包进 jar）
img.resize((256, 256), Image.NEAREST).save(r"D:\DSH work\teleport-staff\texture-preview.png")
print("wrote teleport_staff.png")
