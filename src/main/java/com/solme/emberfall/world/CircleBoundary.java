package com.solme.emberfall.world;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps a player inside the circular designed expedition map, whatever carried them out: an ender pearl, chorus
 * fruit, knockback, a fall, or a teleport. Only runs in the pocket dimension (not in-place runs, which keep the
 * box based {@link ArenaBoundary}).
 *
 * The physical ring wall is the primary defence. This is the backstop for the things geometry cannot stop.
 * Cost: one distance calculation per run player per tick, no entity queries.
 *
 * The geometry lives in {@link CircleMath}, which has no game types so it can be unit tested without a server.
 */
public final class CircleBoundary {
    /** Distance from the centre a player may stand at (the play floor radius). */
    public static final double PLAY_RADIUS = 93.0;
    /** Past this the player is put straight back; between PLAY_RADIUS and here they are pushed. */
    static final double HARD_LIMIT = 3.0;
    /** A player higher than this above the play surface is on or over the wall and is put back. */
    static final double MAX_HEIGHT_ABOVE_FLOOR = 24.0;
    private static final int MESSAGE_COOLDOWN_TICKS = 60;
    private static final Map<UUID, Long> lastMessageTick = new HashMap<>();

    private CircleBoundary() {}

    /**
     * The walkable radius for an arena whose bounding box is {@code width} blocks wide: the designed floor radius,
     * or less for a smaller arena (half its width minus a 2 block margin), so a small test arena is still enforced.
     */
    static double radiusFor(int width) {
        return Math.min(PLAY_RADIUS, width / 2.0 - 2.0);
    }

    /** True when this arena is a designed circular map that this guard owns. */
    static boolean owns(ArenaInstance arena) {
        return arena != null && (arena.isMap() || !arena.inPlace()) && arena.level().dimension().equals(Dimensions.EXPEDITION);
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Integer slot = RunManager.slotOf(player);
            if (slot == null) {
                continue;
            }
            ArenaInstance arena = RunManager.getActive(slot);
            if (!owns(arena) || arena.level() != player.level()) {
                continue;
            }
            var b = arena.bounds();
            double cx = (b.minX() + b.maxX() + 1) / 2.0;
            double cz = (b.minZ() + b.maxZ() + 1) / 2.0;
            double floorY = b.minY();
            keepPlayerIn(player, arena, cx, cz, radiusFor(b.getXSpan()), floorY, now);
        }
    }

    private static void keepPlayerIn(ServerPlayer player, ArenaInstance arena, double cx, double cz, double radius, double floorY, long now) {
        double over = CircleMath.overshoot(cx, cz, player.getX(), player.getZ(), radius);
        boolean tooHigh = player.getY() > floorY + MAX_HEIGHT_ABOVE_FLOOR;
        if (over <= 0 && !tooHigh) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        double[] in = CircleMath.nearestInside(cx, cz, player.getX(), player.getZ(), radius, 2.0);
        if (over > HARD_LIMIT || tooHigh) {
            double y = ArenaBoundary.surfaceY(level, in[0], in[1], floorY + 2);
            player.teleportTo(level, in[0], y, in[1], java.util.Set.of(), player.getYRot(), player.getXRot(), true);
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
        } else {
            Vec3 push = new Vec3(in[0] - player.getX(), 0, in[1] - player.getZ()).normalize().scale(0.35 + over * 0.15);
            player.setDeltaMovement(push.x, Math.max(player.getDeltaMovement().y, 0.0), push.z);
            player.hurtMarked = true;
        }
        Long last = lastMessageTick.get(player.getUUID());
        if (last == null || now - last >= MESSAGE_COOLDOWN_TICKS) {
            lastMessageTick.put(player.getUUID(), now);
            player.displayClientMessage(Component.literal("\u00A76The ember wall holds you in."), true);
        }
    }
}
