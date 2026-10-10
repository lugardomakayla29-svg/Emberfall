package com.solme.emberfall.combat;

import com.solme.emberfall.item.Loadout;
import com.solme.emberfall.item.WeaponGrowth;
import com.solme.emberfall.item.WeaponProgress;
import com.solme.emberfall.world.DelayedTasks;
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
 * The Hunting Bow's growth. Shots were one bolt at the nearest foe and a piercing volley on every 4th. Levels turn the bow into a
 * volley weapon:
 *
 * MULTISHOT, fractional: a shot fires 1.0 + 0.25 per level above 1 arrows (1.0 at level 1, 3.25 at level 10). 2.5 means two arrows and
 * a 50% chance of a third ({@link WeaponGrowth#roll}). The extra arrows go to OTHER foes in range, nearest first, for 70% of the arrow
 * damage, so a pack is thinned instead of one foe being shot more often. Range grows 10 to 13 and the pierce beam widens 1.2 to 2.0.
 *
 * ULTIMATE, Storm of Arrows: the meter fills from the bow's own hits and kills. When full, the bow calls down 8 + level arrows on the
 * densest cluster in range, two ticks apart, each for 1.5 + 0.15 per level times the arrow damage. It fires on its own, never by a key.
 *
 * NO EXTRA ENTITIES: arrows are the existing tracked projectiles ({@code TrackedProjectiles}, geometry and particles), and every
 * delayed arrow of the storm is a task on the bounded {@link DelayedTasks} queue. Kills from any of them are credited to the bow by
 * re-entering its slot with {@link Loadout#acting}.
 */
public final class BowSystem {
    public static final double RANGE_PER_LEVEL = 3.0 / 9.0;
    public static final double EXTRA_ARROWS_PER_LEVEL = 0.25;
    public static final double EXTRA_ARROW_FRACTION = 0.7;
    // IMPACT SPLASH: every ordinary arrow that lands splashes the foes around its target.
    public static final double SPLASH_RADIUS_BASE = 1.75;
    public static final double SPLASH_RADIUS_PER_LEVEL = 1.0 / 9.0;   // 1.75 at level 1 to 2.75 at level 10
    public static final double SPLASH_MULTIPLE = 0.5;                 // of the arrow, on the OTHER foes
    public static final double PIERCE_BASE = 1.2;
    public static final double PIERCE_PER_LEVEL = 0.8 / 9.0;
    public static final int STORM_BASE_ARROWS = 8;
    public static final double STORM_BASE = 1.5;
    public static final double STORM_PER_LEVEL = 0.15;
    public static final double STORM_CLUSTER_RADIUS = 3.5;
    public static final int STEP_TICKS = 2;

    private BowSystem() {}

    /** Extra reach for a bow of this level: 0 at level 1 up to 3.0 at level 10. */
    public static double rangeBonus(int level) {
        return WeaponGrowth.scale(0.0, RANGE_PER_LEVEL, level);
    }

    /** Average extra arrows per shot (beyond the first): 0 at level 1, 2.25 at level 10. */
    public static double extraArrows(int level) {
        return WeaponGrowth.scale(0.0, EXTRA_ARROWS_PER_LEVEL, level);
    }

    public static double splashRadius(int level) {
        return WeaponGrowth.scale(SPLASH_RADIUS_BASE, SPLASH_RADIUS_PER_LEVEL, level);
    }

    public static double pierceRadius(int level) {
        return WeaponGrowth.scale(PIERCE_BASE, PIERCE_PER_LEVEL, level);
    }

    public static double stormMultiple(int level) {
        return WeaponGrowth.scale(STORM_BASE, STORM_PER_LEVEL, level);
    }

    public static int stormArrows(int level) {
        return STORM_BASE_ARROWS + Math.max(1, Math.min(level, WeaponGrowth.MAX_LEVEL));
    }

    /**
     * Called when a bow arrow has landed on {@code target} for {@code damage}. Adds meter, and on the first landing after the meter is
     * full it calls down the storm instead of a second volley. Runs inside the credited (deferred) impact, so the bow is the acting slot.
     */
    public static void afterImpact(ServerLevel level, ServerPlayer player, LivingEntity target, float damage, double reach) {
        if (damage <= 0.0F) {
            return;
        }
        WeaponProgress.onHit(player);
        if (WeaponProgress.ultimateReady(player) && WeaponProgress.consumeUltimate(player)) {
            storm(level, player, WeaponProgress.levelOf(player), damage, reach, Loadout.actingIndexFor(player));
        }
    }

    /**
     * The extra arrows of a shot: other foes in {@code reach} of the player, nearest first, each for 70% of {@code damage} after a
     * two tick stagger. {@code first} (the arrow already flying) is skipped. Returns how many were sent.
     */
    public static int fireExtras(ServerLevel level, ServerPlayer player, LivingEntity first, float damage, double reach, int lvl) {
        int count = WeaponGrowth.roll(extraArrows(lvl) + com.solme.emberfall.relic.RelicTempoGame.bonusStrikes(player), AutoAttackSystem.levelRandom(level));
        if (count <= 0) {
            return 0;
        }
        List<Mob> foes = foesIn(level, player.position(), reach, first);
        final int slotIndex = Loadout.actingIndexFor(player);
        // Echo: a lone foe is shot again too, each arrow a little stronger per level.
        List<LivingEntity> victims = Echo.victims(first, foes, count);
        int sent = 0;
        for (int i = 0; i < victims.size(); i++) {
            final LivingEntity victim = victims.get(i);
            final float arrowDamage = (float) (damage * EXTRA_ARROW_FRACTION * Echo.lift(lvl));
            Vec3 from = player.getEyePosition();
            if (DelayedTasks.runLater(level, STEP_TICKS * (i + 1), o -> arrow(level, player, victim, arrowDamage, from, slotIndex))) {
                sent++;
            }
        }
        return sent;
    }

    /** One instant arrow: a particle line from {@code from} to the foe, damage, on-hit effects. Dropped silently if the foe is gone. */
    private static void arrow(ServerLevel level, ServerPlayer player, LivingEntity victim, float damage, Vec3 from, int slotIndex) {
        if (!victim.isAlive() || !player.isAlive()) {
            return;
        }
        Loadout.acting(player, slotIndex, () -> {
            float before = victim.getHealth();
            AutoAttackSystem.clearInvulnerabilityWindow(victim);
            victim.hurtServer(level, player.damageSources().playerAttack(player), damage);
            OnHitEffects.apply(player, victim, Math.max(0.0F, before - victim.getHealth()));
            StaffSystem.burst(level, player, victim, splashRadius(WeaponProgress.levelOf(player)), damage * (float) SPLASH_MULTIPLE, ParticleTypes.CRIT);
        });
        Vec3 to = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
        line(level, from, to);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.5F, 1.4F);
    }

    /** Calls down the storm on the densest cluster in reach. Returns how many arrows were queued. */
    static int storm(ServerLevel level, ServerPlayer player, int lvl, float baseDamage, double reach, int slotIndex) {
        List<Mob> foes = foesIn(level, player.position(), reach, null);
        if (foes.isEmpty()) {
            return 0;
        }
        Mob centre = densest(foes);
        List<Mob> cluster = new ArrayList<>();
        for (Mob m : foes) {
            if (m.position().distanceTo(centre.position()) <= STORM_CLUSTER_RADIUS) {
                cluster.add(m);
            }
        }
        float damage = (float) (baseDamage * stormMultiple(lvl));
        int arrows = stormArrows(lvl);
        int queued = 0;
        for (int i = 0; i < arrows; i++) {
            final Mob victim = cluster.get(i % cluster.size());
            final Vec3 sky = victim.position().add(0, 9.0, 0);
            if (DelayedTasks.runLater(level, STEP_TICKS * (i + 1), o -> arrow(level, player, victim, damage, sky, slotIndex))) {
                queued++;
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.2F, 0.5F);
        level.sendParticles(ParticleTypes.CLOUD, centre.getX(), centre.getY() + 9.0, centre.getZ(), 12, 1.2, 0.2, 1.2, 0.02);
        return queued;
    }

    /** The foe with the most other foes within the cluster radius (ties: the nearest to the wielder, since the list is sorted). */
    private static Mob densest(List<Mob> foes) {
        Mob best = foes.get(0);
        int bestCount = -1;
        for (Mob a : foes) {
            int n = 0;
            for (Mob b : foes) {
                if (a.position().distanceTo(b.position()) <= STORM_CLUSTER_RADIUS) {
                    n++;
                }
            }
            if (n > bestCount) {
                bestCount = n;
                best = a;
            }
        }
        return best;
    }

    private static List<Mob> foesIn(ServerLevel level, Vec3 centre, double reach, LivingEntity skip) {
        List<Mob> found = new ArrayList<>(level.getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(reach),
                m -> m != skip && m.isAlive() && AutoAttackSystem.isTargetable(m) && m.position().distanceTo(centre) <= reach));
        found.sort(Comparator.comparingDouble(m -> m.position().distanceTo(centre)));
        return found;
    }

    /** A thin line of sparks between two points; at most 14 points whatever the distance. */
    private static void line(ServerLevel level, Vec3 a, Vec3 b) {
        int points = (int) Math.max(3, Math.min(14, a.distanceTo(b) * 1.5));
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            level.sendParticles(i % 4 == 0 ? ParticleTypes.CRIT : ParticleTypes.END_ROD,
                    a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
