package com.solme.emberfall.hub;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Expedition Gate's floor frame. It is scenery only: walking onto it does NOTHING. A run starts from a deliberate right
 * click on the gate's hotspot ({@link GateManager}), because a block that starts a run when touched restarted one for every
 * player returned onto it (the plate loop).
 *
 * It is its own block rather than a vanilla pressure plate so ordinary plates elsewhere in the world
 * are untouched. It has no collision (players walk onto it) and a 1-pixel plate shape matching the
 * vanilla plate model it displays. {@code entityInside} fires every tick a player overlaps it, so a
 * per-player cooldown stops a player who lingers here, or who is refused, from being re-tried and
 * spammed with messages every tick.
 */
public class DepartureBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 1.0, 15.0);
    /** Ticks between attempts for one player (3 seconds). */
    private static final int COOLDOWN_TICKS = 60;
    private static final Map<UUID, Long> LAST_TRY = new HashMap<>();

    public DepartureBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** Forgets a player's cooldown when they leave, so the map cannot grow without bound. */
    public static void forget(UUID player) {
        LAST_TRY.remove(player);
    }
}
