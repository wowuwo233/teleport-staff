package com.wowuwo233.teleport_staff.item;

import com.wowuwo233.teleport_staff.Config;
import com.wowuwo233.teleport_staff.TeleportStaffMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TeleportStaffMod.MODID);

    public static final RegistryObject<Item> TELEPORT_STAFF = ITEMS.register("teleport_staff",
            () -> new TeleportStaffItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    /**
     * 压缩传送粉：由 8 个传送粉 + 1 个末影珍珠压成，1 个能传送 1000 格。
     *
     * <p>和普通传送粉一样可以堆叠到 64，用法完全一致——放进副手即可。
     * 副手只有一个格子，所以不会和普通粉混装：要么整叠普通粉，要么整叠压缩粉。
     *
     * <p>稀有度定为 {@link Rarity#UNCOMMON}：它是玩家自己合成的中间产物，
     * 比原料（普通）贵重，但还不至于像法杖那样是唯一性道具。
     */
    public static final RegistryObject<Item> COMPRESSED_WARP_DUST = ITEMS.register(
            "compressed_warp_dust",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)) {
                @Override
                public void appendHoverText(ItemStack stack, @Nullable Level level,
                                            List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.translatable("tooltip.teleport_staff.compressed_dust",
                                    Config.COMPRESSED_BLOCKS_PER_DUST.get())
                            .withStyle(ChatFormatting.DARK_AQUA));
                    tooltip.add(Component.translatable("tooltip.teleport_staff.compressed_use")
                            .withStyle(ChatFormatting.GRAY));
                }
            });

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    private ModItems() {
    }
}
