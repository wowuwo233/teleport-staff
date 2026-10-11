package com.wowuwo233.teleport_staff.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/**
 * 传送粉块。
 *
 * <p>由 9 个传送粉合成（标准矿物块逻辑），可挖回 9 个传送粉。
 * 它有两个身份：
 * <ul>
 *   <li><b>存储</b>——把散落传送粉压成方块省背包空间（像铁块 / 金块）。</li>
 *   <li><b>时空蚀斑</b>——每次传送法杖使用后，落点脚下的一小片方块会
 *       被侵蚀成这种方块，是「传送会腐蚀世界」的可视化后果
 *       （见 {@link com.wowuwo233.teleport_staff.item.TeleportStaffItem#doTeleport}）。</li>
 * </ul>
 *
 * <p>材质刻意选得「偏软」：不是石头硬度，而是像压实但松脆的粉团
 * （{@link SoundType#SAND}、低硬度、低抗爆），这样侵蚀出来的方块
 * 玩家能轻松挖掉回收，不会被一个没法破坏的方块卡住。
 */
public class WarpDustBlock extends Block {

    public WarpDustBlock() {
        super(Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(0.5F, 0.7F)   // 比沙略硬一点，仍是一镐挖掉的软方块
                .sound(SoundType.SAND));
    }
}