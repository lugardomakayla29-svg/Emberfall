package com.solme.emberfall.pickup;

import com.solme.emberfall.world.RunManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Virtual pickups for in-run XP and Gold. A pickup is a plain record in a per-level list: no entity,
 * no vanilla experience orb, so a horde dying at once costs a few list entries instead of hundreds of
 * entities. Because nothing physical exists, nothing can be forged, duplicated or picked up by hoppers.
 *
 * Cost is bounded three ways: a hard cap per level (extra pickups merge into the nearest one of the same
 * kind, their values adding up), a lifetime after which a pickup simply expires, and particles that are
 * only sent for pickups near a run player.
 *
 * Gold is held per player for the length of one run and is wiped when the run ends (see {@link #clear}).
 */
public final class PickupSystem {
    public enum Kind { XP, GOLD }

    /** Live pickups per level before new ones start merging into existing ones. */
    static final int MAX_PER_LEVEL = 200;
    static final int LIFETIME_TICKS = 1200;      // 60s
    static final double MAGNET_RANGE = 6.0;
    static final double COLLECT_RANGE = 1.0;
    static final double MAGNET_SPEED = 0.55;     // blocks per tick, applied as a fraction of the gap
    static final int GLINT_EVERY_TICKS = 10;
    static final double GLINT_RANGE = 24.0;
    /** A merge target must be this close, otherwise a far pickup would teleport its value across the map. */
    static final double MERGE_RANGE = 3.0;

    private static final class Pickup {
        Vec3 pos;
        final Kind kind;
        int value;
        int age;

        Pickup(Vec3 pos, Kind kind, int value) {
            this.pos = pos;
            this.kind = kind;
            this.value = value;
        }
    }

    private static final Map<ServerLevel, List<Pickup>> pickups = new HashMap<>();
    private static final Map<UUID, Integer> gold = new HashMap<>();

    private PickupSystem() {}

    /** Drops a pickup of {@code value} at {@code pos}. Non-positive values are ignored. */
    public static void spawn(ServerLevel level, Vec3 pos, Kind kind, int value) {
        if (value <= 0) {
            return;
        }
        List<Pickup> list = pickups.computeIfAbsent(level, l -> new ArrayList<>());
        if (list.size() >= MAX_PER_LEVEL) {
            Pickup nearest = null;
            double best = MERGE_RANGE * MERGE_RANGE;
            for (Pickup other : list) {
                double d = other.pos.distanceToSqr(pos);
                if (other.kind == kind && d <= best) {
                    best = d;
                    nearest = other;
                }
            }
            if (nearest != null) {
                nearest.value += value;
                nearest.age = 0;
                return;
            }
            // Cap reached and nothing close to merge with: drop the oldest so the list never grows past the cap.
            list.remove(0);
        }
        list.add(new Pickup(pos, kind, value));
    }

    public static int gold(ServerPlayer player) {
        return gold.getOrDefault(player.getUUID(), 0);
    }

    /** Spends gold if the player has enough. Returns whether it was spent. */
    public static boolean spendGold(ServerPlayer player, int amount) {
        int have = gold(player);
        if (amount < 0 || have < amount) {
            return false;
        }
        gold.put(player.getUUID(), have - amount);
        return true;
    }

    /** Sets a player's gold outright (debug commands and refunds). Negative values are treated as zero. */
    public static void setGold(ServerPlayer player, int amount) {
        gold.put(player.getUUID(), Math.max(0, amount));
    }

    /** Wipes a player's gold. Called when their run ends: gold never carries over. */
    public static void clear(ServerPlayer player) {
        gold.remove(player.getUUID());
    }

    /** Drops every pickup in a level (run teardown). */
    public static void clearLevel(ServerLevel level) {
        pickups.remove(level);
    }

    /** Once per server tick. */
    public static void tick(MinecraftServer server) {
        if (pickups.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<ServerLevel, List<Pickup>>> levels = pickups.entrySet().iterator();
        while (levels.hasNext()) {
            Map.Entry<ServerLevel, List<Pickup>> entry = levels.next();
            ServerLevel level = entry.getKey();
            List<Pickup> list = entry.getValue();
            List<ServerPlayer> runners = new ArrayList<>();
            for (ServerPlayer p : level.players()) {
                if (p.isAlive() && RunManager.slotOf(p) != null) {
                    runners.add(p);
                }
            }
            tickLevel(level, list, runners, server.getTickCount());
            if (list.isEmpty()) {
                levels.remove();
            }
        }
    }

    private static void tickLevel(ServerLevel level, List<Pickup> list, List<ServerPlayer> runners, int tickCount) {
        boolean glint = tickCount % GLINT_EVERY_TICKS == 0;
        Iterator<Pickup> it = list.iterator();
        while (it.hasNext()) {
            Pickup pickup = it.next();
            if (++pickup.age > LIFETIME_TICKS) {
                it.remove();
                continue;
            }
            ServerPlayer target = null;
            double best = Double.MAX_VALUE;
            for (ServerPlayer p : runners) {
                double d = p.position().distanceToSqr(pickup.pos);
                if (d < best) {
                    best = d;
                    target = p;
                }
            }
            if (target == null) {
                continue;
            }
            double dist = Math.sqrt(best);
            if (dist <= COLLECT_RANGE + 0.6) { // aim point is chest height, so allow for it
                collect(level, target, pickup);
                it.remove();
                continue;
            }
            // Magnet Stone widens the pull. A direct stack read: this runs for every pickup every tick, so no allocation here.
            if (dist <= MAGNET_RANGE * com.solme.emberfall.relic.RelicStats.pickupRangeMultiplier(com.solme.emberfall.relic.PlayerRelics.stacks(target, "magnet_stone"))) {
                Vec3 aim = target.position().add(0.0, 0.9, 0.0);
                Vec3 gap = aim.subtract(pickup.pos);
                // Speed rises as it gets closer so a pickup does not crawl the last block.
                double pull = Math.min(1.0, MAGNET_SPEED / Math.max(0.5, gap.length()) + 0.15);
                pickup.pos = pickup.pos.add(gap.scale(pull));
            }
            if (glint && dist <= GLINT_RANGE) {
                if (pickup.kind == Kind.XP) {
                    level.sendParticles(ParticleTypes.END_ROD, pickup.pos.x, pickup.pos.y + 0.3, pickup.pos.z, 1, 0.1, 0.1, 0.1, 0.0);
                } else {
                    level.sendParticles(ParticleTypes.WAX_ON, pickup.pos.x, pickup.pos.y + 0.3, pickup.pos.z, 1, 0.1, 0.1, 0.1, 0.0);
                }
            }
        }
    }

    private static void collect(ServerLevel level, ServerPlayer player, Pickup pickup) {
        if (pickup.kind == Kind.XP) {
            player.giveExperiencePoints(pickup.value);
            player.sendSystemMessage(Component.literal("+" + pickup.value + " XP"), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.6F);
        } else {
            gold.merge(player.getUUID(), pickup.value, Integer::sum);
            player.sendSystemMessage(Component.literal("+" + pickup.value + " Gold"), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.4F);
        }
    }
}
