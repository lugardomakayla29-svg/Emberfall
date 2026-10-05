package com.solme.emberfall.hub;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Judges whether a whole hub FOOTPRINT is safe to build on, before a single block is placed.
 *
 * {@link com.solme.emberfall.world.TerrainScanner} answers "can one mob stand in this column".
 * A hub is a disc of pedestals, banners and a departure ring, so it needs a stricter, whole-area
 * answer. Vanilla terrain is not flat: hills, ravines, sinkholes, cave mouths, lakes, trees and
 * villages all sit inside a 13x13 patch, and building through any of them looks broken or, worse,
 * buries the hub in a cave or floats it over a hole.
 *
 * The analyzer samples EVERY column of the footprint (plus a margin ring) and rejects the site on
 * the first hard failure. It never modifies the world. A site is accepted only when all hold:
 *
 *  1. Every column is loaded (an unloaded column would read as air and pass wrongly).
 *  2. The surface is found with the WORLD_SURFACE heightmap AND confirmed by probing down: the
 *     block under the "surface" must be solid ground, so a floating leaf canopy is not mistaken
 *     for the floor.
 *  3. The sky is visible above the surface (no roof, overhang, or cave ceiling): cave mouths and
 *     sinkholes fail here because their floor has stone overhead or sits far below the rim.
 *  4. Slope is bounded: the highest and lowest surface in the footprint differ by at most
 *     {@link #MAX_RELIEF}. This rejects cliffs, ravines and sinkhole edges.
 *  5. The ground is natural, dry, solid, non-hazard terrain (grass, dirt, sand, stone, snow...):
 *     no water, lava, ice-over-water, leaves, logs, or player-made blocks.
 *  6. Nothing tall or man-made stands in the footprint or margin: no logs, leaves, buildings,
 *     containers, beds, doors, rails, or anything from a generated structure.
 *  7. Under the surface there is real solid rock/dirt for {@link #SOLID_DEPTH} blocks, so the
 *     hub does not sit on a thin crust above a hidden cave or mineshaft.
 *  8. It is not inside a generated structure's bounding box (village, outpost, ruined portal...).
 */
public final class HubSiteAnalyzer {
    /** Half-width of the built hub footprint (9x9, measured as the largest that flat ground reliably offers). */
    public static final int RADIUS = 4;
    /** Extra clear ring around the footprint so the hub does not butt into a tree or wall. */
    public static final int MARGIN = 2;
    /** Max allowed difference between highest and lowest ground: 0 = already perfectly flat, because the hub never reshapes terrain. */
    public static final int MAX_RELIEF = 0;
    /** How many blocks below the surface must be solid (rejects thin crust over caves). */
    public static final int SOLID_DEPTH = 4;
    /** Headroom that must be clear of solid blocks above the ground. */
    public static final int HEADROOM = 6;

    private HubSiteAnalyzer() {}

    /** The outcome of analysing one candidate centre. */
    public record Result(boolean ok, BlockPos ground, int relief, String reason) {
        static Result fail(String reason) {
            return new Result(false, null, 0, reason);
        }
    }

    /**
     * Analyses a hub centred on column (cx, cz). On success, {@link Result#ground()} is the
     * block position of the ground surface at the centre and the whole footprint is flat enough
     * to level to that height.
     */
    public static Result analyse(ServerLevel level, int cx, int cz) {
        return analyse(level, cx, cz, RADIUS, MARGIN, MAX_RELIEF);
    }

    /** Same as {@link #analyse(ServerLevel, int, int)} with explicit footprint/relief, used to measure trade-offs. */
    public static Result analyse(ServerLevel level, int cx, int cz, int radius, int margin, int maxRelief) {
        int reach = radius + margin;
        int minSurface = Integer.MAX_VALUE;
        int maxSurface = Integer.MIN_VALUE;
        int centreSurface = Integer.MIN_VALUE;

        // Reject anything overlapping a generated structure before doing per-column work.
        if (overlapsStructure(level, cx, cz, reach)) {
            return Result.fail("overlaps a generated structure");
        }

        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                p.set(x, level.getSeaLevel(), z);
                if (!level.isLoaded(p)) {
                    return Result.fail("chunk not loaded");
                }
                boolean inFootprint = Math.abs(dx) <= radius && Math.abs(dz) <= radius;

                // Highest non-air, non-plant block. WORLD_SURFACE includes leaves/logs on purpose:
                // we WANT to see a tree so we can reject it, not skip past it.
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (top <= level.getMinY() + SOLID_DEPTH) {
                    return Result.fail("void or bedrock-level column");
                }
                p.set(x, top, z);
                BlockState surface = level.getBlockState(p);
                // WORLD_SURFACE is the highest non-air block, which on grassland is a PLANT (grass, flower,
                // fern, snow layer) resting on the real ground. Step down through those soft blocks to the
                // true ground so the ground/solid-depth checks look at dirt, not at the grass blade.
                // Trees, leaves and water are NOT skipped: they must still reach the forbidden check.
                int guard = 0;
                // The Ember Hearth itself stands on the centre column. It is the thing asking the
                // question, so it must not count against the ground it stands on. Only the centre
                // column is forgiven, and only for a Hearth block, so no other block is excused.
                boolean centreColumn = dx == 0 && dz == 0;
                while ((isSoftCover(surface) || (centreColumn && surface.getBlock() instanceof HearthBlock)) && guard++ < 4) {
                    p.move(0, -1, 0);
                    top = p.getY();
                    surface = level.getBlockState(p);
                }

                String forbidden = forbiddenSurfaceKind(surface);
                if (forbidden != null) {
                    return Result.fail(forbidden + " at " + x + "," + z);
                }

                // The margin ring is only breathing room: nothing is built there, so it must merely be
                // free of trees, water, hazards and man-made blocks (checked above). Flatness, natural-ground
                // type, sky access and the cave checks apply to the footprint the hub actually occupies.
                if (!inFootprint) {
                    continue;
                }

                if (!isNaturalGround(surface)) {
                    return Result.fail("non-natural ground at " + x + "," + z);
                }

                // Sky must be visible above the ground: rejects cave mouths, overhangs, canopies. If the
                // Hearth stands on this centre column it occupies the first cell above the ground, so
                // the sky and headroom checks start one cell higher, above the Hearth.
                int lift = (centreColumn && level.getBlockState(p.above()).getBlock() instanceof HearthBlock) ? 1 : 0;
                if (!level.canSeeSky(p.above(1 + lift))) {
                    return Result.fail("no open sky over " + x + "," + z);
                }
                for (int h = 1 + lift; h <= HEADROOM + lift; h++) {
                    BlockState above = level.getBlockState(p.above(h));
                    if (above.blocksMotion() || !above.getFluidState().isEmpty()) {
                        return Result.fail("obstruction above " + x + "," + z);
                    }
                }

                // Solid ground beneath: not a thin crust over a cave, not a sinkhole floor.
                for (int d = 1; d <= SOLID_DEPTH; d++) {
                    BlockState below = level.getBlockState(p.below(d));
                    if (!below.blocksMotion() || !below.getFluidState().isEmpty() || isCaveLike(below)) {
                        return Result.fail("hollow or fluid under " + x + "," + z + " at depth " + d);
                    }
                }

                if (top < minSurface) minSurface = top;
                if (top > maxSurface) maxSurface = top;
                if (dx == 0 && dz == 0) centreSurface = top;
            }
        }

        int relief = maxSurface - minSurface;
        if (relief > maxRelief) {
            return Result.fail("too steep (relief " + relief + ")");
        }
        // Underground darkness check on the centre: a bright surface is a strong sanity signal that
        // we are outside, not in a lit cave chamber reached via a heightmap quirk.
        BlockPos ground = new BlockPos(cx, centreSurface, cz);
        int probe = level.getBlockState(ground.above()).getBlock() instanceof HearthBlock ? 2 : 1;
        if (level.getBrightness(LightLayer.SKY, ground.above(probe)) < 8) {
            return Result.fail("centre is not lit by the sky");
        }
        return new Result(true, ground, relief, "ok");
    }

    /**
     * True if a real structure PIECE (an actual wall, floor or room of a village, outpost, mineshaft,
     * ruined portal...) covers any sampled block of the square around (cx, cz).
     *
     * This deliberately does not use {@code StructureManager.hasAnyStructureAt}: verified in bytecode,
     * that only tests {@code ChunkAccess.hasAnyStructureReferences()}, a coarse per-chunk flag that is
     * true for any chunk merely NEAR a structure. In a live scan it rejected 86% of sites that were
     * nowhere near a building. {@code getStructureWithPieceAt} returns a start only when a piece's own
     * bounding box contains the exact block.
     *
     * Samples an 3-block grid across the footprint: a piece is at least a few blocks wide, and anything
     * smaller than the grid still fails the per-column checks because man-made blocks are not natural ground.
     */
    private static boolean overlapsStructure(ServerLevel level, int cx, int cz, int reach) {
        var structureManager = level.structureManager();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx += 3) {
            for (int dz = -reach; dz <= reach; dz += 3) {
                // Structures span a vertical range; test the sea-level plane and the surface plane.
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx + dx, cz + dz);
                for (int y : new int[] {surface - 1, surface - 6, level.getSeaLevel()}) {
                    at.set(cx + dx, y, cz + dz);
                    var start = structureManager.getStructureWithPieceAt(at, holder -> true);
                    if (start != null && start.isValid()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Ground cover that sits ON the floor and has no collision: grass, flowers, ferns, snow layers, dead bushes. */
    private static boolean isSoftCover(BlockState s) {
        if (!s.getFluidState().isEmpty() || s.blocksMotion()) {
            return false;
        }
        return s.is(BlockTags.FLOWERS) || s.is(BlockTags.SMALL_FLOWERS) || s.is(BlockTags.SAPLINGS)
                || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN)
                || s.is(Blocks.DEAD_BUSH) || s.is(Blocks.SNOW) || s.is(Blocks.BROWN_MUSHROOM) || s.is(Blocks.RED_MUSHROOM);
    }

    /** Names WHY a surface block is unacceptable (or null if it is fine), so scans report a real cause. */
    private static String forbiddenSurfaceKind(BlockState s) {
        if (s.is(BlockTags.LOGS)) return "tree trunk";
        if (s.is(BlockTags.LEAVES)) return "tree leaves";
        if (!s.getFluidState().isEmpty()) return "water or lava";
        if (s.is(BlockTags.ICE)) return "ice";
        if (s.is(BlockTags.FIRE) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS)
                || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.COBWEB)) return "hazard block";
        if (s.is(BlockTags.WOOL) || s.is(BlockTags.PLANKS) || s.is(BlockTags.DOORS) || s.is(BlockTags.BEDS)
                || s.is(BlockTags.RAILS) || s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS) || s.is(BlockTags.CROPS)
                || s.is(Blocks.CHEST) || s.is(Blocks.BARREL) || s.is(Blocks.SPAWNER)) return "man-made block";
        if (s.is(Blocks.LILY_PAD) || s.is(Blocks.KELP) || s.is(Blocks.SEAGRASS)) return "water plant";
        return null;
    }

    /** Only ordinary terrain counts as ground; anything else is treated as "something is built here". */
    private static boolean isNaturalGround(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD)
                || s.is(BlockTags.TERRACOTTA)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SNOW_BLOCK)
                || s.is(Blocks.MUD) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM)
                || s.is(Blocks.RED_SAND) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE)
                || s.is(Blocks.SNOW) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.SHORT_GRASS)
                || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN);
    }

    /** Blocks that suggest we are looking at a cavity edge rather than solid ground. */
    private static boolean isCaveLike(BlockState s) {
        return s.is(Blocks.COBWEB) || s.is(Blocks.SPAWNER) || s.is(BlockTags.RAILS)
                || s.is(Blocks.CAVE_AIR) || s.is(Blocks.GLOW_LICHEN) || s.is(Blocks.SCULK)
                || s.is(Blocks.SCULK_VEIN) || s.is(Blocks.SCULK_SHRIEKER);
    }
}
