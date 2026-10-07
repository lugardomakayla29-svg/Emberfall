package com.solme.emberfall.entity;

import net.minecraft.core.particles.DustParticleOptions;
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
 * Sticky pink slime left by the {@link PinkSlime}: its fading trail, its spit splats, its slam, and the big puddle it
 * bursts into when it dies. Pure data plus particles: no entity, no block.
 *
 * <p>A separate class from {@link AcidPools} on purpose: the Spitter's acid is tuned and tested (radius, lifetime,
 * damage), and this slime needs two sizes of puddle with their own look. The safety rules are the same, because a
 * slime that leaves a trail every few ticks could otherwise flood the server:</p>
 * <ul>
 *   <li>at most {@value #MAX_POOLS} pools exist at once (oldest dropped first);</li>
 *   <li>a new pool within the merge distance of a live one of the same level refreshes it (and keeps the LARGER
 *       radius and the LATER expiry), so overlapping splats never stack;</li>
 *   <li>a player takes at most one tick of damage per second from ALL pink pools together;</li>
 *   <li>{@link #clearLevel} drops a level's pools when its run ends.</li>
 * </ul>
 */
public final class PinkPools {
    private PinkPools() {}

    public static final int MAX_POOLS = 20;
    public static final double MERGE_DISTANCE = 1.0;
    public static final int SLOW_TICKS = 50;
    /** Pink to match the rosie slime texture (average 241,115,184). */
    public static final int PINK = 0xF173B8;

    public static final int TRAIL_LIFETIME = 50;      // 2.5 s: a trail that fades
    public static final double TRAIL_RADIUS = 1.1;
    public static final float TRAIL_DAMAGE = 1.0F;

    public static final int SPLAT_LIFETIME = 80;      // 4 s
    public static final double SPLAT_RADIUS = 1.5;
    public static final float SPLAT_DAMAGE = 2.0F;

    public static final int BURST_LIFETIME = 120;     // 6 s: the death puddle
    public static final double BURST_RADIUS = 3.2;
    public static final float BURST_DAMAGE = 2.0F;

    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");
    private static final DustParticleOptions DUST = new DustParticleOptions(PINK, 1.2F);
    private static final List<Pool> ACTIVE = new ArrayList<>();

    private static final class Pool {
        final ServerLevel level;
        final Vec3 pos;
        final LivingEntity owner;
        long expiresAtTick;
        long spawnedAtTick;
        double radius;
        float damage;

        Pool(ServerLevel level, Vec3 pos, LivingEntity owner, long now, long expires, double radius, float damage) {
            this.level = level;
            this.pos = pos;
            this.owner = owner;
            this.spawnedAtTick = now;
            this.expiresAtTick = expires;
            this.radius = radius;
            this.damage = damage;
        }
    }

    /** A thin trail puddle under a moving slime. Returns true if a NEW pool was added. */
    public static boolean trail(ServerLevel level, Vec3 pos, LivingEntity owner) {
        return add(level, pos, owner, TRAIL_LIFETIME, TRAIL_RADIUS, TRAIL_DAMAGE, "trail");
    }

    /** Where a spat ball landed. */
    public static boolean splat(ServerLevel level, Vec3 pos, LivingEntity owner) {
        return add(level, pos, owner, SPLAT_LIFETIME, SPLAT_RADIUS, SPLAT_DAMAGE, "splat");
    }

    /** The large puddle a dying slime bursts into. */
    public static boolean burst(ServerLevel level, Vec3 pos, LivingEntity owner) {
        return add(level, pos, owner, BURST_LIFETIME, BURST_RADIUS, BURST_DAMAGE, "burst");
    }

    private static boolean add(ServerLevel level, Vec3 pos, LivingEntity owner, int lifetime, double radius,
                               float damage, String kind) {
        long now = level.getGameTime();
        long expires = now + lifetime;
        for (Pool pool : ACTIVE) {
            if (pool.level == level && pool.pos.distanceToSqr(pos) <= MERGE_DISTANCE * MERGE_DISTANCE) {
                if (expires > pool.expiresAtTick) {
                    pool.expiresAtTick = expires;
                }
                if (radius > pool.radius) {
                    pool.radius = radius;
                    pool.damage = Math.max(pool.damage, damage);
                }
                if (TEST_MODE) {
                    com.solme.emberfall.EmberfallMod.LOGGER.info("PINK_TEST pool refresh kind={} total={}", kind, ACTIVE.size());
                }
                return false;
            }
        }
        if (ACTIVE.size() >= MAX_POOLS) {
            ACTIVE.remove(0);                        // oldest first: the list is in creation order
        }
        ACTIVE.add(new Pool(level, pos, owner, now, expires, radius, damage));
        if (TEST_MODE) {
            com.solme.emberfall.EmberfallMod.LOGGER.info("PINK_TEST pool add kind={} total={}", kind, ACTIVE.size());
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
                // Fade: the puddle thins out as it nears its end, so a trail visibly dries up.
                long total = Math.max(1L, pool.expiresAtTick - pool.spawnedAtTick);
                double life = (pool.expiresAtTick - now) / (double) total;
                int dust = Math.max(1, (int) Math.round(4 * life * Math.min(2.0, pool.radius)));
                pool.level.sendParticles(DUST, pool.pos.x, pool.pos.y + 0.08, pool.pos.z,
                        dust, pool.radius * 0.45, 0.02, pool.radius * 0.45, 0.0);
                if (life > 0.5) {
                    pool.level.sendParticles(PinkSlime.pinkGlob(), pool.pos.x, pool.pos.y + 0.1, pool.pos.z,
                            1, pool.radius * 0.4, 0.02, pool.radius * 0.4, 0.0);
                }
            }
        }
        // Damage is applied per PLAYER, once a second, however many pools overlap them.
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            // Snapshot: hurtServer can kill the last player, which moves them out of this level and ends the run mid-iteration.
            for (Player player : new java.util.ArrayList<>(level.players())) {
                if (!player.isAlive() || player.isSpectator()) {
                    continue;
                }
                Pool hit = null;
                for (Pool pool : ACTIVE) {
                    if (pool.level != level) {
                        continue;
                    }
                    AABB box = new AABB(pool.pos.x - pool.radius, pool.pos.y - 0.5, pool.pos.z - pool.radius,
                            pool.pos.x + pool.radius, pool.pos.y + 1.0, pool.pos.z + pool.radius);
                    if (box.intersects(player.getBoundingBox()) && (hit == null || pool.damage > hit.damage)) {
                        hit = pool;              // the worst overlapping pool decides, they never add up
                    }
                }
                if (hit == null) {
                    continue;
                }
                LivingEntity owner = hit.owner != null && hit.owner.isAlive() ? hit.owner : null;
                net.minecraft.world.damagesource.DamageSource source = owner != null
                        ? level.damageSources().mobAttack(owner) : level.damageSources().generic();
                player.hurtServer(level, source, hit.damage);
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 1, false, true));
                if (TEST_MODE) {
                    com.solme.emberfall.EmberfallMod.LOGGER.info("PINK_TEST pool burn damage={}", hit.damage);
                }
            }
        }
    }
}
