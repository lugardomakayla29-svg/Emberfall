package com.solme.emberfall.item;

import com.solme.emberfall.rift.RiftManager;
import com.solme.emberfall.rift.RiftRules;
import com.solme.emberfall.rift.RiftShape;
import com.solme.emberfall.rift.RiftSpot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * The Rift Shard: use it on the ground and a Rift tears open in front of you, facing you. One shard opens one Rift and is used up
 * (creative keeps it). It does NOT place any block and creates NO entity, so there is no hole to dig out of and no terrain to restore.
 * A refused use (too close to another Rift, no open air, inside a run) keeps the shard and says why.
 */
public final class RiftShardItem extends Item {
    public RiftShardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        if (!RiftRules.canOpen(ctx.getItemInHand().getCount()) && !sp.getAbilities().instabuild) {
            return InteractionResult.FAIL;
        }
        // The anchor is 6 blocks along the player's horizontal look, hanging so the box clears the clicked ground: the click point
        // is the floor under the Rift, the anchor is half the box height above it.
        var look = sp.getLookAngle();
        double len = Math.sqrt(look.x * look.x + look.z * look.z);
        double fx = len < 1e-6 ? 0.0 : look.x / len;
        double fz = len < 1e-6 ? 1.0 : look.z / len;
        double x = ctx.getClickLocation().x + fx * 5.0;
        double z = ctx.getClickLocation().z + fz * 5.0;
        double y = ctx.getClickLocation().y + RiftShape.BOX_H / 2.0;
        int facing = RiftSpot.facingToward(sp.getX() - x, sp.getZ() - z);
        RiftManager.Result r = RiftManager.open(level, x, y, z, facing, level.getGameTime() ^ sp.getUUID().getLeastSignificantBits());
        if (!r.ok()) {
            sp.sendSystemMessage(Component.literal("§cThe shard cannot open a Rift here: " + r.refusal() + "."), true);
            return InteractionResult.FAIL;
        }
        if (!sp.getAbilities().instabuild) {
            ctx.getItemInHand().shrink(RiftRules.SHARDS_PER_RIFT);
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
