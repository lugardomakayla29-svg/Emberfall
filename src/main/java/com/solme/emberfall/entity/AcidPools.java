package com.solme.emberfall.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Lingering acid puddles left where a Spitter's spit lands. Pure data plus particles: no entity, no block.
 *
 * <p>Why not reuse {@link ScorchedGround}: that one is fire (flame particles, hot-floor damage), its list is unbounded,
 * nothing clears it when a run ends, and every overlapping patch deals its own damage. This class fixes all three,
 * because a wave of Spitters can spit every few seconds:</p>
 * <ul>
 *   <li>at most {@value #MAX_POOLS} pools exist at once per server (the oldest is dropped first);</li>
 *   <li>a new pool that lands within {@value #MERGE_DISTANCE} blocks of a live one of the same level refreshes it
 *       instead of adding another, so a player is never hit by stacked copies;</li>
 *   <li>a player takes at most one tick of damage per second from ALL pools together;</li>
 *   <li>{@link #clearLevel} drops a level's pools when its run ends.</li>
 * </ul>
 */
public final class AcidPools {
    private AcidPools() {}

    public static final int MAX_POOLS = 24;
    public static final double MERGE_DISTANCE = 1.2;
    public static final int LIFETIME_TICKS = 100;        // 5 s
    public static final double RADIUS = 1.6;
    public static final float DAMAGE_PER_SECOND = 2.0F;
    public static final int SLOW_TICKS = 40;

    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");
    private static final List<Pool> ACTIVE = new ArrayList<>();

    private static final class Pool {
        final ServerLevel level;
        final Vec3 pos;
        final LivingEntity owner;
        long expiresAtTick;

        Pool(ServerLevel level, Vec3 pos, LivingEntity owner, long expiresAtTick) {
            this.level = level;
            this.pos = pos;
            this.owner = owner;
            this.expiresAtTick = expiresAtTick;
        }
    }

    /** Leaves a puddle at {@code pos}, or refreshes a nearby one. Returns true if a NEW pool was added. */
    public static boolean spawn(ServerLevel level, Vec3 pos, LivingEntity owner) {
        long expires = level.getGameTime() + LIFETIME_TICKS;
        for (Pool pool : ACTIVE) {
            if (pool.level == level && pool.pos.distanceToSqr(pos) <= MERGE_DISTANCE * MERGE_DISTANCE) {
                pool.expiresAtTick = expires;
                if (TEST_MODE) {
                    com.solme.emberfall.EmberfallMod.LOGGER.info("SPITTER_TEST pool refresh total={}", ACTIVE.size());
                }
                return false;
            }
        }
        if (ACTIVE.size() >= MAX_POOLS) {
            ACTIVE.remove(0);                            // oldest first: the list is in creation order
        }
        ACTIVE.add(new Pool(level, pos, owner, expires));
        if (TEST_MODE) {
            com.solme.emberfall.EmberfallMod.LOGGER.info("SPITTER_TEST pool add total={}", ACTIVE.size());
        }
        return true;
    }

    public static int count() {
        return ACTIVE.size();
    }

    /** Called when a run's arena level is torn down, so no puddle outlives its run. */
    public static void clearLevel(ServerLevel level) {
        ACTIVE.removeIf(p -> p.level == level);
    }

    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Pool> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Pool pool = it.next();
            long now = pool.level.getGameTime();
            if (now >= pool.expiresAtTick) {
                it.remove();
                continue;
            }
            if ((now & 3L) == 0L) {
                pool.level.sendParticles(ParticleTypes.ITEM_SLIME, pool.pos.x, pool.pos.y + 0.1, pool.pos.z,
                        3, RADIUS * 0.45, 0.03, RADIUS * 0.45, 0.0);
                pool.level.sendParticles(ParticleTypes.SNEEZE, pool.pos.x, pool.pos.y + 0.15, pool.pos.z,
                        1, RADIUS * 0.4, 0.02, RADIUS * 0.4, 0.0);
            }
        }
        // Damage is applied per PLAYER, once a second, however many pools overlap them.
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Player player : level.players()) {
                if (!player.isAlive() || player.isSpectator()) {
                    continue;
                }
                Pool hit = null;
                for (Pool pool : ACTIVE) {
                    if (pool.level != level) {
                        continue;
                    }
                    AABB box = new AABB(pool.pos.x - RADIUS, pool.pos.y - 0.5, pool.pos.z - RADIUS,
                            pool.pos.x + RADIUS, pool.pos.y + 1.0, pool.pos.z + RADIUS);
                    if (box.intersects(player.getBoundingBox())) {
                        hit = pool;
                        break;
                    }
                }
                if (hit == null) {
                    continue;
                }
                LivingEntity owner = hit.owner != null && hit.owner.isAlive() ? hit.owner : null;
                net.minecraft.world.damagesource.DamageSource source = owner != null
                        ? level.damageSources().mobAttack(owner) : level.damageSources().generic();
                player.hurtServer(level, source, DAMAGE_PER_SECOND);
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 0, false, true));
                if (TEST_MODE) {
                    com.solme.emberfall.EmberfallMod.LOGGER.info("SPITTER_TEST pool burn");
                }
            }
        }
    }
}
