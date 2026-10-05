package com.solme.emberfall.world;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Owns the static map builds. A slot is built once, a fixed number of blocks per tick, and then kept: a run only
 * reverts the few blocks it changed (through the block journal), it never rebuilds the map.
 *
 * {@link #ensureBuilt} is the single entry point. It is cheap to call again while a build is running or after it is done.
 */
public final class MapManager {
    private static final class Job {
        final MapBuilder builder;
        final long startNs = System.nanoTime();
        long worstTickNs = 0;
        long totalNs = 0;
        int ticks = 0;
        final Consumer<MapBuilder> onDone;

        Job(MapBuilder builder, Consumer<MapBuilder> onDone) {
            this.builder = builder;
            this.onDone = onDone;
        }
    }

    private static final Map<Integer, Job> running = new HashMap<>();
    private static final Map<Integer, MapBuilder> built = new HashMap<>();
    /** Last finished build's measurements, for the debug command and the tests. */
    private static String lastReport = "no map built yet";

    private MapManager() {}

    public static void init() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(MapManager::tick);
    }

    public static boolean isBuilt(int slot) {
        return built.containsKey(slot);
    }

    public static MapBuilder builtFor(int slot) {
        return built.get(slot);
    }

    public static boolean isBuilding(int slot) {
        return running.containsKey(slot);
    }

    public static String lastReport() {
        return lastReport;
    }

    /**
     * Starts building the map for {@code slot} unless it already is or was built. {@code onDone} runs on the server
     * thread once the last block is written (immediately if the slot is already built).
     */
    public static void ensureBuilt(ServerLevel level, int slot, BlockPos origin, Consumer<MapBuilder> onDone) {
        MapBuilder existing = built.get(slot);
        if (existing != null) {
            onDone.accept(existing);
            return;
        }
        if (running.containsKey(slot)) {
            return;
        }
        MapBuilder builder = new MapBuilder(level, origin);
        builder.forceChunks();
        running.put(slot, new Job(builder, onDone));
        EmberfallMod.LOGGER.info("MapManager: slot {} build started, {} blocks", slot, builder.total());
    }

    /** Forgets a slot so a later run rebuilds it (used when a slot's world was wiped). */
    public static void forget(int slot) {
        built.remove(slot);
        running.remove(slot);
    }

    private static void tick(MinecraftServer server) {
        if (running.isEmpty()) {
            return;
        }
        java.util.Iterator<Map.Entry<Integer, Job>> it = running.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Job> e = it.next();
            Job job = e.getValue();
            long t0 = System.nanoTime();
            boolean finished = job.builder.step();
            long dt = System.nanoTime() - t0;
            job.totalNs += dt;
            job.ticks++;
            job.worstTickNs = Math.max(job.worstTickNs, dt);
            if (finished) {
                it.remove();
                job.builder.releaseChunks();
                built.put(e.getKey(), job.builder);
                double wall = (System.nanoTime() - job.startNs) / 1.0e9;
                lastReport = String.format("slot %d built: %d blocks in %d ticks, %.1f s wall, avg %.2f ms per tick, worst %.1f ms",
                        e.getKey(), job.builder.total(), job.ticks, wall, job.totalNs / 1.0e6 / job.ticks, job.worstTickNs / 1.0e6);
                EmberfallMod.LOGGER.info("MapManager: {}", lastReport);
                job.onDone.accept(job.builder);
            }
        }
    }
}
