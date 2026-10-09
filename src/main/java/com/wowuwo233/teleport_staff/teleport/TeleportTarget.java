package com.wowuwo233.teleport_staff.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 传送落点的计算。
 *
 * <p>规则是「沿东南西北中的一个方向，笔直走 N 格」：
 * <ul>
 *   <li>方向由 {@link CardinalDirection} 吸附得到，<b>只有轴向位移</b>——
 *       朝北传时 X 坐标逐位不变，朝东传时 Z 坐标逐位不变。</li>
 *   <li>垂直方向不参与位移：不会因为抬头就往上飞。高度只在落点处调整。</li>
 *   <li><b>传送途中不看路</b>：不会因为中途有山、有树就停下来。见下方说明。</li>
 * </ul>
 *
 * <h2>为什么中途不做碰撞检测</h2>
 *
 * <p>64 个传送粉一次要跨 6400 格，也就是约 400 个区块。要判断「路上有没有方块」，
 * 就得把这 400 个区块全部加载出来逐格查——那会让服务器卡死几十秒，
 * 而玩家本来只是想赶路。所以这里<b>直接算终点坐标</b>，只在终点做一次安全落点吸附。
 *
 * <p>代价是传送会「穿过」途经的山体和建筑。这是刻意的取舍：
 * 传 500 格的意义就在于不被路上第一棵树拦下。
 *
 * <p>高度用 Heightmap 取地表，而不是从玩家当前高度平移：跨 6400 格之后地形高差可能上百格，
 * 平移过去大概率是悬空或埋进山里。
 */
public final class TeleportTarget {

    /** 落点上下搜索范围（格）。取 32 约两个区块的高度，够跨越常见的悬崖与海面落差。 */
    private static final int VERTICAL_SEARCH = 32;

    /** 落点结果。 */
    public static final class Result {
        /** 最终落点（脚部坐标，即实体 Y 的基准）。 */
        public final Vec3 position;
        /** 请求传送的格数。 */
        public final int requested;
        /** 是否撞到了世界边界，导致距离被截短。 */
        public final boolean clampedByBorder;

        Result(Vec3 position, int requested, boolean clampedByBorder) {
            this.position = position;
            this.requested = requested;
            this.clampedByBorder = clampedByBorder;
        }
    }

    /**
     * 计算落点。
     *
     * @param level         世界
     * @param player        传送的玩家（用它的碰撞箱尺寸判断放不放得下）
     * @param direction     吸附后的基本方位
     * @param requested     请求传送的格数（副手数量 × 每粉格数）
     */
    public static Result compute(Level level, Player player, CardinalDirection direction,
                                 int requested) {
        double targetX = direction.offsetX(player.getX(), requested);
        double targetZ = direction.offsetZ(player.getZ(), requested);

        // 世界边界是硬约束：越过它会被服务端拽回来，不如自己先截断，
        // 免得玩家看到的位置和实际落点对不上。
        boolean clamped = false;
        double minX = level.getWorldBorder().getMinX() + 1.0D;
        double maxX = level.getWorldBorder().getMaxX() - 1.0D;
        double minZ = level.getWorldBorder().getMinZ() + 1.0D;
        double maxZ = level.getWorldBorder().getMaxZ() - 1.0D;
        if (targetX < minX) { targetX = minX; clamped = true; }
        if (targetX > maxX) { targetX = maxX; clamped = true; }
        if (targetZ < minZ) { targetZ = minZ; clamped = true; }
        if (targetZ > maxZ) { targetZ = maxZ; clamped = true; }

        // 必须先把目标区块加载出来，否则 getHeight 会静默返回基岩层高度——
        // Level.getHeight 对未加载的区块直接返回 getMinBuildHeight，
        // 不会报错也不会自动加载，结果就是把玩家塞到世界底部。
        // getChunk(x, z) 内部走 ChunkStatus.FULL，会同步生成/读取区块；
        // 这一步正是「传 6400 格会卡一下」的来源。
        BlockPos target = BlockPos.containing(targetX, 0, targetZ);
        level.getChunk(target.getX() >> 4, target.getZ() >> 4);

        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                target.getX(), target.getZ());

        Double y = findStandY(level, player, targetX, targetZ, surfaceY);
        // 实在找不到落脚点（比如落点在基岩层以下的封闭结构里）就用地表高度，
        // 玩家会往下掉一点，但有传送后的无敌时间兜底。
        double finalY = (y != null) ? y : surfaceY;

        return new Result(new Vec3(targetX, finalY, targetZ), requested, clamped);
    }

    /**
     * 在 (x, z) 这一列上找一个能放下玩家的 Y。
     *
     * <p>以地表高度为起点由近及远交替上下试探：优先返回<b>脚下有支撑</b>的高度；
     * 整列都没有支撑（落点在深海或峡谷上方）时返回最接近地表的一个空位，
     * 让玩家落在那儿自然下坠——配合传送后的无敌时间不会摔死。
     *
     * @return 可用的脚部 Y；整列都被堵死时返回 {@code null}
     */
    private static Double findStandY(Level level, Player player, double x, double z, int surfaceY) {
        Double airOnly = null;
        for (int i = 0; i <= VERTICAL_SEARCH; i++) {
            int[] offsets = (i == 0) ? new int[]{0} : new int[]{i, -i};
            for (int dy : offsets) {
                double y = surfaceY + dy;
                if (y <= level.getMinBuildHeight()
                        || y + player.getBbHeight() >= level.getMaxBuildHeight()) {
                    continue;
                }
                if (!fits(level, player, x, y, z)) {
                    continue;
                }
                if (hasSupport(level, x, y, z)) {
                    return y;
                }
                if (airOnly == null) {
                    airOnly = y;
                }
            }
        }
        return airOnly;
    }

    /** 玩家的碰撞箱放在 (x, y, z) 时是否不与其他方块重叠。 */
    private static boolean fits(Level level, Player player, double x, double y, double z) {
        double half = player.getBbWidth() / 2.0D;
        AABB box = new AABB(
                x - half, y, z - half,
                x + half, y + player.getBbHeight(), z + half);
        return level.noCollision(box);
    }

    /** 脚下是否有东西接着（实心方块或液体），没有就是悬空。 */
    private static boolean hasSupport(Level level, double x, double y, double z) {
        BlockPos below = BlockPos.containing(x, y - 0.1D, z);
        BlockState state = level.getBlockState(below);
        if (!state.getCollisionShape(level, below).isEmpty()) {
            return true;
        }
        FluidState fluid = level.getFluidState(below);
        return !fluid.isEmpty();
    }

    private TeleportTarget() {
    }
}
