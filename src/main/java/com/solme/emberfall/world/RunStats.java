package com.solme.emberfall.world;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player counters for the run-end screen. In memory only: a run never survives a restart, so
 * there is nothing to persist. Reset on run start and on run exit so a value can never leak into
 * the next run (see {@link RunManager#joinPlayer} / {@link RunManager#leavePlayer}).
 */
public final class RunStats {
    private static final Map<UUID, Integer> KILLS = new HashMap<>();

    private RunStats() {}

    public static void addKill(ServerPlayer player) {
        KILLS.merge(player.getUUID(), 1, Integer::sum);
    }

    public static int kills(ServerPlayer player) {
        return KILLS.getOrDefault(player.getUUID(), 0);
    }

    public static void clear(ServerPlayer player) {
        KILLS.remove(player.getUUID());
    }
}
