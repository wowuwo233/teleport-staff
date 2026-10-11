package com.wowuwo233.teleport_staff.teleport;

import com.wowuwo233.teleport_staff.Config;
import com.wowuwo233.teleport_staff.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * 时空蚀斑：每次传送后，把落点脚下的一片方块侵蚀成「传送粉块」。
 *
 * <p>设计初衷：传送在游戏里原本是一种近乎零成本的移动。这个机制给它重新
 * 加上一点「代价 / 痕迹」——传得越多，世界被腐蚀出越多的传送粉块。
 * 这既是「传送会留下痕迹」的可视化，也间接给了玩家一个
 * 「传远距离顺带大量回收传送粉」的玩法出口。
 *
 * <h2>只侵蚀「可替换」的方块</h2>
 *
 * <p>不是所有方块都该被换掉。这里用一系列保守规则跳过不该动的方块：
 * <ul>
 *   <li>只替换方块实体为空、命中箱非空的普通方块（泥土、石头、草那样）；</li>
 *   <li>跳过基岩、末地传送门等玩家不该破坏的结构；</li>
 *   <li>已存在方块实体（箱子、熔炉）不碰；</li>
 *   <li>已经是传送粉块的不重复放。</li>
 * </ul>
 * 流程里对每一次替换都用 {@code level.setBlockAndUpdate}（会同步客户端），
 * 落点区块刚被 {@code TeleportTarget} 加载过，替换不会额外触发大量区块生成。
 */
public final class Corrosion {

    private Corrosion() {
    }

    /**
     * 在 {@code center} 脚下侵蚀一片方块为传送粉块。
     *
     * @param level  世界
     * @param center 落点中心（这是实体脚部坐标所在的格位）
     */
    public static void corrode(Level level, BlockPos center) {
        if (!Config.CORROSION_ENABLED.get()) {
            return;
        }
        int radius = Config.CORROSION_RADIUS.get();
        if (radius <= 0) {
            return;
        }

        // 概率：不是每次传送都侵蚀。用世界随机源（LevelRandom），
        // 这样同样的输入在不同时间/位置会有不同结果，不会出现
        // 「换个世界就永远蚀斑 / 永不蚀斑」的确定性异常。
        double chance = Config.CORROSION_CHANCE.get();
        if (!(level.getRandom().nextDouble() < chance)) {
            return;
        }

        Block replacement = ModBlocks.WARP_DUST_BLOCK.get();
        BlockState dust = replacement.defaultBlockState();

        // 以「脚下第一层」为基准，向上、向下各试一层，优先换紧贴脚下的方块。
        // 落点高度可能略高于地表，所以先把「脚下那格」放进去，再向下找一层，
        // 保证地面上方与地面本身都被覆盖到。
        for (int dy = 0; dy >= -1; dy--) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (canReplace(level, pos)) {
                        level.setBlockAndUpdate(pos, dust);
                    }
                }
            }
        }
    }

    /** 这个位置能否被侵蚀成传送粉块。 */
    private static boolean canReplace(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        // 已是我们自己的方块，跳过，避免重复写事件 / 无谓的方块更新。
        if (state.getBlock() == ModBlocks.WARP_DUST_BLOCK.get()) {
            return false;
        }

        // 空气 / 液体不替换：我们只侵蚀实心方块，不把水体 / 虚空填成方块。
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }

        // 带方块实体的（箱子、熔炉、告示牌）不碰，否则会把里面存的东西毁掉。
        if (state.hasBlockEntity()) {
            return false;
        }

        // 命中箱为空（草、花、火把这类非实心物）不碰：换掉它们视觉上很奇怪，
        // 而且大概率是玩家刻意摆放的装饰。
        if (state.getCollisionShape(level, pos).isEmpty()) {
            return false;
        }

        // 破坏抗性极高（基岩、末地门、黑曜石等）不碰——这些通常是世界结构
        // 或者玩家不愿被动到的东西，强行换掉会让传送变得有破坏性。
        float resistance = state.getDestroySpeed(level, pos);
        if (resistance < 0.0F) {
            // 不可破坏的方块（基岩）destroySpeed 为 -1。
            return false;
        }
        if (resistance > 10.0F) {
            // 黑曜石约 50，末地门 -1，基岩 -1；>10 的当作「硬结构」跳过。
            return false;
        }

        // 颜色上已经是紫色系（比如下界传送门边框之外的紫）也跳过，避免视觉重复。
        // 这里不额外做颜色判断：保留给未来扩展。当前规则已足够保守。

        return true;
    }
}