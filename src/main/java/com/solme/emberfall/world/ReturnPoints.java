package com.solme.emberfall.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Where a player stood when they left for the static expedition map, so leaving (or the run ending) puts them back.
 * Kept in memory only: if the server restarts mid-run the existing orphan rescue in {@link RunEndHandler} sends an
 * expedition-dimension login to the overworld spawn instead.
 */
public final class ReturnPoints {
    private record Point(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot) {}

    private static final Map<UUID, Point> POINTS = new HashMap<>();

    private ReturnPoints() {}

    public static void remember(ServerPlayer player) {
        POINTS.put(player.getUUID(), new Point(player.level().dimension(), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()));
    }

    /** Remembers an explicit spot (the Expedition Gate's "beside the gate"), in the player's current dimension. */
    public static void rememberAt(ServerPlayer player, double x, double y, double z) {
        POINTS.put(player.getUUID(), new Point(player.level().dimension(), x, y, z, player.getYRot(), player.getXRot()));
    }

    public static boolean has(ServerPlayer player) {
        return POINTS.containsKey(player.getUUID());
    }

    public static void forget(ServerPlayer player) {
        POINTS.remove(player.getUUID());
    }

    /**
     * Teleports the player back to the remembered spot, or to the overworld spawn when none is stored or that world is
     * gone. Does nothing when the player is not in the expedition dimension. Returns true if they were moved.
     */
    public static boolean sendBack(ServerPlayer player, MinecraftServer server) {
        if (player.level().dimension() != Dimensions.EXPEDITION) {
            POINTS.remove(player.getUUID());
            return false;
        }
        Point p = POINTS.remove(player.getUUID());
        ServerLevel target = p == null ? null : server.getLevel(p.dimension());
        if (target == null) {
            target = server.getLevel(Level.OVERWORLD);
            if (target == null) {
                return false;
            }
            var spawn = target.getLevelData().getRespawnData().pos();
            player.teleportTo(target, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
            return true;
        }
        player.teleportTo(target, p.x(), p.y(), p.z(), Set.of(), p.yRot(), p.xRot(), true);
        return true;
    }
}
