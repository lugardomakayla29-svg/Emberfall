package com.solme.emberfall.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared virtual-projectile tracker for Corrupted archetype ranged
 * abilities (currently Blightfeather Marksman's Fan Volley / Homing
 * Volley). Deliberately does NOT spawn real vanilla Arrow entities:
 * SlopPack's version did (real Arrow + a per-tick BukkitRunnable doing
 * its own manual proximity-hit check alongside it), which left a real
 * race - if the arrow's own vanilla collision resolved in the same tick
 * as the manual proximity check, a target could take both hits at once.
 * A purely virtual bolt (position/velocity simulated here, hit purely by
 * this class's own single distance check, particle trail for visibility,
 * no physical entity at all) has no such race and costs no entity slots -
 * a straight improvement, not a reskin.
 */
public final class TrackedProjectiles {
    private TrackedProjectiles() {}

    private static final List<Bolt> ACTIVE = new ArrayList<>();

    /** A sweep bolt may climb this many blocks in total to follow a slope; a taller face is a wall. */
    static final double SWEEP_CLIMB_BUDGET = 3.0;

    public static final class Bolt {
        final ServerLevel level;
        Vec3 pos;
        Vec3 velocity;
        final LivingEntity shooter;
        final LivingEntity homingTarget; // null = straight-line, no steering
        final double homingStrength;     // 0 = no steering
        final double hitRadius;
        final int maxTicks;
        final ParticleOptions trailParticle;
        final Consumer<Vec3> onHit;    // called with impact position when a living target is reached
        final Runnable onExpire;       // called if it times out without hitting anything (may be null)
        LivingEntity hitTargetHint;    // if set, only this entity is checked (fan volley's per-arrow target); null = nearest-living sweep handled by caller via onHit each tick
        /** Sweep bolts (Spin Barrage) have no assigned target: they hit the first living Emberfall
         *  hostile they cross via {@link #sweepHit}, and stop dead against any solid block. */
        java.util.function.Function<Vec3, LivingEntity> sweepHit; // null = ordinary targeted bolt
        java.util.function.BiConsumer<Vec3, LivingEntity> sweepOnHit;
        /** Optional: called with the last position when a sweep bolt ends WITHOUT a victim (wall, ground or timeout). */
        Consumer<Vec3> sweepOnEnd;

        int ticks = 0;
        boolean done = false;
        double climbLeft = SWEEP_CLIMB_BUDGET; // blocks a sweep bolt may still rise to follow rising ground

        Bolt(ServerLevel level, Vec3 pos, Vec3 velocity, LivingEntity shooter, LivingEntity homingTarget,
             double homingStrength, double hitRadius, int maxTicks, ParticleOptions trailParticle,
             Consumer<Vec3> onHit, Runnable onExpire, LivingEntity hitTargetHint) {
            this.level = level;
            this.pos = pos;
            this.velocity = velocity;
            this.shooter = shooter;
            this.homingTarget = homingTarget;
            this.homingStrength = homingStrength;
            this.hitRadius = hitRadius;
            this.maxTicks = maxTicks;
            this.trailParticle = trailParticle;
            this.onHit = onHit;
            this.onExpire = onExpire;
            this.hitTargetHint = hitTargetHint;
        }
    }

    /**
     * Launches a bolt aimed at a specific tracked entity (Fan Volley style):
     * flies straight, checks only against {@code target}, single-hit-then-gone.
     */
    public static void launchAtTarget(ServerLevel level, Vec3 origin, Vec3 velocity, LivingEntity shooter,
                                       LivingEntity target, double hitRadius, int maxTicks,
                                       ParticleOptions trailParticle, Consumer<Vec3> onHit) {
        ACTIVE.add(new Bolt(level, origin, velocity, shooter, null, 0.0, hitRadius, maxTicks,
                trailParticle, onHit, null, target));
    }

