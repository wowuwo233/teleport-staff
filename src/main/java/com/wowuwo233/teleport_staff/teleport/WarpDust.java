package com.wowuwo233.teleport_staff.teleport;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 「传送粉」的识别。
 *
 * <p>这里刻意<b>不</b>导入 {@code net.blay09.mods.waystones} 下的任何类，也不用它的
 * {@code ModItems}：本模组只依赖「传送石碑这个模组存在」这个事实，以及它注册的物品名
 * {@code waystones:warp_dust}。这样做的代价是名字写死，好处是编译期不需要 Waystones 的
 * jar——它只在运行开发环境时才需要（放进 {@code libs/}）。
 */
public final class WarpDust {

    /**
     * 传送石碑的传送粉，取自该模组 {@code zh_cn.json} 的 {@code item.waystones.warp_dust} = "传送粉"。
     *
     * <p>用 {@code tryBuild} 而不是 {@code new ResourceLocation(...)}：后者在 Forge 47.4
     * 已被标记 {@code forRemoval}。这里两个参数都是写死的合法值，不会真的抛错。
     */
    public static final ResourceLocation ID =
            ResourceLocation.tryBuild("waystones", "warp_dust");

    private static Item cached;

    public static boolean isWarpDust(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item dust = resolve();
        if (dust != null && stack.getItem() == dust) {
            return true;
        }
        // 兜底：万一代码里拿到的实例和注册表里的不是同一个（比如被别的模组替换过），
        // 仍按注册名认一次。
        return ID.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    /**
     * 传送粉这个物品本身，用于新建堆叠（自动补货时要造一叠粉放进副手）。
     *
     * @return 找不到时返回 {@code null}；调用方<b>必须判空</b>，
     *         直接拿去做 {@code new ItemStack(...)} 会在依赖缺失时抛异常
     */
    public static Item item() {
        return resolve();
    }

    private static Item resolve() {
        Item dust = cached;
        if (dust == null) {
            dust = ForgeRegistries.ITEMS.getValue(ID);
            cached = dust;
        }
        return dust;
    }

    private WarpDust() {
    }
}
