package com.wowuwo233.teleport_staff;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 服务端与客户端共用的配置。
 *
 * <p>核心规则：<b>1 个传送粉 = 100 格</b>。传送时把副手的传送粉<b>整叠消耗</b>，
 * 距离 = 副手数量 × {@link #BLOCKS_PER_DUST}。副手 5 个就是 500 格，64 个就是 6400 格。
 * 传送结束后会自动从背包把副手补满（{@link #REFILL_OFFHAND}）。
 */
public final class Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue BLOCKS_PER_DUST;
    public static final ForgeConfigSpec.IntValue COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue INVULNERABILITY_TICKS;
    public static final ForgeConfigSpec.BooleanValue REFILL_OFFHAND;
    public static final ForgeConfigSpec.BooleanValue CONSUME_IN_CREATIVE;

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.comment("Teleport Staff / 传送法杖").push("teleport_staff");

        BLOCKS_PER_DUST = BUILDER
                .comment("一个传送粉传送的距离（格）。",
                        "传送时副手的传送粉会被整叠消耗，所以实际距离 = 副手数量 × 本项。",
                        "副手 5 个 -> 500 格；副手 64 个 -> 6400 格。",
                        "注意：距离很大时会一步跨进未加载的区块，服务器需要同步生成地形，",
                        "可能造成数秒卡顿。64 个粉的 6400 格尤其如此。",
                        "Distance covered by one warp dust. The whole offhand stack is consumed,",
                        "so the actual distance is (offhand count) x this value.")
                .defineInRange("blocks_per_dust", 100, 1, 10_000);

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

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private Config() {
    }
}
