package com.solme.emberfall.combat;

import com.solme.emberfall.item.Loadout;
import com.solme.emberfall.item.WeaponGrowth;
import com.solme.emberfall.item.WeaponProgress;
import com.solme.emberfall.world.DelayedTasks;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Arcane Staff's growth. A staff shot was one bolt at the nearest foe and a nova on every 4th. Levels make it a caster:
 *
 * MULTIBOLT, fractional: a shot fires 1.0 + 0.2 per level above 1 bolts (1.0 at level 1, 2.8 at level 10). 1.5 means one bolt and a 50%
 * chance of a second ({@link WeaponGrowth#roll}). Each extra bolt flies at another foe, and EACH ONE can proc the nova.
 * NOVA GROWS: radius 3.5 to 5.5, and every ordinary bolt that lands has a 4% + 2% per level chance of a small detonation of its own, so
 * the 4th-shot nova stops being the only AOE.
 * PURPLE FIRE: the staff's bolts trail witch magic and soul fire instead of crit stars ({@link #TRAIL}).
 *
 * ULTIMATE, Starfall: the meter fills from the staff's own hits and kills. When full, 6 + level stars fall over the next seconds, each on
 * the densest remaining cluster in reach, a detonation of radius 2.5 for 2.0 + 0.2 per level times the bolt. It fires on its own.
 *
 * NO EXTRA ENTITIES: bolts are the existing tracked projectiles, detonations are instant, stars are tasks on the bounded
 * {@link DelayedTasks} queue. Kills are credited to the staff by re-entering its slot with {@link Loadout#acting}.
 */
public final class StaffSystem {
    /** Purple fire, used by fireBolt for the staff only. */
    public static final ParticleOptions TRAIL = ParticleTypes.WITCH;
    public static final double BOLTS_PER_LEVEL = 0.2;
    public static final double NOVA_RADIUS_BASE = 3.5;
    public static final double NOVA_RADIUS_PER_LEVEL = 2.0 / 9.0;
    // IMPACT BURST: every ordinary bolt that lands bursts around its target (the old 4% + 2% per level chance of a small blast is gone).
    public static final double BURST_RADIUS_BASE = 2.75;
    public static final double BURST_RADIUS_PER_LEVEL = 0.75 / 9.0;   // 2.75 at level 1 to 3.5 at level 10
    public static final double BURST_MULTIPLE = 0.6;                  // of the bolt, on the OTHER foes; the target already took the full hit
    public static final double RANGE_PER_LEVEL = 2.0 / 9.0;
    public static final int STAR_BASE = 6;
    public static final double STAR_MULTIPLE_BASE = 2.0;
    public static final double STAR_MULTIPLE_PER_LEVEL = 0.2;
    public static final double STAR_RADIUS = 2.5;
    public static final int STAR_STEP_TICKS = 6;

    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");

    private StaffSystem() {}

    /** Extra reach for a staff of this level: 0 at level 1 up to 2.0 at level 10. */
    public static double rangeBonus(int level) {
        return WeaponGrowth.scale(0.0, RANGE_PER_LEVEL, level);
    }

    /** Average bolts per shot: 1.0 at level 1 up to 2.8 at level 10. */
    public static double boltsPerShot(int level) {
        return WeaponGrowth.scale(1.0, BOLTS_PER_LEVEL, level);
    }

    public static double novaRadius(int level) {
        return WeaponGrowth.scale(NOVA_RADIUS_BASE, NOVA_RADIUS_PER_LEVEL, level);
    }

    public static double burstRadius(int level) {
        return WeaponGrowth.scale(BURST_RADIUS_BASE, BURST_RADIUS_PER_LEVEL, level);
    }

    public static double starMultiple(int level) {
        return WeaponGrowth.scale(STAR_MULTIPLE_BASE, STAR_MULTIPLE_PER_LEVEL, level);
    }

    public static int starCount(int level) {
        return STAR_BASE + Math.max(1, Math.min(level, WeaponGrowth.MAX_LEVEL));
    }

    /** How many EXTRA bolts (beyond the aimed one) this shot fires: the whole part of boltsPerShot minus one, plus the fractional roll. */
    public static int extraBolts(int level, ServerLevel world, net.minecraft.server.level.ServerPlayer player) {
        return Math.max(0, WeaponGrowth.roll(boltsPerShot(level) + com.solme.emberfall.relic.RelicTempoGame.bonusStrikes(player), AutoAttackSystem.levelRandom(world)) - 1);
    }

    /**
     * The impact burst of a landed ordinary bolt or arrow: every OTHER live hostile within {@code radius} of {@code centre.position()} takes
     * {@code damage}, with the usual on-hit effects. The struck foe is skipped because it already took the direct hit. Returns how many
     * foes the burst reached. {@code ring} picks the particle: witch magic for the staff, crit for the bow.
     */
    public static int burst(ServerLevel level, ServerPlayer player, LivingEntity centre, double radius, float damage, ParticleOptions ring) {
        Vec3 at = centre.position();
        Fx.impactRing(level, at, radius, ring);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F, 1.7F);
        int hitCount = 0;
        for (Mob mob : foesIn(level, at, radius, centre)) {
            float before = mob.getHealth();
            AutoAttackSystem.clearInvulnerabilityWindow(mob);
            mob.hurtServer(level, player.damageSources().playerAttack(player), damage);
            OnHitEffects.apply(player, mob, Math.max(0.0F, before - mob.getHealth()));
            hitCount++;
        }
        if (TEST_MODE) { // sandbox test server only (-Demberfall.testMode); splash_test judges radius and reach from this line
            com.solme.emberfall.EmberfallMod.LOGGER.info("SPLASH_TEST burst radius={} reached={} damage={}", String.format(java.util.Locale.ROOT, "%.2f", radius), hitCount, String.format(java.util.Locale.ROOT, "%.2f", damage));
        }
        return hitCount;
    }

    /** Foes in reach, nearest first, other than {@code skip}. Used to aim the extra bolts. */
    public static List<Mob> foesIn(ServerLevel level, Vec3 centre, double reach, LivingEntity skip) {
        List<Mob> found = new ArrayList<>(level.getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(reach),
                m -> m != skip && m.isAlive() && AutoAttackSystem.isEmberfallHostile(m) && m.position().distanceTo(centre) <= reach));
        found.sort(Comparator.comparingDouble(m -> m.position().distanceTo(centre)));
        return found;
    }

    /**
     * Adds meter for a landed bolt and, if the meter is now full, starts Starfall. Runs inside the credited (deferred) impact, so the staff
     * is the acting slot.
     */
    public static void afterImpact(ServerLevel level, ServerPlayer player, float damage, double reach) {
        if (damage <= 0.0F) {
            return;
        }
        WeaponProgress.onHit(player);
        if (WeaponProgress.ultimateReady(player) && WeaponProgress.consumeUltimate(player)) {
            starfall(level, player, WeaponProgress.levelOf(player), damage, reach, Loadout.actingIndexFor(player));
        }
    }

    /** Queues the stars. Returns how many were queued. */
    static int starfall(ServerLevel level, ServerPlayer player, int lvl, float baseDamage, double reach, int slotIndex) {
        float damage = (float) (baseDamage * starMultiple(lvl));
        int stars = starCount(lvl);
        int queued = 0;
        for (int i = 0; i < stars; i++) {
            if (DelayedTasks.runLater(level, STAR_STEP_TICKS * (i + 1), o -> star(level, player, damage, reach, slotIndex))) {
                queued++;
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.6F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.6, 0.8, 0.6, 0.1);
        return queued;
    }

    /** One star: pick the densest cluster still alive, drop a purple detonation on it. Does nothing if no foe is in reach. */
    private static void star(ServerLevel level, ServerPlayer player, float damage, double reach, int slotIndex) {
        if (!player.isAlive()) {
            return;
        }
        List<Mob> foes = foesIn(level, player.position(), reach, null);
        if (foes.isEmpty()) {
            return;
        }
        Mob centre = foes.get(0);
        int bestCount = -1;
        for (Mob a : foes) {
            int n = 0;
            for (Mob b : foes) {
                if (a.position().distanceTo(b.position()) <= STAR_RADIUS) {
                    n++;
                }
            }
            if (n > bestCount) {
                bestCount = n;
                centre = a;
            }
        }
        final Vec3 at = centre.position();
        Loadout.acting(player, slotIndex, () -> detonate(level, player, at, STAR_RADIUS, damage));
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 8.0, at.z, 6, 0.1, 3.0, 0.1, 0.0);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.3, at.z, 20, radiusSpread(STAR_RADIUS), 0.2, radiusSpread(STAR_RADIUS), 0.02);
    }

    private static double radiusSpread(double radius) {
        return radius * 0.5;
    }

    /** A plain detonation: every live hostile within {@code radius} of {@code at} takes {@code damage}, with the usual on-hit effects. */
    static int detonate(ServerLevel level, ServerPlayer player, Vec3 at, double radius, float damage) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 1.5F);
        Fx.impactRing(level, at, radius, ParticleTypes.WITCH);
        int hitCount = 0;
        for (Mob mob : foesIn(level, at, radius, null)) {
            float before = mob.getHealth();
            AutoAttackSystem.clearInvulnerabilityWindow(mob);
            mob.hurtServer(level, player.damageSources().playerAttack(player), damage);
            OnHitEffects.apply(player, mob, Math.max(0.0F, before - mob.getHealth()));
            hitCount++;
        }
        return hitCount;
    }
}
