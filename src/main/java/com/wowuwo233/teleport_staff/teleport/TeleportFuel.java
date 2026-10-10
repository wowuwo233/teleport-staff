package com.wowuwo233.teleport_staff.teleport;

import com.wowuwo233.teleport_staff.Config;
import com.wowuwo233.teleport_staff.item.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 「传送燃料」的统一识别与计价。
 *
 * <p>目前有两种燃料：
 * <ul>
 *   <li>{@code waystones:warp_dust} —— 传送石碑的传送粉，1 个 = {@link Config#BLOCKS_PER_DUST} 格</li>
 *   <li>{@code teleport_staff:compressed_warp_dust} —— 本模组的压缩传送粉，
 *       1 个 = {@link Config#COMPRESSED_BLOCKS_PER_DUST} 格</li>
 * </ul>
 *
 * <h2>为什么副手不会同时有两种粉</h2>
 *
 * <p>副手只有一个物品格，所以玩家要么拿一叠普通粉、要么拿一叠压缩粉，
 * 不存在混装。距离因此可以简单地表成「副手数量 × 该种粉的单价值」，
 * 不需要做任何加权平均。
 *
 * <h2>为什么单独抽一个类</h2>
 *
 * <p>法杖的逻辑不该关心「燃料叫什么名字、值多少格」——它只问
 * {@link #valueOf} 要一个数字。以后再加第三种粉，只要在这里登记，
 * 法杖、补货、提示文案都不用改。
 */
public final class TeleportFuel {

    /**
     * 这一叠物品能不能当燃料。
     *
     * <p>普通粉走 {@link WarpDust} 的注册名查找（那是别的模组的物品，
     * 编译期不依赖它）；压缩粉是本模组自己注册的，直接比对注册对象。
     */
    public static boolean isFuel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return WarpDust.isWarpDust(stack) || isCompressed(stack);
    }

    /** 是不是本模组的压缩传送粉。 */
    public static boolean isCompressed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item compressed = ModItems.COMPRESSED_WARP_DUST.get();
        return compressed != null && stack.getItem() == compressed;
    }

    /**
     * 单个该种燃料值多少格。
     *
     * <p>不是燃料时返回 0——调用方据此判断「这东西不能用来传送」，
     * 而不是抛异常。
     */
    public static int valueOf(ItemStack stack) {
        if (WarpDust.isWarpDust(stack)) {
            return Config.BLOCKS_PER_DUST.get();
        }
        if (isCompressed(stack)) {
            return Config.COMPRESSED_BLOCKS_PER_DUST.get();
        }
        return 0;
    }

    /**
     * 一整叠燃料能传送的总距离。
     *
     * <p>副手只有一个格子，所以这里就是「数量 × 单价值」，
     * 不存在两种粉混装需要加权的情况。
     */
    public static int totalDistance(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        int per = valueOf(stack);
        if (per <= 0) {
            return 0;
        }
        return per * stack.getCount();
    }

    /**
     * 补货时应该用哪种物品。
     *
     * <p>把刚消耗掉的那叠的模板原样递回来——补货必须补<b>同一种</b>粉。
     * 如果副手放的是压缩粉却补了普通粉，玩家下一次右键的距离会突然变成十分之一，
     * 这种「补货把配置改掉」的行为比不补货还糟。
     *
     * @return 不能识别为燃料时返回 {@code null}；调用方必须判空
     */
    public static Item templateOf(ItemStack consumed) {
        if (consumed == null || consumed.isEmpty()) {
            return null;
        }
        if (isCompressed(consumed)) {
            return ModItems.COMPRESSED_WARP_DUST.get();
        }
        if (WarpDust.isWarpDust(consumed)) {
            return WarpDust.item();
        }
        return null;
    }

    private TeleportFuel() {
    }
}
