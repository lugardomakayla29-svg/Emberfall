package com.solme.emberfall.world;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.solme.emberfall.EmberfallMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The static, hand-authored expedition map. Nothing here is random: the game reads
 * {@code data/emberfall/map/expedition_map.json} (written by mapwork/gen_map.py and checked by mapwork/verify_map.py)
 * and writes exactly those blocks. Frame: x and z are blocks from the circle centre, y = 0 is the play surface, the
 * base fills y -6..-1. World position = slot origin + (x, y, z).
 *
 * The block list is built once into a flat queue of (x, y, z, state) and then drained a fixed number of blocks per
 * tick, because block writes measured cheap (500 per tick is about 4 ms) while chunk creation is the cost.
 */
public final class StaticMap {
    public static final int RADIUS_OUT = 100;
    public static final int RADIUS_FLOOR = 95;
    public static final int BASE_DEPTH = 6;

    private static StaticMap INSTANCE;

    private final int[] heights;      // (x+100)*201 + (z+100)
    private final int[] wallTops;
    private final JsonArray shrines;
    private final List<int[]> housesCentres;
    private final int[] entry;
    private final int[] boss;
    private final int[] spawnRing;
    /** Ground cells (x, z) where a house, tree, boulder or shrine block stands at the first layer above the grass. */
    private final java.util.Set<Long> propCells = new java.util.HashSet<>();

    private StaticMap(JsonObject root) {
        this.heights = toIntArray(root.getAsJsonArray("heights"));
        this.wallTops = toIntArray(root.getAsJsonArray("wallTops"));
        this.shrines = root.getAsJsonArray("shrines");
        JsonArray hs = root.getAsJsonArray("houses");
        this.housesCentres = new ArrayList<>();
        for (int i = 0; i < hs.size(); i++) {
            JsonArray h = hs.get(i).getAsJsonArray();
            this.housesCentres.add(new int[]{h.get(1).getAsInt(), h.get(2).getAsInt()});
        }
        this.entry = toIntArray(root.getAsJsonArray("entry"));
        this.boss = toIntArray(root.getAsJsonArray("boss"));
        this.spawnRing = toIntArray(root.getAsJsonArray("spawnRing"));
        collectPropCells(root);
    }