    /**
     * Launches a homing bolt that steers toward {@code target} over time
     * (Homing Volley style). Detonates via {@code onHit} if it reaches the
     * target, or via {@code onExpire} if the target is lost/it times out.
     */
    public static void launchHoming(ServerLevel level, Vec3 origin, Vec3 velocity, LivingEntity shooter,
                                     LivingEntity target, double homingStrength, double hitRadius, int maxTicks,
                                     ParticleOptions trailParticle, Consumer<Vec3> onHit, Runnable onExpire) {
        ACTIVE.add(new Bolt(level, origin, velocity, shooter, target, homingStrength, hitRadius, maxTicks,
                trailParticle, onHit, onExpire, target));
    }

    /**
     * Launches a straight "sweep" bolt with no assigned target (Spin Barrage). Each tick it asks
     * {@code hitFinder} for a living victim at its current position; the first one found consumes
     * the bolt and {@code onHit} receives that victim. The bolt also ends the moment it enters a
     * non-empty collision shape, so it can never pass through walls and, being purely virtual,
     * leaves nothing behind in the world (no arrow entity, no block, no saved data).
     */
    public static void launchSweep(ServerLevel level, Vec3 origin, Vec3 velocity, LivingEntity shooter,
                                    int maxTicks, ParticleOptions trailParticle,
                                    java.util.function.Function<Vec3, LivingEntity> hitFinder,
                                    java.util.function.BiConsumer<Vec3, LivingEntity> onHit) {
        launchSweep(level, origin, velocity, shooter, maxTicks, trailParticle, hitFinder, onHit, null);
    }

    /** As above, plus {@code onEnd}: called once with the bolt's last position if it ends without a victim. */
    public static void launchSweep(ServerLevel level, Vec3 origin, Vec3 velocity, LivingEntity shooter,
                                    int maxTicks, ParticleOptions trailParticle,
                                    java.util.function.Function<Vec3, LivingEntity> hitFinder,
                                    java.util.function.BiConsumer<Vec3, LivingEntity> onHit,
                                    Consumer<Vec3> onEnd) {
        launchSweep(level, origin, velocity, shooter, maxTicks, trailParticle, hitFinder, onHit, onEnd, SWEEP_CLIMB_BUDGET);
    }

