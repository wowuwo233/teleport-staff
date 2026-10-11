package com.wowuwo233.teleport_staff;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 服务端与客户端共用的配置。
 *
 * <p>核心规则：燃料<b>整叠消耗</b>，距离 = 副手数量 × 该种燃料的单价值。
 * 两种燃料：
 * <ul>
 *   <li>传送石碑的传送粉 —— {@link #BLOCKS_PER_DUST}，默认 100 格/个</li>
 *   <li>本模组的压缩传送粉 —— {@link #COMPRESSED_BLOCKS_PER_DUST}，默认 1000 格/个</li>
 * </ul>
 *
 * <p>副手只有一个物品格，所以两种粉不会混装：要么整叠普通粉，
 * 要么整叠压缩粉。传送结束后自动从背包补回同一种粉（{@link #REFILL_OFFHAND}）。
 */
public final class Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue BLOCKS_PER_DUST;
    public static final ForgeConfigSpec.IntValue COMPRESSED_BLOCKS_PER_DUST;
    public static final ForgeConfigSpec.IntValue COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue INVULNERABILITY_TICKS;
    public static final ForgeConfigSpec.IntValue CORROSION_RADIUS;
    public static final ForgeConfigSpec.DoubleValue CORROSION_CHANCE;
    public static final ForgeConfigSpec.BooleanValue CORROSION_ENABLED;
    public static final ForgeConfigSpec.BooleanValue REFILL_OFFHAND;
    public static final ForgeConfigSpec.BooleanValue CONSUME_IN_CREATIVE;

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.comment("Teleport Staff / 传送法杖").push("teleport_staff");

        BLOCKS_PER_DUST = BUILDER
                .comment("一个传送粉传送的距离（格）。",
                        "传送时副手的燃料会被整叠消耗，所以实际距离 = 副手数量 × 本项。",
                        "副手 5 个 -> 500 格；副手 64 个 -> 6400 格。",
                        "注意：距离很大时会一步跨进未加载的区块，服务器需要同步生成地形，",
                        "可能造成数秒卡顿。64 个粉的 6400 格尤其如此。",
                        "Distance covered by one warp dust. The whole offhand stack is consumed,",
                        "so the actual distance is (offhand count) x this value.")
                .defineInRange("blocks_per_dust", 100, 1, 100_000);

        COMPRESSED_BLOCKS_PER_DUST = BUILDER
                .comment("一个压缩传送粉传送的距离（格）。默认 1000，即普通传送粉的 10 倍。",
                        "压缩传送粉由 8 个传送粉 + 1 个末影珍珠合成，所以 10 倍是刻意的：",
                        "8 个原料本该值 800 格，压缩后给到 1000 格，多出来的 200 格",
                        "就是那颗末影珍珠和一次合成的价值，玩家才有动力去压。",
                        "如果改成低于 800，这个配方就纯粹是亏本买卖，没人会做。",
                        "Distance covered by one compressed warp dust (default 1000 = 10x normal).")
                .defineInRange("compressed_blocks_per_dust", 1000, 1, 1_000_000);

        COOLDOWN_TICKS = BUILDER
                .comment("两次传送之间的冷却（tick，20 tick = 1 秒）。0 表示没有冷却。",
                        "Cooldown between two teleports, in ticks.")
                .defineInRange("cooldown_ticks", 20, 0, 20 * 600);

        INVULNERABILITY_TICKS = BUILDER
                .comment("传送后的无敌时间（tick，20 tick = 1 秒）。默认 100 tick = 5 秒。",
                        "落点有时会比预期高（比如落点正下方是峡谷），没有这段保护很容易摔死。",
                        "设为 0 可关闭。",
                        "Invulnerability window after a teleport, in ticks. 0 disables it.")
                .defineInRange("invulnerability_ticks", 100, 0, 20 * 600);

        REFILL_OFFHAND = BUILDER
                .comment("传送后是否自动从背包把副手的传送粉补满。默认 true。",
                        "副手整叠传送粉会被一次消耗光，没有这个补货就得每次手动拖，",
                        "带一堆粉连续赶路时会非常烦。",
                        "Automatically refill the offhand with warp dust from the inventory after a teleport.")
                .define("refill_offhand", true);

        CONSUME_IN_CREATIVE = BUILDER
                .comment("创造模式下是否也消耗传送粉。默认 false：创造模式不消耗。",
                        "关掉时创造模式仍然按副手数量决定距离，只是不真的扣掉。",
                        "Whether warp dust is consumed in creative mode.")
                .define("consume_in_creative", false);

        CORROSION_ENABLED = BUILDER
                .comment("传送后是否在落点侵蚀一部分方块，使其变成传送粉块（时空蚀斑）。默认 true。",
                        "这是「传送会腐蚀世界」的可视化后果：每次传送后，落点脚下",
                        "一小片范围内的可替换方块会被换成传送粉块，玩家能挖掉回收到传送粉。",
                        "设为 false 可关闭。",
                        "Whether teleporting corrodes blocks at the destination into warp dust blocks.")
                .define("corrosion_enabled", true);

        CORROSION_RADIUS = BUILDER
                .comment("时空蚀斑的半径（格）。决定每次传送在落点脚下侵蚀多大的方形区域。",
                        "默认 2：落点脚下 5x5 的范围内会被侵蚀。设为 0 等价于关闭。",
                        "范围越大，传送留下的传送粉块越多（可用于大量回收传送粉）。",
                        "Radius of the warp dust corrosion area at the destination, in blocks.")
                .defineInRange("corrosion_radius", 2, 0, 8);

        CORROSION_CHANCE = BUILDER
                .comment("每次传送触发时空蚀斑的概率（0.0 ~ 1.0）。默认 0.3：约三成传送会留下蚀斑。",
                        "设为 1.0 = 每次传送都侵蚀；0.0 = 从不侵蚀（等价于关闭 CORROSION_ENABLED）。",
                        "这是为了让蚀斑成为「偶发的痕迹」而不是每次必现的资源：",
                        "每次都掉传送粉会让传送变成白嫖发放机。",
                        "Chance that a teleport spawns a corrosion patch (0.0 to 1.0).")
                .defineInRange("corrosion_chance", 0.3, 0.0, 1.0);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private Config() {
    }
}
