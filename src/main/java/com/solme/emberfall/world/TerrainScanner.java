package com.solme.emberfall.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds safe fight positions on NATURAL terrain around a centre point, in any dimension.
 *
 * Deliberately does NOT use the heightmap: in the Nether or a cave the heightmap is the roof,
 * not the floor. Instead each candidate column is probed vertically from just above the
 * player's feet, looking for a standable spot: solid, non-liquid ground with two blocks of
 * clear, fluid-free air above it, within {@link #MAX_STEP} blocks of the player's own level.
 * That keeps spawn points out of cliff faces, lakes, lava, tree canopies and rock ceilings.
 */
public final class TerrainScanner {
    /** Fight radius (blocks) from the player at run start. */
    public static final int ARENA_RADIUS = 28;
    /** Spawn points sit on a ring this far out so mobs arrive from a distance, not on top of the player. */
    public static final int SPAWN_RING_MIN = 16;
    public static final int SPAWN_RING_MAX = 24;
    /** Max vertical distance from the player's level a spawn/shrine/boss spot may be. */
    private static final int MAX_STEP = 6;

    private TerrainScanner() {}

    /** Bounding box of the fight zone: a cylinder-ish box around the centre, tall enough for jumping/verticality. */
    public static BoundingBox arenaBounds(BlockPos center) {
        return new BoundingBox(
                center.getX() - ARENA_RADIUS, center.getY() - 12, center.getZ() - ARENA_RADIUS,
                center.getX() + ARENA_RADIUS, center.getY() + 24, center.getZ() + ARENA_RADIUS);
    }

    /**
     * Returns a standable position (the air block the entity's feet occupy) in the column
     * (x, z), or null if this column has none within {@link #MAX_STEP} of {@code refY}.
     * Searches upward first from refY-MAX_STEP so the lowest valid floor wins consistently.
     */
    public static BlockPos findStandable(ServerLevel level, int x, int z, int refY) {
        if (!level.isLoaded(new BlockPos(x, refY, z))) {
            return null;
        }
        int minY = Math.max(level.getMinY() + 1, refY - MAX_STEP);
        int maxY = Math.min(level.getMaxY() - 3, refY + MAX_STEP);
        // Prefer the candidate closest to the player's own level, so hills/valleys do not put the mob a floor away.
        BlockPos best = null;
        int bestDelta = Integer.MAX_VALUE;
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            feet.set(x, y, z);
            if (isStandable(level, feet)) {
                int delta = Math.abs(y - refY);
                if (delta < bestDelta) {
                    bestDelta = delta;
                    best = feet.immutable();
                }
            }
        }
        return best;
    }

    /** True if {@code feet} is a valid stand spot: solid dry floor below, two clear dry blocks at feet+head. */
    public static boolean isStandable(ServerLevel level, BlockPos feet) {
        BlockState floor = level.getBlockState(feet.below());
        BlockState body = level.getBlockState(feet);
        BlockState head = level.getBlockState(feet.above());
        if (!floor.blocksMotion() || !floor.getFluidState().isEmpty()) {
            return false;
        }
        // Not standing on something that hurts or is not really a floor.
        if (floor.is(net.minecraft.tags.BlockTags.FIRE) || floor.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                || floor.is(net.minecraft.world.level.block.Blocks.CACTUS)
                || floor.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                || floor.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                || floor.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                || floor.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) {
            return false;
        }
        return !body.blocksMotion() && body.getFluidState().isEmpty() && !isHazard(body)
                && !head.blocksMotion() && head.getFluidState().isEmpty() && !isHazard(head);
    }

    private static boolean isHazard(BlockState s) {
        return s.is(net.minecraft.tags.BlockTags.FIRE)
                || s.is(net.minecraft.world.level.block.Blocks.COBWEB)
                || s.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                || s.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW);
    }

    /**
     * Picks up to {@code count} standable points spread around a ring of radius [minR, maxR]
     * from {@code center}, evenly by angle (with jitter) so mobs come from all sides.
     * Tries extra jitter attempts per slot so one bad column (water, wall) does not lose the slot.
     */
    public static List<BlockPos> ringPoints(ServerLevel level, BlockPos center, int count, int minR, int maxR) {
        RandomSource random = level.getRandom();
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BlockPos found = null;
            for (int attempt = 0; attempt < 12 && found == null; attempt++) {
                double angle = (Math.PI * 2.0 * i / count) + (random.nextDouble() - 0.5) * (Math.PI / count);
                double r = minR + random.nextDouble() * (maxR - minR);
                int x = center.getX() + (int) Math.round(Math.cos(angle) * r);
                int z = center.getZ() + (int) Math.round(Math.sin(angle) * r);
                found = findStandable(level, x, z, center.getY());
            }
            if (found != null) {
                out.add(found);
            }
        }
        return out;
    }

    /** Spread-out points inside the arena (not on the outer ring), for shrines: not too close to the player or each other. */
    public static List<BlockPos> shrinePoints(ServerLevel level, BlockPos center, int count) {
        RandomSource random = level.getRandom();
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            for (int attempt = 0; attempt < 20; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double r = 8 + random.nextDouble() * 10; // 8..18 blocks out: reachable, not on top of the player
                int x = center.getX() + (int) Math.round(Math.cos(angle) * r);
                int z = center.getZ() + (int) Math.round(Math.sin(angle) * r);
                BlockPos p = findStandable(level, x, z, center.getY());
                if (p == null) {
                    continue;
                }
                boolean farFromOthers = true;
                for (BlockPos other : out) {
                    if (other.distSqr(p) < 8 * 8) {
                        farFromOthers = false;
                        break;
                    }
                }
                if (farFromOthers) {
                    out.add(p);
                    break;
                }
            }
        }
        return out;
    }
}
