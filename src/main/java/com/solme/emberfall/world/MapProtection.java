package com.solme.emberfall.world;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;

/**
 * Makes the designed expedition map unbreakable by players, in every way a player can touch a block.
 *
 * Scope: ONLY the emberfall:expedition dimension. Nothing in any other dimension is affected.
 *
 * The mod's own writes (shrine props, boss floors, cauldron trick) are server side setBlock calls and never pass
 * through these player hooks, so they keep working. Explosions are closed by EmberfallExplosionMixin. NOTE: game
 * rules are ONE shared object for the whole server in 1.21.11, so they can not be used to scope a rule to one dimension.
 *
 * Deliberately cheap: three event callbacks that do one dimension comparison, no ticking, no scanning.
 */
public final class MapProtection {
    private MapProtection() {}

    public static boolean isProtected(Level level) {
        return level.dimension().equals(Dimensions.EXPEDITION);
    }

    public static void init() {
        // 1) Breaking: cancel before the block changes (covers survival mining and instant creative breaking).
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> !isProtected(world));
        // 2) Starting to break: stops the crack animation and creative instant-break at the source.
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) ->
                isProtected(world) ? InteractionResult.FAIL : InteractionResult.PASS);
        // 3a) Items used WITHOUT a block target: a bucket raycasts to a fluid or block itself (BucketItem.use), so
        // UseBlockCallback never sees it. Refuse every bucket here.
        UseItemCallback.EVENT.register((player, world, hand) ->
                isProtected(world) && player.getItemInHand(hand).getItem() instanceof BucketItem
                        ? InteractionResult.FAIL : InteractionResult.PASS);
        // 3) Placing or using: blocks, buckets, flint and steel, spawn eggs on blocks, bone meal, hoes and axes.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!isProtected(world)) {
                return InteractionResult.PASS;
            }
            ItemStack stack = player.getItemInHand(hand);
            boolean changesWorld = stack.getItem() instanceof BlockItem
                    || stack.getItem() instanceof BucketItem
                    || stack.is(net.minecraft.world.item.Items.FLINT_AND_STEEL)
                    || stack.is(net.minecraft.world.item.Items.FIRE_CHARGE)
                    || stack.is(net.minecraft.world.item.Items.BONE_MEAL)
                    || stack.getItem() instanceof net.minecraft.world.item.HoeItem
                    || stack.getItem() instanceof net.minecraft.world.item.AxeItem
                    || stack.getItem() instanceof net.minecraft.world.item.ShovelItem;
            return changesWorld ? InteractionResult.FAIL : InteractionResult.PASS;
        });
    }
}
