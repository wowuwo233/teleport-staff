package com.wowuwo233.teleport_staff;

import com.wowuwo233.teleport_staff.block.ModBlocks;
import com.wowuwo233.teleport_staff.item.ModItems;
import com.wowuwo233.teleport_staff.teleport.Invulnerability;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Teleport Staff — 主手拿传送法杖、副手拿燃料（传送石碑的传送粉，或本模组的压缩传送粉），
 * 右键即朝面朝的基本方位传送。副手整叠燃料一次消耗，距离 = 数量 × 该种燃料的单价值。
 *
 * <p>事件总线由构造函数注入，而不是调 {@code FMLJavaModLoadingContext.get()}：
 * 后者在 Forge 47.4 已被标记 {@code forRemoval}（1.21.1 起移除），
 * 构造注入是从那以后一直有效的写法。
 */
@Mod(TeleportStaffMod.MODID)
public class TeleportStaffMod {

    public static final String MODID = "teleport_staff";

    public TeleportStaffMod(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        ModItems.register(modBus);
        ModBlocks.register(modBus);
        modBus.addListener(this::addCreative);

        // 配置文件注册在注入进来的 context 上，理由同上。
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // 传送后的无敌由 Invulnerability 处理（取消受伤事件），
        // 所以注册的是它而不是主类自己。
        MinecraftForge.EVENT_BUS.register(new Invulnerability());
    }

    /**
     * 把本模组的物品放进原版的创造模式物品栏。
     *
     * <p>Forge 只自动注册物品，<b>不会</b>把它们塞进任何创造标签页——
     * 想让创造模式的玩家找到，必须自己监听这个事件。漏了的话物品依然存在于
     * 注册表里（可以合成、可以被命令给予），但创造物品栏里就是看不到。
     *
     * <p>两件物品分开放，按它们各自的性质：
     * <ul>
     *   <li>法杖 —— 「工具与实用物品」页，和末影珍珠、望远镜这类功能性道具一起</li>
     *   <li>压缩传送粉 —— 「材料」页，和火药、红石这类可堆叠的材料一起。
     *       它是合成出来的中间产物，不是工具，放材料页更符合玩家的查找直觉。</li>
     * </ul>
     */
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.TELEPORT_STAFF.get());
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.COMPRESSED_WARP_DUST.get());
        } else if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModItems.WARP_DUST_BLOCK.get());
        }
    }
}