    /**
     * As above, plus {@code climbBudget}: how many blocks the bolt may rise to follow rising ground. The default
     * ({@link #SWEEP_CLIMB_BUDGET}) lets a bolt hop over a one-block step, which suits a weapon that should follow a
     * slope; 0 makes every solid cell a wall, which suits a glob of acid that must splash against it.
     */
    public static void launchSweep(ServerLevel level, Vec3 origin, Vec3 velocity, LivingEntity shooter,
                                    int maxTicks, ParticleOptions trailParticle,
                                    java.util.function.Function<Vec3, LivingEntity> hitFinder,
                                    java.util.function.BiConsumer<Vec3, LivingEntity> onHit,
                                    Consumer<Vec3> onEnd, double climbBudget) {
        Bolt bolt = new Bolt(level, origin, velocity, shooter, null, 0.0, 0.0, maxTicks,
                trailParticle, null, null, null);
        bolt.sweepHit = hitFinder;
        bolt.sweepOnHit = onHit;
        bolt.sweepOnEnd = onEnd;
        bolt.climbLeft = climbBudget;
        ACTIVE.add(bolt);
    }

    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        // Snapshot before iterating: an onHit callback can itself launch new bolts
        // (Arcane Convergence's Nova-triggers-seeking-sparks-that-trigger-more-Novas
        // chain does exactly this) which calls launchAtTarget/launchHoming -> ACTIVE.add
        // while this method is mid-iteration. Iterating ACTIVE directly threw a real,
        // reproduced ConcurrentModificationException the first time a callback re-entered
        // it that way; a fresh bolt added mid-tick just starts ticking next server tick
        // instead of this one, which is a fine one-tick delay for something that only
        // just spawned. removeIf below still targets the live ACTIVE list (now possibly
        // grown with this tick's new bolts, none of which are done) so nothing is lost.
        for (Bolt bolt : new ArrayList<>(ACTIVE)) {
            if (bolt.done) {
                continue;
            }
            bolt.ticks++;
            if (bolt.sweepHit != null) {
                tickSweep(bolt);
                continue;
            }
            LivingEntity trackedTarget = bolt.hitTargetHint;
            boolean targetGone = trackedTarget != null && (!trackedTarget.isAlive());

            if (bolt.homingTarget != null && !targetGone) {
                Vec3 toTarget = bolt.homingTarget.getEyePosition().subtract(bolt.pos);
                if (toTarget.lengthSqr() > 0.0001) {
                    double speed = bolt.velocity.length();
                    Vec3 steered = bolt.velocity.normalize().add(toTarget.normalize().scale(bolt.homingStrength))
                            .normalize().scale(speed);
                    bolt.velocity = steered;
                }
            }

            bolt.pos = bolt.pos.add(bolt.velocity);
            bolt.level.sendParticles(bolt.trailParticle, bolt.pos.x, bolt.pos.y, bolt.pos.z, 1, 0.0, 0.0, 0.0, 0.0);

            boolean expired = bolt.ticks >= bolt.maxTicks;
            boolean hit = false;
            if (!targetGone && trackedTarget != null) {
                double distSq = bolt.pos.distanceToSqr(trackedTarget.getEyePosition());
                if (distSq <= bolt.hitRadius * bolt.hitRadius) {
                    hit = true;
                }
            }

            if (hit) {
                bolt.done = true;
                if (bolt.onHit != null) {
                    bolt.onHit.accept(bolt.pos);
                }
            } else if (expired || targetGone) {
                bolt.done = true;
                if (bolt.onExpire != null) {
                    bolt.onExpire.run();
                }
            }
        }
        ACTIVE.removeIf(b -> b.done);
    }

    /** One tick of a sweep bolt: advance, stop against terrain, otherwise test for a victim. */
    private static void tickSweep(Bolt bolt) {
        Vec3 next = bolt.pos.add(bolt.velocity);
        net.minecraft.core.BlockPos blockPos = net.minecraft.core.BlockPos.containing(next);
        // Follow rising ground: if the next cell is solid but the cell one block up is free, climb over it.
        // Each climb spends from a small budget, so a gentle hill is crossed but a cliff face still stops it.
        if (bolt.level.isLoaded(blockPos) && bolt.climbLeft >= 1.0
                && !bolt.level.getBlockState(blockPos).getCollisionShape(bolt.level, blockPos).isEmpty()) {
            net.minecraft.core.BlockPos up = blockPos.above();
            if (bolt.level.isLoaded(up)
                    && bolt.level.getBlockState(up).getCollisionShape(bolt.level, up).isEmpty()) {
                next = new Vec3(next.x, blockPos.getY() + 1.0 + 0.5, next.z);
                blockPos = net.minecraft.core.BlockPos.containing(next);
                bolt.climbLeft -= 1.0;
            }
        }
        if (!bolt.level.isLoaded(blockPos)
                || !bolt.level.getBlockState(blockPos).getCollisionShape(bolt.level, blockPos).isEmpty()) {
            bolt.done = true; // hit a wall or the ground: vanish, nothing persists
            if (bolt.sweepOnEnd != null) {
                bolt.sweepOnEnd.accept(bolt.pos);   // the last FREE position, i.e. just in front of the surface
            }
            return;
        }
        bolt.pos = next;
        bolt.level.sendParticles(bolt.trailParticle, next.x, next.y, next.z, 1, 0.0, 0.0, 0.0, 0.0);
        LivingEntity victim = bolt.sweepHit.apply(next);
        if (victim != null) {
            bolt.done = true;
            bolt.sweepOnHit.accept(next, victim);
        } else if (bolt.ticks >= bolt.maxTicks) {
            bolt.done = true;
            if (bolt.sweepOnEnd != null) {
                bolt.sweepOnEnd.accept(bolt.pos);
            }
        }
    }
}
