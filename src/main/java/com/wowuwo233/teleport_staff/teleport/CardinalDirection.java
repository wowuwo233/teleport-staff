package com.wowuwo233.teleport_staff.teleport;

import java.util.Locale;

/**
 * 四个基本方位，以及「玩家朝向最接近哪一个」的判定。
 *
 * <p>刻意不使用 Minecraft 的 {@code Direction}：那个枚举有 6 个值（含上下）、
 * 还带着方块面朝向的语义，用在这里既多余又容易写错。更重要的是，
 * 这个类<b>不引用任何 Minecraft 类型</b>，因此可以被脱机测试直接覆盖
 * ——方位吸附的临界角（正东北 45° 到底算东还是北）正是那种
 * 「在游戏里凭手感永远测不准、但写错又很明显」的逻辑。
 *
 * <p>坐标约定（与 Minecraft 一致）：<b>东 = +X，西 = -X，南 = +Z，北 = -Z</b>。
 */
public enum CardinalDirection {

    NORTH(0, -1),
    SOUTH(0, 1),
    EAST(1, 0),
    WEST(-1, 0);

    private final int stepX;
    private final int stepZ;

    CardinalDirection(int stepX, int stepZ) {
        this.stepX = stepX;
        this.stepZ = stepZ;
    }

    public int stepX() {
        return stepX;
    }

    public int stepZ() {
        return stepZ;
    }

    /** 用于拼翻译键，如 {@code direction.teleport_staff.north}。 */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * 沿本方向走 {@code distance} 格之后的目标 X 坐标。
     *
     * <p>朝北/朝南时 {@link #stepX()} 是 0，所以这里<b>原样返回</b> {@code startX}。
     * 这个「不动的那个轴必须逐位不变」是本模组「没有侧向偏移」的硬保证，
     * 刻意写成独立方法是为了让脱机测试能直接断言它
     * （见 {@code tools/Check.java}）。
     */
    public double offsetX(double startX, int distance) {
        return startX + (double) stepX * distance;
    }

    /** 沿本方向走 {@code distance} 格之后的目标 Z 坐标。朝东/朝西时原样返回 {@code startZ}。 */
    public double offsetZ(double startZ, int distance) {
        return startZ + (double) stepZ * distance;
    }

    /**
     * 判定水平朝向最接近哪个基本方位。
     *
     * <p>判据是「哪个轴的投影更大」——这等价于判断朝向落在哪个 90° 扇区内，
     * 所以不需要处理 yaw 的 0°/360° 回绕，也不会因为浮点角度而在边界上抖动。
     *
     * <p>正 45° 的平局（{@code |dx| == |dz|}）判给东西方向，是个刻意的选择：
     * 只要保证同一种输入永远得到同一个结果，玩家就不会觉得「时灵时不灵」。
     *
     * @param dx 视线方向的 X 分量（东为正）
     * @param dz 视线方向的 Z 分量（南为正）
     */
    public static CardinalDirection nearest(double dx, double dz) {
        if (dx == 0.0D && dz == 0.0D) {
            // 退化输入：视线正好垂直向下或向上时水平投影为零。
            // 调用方通常会先用俯仰角挡掉这种情况，这里给个确定的兜底值。
            return SOUTH;
        }
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0.0D ? EAST : WEST;
        }
        return dz >= 0.0D ? SOUTH : NORTH;
    }
}
