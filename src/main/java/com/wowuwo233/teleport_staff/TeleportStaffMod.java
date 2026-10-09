package com.wowuwo233.teleport_staff;

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
 * Teleport Staff — 主手拿传送法杖、副手拿传送石碑的「传送粉」，
 * 右键即朝面朝的基本方位传送 100 格，每次固定消耗 1 个传送粉。
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
        modBus.addListener(this::addCreative);

        // 配置文件注册在注入进来的 context 上，理由同上。
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // 传送后的无敌由 Invulnerability 处理（取消受伤事件），
        // 所以注册的是它而不是主类自己。
        MinecraftForge.EVENT_BUS.register(new Invulnerability());
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.TELEPORT_STAFF.get());
        }
    }
}
