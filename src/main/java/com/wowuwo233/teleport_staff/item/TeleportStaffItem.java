package com.wowuwo233.teleport_staff.item;

import com.wowuwo233.teleport_staff.Config;
import com.wowuwo233.teleport_staff.teleport.CardinalDirection;
import com.wowuwo233.teleport_staff.teleport.Invulnerability;
import com.wowuwo233.teleport_staff.teleport.TeleportFuel;
import com.wowuwo233.teleport_staff.teleport.TeleportTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 传送法杖。
 *
 * <p>用法：<b>主手</b>拿法杖、<b>副手</b>拿燃料（传送粉或压缩传送粉），
 * 右键朝你面朝的基本方位传送。
 *
 * <h2>三条规则</h2>
 * <ol>
 *   <li><b>1 个粉 = 100 格，副手整叠一次消耗。</b>
 *       距离 = 副手数量 × {@link Config#BLOCKS_PER_DUST}：副手 5 个传 500 格，
 *       64 个传 6400 格。粉被一次扣光，不按距离折算。</li>
 *   <li><b>方向吸附到东南西北。</b>轨迹严格沿单轴，没有任何侧向偏移；
 *       抬头低头不影响方向。</li>
 *   <li><b>传送后短暂无敌</b>，并在传送后自动从背包把副手补满。</li>
 * </ol>
 *
 * <h2>客户端与服务端的分工</h2>
 *
 * <p>客户端只做「副手有没有粉」这类判断——不需要知道落点就能给反馈。
 * 检查通过就返回 {@code success}，让原版把这次交互发给服务端；
 * 落点计算、扣粉、补货、位移一律只在服务端做，免得两边各算一次、结果对不上。
 *
 * <p>另外，「副手没粉」这种直接 {@code return fail} 的分支<b>根本不会发包</b>，
 * 所以那条提示必须发在客户端，否则玩家什么也看不到。
 */
public class TeleportStaffItem extends Item {

    public TeleportStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack staff = player.getItemInHand(hand);

        // 只认主手的法杖：副手如果也拿着法杖，用副手那次不触发，
        // 免得玩家分不清是哪只手的物品在生效。
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(staff);
        }

        ItemStack offhand = player.getOffhandItem();
        if (!TeleportFuel.isFuel(offhand)) {
            if (level.isClientSide) {
                warn(player, "message.teleport_staff.need_dust");
            }
            return InteractionResultHolder.fail(staff);
        }

        if (level.isClientSide) {
            return InteractionResultHolder.success(staff);
        }

        return serverUse((ServerPlayer) player, (ServerLevel) level, staff, offhand);
    }

    private InteractionResultHolder<ItemStack> serverUse(ServerPlayer player, ServerLevel level,
                                                         ItemStack staff, ItemStack offhand) {
        // 距离 = 副手数量 × 该种燃料的单价值。副手只有一个格子，
        // 所以两种粉不会混装，不需要加权。
        int fuelCount = offhand.getCount();
        int distance = TeleportFuel.totalDistance(offhand);

        // 补货要用同一种燃料的模板：副手放的是压缩粉，就不能补成普通粉，
        // 否则下一次右键的距离会突然变成十分之一。
        Item template = TeleportFuel.templateOf(offhand);

        Vec3 look = player.getViewVector(1.0F);
        CardinalDirection direction = CardinalDirection.nearest(look.x, look.z);

        TeleportTarget.Result result =
                TeleportTarget.compute(level, player, direction, distance);

        boolean creative = player.getAbilities().instabuild;
        boolean consume = !creative || Config.CONSUME_IN_CREATIVE.get();

        doTeleport(player, level, result);

        if (consume) {
            offhand.setCount(0); // 整叠消耗
            // 消耗之后再补货，否则会把刚补进来的粉又清掉。
            if (Config.REFILL_OFFHAND.get()) {
                refillOffhand(player, template, fuelCount);
            }
        }

        int invulnerable = Config.INVULNERABILITY_TICKS.get();
        if (invulnerable > 0) {
            Invulnerability.grant(player, invulnerable);
        }

        player.displayClientMessage(Component.translatable("message.teleport_staff.teleported",
                        Component.translatable("direction.teleport_staff." + direction.key()),
                        result.requested)
                .withStyle(ChatFormatting.AQUA), true);

        int cooldown = Config.COOLDOWN_TICKS.get();
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(this, cooldown);
        }

        return InteractionResultHolder.success(staff);
    }

    /**
     * 把背包里的燃料移到副手，补回到 {@code want} 个（也就是刚刚消耗掉的数量）。
     *
     * <p>为什么需要它：副手整叠会被一次扣光，没有这个补货，玩家每传一次都得
     * 手动从背包拖一叠粉到副手——带 64 个粉连续赶路时要拖 64 次，没人受得了。
     *
     * <p><b>补回到原来的数量，而不是「尽可能多」</b>：玩家放 5 个粉在副手，
     * 说明他想一次走 500 格；如果补货时把背包里 64 个全塞进副手，
     * 下一次右键就会突然变成 6400 格，这种意外很难受。保持一致更符合直觉。
     *
     * <p><b>只补同一种燃料</b>：模板由调用方（{@code TeleportFuel.templateOf}）
     * 从事先记下的那叠燃料取，这里不去猜。副手放压缩粉却补成普通粉，
     * 等于偷偷把玩家的配置改成十分之一。
     *
     * <p>副手如果是别的物品（比如盾牌）则完全不动，免得覆盖掉。
     */
    private void refillOffhand(ServerPlayer player, Item template, int want) {
        if (want <= 0 || template == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        ItemStack offhand = player.getOffhandItem();

        int current = 0;
        if (!offhand.isEmpty()) {
            if (!TeleportFuel.isFuel(offhand)) {
                return; // 副手放的是别的东西（盾牌之类），不要覆盖
            }
            current = offhand.getCount();
        }
        if (current >= want) {
            return;
        }

        // 只遍历背包主格（0..35），不含盔甲和副手自己。
        // 关键：只搬【和模板同一种】的燃料。如果不管种类地搬，
        // 副手放压缩粉时可能被普通粉填上，下一次右键的距离直接缩到十分之一。
        for (int i = 0; i < Inventory.INVENTORY_SIZE && current < want; i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot.isEmpty() || slot.getItem() != template) {
                continue;
            }
            int moved = Math.min(want - current, slot.getCount());
            current += moved;
            slot.shrink(moved);
            if (slot.isEmpty()) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }

        if (current <= 0) {
            return;
        }
        if (offhand.isEmpty()) {
            // 注意不能改 offhand 本身：副手为空时拿到的是 ItemStack.EMPTY
            // 这个全局共享单例，动它会污染整个游戏。
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(template, current));
        } else {
            offhand.setCount(current);
        }
        inventory.setChanged();
    }

    private void doTeleport(ServerPlayer player, ServerLevel level, TeleportTarget.Result result) {
        Vec3 to = result.position;

        // 先断开骑乘：骑着马传送会让坐骑被卡在落点里。
        player.stopRiding();

        // teleportTo(ServerLevel, x, y, z, yaw, pitch) 会自己同步位置包并触发区块加载，
        // 比手工 setPos + connection.teleport 稳妥。
        player.teleportTo(level, to.x, to.y, to.z, player.getYRot(), player.getXRot());

        // 传送后必须清掉下落速度和摔落距离，否则从高处往下传会立刻摔死。
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.hurtMarked = true; // 让服务端把速度同步给客户端，避免客户端继续按旧速度飘

        level.sendParticles(ParticleTypes.PORTAL,
                to.x, to.y + 1.0D, to.z,
                48, 0.4D, 0.7D, 0.4D, 0.15D);

        level.playSound(null, to.x, to.y, to.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void warn(Player player, String key) {
        player.displayClientMessage(
                Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.teleport_staff.usage")
                .withStyle(ChatFormatting.GRAY));
        // 两种燃料的单价分开列，玩家一眼能看出压缩粉值 10 倍。
        tooltip.add(Component.translatable("tooltip.teleport_staff.distance",
                        Config.BLOCKS_PER_DUST.get())
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.teleport_staff.distance_compressed",
                        Config.COMPRESSED_BLOCKS_PER_DUST.get())
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.teleport_staff.stack")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.teleport_staff.cardinal")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 常驻附魔光效：这是一件魔法道具，有光晕更好认。 */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
