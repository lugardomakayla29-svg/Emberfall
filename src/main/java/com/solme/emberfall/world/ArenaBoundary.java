package com.solme.emberfall.world;

import com.solme.emberfall.combat.AutoAttackSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps a run inside its play area. The arena box has always existed (spawning and the horde cap use it), but
 * nothing stopped anybody leaving it.
 *
 * Players get a soft wall: a curtain of particles appears when they come within {@link #WARN_BAND} blocks of an
 * edge, and crossing the edge pushes them back inward (a velocity push, then a hard clamp if they are already
 * well outside, so a fall or a knockback can never carry them out for good).
 *
 * Mobs are checked once a second with ONE entity query per run (no per-mob ticking). A mob outside the box is
 * moved back to the nearest inside point on its own height, so nothing wanders off and leaves the fight, and a
 * knocked-back mob or a boss cannot end up stranded.
 *
 * Only in-place runs are enforced: a pasted arena structure already has physical walls.
 */
public final class ArenaBoundary {
    /** Blocks from an edge at which the particle curtain starts to show. */
    static final double WARN_BAND = 5.0;
    /** Blocks past the edge the player is allowed before being clamped straight back in. */
    static final double HARD_LIMIT = 3.0;
    private static final int MOB_CHECK_EVERY_TICKS = 20;
    private static final int CURTAIN_EVERY_TICKS = 5;
    private static final int MESSAGE_COOLDOWN_TICKS = 60;

    private static final DustParticleOptions WALL_DUST = new DustParticleOptions(0xFF6A2A, 1.2F);

    private static final Map<UUID, Long> lastMessageTick = new HashMap<>();

    private ArenaBoundary() {}

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        boolean mobPass = now % MOB_CHECK_EVERY_TICKS == 0;
        boolean curtainPass = now % CURTAIN_EVERY_TICKS == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Integer slot = RunManager.slotOf(player);
            if (slot == null) {
                continue;
            }
            ArenaInstance arena = RunManager.getActive(slot);
            if (arena == null || !arena.inPlace() || arena.isMap() || arena.level() != player.level()) {
                continue;
            }
            AABB box = playArea(arena);
            keepPlayerIn(player, box, now, curtainPass);
            if (mobPass) {
                pullMobsBack((ServerLevel) player.level(), box);
            }
        }
    }

    /** The playable box, in block-edge coordinates (max is exclusive, like an AABB). */
    static AABB playArea(ArenaInstance arena) {
        var b = arena.bounds();
        return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
    }

    /** How far outside the box a point is on the worst axis: negative or 0 when inside. Horizontal only. */
    static double overshoot(AABB box, double x, double z) {
        double dx = Math.max(box.minX - x, x - box.maxX);
        double dz = Math.max(box.minZ - z, z - box.maxZ);
        return Math.max(dx, dz);
    }

    /** Where a point is pulled to: the nearest point inside the box, kept {@code inset} from the edge. */
    static double[] nearestInside(AABB box, double x, double z, double inset) {
        double nx = Math.min(Math.max(x, box.minX + inset), box.maxX - inset);
        double nz = Math.min(Math.max(z, box.minZ + inset), box.maxZ - inset);
        return new double[]{nx, nz};
    }

    private static void keepPlayerIn(ServerPlayer player, AABB box, long now, boolean curtainPass) {
        double over = overshoot(box, player.getX(), player.getZ());
        if (over > 0) {
            double[] in = nearestInside(box, player.getX(), player.getZ(), 1.5);
            if (over > HARD_LIMIT) {
                // Carried far out (fall, ender pearl, big knockback): put them back at the edge on solid ground.
                ServerLevel level = (ServerLevel) player.level();
                double y = surfaceY(level, in[0], in[1], player.getY());
                player.teleportTo(level, in[0], y, in[1], java.util.Set.of(), player.getYRot(), player.getXRot(), true);
                player.setDeltaMovement(Vec3.ZERO);
            } else {
                // Just over the line: a firm push back inward, proportional to how far over they are.
                Vec3 push = new Vec3(in[0] - player.getX(), 0, in[1] - player.getZ()).normalize().scale(0.35 + over * 0.15);
                player.setDeltaMovement(push.x, Math.max(player.getDeltaMovement().y, 0.0), push.z);
                player.hurtMarked = true;
            }
            notifyOnce(player, now);
        }
        if (curtainPass && over > -WARN_BAND) {
            drawCurtain((ServerLevel) player.level(), player, box);
        }
    }

    private static void notifyOnce(ServerPlayer player, long now) {
        Long last = lastMessageTick.get(player.getUUID());
        if (last == null || now - last >= MESSAGE_COOLDOWN_TICKS) {
            lastMessageTick.put(player.getUUID(), now);
            player.displayClientMessage(Component.literal("\u00A76The ember wall holds you in."), true);
        }
    }

    /** Draws a small wall of dust on each edge the player is near, around their height. Few particles, near them only. */
    private static void drawCurtain(ServerLevel level, ServerPlayer player, AABB box) {
        double px = player.getX();
        double pz = player.getZ();
        double baseY = player.getY();
        if (px - box.minX < WARN_BAND) {
            wallX(level, box.minX, pz, baseY);
        }
        if (box.maxX - px < WARN_BAND) {
            wallX(level, box.maxX, pz, baseY);
        }
        if (pz - box.minZ < WARN_BAND) {
            wallZ(level, box.minZ, px, baseY);
        }
        if (box.maxZ - pz < WARN_BAND) {
            wallZ(level, box.maxZ, px, baseY);
        }
    }

    private static void wallX(ServerLevel level, double x, double aroundZ, double baseY) {
        for (int dz = -4; dz <= 4; dz += 2) {
            for (int dy = 0; dy <= 3; dy += 1) {
                level.sendParticles(WALL_DUST, x, baseY + dy, aroundZ + dz, 1, 0, 0, 0, 0);
            }
        }
    }

    private static void wallZ(ServerLevel level, double z, double aroundX, double baseY) {
        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dy = 0; dy <= 3; dy += 1) {
                level.sendParticles(WALL_DUST, aroundX + dx, baseY + dy, z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** One query for the whole run: every emberfall mob within a generous margin of the box that is outside it. */
    private static void pullMobsBack(ServerLevel level, AABB box) {
        AABB search = box.inflate(48.0, 64.0, 48.0);
        List<Mob> strays = level.getEntitiesOfClass(Mob.class, search,
                m -> m.isAlive() && AutoAttackSystem.isEmberfallHostile(m)
                        && overshoot(box, m.getX(), m.getZ()) > 0);
        for (Mob mob : strays) {
            double[] in = nearestInside(box, mob.getX(), mob.getZ(), 2.0);
            double y = surfaceY(level, in[0], in[1], mob.getY());
            mob.teleportTo(in[0], y, in[1]);
            mob.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** Feet height at (x,z): the first standable spot at/under {@code nearY}, else {@code nearY} itself. */
    static double surfaceY(ServerLevel level, double x, double z, double nearY) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int start = (int) Math.floor(nearY) + 6;
        int min = Math.max(level.getMinY(), (int) Math.floor(nearY) - 24);
        for (int y = start; y >= min; y--) {
            BlockPos below = new BlockPos(bx, y - 1, bz);
            BlockPos feet = new BlockPos(bx, y, bz);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                    && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                    && level.getFluidState(feet).isEmpty()) {
                return y;
            }
        }
        return nearY;
    }

    /** Debug/test: "minX,minZ,maxX,maxZ|overshoot" for this player's run, or "none". */
    public static String describe(ServerPlayer player) {
        Integer slot = RunManager.slotOf(player);
        ArenaInstance arena = slot == null ? null : RunManager.getActive(slot);
        if (arena == null) {
            return "none";
        }
        AABB box = playArea(arena);
        return String.format("%.0f,%.0f,%.0f,%.0f|%.2f|%b", box.minX, box.minZ, box.maxX, box.maxZ,
                overshoot(box, player.getX(), player.getZ()), arena.inPlace());
    }

    public static void clear(ServerPlayer player) {
        lastMessageTick.remove(player.getUUID());
    }
}
