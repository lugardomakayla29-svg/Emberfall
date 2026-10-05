package com.solme.emberfall.combat;

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
 * The Twin Daggers' growth. A dagger swing already lands a fast hit, stacks Rend and makes every 4th hit a double. Levels turn it into
 * a flurry: the two blades keep cutting, hopping from the target to foes near it, so one swing "does ninja moves on many enemies".
 *
 * Per level above 1: +0.22 reach (3.0 to 5.0 at level 10) and +0.2 flurry strikes (0.2 at level 1 to 2.0 at level 10, FRACTIONAL:
 * 1.5 means one strike and a 50% chance of a second, see {@link WeaponGrowth#roll}). Each flurry strike lands for 60% of the hit on a
 * different foe near the target, two ticks after the one before, and adds a Rend stack.
 *
 * ULTIMATE, Phantom Blades (see {@link PhantomBladeSystem} and {@link PhantomBlades}): the meter builds from the daggers' own hits. When full,
 * two ghost daggers float at the wielder's sides and FOLLOW him for 3 s (5 s at level 10), each darting at 50 blocks a second from foe to foe
 * within 6 blocks of him (14 at level 10), cutting every 4 ticks (2 at level 10) for 0.6 of the hit (0.96). They spread over a crowd, slide back
 * to his side when nothing is in reach, and each cut adds a Rend stack. It fires on its own, never through a key.
 *
 * VIRTUAL, NOT ENTITIES: like the Sickles and the Totem, the blades are geometry and particles. Every follow-up is a task on the bounded
 * {@link DelayedTasks} queue, so the cost stays flat however many players carry the weapon.
 */
public final class DaggerSystem {
    public static final double REACH_PER_LEVEL = 2.0 / 9.0;
    public static final double FLURRY_BASE = 1.0;
    public static final double FLURRY_PER_LEVEL = 0.3;
    public static final double FLURRY_FRACTION = 0.6;
    public static final double FLURRY_HOP_RADIUS = 3.0;
    public static final int STEP_TICKS = 2;

    private DaggerSystem() {}

    /** Extra reach for a dagger of this level: 0 at level 1 up to 2.0 at level 10. */
    public static double reachBonus(int level) {
        return WeaponGrowth.scale(0.0, REACH_PER_LEVEL, level);
    }

    public static double flurryStrikes(int level) {
        return WeaponGrowth.scale(FLURRY_BASE, FLURRY_PER_LEVEL, level);
    }

    /** Called after a dagger swing that dealt {@code hitDamage} to {@code target}. */
    public static void afterSwing(ServerLevel level, ServerPlayer player, LivingEntity target, float hitDamage, double reach) {
        if (hitDamage <= 0.0F) {
            return;
        }
        final int slotIndex = com.solme.emberfall.item.Loadout.actingIndexFor(player);
        int lvl = WeaponProgress.levelOf(player);
        WeaponProgress.onHit(player);
        if (WeaponProgress.ultimateReady(player) && WeaponProgress.consumeUltimate(player)) {
            PhantomBladeSystem.start(level, player, lvl, hitDamage, slotIndex);
            return;
        }
        int strikes = WeaponGrowth.roll(flurryStrikes(lvl) + com.solme.emberfall.relic.RelicTempoGame.bonusStrikes(player), AutoAttackSystem.levelRandom(level));
        if (strikes <= 0) {
            return;
        }
        List<Mob> hops = foesNear(level, target.position(), FLURRY_HOP_RADIUS, target, reach + FLURRY_HOP_RADIUS, player);
        // Echo: a lone foe is cut again and again (the primary fills the strikes the crowd cannot), and each cut grows with the level.
        List<LivingEntity> victims = Echo.victims((LivingEntity) target, hops, strikes);
        Vec3 from = target.position().add(0, target.getBbHeight() * 0.5, 0);
        for (int i = 0; i < victims.size(); i++) {
            final LivingEntity victim = victims.get(i);
            final Vec3 origin = from;
            final float damage = (float) (hitDamage * FLURRY_FRACTION * Echo.lift(lvl));
            DelayedTasks.runLater(level, STEP_TICKS * (i + 1), o -> strike(level, player, victim, damage, origin, slotIndex));
            from = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
        }
    }

    /** One follow-up cut: damage, a Rend stack, a crit streak from where the last cut ended. Dropped silently if the foe is gone. */
    private static void strike(ServerLevel level, ServerPlayer player, LivingEntity victim, float damage, Vec3 from, int slotIndex) {
        if (!victim.isAlive() || !player.isAlive()) {
            return;
        }
        // The acting slot is only named for the duration of a swing, so a delayed kill is credited to the daggers explicitly.
        com.solme.emberfall.item.Loadout.acting(player, slotIndex, () -> {
            float before = victim.getHealth();
            AutoAttackSystem.clearInvulnerabilityWindow(victim);
            victim.hurtServer(level, player.damageSources().playerAttack(player), damage);
            OnHitEffects.apply(player, victim, Math.max(0.0F, before - victim.getHealth()));
            AutoAttackSystem.applyRend(victim);
        });
        Vec3 to = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
        streak(level, from, to);
    }

    /** Hostile foes within {@code radius} of {@code centre}, nearest first, skipping {@code skip}, and within {@code playerLimit} of the wielder. */
    private static List<Mob> foesNear(ServerLevel level, Vec3 centre, double radius, LivingEntity skip, double playerLimit, ServerPlayer player) {
        List<Mob> found = new ArrayList<>(level.getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(radius),
                m -> m != skip && m.isAlive() && AutoAttackSystem.isEmberfallHostile(m)
                        && m.position().distanceTo(centre) <= radius && m.position().distanceTo(player.position()) <= playerLimit));
        found.sort(Comparator.comparingDouble(m -> m.position().distanceTo(centre)));
        return found;
    }

    /** A short line of crit and steel-coloured sparks between two points; fixed size (at most 12 points) whatever the distance. */
    private static void streak(ServerLevel level, Vec3 a, Vec3 b) {
        int points = (int) Math.max(3, Math.min(12, a.distanceTo(b) * 3));
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            level.sendParticles(i % 3 == 0 ? ParticleTypes.CRIT : ParticleTypes.ELECTRIC_SPARK,
                    a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
