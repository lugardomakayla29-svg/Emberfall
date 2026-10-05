package com.solme.emberfall.world;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.solme.emberfall.EmberfallMod;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds one slot of the static expedition map, a fixed number of blocks per tick.
 *
 * The full ordered write list is assembled up front: base, hills and wall first, then the props and the three shrine
 * structures on top, so nothing is ever written into a column that is not solid yet. Every distinct block-state string is
 * parsed exactly once. A string that does not parse throws, so a typo can never silently become air.
 *
 * The chunks under the map are force-loaded for the build and released when it finishes.
 */
public final class MapBuilder {
    /** Blocks written per server tick. Measured: 500 per tick costs about 4 ms. */
    public static final int BLOCKS_PER_TICK = 500;

    private final ServerLevel level;
    private final BlockPos origin;
    private int[] px, py, pz;
    private BlockState[] states;
    private int cursor = 0;
    private final List<ChunkPos> forced = new ArrayList<>();
    private boolean chunksForced = false;

    /** Where each shrine ended up in world space, filled while assembling. */
    public final Map<String, BlockPos> shrineAnchors = new HashMap<>();
    /** The world-space footprint of each shrine (inclusive min, max), for the click hotspot. */
    public final Map<String, BlockPos[]> shrineBounds = new HashMap<>();

    public MapBuilder(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.origin = origin;
        assemble();
    }

    private static final Map<String, BlockState> STATE_CACHE = new HashMap<>();

    static BlockState parse(String s) {
        BlockState cached = STATE_CACHE.get(s);
        if (cached != null) {
            return cached;
        }
        try {
            BlockState st = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, s, false).blockState();
            STATE_CACHE.put(s, st);
            return st;
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("map block state does not parse: '" + s + "' (" + e.getMessage() + ")", e);
        }
    }

    private void assemble() {
        StaticMap map = StaticMap.get();
        List<StaticMap.Put> terrain = map.terrainAndWall();

        JsonObject root;
        try (InputStream in = StaticMap.class.getResourceAsStream("/data/emberfall/map/expedition_map.json")) {
            root = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }

        List<int[]> coords = new ArrayList<>(terrain.size() + 20_000);
        List<BlockState> sts = new ArrayList<>(terrain.size() + 20_000);
        for (StaticMap.Put p : terrain) {
            coords.add(new int[]{p.x(), p.y(), p.z()});
            sts.add(p.state());
        }

        // props stand on the grass surface: their y = 0 layer sits one block above the play surface (grass is at y = 0)
        JsonArray houses = root.getAsJsonArray("houses");
        JsonObject houseBlocks = root.getAsJsonObject("houseBlocks");
        for (int i = 0; i < houses.size(); i++) {
            JsonArray h = houses.get(i).getAsJsonArray();
            int hx = h.get(1).getAsInt();
            int hz = h.get(2).getAsInt();
            // each house is a real vanilla village building, stored under its own name and already rotated to face the plaza
            String name = h.get(0).getAsString();
            addProp(coords, sts, houseBlocks.getAsJsonArray(name), hx, hz);
        }
        JsonArray trees = root.getAsJsonArray("trees");
        JsonArray treeBlocks = root.getAsJsonArray("treeBlocks");
        for (int i = 0; i < trees.size(); i++) {
            JsonArray t = trees.get(i).getAsJsonArray();
            addProp(coords, sts, treeBlocks.get(i % 2).getAsJsonArray(), t.get(0).getAsInt(), t.get(1).getAsInt());
        }
        JsonArray boulders = root.getAsJsonArray("boulders");
        JsonObject boulderBlocks = root.getAsJsonObject("boulderBlocks");
        for (int i = 0; i < boulders.size(); i++) {
            JsonArray b = boulders.get(i).getAsJsonArray();
            addProp(coords, sts, boulderBlocks.getAsJsonArray(String.valueOf(b.get(2).getAsInt())), b.get(0).getAsInt(), b.get(1).getAsInt());
        }

        // the three shrines: the user's own schematics, trimmed, with no grass markers and no air
        JsonArray shrines = root.getAsJsonArray("shrines");
        for (int i = 0; i < shrines.size(); i++) {
            JsonObject s = shrines.get(i).getAsJsonObject();
            String type = s.get("type").getAsString();
            JsonArray size = s.getAsJsonArray("size");
            int w = size.get(0).getAsInt();
            int h = size.get(1).getAsInt();
            int l = size.get(2).getAsInt();
            int x0 = s.get("x").getAsInt() - w / 2;
            int z0 = s.get("z").getAsInt() - l / 2;
            addProp(coords, sts, s.getAsJsonArray("blocks"), x0, z0);
            BlockPos min = origin.offset(x0, 1, z0);
            BlockPos max = origin.offset(x0 + w - 1, h, z0 + l - 1);
            shrineBounds.put(type, new BlockPos[]{min, max});
            shrineAnchors.put(type, origin.offset(s.get("x").getAsInt(), 1, s.get("z").getAsInt()));
        }

        int n = coords.size();
        px = new int[n];
        py = new int[n];
        pz = new int[n];
        states = new BlockState[n];
        for (int i = 0; i < n; i++) {
            int[] c = coords.get(i);
            px[i] = c[0];
            py[i] = c[1];
            pz[i] = c[2];
            states[i] = sts.get(i);
        }
    }

    /** A prop block list is [[dx,dy,dz,"state"], ...] (shrines use [x,y,z,"state"] from their trimmed corner). */
    private static void addProp(List<int[]> coords, List<BlockState> sts, JsonArray blocks, int ax, int az) {
        for (int i = 0; i < blocks.size(); i++) {
            JsonArray b = blocks.get(i).getAsJsonArray();
            // the surface grass block is at y = 0, so a prop's first layer (dy = 0) goes at y = 1
            coords.add(new int[]{ax + b.get(0).getAsInt(), 1 + b.get(1).getAsInt(), az + b.get(2).getAsInt()});
            sts.add(parse(b.get(3).getAsString()));
        }
    }

    public int total() {
        return states.length;
    }

    public int written() {
        return cursor;
    }

    public boolean done() {
        return cursor >= states.length;
    }

    /** Force-loads every chunk the map touches. Call once before the first {@link #step}. */
    public void forceChunks() {
        if (chunksForced) {
            return;
        }
        int r = StaticMap.RADIUS_OUT + 1;
        int minCx = (origin.getX() - r) >> 4;
        int maxCx = (origin.getX() + r) >> 4;
        int minCz = (origin.getZ() - r) >> 4;
        int maxCz = (origin.getZ() + r) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                level.setChunkForced(cx, cz, true);
                forced.add(new ChunkPos(cx, cz));
            }
        }
        chunksForced = true;
    }

    /** Releases the force-load tickets taken by {@link #forceChunks}. */
    public void releaseChunks() {
        if (!chunksForced) {
            return;
        }
        for (ChunkPos cp : forced) {
            level.setChunkForced(cp.x, cp.z, false);
        }
        forced.clear();
        chunksForced = false;
    }

    /** Writes up to {@link #BLOCKS_PER_TICK} blocks. Returns true once everything is written. */
    public boolean step() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int end = Math.min(states.length, cursor + BLOCKS_PER_TICK);
        while (cursor < end) {
            pos.set(origin.getX() + px[cursor], origin.getY() + py[cursor], origin.getZ() + pz[cursor]);
            level.setBlock(pos, states[cursor], 2 | 16);
            cursor++;
        }
        return done();
    }

    public BlockPos origin() {
        return origin;
    }

    public static void log(String msg) {
        EmberfallMod.LOGGER.info("MapBuilder: {}", msg);
    }
}
