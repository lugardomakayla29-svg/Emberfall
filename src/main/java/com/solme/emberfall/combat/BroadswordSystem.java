package com.solme.emberfall.combat;

import com.solme.emberfall.item.WeaponGrowth;
import com.solme.emberfall.item.WeaponProgress;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * The Broadsword's growth. The plain hit on the nearest foe is unchanged (vanilla {@code player.attack}, so every tome and
 * bonus still applies). On top of it the blade SWEEPS: an arc in front of the wielder that hits every other foe inside it, and
 * that arc, its reach, its splash damage and the number of extra sweeps all grow with the weapon's level.
 *
 * Per level above 1: +16.7 degrees of arc, +0.15 reach, +5% splash damage, +0.2 extra sweeps. Extra sweeps are a fractional
 * count (see {@link WeaponGrowth#roll}): 1.8 means one guaranteed extra sweep and an 80% chance of a second. Each extra sweep
 * repeats the whole arc a few ticks later and widens it a little, so a high level reads as a flurry rather than one slash.
 *
 * ULTIMATE, Sunbrand Sweep: when the meter is full the blade spins a full circle that hits EVERY foe within a radius that also
 * grows with level, for a multiple of the sword's damage, and knocks them outward. It fires on its own, never needs a key.
 *
 * Numbers live in the static methods below so the tests and the balance pass read the same source of truth.
 */
public final class BroadswordSystem {
    public static final double BASE_ARC_DEG = 150.0;
    public static final double ARC_PER_LEVEL_DEG = 150.0 / 9.0;  // 150 at level 1, 300 at level 10
    public static final double REACH_PER_LEVEL = 0.15;
    public static final double SPLASH_BASE = 0.50;
    public static final double SPLASH_PER_LEVEL = 0.05;
    public static final double EXTRA_SWEEPS_PER_LEVEL = 0.2;
    public static final int SWEEP_GAP_TICKS = 4;
    public static final double ULT_RADIUS_BASE = 5.0;
    public static final double ULT_RADIUS_PER_LEVEL = 0.35;
    public static final double ULT_DAMAGE_BASE = 3.0;             // multiples of the sword's own attack damage
    public static final double ULT_DAMAGE_PER_LEVEL = 0.25;

    private BroadswordSystem() {}

    public static double arcDegrees(int level) {
        return WeaponGrowth.scale(BASE_ARC_DEG, ARC_PER_LEVEL_DEG, level);
    }

    public static double reachBonus(int level) {
        return WeaponGrowth.scale(0.0, REACH_PER_LEVEL, level);
    }

    public static double splashFraction(int level) {
        return WeaponGrowth.scale(SPLASH_BASE, SPLASH_PER_LEVEL, level);
    }

    public static double extraSweeps(int level) {
        return WeaponGrowth.scale(0.0, EXTRA_SWEEPS_PER_LEVEL, level);
    }

    public static double ultimateRadius(int level) {
        return WeaponGrowth.scale(ULT_RADIUS_BASE, ULT_RADIUS_PER_LEVEL, level);
    }

    public static double ultimateDamageMultiple(int level) {
        return WeaponGrowth.scale(ULT_DAMAGE_BASE, ULT_DAMAGE_PER_LEVEL, level);
    }

    /**
     * Called by the dispatcher after the Broadsword's normal hit on {@code primary}. Runs the level sweeps, then fires the
     * ultimate if the meter is full. {@code swordDamage} is the damage one plain hit deals, the base for every splash.
     */
    public static void afterHit(ServerLevel level, ServerPlayer player, LivingEntity primary, double baseReach, float swordDamage) {
        int lvl = WeaponProgress.levelOf(player);
        WeaponProgress.onHit(player);

        double reach = baseReach + reachBonus(lvl);
        double arc = Math.toRadians(arcDegrees(lvl));
        sweep(level, player, primary, reach, arc, (float) (swordDamage * splashFraction(lvl)));

        net.minecraft.util.RandomSource mc = level.getRandom();
        int extra = WeaponGrowth.roll(extraSweeps(lvl) + com.solme.emberfall.relic.RelicTempoGame.bonusStrikes(player), new java.util.random.RandomGenerator() {
            @Override public long nextLong() { return mc.nextLong(); }
            @Override public double nextDouble() { return mc.nextDouble(); }
        });
        for (int i = 1; i <= extra; i++) {
            final int n = i;
            final double widen = 1.0 + 0.12 * n;
            final float echoDamage = (float) (swordDamage * splashFraction(lvl) * Echo.lift(lvl));
            com.solme.emberfall.world.DelayedTasks.runLater(level, SWEEP_GAP_TICKS * n,
                    com.solme.emberfall.item.Loadout.deferred(player, ignored -> {
                        if (player.isAlive()) {
                            sweep(level, player, primary, reach * widen, Math.min(Math.PI * 2, arc * widen), echoDamage);
                            // Echo: the sweep skips the primary (it took the real hit), so an echo strikes it again, which is what makes a lone foe feel levels.
                            if (primary.isAlive()) {
                                float before = primary.getHealth();
                                AutoAttackSystem.clearInvulnerabilityWindow(primary);
                                primary.hurtServer(level, player.damageSources().playerAttack(player), echoDamage);
                                OnHitEffects.apply(player, primary, Math.max(0.0F, before - primary.getHealth()));
                            }
                        }
                    }));
        }

        if (WeaponProgress.ultimateReady(player) && WeaponProgress.consumeUltimate(player)) {
            sunbrandSweep(level, player, lvl, swordDamage);
        }
    }

    /** Hits every foe inside the forward arc except {@code primary}, which already took the real hit. Returns how many were hit. */
    static int sweep(ServerLevel level, ServerPlayer player, LivingEntity primary, double reach, double arcRad, float splashDamage) {
        Vec3 origin = player.position();
        Vec3 facing = facing(player, primary);
        List<Mob> foes = level.getEntitiesOfClass(Mob.class, new AABB(origin, origin).inflate(reach),
                m -> m.isAlive() && m != primary && AutoAttackSystem.isTargetable(m)
                        && inArc(origin, facing, m.position(), reach, arcRad));
        drawArc(level, player, facing, reach, arcRad);
        for (Mob m : foes) {
            float before = m.getHealth();
            AutoAttackSystem.clearInvulnerabilityWindow(m);
            m.hurtServer(level, player.damageSources().playerAttack(player), splashDamage);
            OnHitEffects.apply(player, m, Math.max(0.0F, before - m.getHealth()));
        }
        return foes.size();
    }

    /** The ultimate: a full circle at {@link #ultimateRadius}, every foe takes a multiple of the sword's damage and is shoved outward. */
    static int sunbrandSweep(ServerLevel level, ServerPlayer player, int lvl, float swordDamage) {
        double radius = ultimateRadius(lvl);
        float damage = (float) (swordDamage * ultimateDamageMultiple(lvl));
        Vec3 origin = player.position();
        List<Mob> foes = level.getEntitiesOfClass(Mob.class, new AABB(origin, origin).inflate(radius),
                m -> m.isAlive() && AutoAttackSystem.isTargetable(m) && m.position().distanceTo(origin) <= radius);
        foes.sort(Comparator.comparingDouble(m -> m.position().distanceTo(origin)));
        for (Mob m : foes) {
            float before = m.getHealth();
            AutoAttackSystem.clearInvulnerabilityWindow(m);
            m.hurtServer(level, player.damageSources().playerAttack(player), damage);
            Vec3 away = m.position().subtract(origin).multiply(1, 0, 1);
            if (away.lengthSqr() > 1.0E-4) {
                m.push(away.normalize().scale(0.9).add(0, 0.25, 0));
            }
            OnHitEffects.apply(player, m, Math.max(0.0F, before - m.getHealth()));
        }
        for (int ring = 0; ring < 3; ring++) {
            double r = radius * (0.45 + 0.275 * ring);
            int points = (int) Math.max(24, r * 10);
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points;
                level.sendParticles(ring == 1 ? ParticleTypes.FLAME : ParticleTypes.CRIT,
                        origin.x + Math.cos(a) * r, origin.y + 0.9, origin.z + Math.sin(a) * r, 1, 0.0, 0.05, 0.0, 0.0);
            }
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, origin.x, origin.y + 1.0, origin.z, 6, radius * 0.4, 0.2, radius * 0.4, 0.0);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
        return foes.size();
    }

    private static Vec3 facing(ServerPlayer player, LivingEntity primary) {
        Vec3 d = primary.position().subtract(player.position()).multiply(1, 0, 1);
        return d.lengthSqr() < 1.0E-4 ? player.getLookAngle().multiply(1, 0, 1).normalize() : d.normalize();
    }

    static boolean inArc(Vec3 origin, Vec3 facing, Vec3 point, double reach, double arcRad) {
        Vec3 d = point.subtract(origin).multiply(1, 0, 1);
        double dist = d.length();
        if (dist > reach) {
            return false;
        }
        if (dist < 1.0E-4) {
            return true;
        }
        double cos = d.normalize().dot(facing);
        return Math.acos(Math.max(-1.0, Math.min(1.0, cos))) <= arcRad / 2.0;
    }

    private static void drawArc(ServerLevel level, ServerPlayer player, Vec3 facing, double reach, double arcRad) {
        double base = Math.atan2(facing.z, facing.x);
        int steps = (int) Math.max(8, Math.toDegrees(arcRad) / 12.0);
        for (int i = 0; i <= steps; i++) {
            double a = base - arcRad / 2.0 + arcRad * i / steps;
            level.sendParticles(ParticleTypes.CRIT, player.getX() + Math.cos(a) * reach, player.getY() + 1.0,
                    player.getZ() + Math.sin(a) * reach, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
