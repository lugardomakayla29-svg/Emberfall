package com.solme.emberfall.combat;

import com.solme.emberfall.item.WeaponGrowth;
import com.solme.emberfall.item.WeaponProgress;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * The War Halberd's growth. The halberd already cleaves: its hit lands in full on the target and the swing carries on into a few
 * nearby foes for the same damage, leaving Sunder on each. Levels widen and deepen that cleave; the ultimate is the build-up War Slam.
 *
 * Per level above 1: +0.17 cleave radius (2.5 at level 1 to 4.0 at level 10, on top of the Sundering Wake tome's own bonus) and
 * +0.5 cleave targets (2 to 6.5, fractional, see {@link WeaponGrowth#roll}: 3.5 means three foes and a 50% chance of a fourth).
 *
 * ULTIMATE, War Slam: the meter builds from the halberd's own hits and kills. When full the halberd slams the ground and a SHOCK WAVE
 * leaves it, racing outward at {@link QuakeWave#FRONT_SPEED} blocks a tick to 6 blocks (growing +0.67 per level to 12). Each foe takes a large
 * multiple of the hit on the tick the front passes it, is left at maximum Sunder, stunned and thrown up. Two AFTERSHOCKS (45% and 25%) then
 * sweep the same ground. It fires on its own, never through a key.
 */
public final class HalberdSystem {
    public static final double CLEAVE_RADIUS_BASE = 2.5;
    public static final double CLEAVE_RADIUS_PER_LEVEL = 2.5 / 9.0;   // 2.5 at level 1 to 5.0 at level 10: the biggest everyday footprint of the eight
    public static final double CLEAVE_TARGETS_BASE = 2.0;
    public static final double CLEAVE_TARGETS_PER_LEVEL = 0.5;
    public static final double SLAM_RADIUS_BASE = 6.0;
    public static final double SLAM_RADIUS_PER_LEVEL = 6.0 / 9.0;    // 6 at level 1 to 12 at level 10: the biggest ultimate footprint of the eight
    public static final double SLAM_DAMAGE_BASE = 4.0;
    public static final double SLAM_DAMAGE_PER_LEVEL = 0.5;
    public static final int SLAM_STUN_TICKS = 40;
    /** Crushing Combo: follow-up chops after the main one. 0 at level 1 up to 2.7 at level 10 (fractional, rolled per swing). */
    public static final double COMBO_PER_LEVEL = 0.3;
    public static final double COMBO_FRACTION = 0.6;     // of the main chop, before the level lift
    public static final int COMBO_GAP_TICKS = 5;
    public static final double COMBO_WIDEN = 0.15;       // each follow-up chop is this much wider than the one before

    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");

    private HalberdSystem() {}

    public static double cleaveRadius(int level) {
        return WeaponGrowth.scale(CLEAVE_RADIUS_BASE, CLEAVE_RADIUS_PER_LEVEL, level);
    }

    public static double cleaveTargets(int level) {
        return WeaponGrowth.scale(CLEAVE_TARGETS_BASE, CLEAVE_TARGETS_PER_LEVEL, level);
    }

    public static double slamRadius(int level) {
        return WeaponGrowth.scale(SLAM_RADIUS_BASE, SLAM_RADIUS_PER_LEVEL, level);
    }

    public static double slamDamageMultiple(int level) {
        return WeaponGrowth.scale(SLAM_DAMAGE_BASE, SLAM_DAMAGE_PER_LEVEL, level);
    }

    public static double comboChops(int level) {
        return WeaponGrowth.scale(0.0, COMBO_PER_LEVEL, level);
    }

    /** Whole cleave victims for this swing: the guaranteed part plus a chance of one more. */
    public static int cleaveCount(int level, java.util.random.RandomGenerator rng) {
        return WeaponGrowth.roll(cleaveTargets(level), rng);
    }

    /** Called after a cleave swing that dealt {@code primaryDamage}. Fires the War Slam if the meter is full. */
    public static void afterCleave(ServerLevel level, ServerPlayer player, Mob center, float primaryDamage) {
        WeaponProgress.onHit(player);
        crushingCombo(level, player, center, primaryDamage);
        if (WeaponProgress.ultimateReady(player) && WeaponProgress.consumeUltimate(player)) {
            warSlam(level, player, WeaponProgress.levelOf(player), primaryDamage);
        }
    }

    /**
     * Crushing Combo: the halberd is slow, so instead of one chop it follows through. Each echo chop re-hits the struck foe and every foe in the
     * (slightly wider) cleave ring, for {@link #COMBO_FRACTION} of the main chop times the level lift. Measured need: the halberd did exactly the
     * same damage to a lone foe at level 1 and level 10.
     */
    static void crushingCombo(ServerLevel level, ServerPlayer player, Mob center, float primaryDamage) {
        final int lvl = WeaponProgress.levelOf(player);
        final int chops = WeaponGrowth.roll(comboChops(lvl) + com.solme.emberfall.relic.RelicTempoGame.bonusStrikes(player), AutoAttackSystem.levelRandom(level));
        if (chops <= 0) {
            return;
        }
        final int slotIndex = com.solme.emberfall.item.Loadout.actingIndexFor(player);
        final float damage = (float) (primaryDamage * COMBO_FRACTION * Echo.lift(lvl));
        for (int i = 1; i <= chops; i++) {
            final double radius = cleaveRadius(lvl) * (1.0 + COMBO_WIDEN * i);
            com.solme.emberfall.world.DelayedTasks.runLater(level, COMBO_GAP_TICKS * i, o ->
                    com.solme.emberfall.item.Loadout.acting(player, slotIndex, () -> {
                        if (!player.isAlive() || !center.isAlive()) {
                            return;
                        }
                        Vec3 at = center.position();
                        List<Mob> foes = level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(radius),
                                m -> m.isAlive() && AutoAttackSystem.isEmberfallHostile(m) && m.position().distanceTo(at) <= radius);
                        Fx.impactRing(level, at, radius, ParticleTypes.CRIT);
                        for (Mob m : foes) {
                            float before = m.getHealth();
                            AutoAttackSystem.clearInvulnerabilityWindow(m);
                            m.hurtServer(level, player.damageSources().playerAttack(player), damage);
                            OnHitEffects.apply(player, m, Math.max(0.0F, before - m.getHealth()));
                        }
                        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 0.9F);
                    }));
        }
    }

    /**
     * Starts the War Slam: the first shock wave now, two aftershocks after it. Returns the number of waves queued (always {@link QuakeWave#waves()}
     * unless the task queue is full). No entities: every step is a bounded task and a ring of particles.
     */
    static int warSlam(ServerLevel level, ServerPlayer player, int lvl, float hitDamage) {
        final double radius = slamRadius(lvl);
        final double slamMultiple = slamDamageMultiple(lvl);
        final Vec3 origin = player.position();
        final int slotIndex = com.solme.emberfall.item.Loadout.actingIndexFor(player);
        final int travel = QuakeWave.travelTicks(radius);
        int queued = 0;
        for (int wave = 0; wave < QuakeWave.waves(); wave++) {
            final int w = wave;
            final float damage = (float) (hitDamage * QuakeWave.shareOf(w, slamMultiple));
            final int start = w * (travel + QuakeWave.AFTERSHOCK_GAP_TICKS);
            final java.util.Set<java.util.UUID> struck = new java.util.HashSet<>();
            for (int step = 1; step <= travel; step++) {
                final int st = step;
                if (com.solme.emberfall.world.DelayedTasks.runLater(level, start + step, o ->
                        waveStep(level, player, origin, radius, damage, slotIndex, st, w, struck))) {
                    queued++;
                }
            }
        }
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 0.7F);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.5F);
        level.sendParticles(ParticleTypes.EXPLOSION, origin.x, origin.y + 0.3, origin.z, 2, 0.6, 0.1, 0.6, 0.0);
        return queued;
    }

    /** One tick of a shock wave: hit every foe the front just passed, draw the front as a ring, crack the ground on the first wave. */
    private static void waveStep(ServerLevel level, ServerPlayer player, Vec3 origin, double radius, float damage, int slotIndex, int step, int wave,
                                 java.util.Set<java.util.UUID> struck) {
        if (!player.isAlive()) {
            return;
        }
        final double front = QuakeWave.frontAt(radius, step);
        com.solme.emberfall.item.Loadout.acting(player, slotIndex, () -> {
            List<Mob> foes = level.getEntitiesOfClass(Mob.class, new AABB(origin, origin).inflate(radius),
                    m -> m.isAlive() && AutoAttackSystem.isEmberfallHostile(m));
            for (Mob m : foes) {
                double d = m.position().distanceTo(origin);
                if (!QuakeWave.passedOnStep(radius, step, d) || !struck.add(m.getUUID())) {
                    continue;
                }
                float before = m.getHealth();
                AutoAttackSystem.clearInvulnerabilityWindow(m);
                m.hurtServer(level, player.damageSources().playerAttack(player), damage);
                if (m.isAlive()) {
                    if (wave == 0) {
                        m.addEffect(new MobEffectInstance(ModEffects.SUNDER, AutoAttackSystem.SUNDER_DURATION_TICKS,
                                AutoAttackSystem.SUNDER_MAX_AMPLIFIER));
                        m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLAM_STUN_TICKS, 6, false, true, true));
                    }
                    // thrown up and a little out: the ground heaves under the foe as the front goes by
                    Vec3 out = m.position().subtract(origin);
                    Vec3 push = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize().scale(0.25);
                    m.setDeltaMovement(m.getDeltaMovement().add(push.x, 0.42 - 0.08 * wave, push.z));
                    m.hurtMarked = true;
                }
                OnHitEffects.apply(player, m, Math.max(0.0F, before - m.getHealth()));
            }
        });
        // the front: a ring of dust and crit sparks, denser as it grows; aftershocks are drawn in smoke only
        int points = (int) Math.max(20, front * 7);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(wave == 0 ? ParticleTypes.CRIT : ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    origin.x + Math.cos(a) * front, origin.y + 0.15, origin.z + Math.sin(a) * front, 1, 0.0, 0.1, 0.0, 0.0);
        }
        if (wave == 0 && step % 3 == 0) {
            // ground cracks: 8 spokes drawn out to the front, so the wave reads as splitting the floor
            for (int spoke = 0; spoke < 8; spoke++) {
                double a = Math.PI * 2 * spoke / 8.0 + 0.2 * Math.sin(step);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, origin.x + Math.cos(a) * front, origin.y + 0.1, origin.z + Math.sin(a) * front,
                        1, 0.05, 0.02, 0.05, 0.0);
            }
        }
        if (step == 1 || step % 6 == 0) {
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                    0.35F, 0.6F + 0.1F * wave);
        }
        if (TEST_MODE && step == QuakeWave.travelTicks(radius)) {
            // permanent trace read by bot/quake_test.js: one line per wave when its front reaches the edge
            com.solme.emberfall.EmberfallMod.LOGGER.info("QUAKE_TEST wave={} radius={} struck={} damage={}", wave, radius, struck.size(), damage);
        }
    }
}
