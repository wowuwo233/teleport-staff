package com.wowuwo233.teleport_staff.block;

import com.wowuwo233.teleport_staff.TeleportStaffMod;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TeleportStaffMod.MODID);

    public static final RegistryObject<Block> WARP_DUST_BLOCK =
            BLOCKS.register("warp_dust_block", WarpDustBlock::new);

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }

    private ModBlocks() {
    }
}