    private static long cell(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /** Reads the same prop lists MapBuilder pastes, so "occupied" here means exactly "a block is written there". */
    private void collectPropCells(JsonObject root) {
        JsonObject houseBlocks = root.getAsJsonObject("houseBlocks");
        JsonArray houses = root.getAsJsonArray("houses");
        for (int i = 0; i < houses.size(); i++) {
            JsonArray h = houses.get(i).getAsJsonArray();
            addCells(houseBlocks.getAsJsonArray(h.get(0).getAsString()), h.get(1).getAsInt(), h.get(2).getAsInt());
        }
        JsonArray trees = root.getAsJsonArray("trees");
        JsonArray treeBlocks = root.getAsJsonArray("treeBlocks");
        for (int i = 0; i < trees.size(); i++) {
            JsonArray t = trees.get(i).getAsJsonArray();
            addCells(treeBlocks.get(i % 2).getAsJsonArray(), t.get(0).getAsInt(), t.get(1).getAsInt());
        }
        JsonArray boulders = root.getAsJsonArray("boulders");
        JsonObject boulderBlocks = root.getAsJsonObject("boulderBlocks");
        for (int i = 0; i < boulders.size(); i++) {
            JsonArray b = boulders.get(i).getAsJsonArray();
            addCells(boulderBlocks.getAsJsonArray(String.valueOf(b.get(2).getAsInt())), b.get(0).getAsInt(), b.get(1).getAsInt());
        }
    }

    private void addCells(JsonArray blocks, int ax, int az) {
        for (int i = 0; i < blocks.size(); i++) {
            JsonArray b = blocks.get(i).getAsJsonArray();
            if (b.get(1).getAsInt() == 0 && !b.get(3).getAsString().contains("air")) {
                propCells.add(cell(ax + b.get(0).getAsInt(), az + b.get(2).getAsInt()));
            }
        }
    }

    /** True if a prop block stands on this ground cell or on any of its 8 neighbours. */
    public boolean propNear(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (propCells.contains(cell(x + dx, z + dz))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int[] toIntArray(JsonArray a) {
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsInt();
        }
        return out;
    }

    public static synchronized StaticMap get() {
        if (INSTANCE == null) {
            try (InputStream in = StaticMap.class.getResourceAsStream("/data/emberfall/map/expedition_map.json")) {
                if (in == null) {
                    throw new IllegalStateException("expedition_map.json is missing from the jar");
                }
                JsonObject root = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
                INSTANCE = new StaticMap(root);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("cannot read expedition_map.json", e);
            }
        }
        return INSTANCE;
    }

    /** Ground height at (x, z) relative to the play surface; 0 outside the floor. */
    public int height(int x, int z) {
        if (x < -RADIUS_OUT || x > RADIUS_OUT || z < -RADIUS_OUT || z > RADIUS_OUT) {
            return 0;
        }
        return heights[(x + RADIUS_OUT) * (2 * RADIUS_OUT + 1) + (z + RADIUS_OUT)];
    }

    /**
     * Flat, standable spawn points for the wave director: on the ring between {@code spawnRing[0]} and {@code spawnRing[1]}
     * blocks from the centre, on height-0 ground, kept clear of every structure footprint and of the player entry and boss
     * spot. Deterministic (fixed grid, fixed stride), so every run gets the same set. Positions are the block a mob stands IN
     * (one above the grass).
     */
    public List<int[]> spawnPoints(int stride) {
        List<int[]> out = new ArrayList<>();
        List<int[]> keepClear = new ArrayList<>();
        for (int i = 0; i < shrines.size(); i++) {
            var sh = shrines.get(i).getAsJsonObject();
            keepClear.add(new int[]{sh.get("x").getAsInt(), sh.get("z").getAsInt(), 10});
        }
        for (var h : housesCentres) {
            keepClear.add(new int[]{h[0], h[1], 10});
        }
        for (int x = -RADIUS_FLOOR; x <= RADIUS_FLOOR; x += stride) {
            for (int z = -RADIUS_FLOOR; z <= RADIUS_FLOOR; z += stride) {
                double d = Math.sqrt((double) x * x + (double) z * z);
                if (d < spawnRing[0] || d > spawnRing[1] || height(x, z) != 0) {
                    continue;
                }
                boolean clear = true;
                for (int[] k : keepClear) {
                    if (Math.hypot(x - k[0], z - k[1]) < k[2]) {
                        clear = false;
                        break;
                    }
                }
                if (clear && flatAround(x, z, 1)) {
                    out.add(new int[]{x, 1, z});
                }
            }
        }
        return out;
    }

    /**
     * Every flat, standable candidate for a chest: height-0 ground with a flat 3x3 around it, between {@code minR} and {@code maxR}
     * blocks from the centre, {@code clearance} blocks away from every shrine, house, the player entry and the boss spot. Pure
     * function of the map data, in grid order (the caller shuffles with the run's own random, so each run differs). Each result
     * is {x, 1, z}: the block a chest occupies, one above the grass.
     */
    public List<int[]> chestCandidates(int stride, double minR, double maxR, double clearance) {
        List<int[]> keepClear = new ArrayList<>();
        for (int i = 0; i < shrines.size(); i++) {
            var sh = shrines.get(i).getAsJsonObject();
            keepClear.add(new int[]{sh.get("x").getAsInt(), sh.get("z").getAsInt()});
        }
        for (var h : housesCentres) {
            keepClear.add(new int[]{h[0], h[1]});
        }
        keepClear.add(new int[]{entry[0], entry[1]});
        keepClear.add(new int[]{boss[0], boss[1]});
        List<int[]> out = new ArrayList<>();
        for (int x = -RADIUS_FLOOR; x <= RADIUS_FLOOR; x += stride) {
            for (int z = -RADIUS_FLOOR; z <= RADIUS_FLOOR; z += stride) {
                double d = Math.sqrt((double) x * x + (double) z * z);
                if (d < minR || d > maxR || height(x, z) != 0 || !flatAround(x, z, 1) || propNear(x, z)) {
                    continue;
                }
                boolean clear = true;
                for (int[] k : keepClear) {
                    if (Math.hypot(x - k[0], z - k[1]) < clearance) {
                        clear = false;
                        break;
                    }
                }
                if (clear) {
                    out.add(new int[]{x, 1, z});
                }
            }
        }
        return out;
    }

    private boolean flatAround(int x, int z, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (height(x + dx, z + dz) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Shrine centres as {type, x, z}, in the order of the data file. */
    public List<Object[]> shrineSpots() {
        List<Object[]> out = new ArrayList<>();
        for (int i = 0; i < shrines.size(); i++) {
            var sh = shrines.get(i).getAsJsonObject();
            out.add(new Object[]{sh.get("type").getAsString(), sh.get("x").getAsInt(), sh.get("z").getAsInt()});
        }
        return out;
    }

    public int[] entry() { return entry; }
    public int[] boss() { return boss; }
    public int[] spawnRing() { return spawnRing; }
    public int shrineCount() { return shrines.size(); }

    /** One queued block write, relative to the slot origin. */
    public record Put(int x, int y, int z, BlockState state) {}

    /** The deterministic list of every block of the base, hills and wall, bottom to top. */
    public List<Put> terrainAndWall() {
        List<Put> out = new ArrayList<>(320_000);
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState basalt = Blocks.BASALT.defaultBlockState();
        BlockState blackstone = Blocks.BLACKSTONE.defaultBlockState();
        for (int x = -RADIUS_OUT; x <= RADIUS_OUT; x++) {
            for (int z = -RADIUS_OUT; z <= RADIUS_OUT; z++) {
                double d = Math.sqrt((double) x * x + (double) z * z);
                if (d > RADIUS_OUT) {
                    continue;
                }
                // base: a solid 6 thick disc
                for (int y = -BASE_DEPTH; y < 0; y++) {
                    out.add(new Put(x, y, z, y == -1 ? dirt : stone));
                }
                if (d <= RADIUS_FLOOR) {
                    int h = height(x, z);
                    // hills: dirt under a grass top, from the play surface (y 0) up to the local height
                    for (int y = 0; y < h; y++) {
                        out.add(new Put(x, y, z, dirt));
                    }
                    out.add(new Put(x, h, z, grass));
                    // the surface grass sits AT height h; flat ground (h = 0) puts grass at y = 0, so the base top
                    // dirt (y = -1) and the grass (y = 0) meet with no gap.
                } else {
                    // the wall: jagged basalt pillars, vertical inner face, top from the fixed profile
                    int arc = wallTops.length;
                    double ang = Math.atan2(z, x);
                    if (ang < 0) {
                        ang += 2 * Math.PI;
                    }
                    int top = wallTops[(int) (ang / (2 * Math.PI) * arc) % arc];
                    for (int y = 0; y < top; y++) {
                        out.add(new Put(x, y, z, ((x + y + z) & 3) == 0 ? blackstone : basalt));
                    }
                }
            }
        }
        return out;
    }

    public BoundingBox bounds(BlockPos origin) {
        return new BoundingBox(origin.getX() - RADIUS_OUT, origin.getY() - BASE_DEPTH, origin.getZ() - RADIUS_OUT,
                origin.getX() + RADIUS_OUT, origin.getY() + 24, origin.getZ() + RADIUS_OUT);
    }

    public static void log(String msg) {
        EmberfallMod.LOGGER.info("StaticMap: {}", msg);
    }
}
