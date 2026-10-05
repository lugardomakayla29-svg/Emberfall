package com.solme.emberfall.combat;

import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * One place for every readable combat effect, so mobs, weapons and bosses all speak the same visual
 * language instead of each hand-rolling a one-off {@code sendParticles}.
 *
 * <p>Design rules (measured, not assumed): a live 45 second run with 61 mobs on screen delivered only
 * 2 particle packets, because every existing effect sat behind a rare condition (empowered hit, veteran
 * ability). Feedback that only fires occasionally reads as "nothing happens", so the BASE loop must speak:
 * a hit always sparks, a windup always telegraphs, a death always pays off.
 *
 * <p>Delivery: {@code overrideLimiter=true, alwaysShow=true} makes the client render the packet even when
 * the player has "minimal" particles set or the point is far away. It is reserved for TELEGRAPHS (the ones
 * a player must see to react); ambient decoration uses the normal path so it can still be culled.
 *
 * <p>Performance: counts are deliberately small and rings use a fixed point budget. The caller decides how
 * often to call; see {@link #shouldPulse(LivingEntity, int)} for the cheap tick gate.
 */
public final class Fx {
    private Fx() {}

    // A restrained, consistent palette. Colour carries meaning: tier, not decoration.
    public static final int VETERAN_A = 0xFF9A1A;  // amber
    public static final int VETERAN_B = 0xFFE066;
    public static final int ELITE_A = 0xC030FF;    // violet
    public static final int ELITE_B = 0xFF5CE1;
    public static final int WARN_RED = 0xFF2A2A;
    public static final int WARN_ORANGE = 0xFF8A1A;

    /** Cheap per-mob gate so an aura does not run every tick for every mob. */
    public static boolean shouldPulse(LivingEntity e, int everyTicks) {
        return (e.tickCount + e.getId()) % everyTicks == 0;
    }

    // ------------------------------------------------------------------ telegraphs

    /**
     * A ground ring that tells the player "this spot is about to hurt". Fixed 16-point budget regardless of
     * radius. Forced through the client's particle limiter because the player must see it to dodge.
     */
    public static void telegraphRing(ServerLevel level, Vec3 centre, double radius, int rgb) {
        DustParticleOptions dust = new DustParticleOptions(rgb, 1.3F);
        int points = 16;
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0 * i) / points;
            level.sendParticles(dust, true, true,
                    centre.x + Math.cos(a) * radius, centre.y + 0.1, centre.z + Math.sin(a) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * A brief ring at the true radius of a PLAYER's own area hit, so they learn each weapon's reach.
     * Deliberately lighter than {@link #telegraphRing}: 12 points and not forced past the client's particle
     * limiter, because a player attack can land many times a second and there is nothing to dodge, so it
     * must never cost the way a mob warning does. It is drawn on impact, never as a wind-up.
     */
    public static void impactRing(ServerLevel level, Vec3 centre, double radius, ParticleOptions particle) {
        int points = 12;
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0 * i) / points;
            level.sendParticles(particle, centre.x + Math.cos(a) * radius, centre.y + 0.15, centre.z + Math.sin(a) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** A filled telegraph: the ring plus an inner cross so a large area reads as "danger", not just an outline. */
    public static void telegraphZone(ServerLevel level, Vec3 centre, double radius, int rgb) {
        telegraphRing(level, centre, radius, rgb);
        telegraphRing(level, centre, radius * 0.55, rgb);
    }

    /**
     * A ring that FILLS as the attack approaches: an outer ring at the true damage radius (so the area is
     * always readable) and an inner ring growing from the centre to meet it (so the moment of impact is
     * readable too). {@code progress} runs 0 to 1. Two rings of 16 points, so the cost never depends on
     * how big the attack is.
     */
    public static void telegraphFill(ServerLevel level, Vec3 centre, double radius, double progress, int rgb) {
        telegraphRing(level, centre, radius, rgb);
        double inner = radius * Math.max(0.05, Math.min(1.0, progress));
        telegraphRing(level, centre, inner, WARN_RED);
    }

    /**
     * A flat wedge for frontal attacks (roars, breath, cleaves). {@code halfAngleDeg} is the half width, so
     * a 90 degree cone is 45. Draws the two edges and an arc at the far end: 3 lines' worth of points, a
     * fixed budget of 15 particles per draw (8 on the edges, 7 on the arc) however long the cone is,
     * so callers should redraw it no more often than every 4 to 8 ticks.
     */
    public static void telegraphCone(ServerLevel level, Vec3 origin, Vec3 direction, double range,
                                     double halfAngleDeg, int rgb) {
        DustParticleOptions dust = new DustParticleOptions(rgb, 1.3F);
        Vec3 flat = new Vec3(direction.x, 0.0, direction.z);
        if (flat.lengthSqr() < 1.0E-6) {
            return;
        }
        double base = Math.atan2(flat.z, flat.x);
        double half = Math.toRadians(halfAngleDeg);
        // The two straight edges.
        for (int side = -1; side <= 1; side += 2) {
            double a = base + half * side;
            for (int i = 1; i <= 4; i++) {
                double d = range * i / 4.0;
                level.sendParticles(dust, true, true, origin.x + Math.cos(a) * d, origin.y + 0.1,
                        origin.z + Math.sin(a) * d, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        // The far arc.
        for (int i = 0; i <= 6; i++) {
            double a = base - half + (2.0 * half * i) / 6.0;
            level.sendParticles(dust, true, true, origin.x + Math.cos(a) * range, origin.y + 0.1,
                    origin.z + Math.sin(a) * range, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * A straight lane for lunges, charges and beams. Fixed 8 points along the path plus 2 across the end,
     * so a long lane costs the same as a short one.
     */
    public static void telegraphLine(ServerLevel level, Vec3 from, Vec3 to, double halfWidth, int rgb) {
        DustParticleOptions dust = new DustParticleOptions(rgb, 1.2F);
        Vec3 span = new Vec3(to.x - from.x, 0.0, to.z - from.z);
        double len = span.length();
        if (len < 0.01) {
            return;
        }
        Vec3 dir = span.scale(1.0 / len);
        Vec3 side = new Vec3(-dir.z, 0.0, dir.x).scale(halfWidth);
        for (int i = 0; i <= 8; i++) {
            Vec3 c = from.add(dir.scale(len * i / 8.0));
            level.sendParticles(dust, true, true, c.x + side.x, from.y + 0.1, c.z + side.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(dust, true, true, c.x - side.x, from.y + 0.1, c.z - side.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * A flat square outline for attacks whose damage is an axis-aligned box ({@code AABB}) rather than a
     * distance check. {@code halfExtent} is the distance from the centre to each edge. A ring would
     * under-warn at the corners, which sit up to 1.41 times further out. 16 points, so it costs the same as
     * a ring: 4 corners plus 3 points along each edge.
     */
    public static void telegraphSquare(ServerLevel level, Vec3 centre, double halfExtent, int rgb) {
        DustParticleOptions dust = new DustParticleOptions(rgb, 1.3F);
        double y = centre.y + 0.1;
        for (int i = 0; i < 4; i++) {
            // Edge i runs from one corner toward the next; the corner itself is point 0 of the edge.
            double cx = (i == 0 || i == 3) ? -halfExtent : halfExtent;
            double cz = (i < 2) ? -halfExtent : halfExtent;
            double dx = (i == 0) ? 1 : (i == 2) ? -1 : 0;
            double dz = (i == 1) ? 1 : (i == 3) ? -1 : 0;
            for (int k = 0; k < 4; k++) {
                double t = k * halfExtent * 2.0 / 4.0;
                level.sendParticles(dust, true, true, centre.x + cx + dx * t, y, centre.z + cz + dz * t,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /**
     * The square counterpart of {@link #telegraphFill}: an outer square at the true extent (so the area is
     * always readable) plus an inner square growing from the centre to meet it (so the moment of impact is
     * readable). {@code progress} runs 0 to 1. Two squares of 16 points each.
     */
    /**
     * A filled rectangle on the ground, turned to face {@code axis} (unit, horizontal). {@code along} is the length along the
     * axis and {@code across} the width, both full lengths. Dust is laid on a grid about {@code spacing} apart, so the whole
     * rectangle reads as covered (an edge-only outline left unmarked ground inside a dangerous lane).
     */
    public static void telegraphRect(ServerLevel level, Vec3 centre, Vec3 axis, double along, double across,
                                     double spacing, int rgb) {
        DustParticleOptions dust = new DustParticleOptions(rgb, 1.3F);
        double sx = -axis.z, sz = axis.x; // the sideways direction
        int na = Math.max(1, (int) Math.round(along / spacing));
        int nc = Math.max(1, (int) Math.round(across / spacing));
        double y = centre.y + 0.1;
        for (int i = 0; i <= na; i++) {
            double u = -along * 0.5 + along * i / na;
            for (int k = 0; k <= nc; k++) {
                double v = -across * 0.5 + across * k / nc;
                level.sendParticles(dust, true, true, centre.x + axis.x * u + sx * v, y, centre.z + axis.z * u + sz * v,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    public static void telegraphSquareFill(ServerLevel level, Vec3 centre, double halfExtent, double progress, int rgb) {
        telegraphSquare(level, centre, halfExtent, rgb);
        telegraphSquare(level, centre, halfExtent * Math.max(0.05, Math.min(1.0, progress)), WARN_RED);
    }

    /** A marker over a single victim ("you are the one being targeted"): a small ring at their feet and a spark above. */
    public static void telegraphTarget(ServerLevel level, LivingEntity target, int rgb) {
        telegraphRing(level, target.position(), 0.9, rgb);
        Vec3 p = target.position();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, p.x, p.y + target.getBbHeight() + 0.4, p.z,
                2, 0.15, 0.1, 0.15, 0.0);
    }

    /** Short "it is winding up" tell on the attacker itself: rising sparks and a low charge sound. */
    public static void windup(ServerLevel level, LivingEntity mob, int rgb) {
        Vec3 p = mob.position();
        double h = mob.getBbHeight();
        level.sendParticles(new DustParticleOptions(rgb, 1.1F), true, true,
                p.x, p.y + h * 0.6, p.z, 4, mob.getBbWidth() * 0.35, h * 0.25, mob.getBbWidth() * 0.35, 0.0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + h * 0.7, p.z, 2, 0.25, 0.25, 0.25, 0.05);
    }

    // ------------------------------------------------------------------ impacts

    /** The base-loop hit spark. Every player hit calls this, so combat always speaks. */
    public static void hit(ServerLevel level, LivingEntity target, boolean crit) {
        Vec3 c = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
        level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, crit ? 10 : 4, 0.25, 0.25, 0.25, 0.15);
        if (crit) {
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.2);
        }
        Blood.onHit(level, target, crit);
    }

    /** A heavier landing effect for slams, explosions and boss attacks. */
    public static void slam(ServerLevel level, Vec3 centre, double radius, int rgb) {
        level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y + 0.2, centre.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(new DustParticleOptions(rgb, 1.6F), true, true,
                centre.x, centre.y + 0.3, centre.z, 24, radius * 0.5, 0.15, radius * 0.5, 0.0);
        level.sendParticles(ParticleTypes.CLOUD, centre.x, centre.y + 0.1, centre.z, 10, radius * 0.4, 0.05, radius * 0.4, 0.04);
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.7F, 1.3F);
    }

    // ------------------------------------------------------------------ life cycle

    /** Death payoff scaled by tier: 0 = filler, 1 = veteran, 2 = elite/boss. */
    public static void death(ServerLevel level, LivingEntity mob, int tier) {
        Vec3 c = mob.position().add(0.0, mob.getBbHeight() * 0.5, 0.0);
        level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 6 + tier * 6, 0.3, 0.3, 0.3, 0.05);
        if (tier >= 1) {
            level.sendParticles(new DustColorTransitionOptions(VETERAN_A, VETERAN_B, 1.4F),
                    c.x, c.y, c.z, 10, 0.4, 0.4, 0.4, 0.0);
        }
        if (tier >= 2) {
            level.sendParticles(new DustColorTransitionOptions(ELITE_A, ELITE_B, 1.8F),
                    c.x, c.y, c.z, 24, 0.6, 0.6, 0.6, 0.0);
            level.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y, c.z, 8, 0.4, 0.5, 0.4, 0.04);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.HOSTILE, 0.4F, 1.6F);
        }
    }

    /** Portal-style arrival so a spawn is an event the player notices, not a pop-in. */
    public static void spawn(ServerLevel level, LivingEntity mob, int tier) {
        Vec3 p = mob.position();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y + 0.5, p.z, 12 + tier * 8, 0.3, 0.5, 0.3, 0.1);
        if (tier >= 1) {
            level.playSound(null, p.x, p.y, p.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.5F, 0.7F + tier * 0.15F);
        }
    }

    // ------------------------------------------------------------------ auras

    /** Amber embers for Veterans. Cheap: 2 particles, gated by {@link #shouldPulse}. */
    public static void veteranAura(ServerLevel level, LivingEntity mob) {
        if (!shouldPulse(mob, 6)) return;
        Vec3 p = mob.position();
        level.sendParticles(new DustColorTransitionOptions(VETERAN_A, VETERAN_B, 1.0F),
                p.x, p.y + mob.getBbHeight() * 0.5, p.z, 2, mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.3, mob.getBbWidth() * 0.4, 0.0);
    }

    /** Violet wisps for Elites, heavier than the Veteran aura so the tiers never read the same. */
    public static void eliteAura(ServerLevel level, LivingEntity mob) {
        if (!shouldPulse(mob, 4)) return;
        Vec3 p = mob.position();
        double w = mob.getBbWidth() * 0.5, h = mob.getBbHeight();
        level.sendParticles(new DustColorTransitionOptions(ELITE_A, ELITE_B, 1.3F),
                p.x, p.y + h * 0.5, p.z, 3, w, h * 0.35, w, 0.0);
        level.sendParticles(ParticleTypes.SCULK_SOUL, p.x, p.y + h * 0.3, p.z, 1, w * 0.6, 0.1, w * 0.6, 0.01);
    }

    // ------------------------------------------------------------------ sound helpers

    public static void sound(ServerLevel level, Vec3 at, SoundEvent event, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, event, SoundSource.HOSTILE, volume, pitch);
    }

    /** Overload for the registry-holder sound constants that 1.21.11 uses. */
    public static void sound(ServerLevel level, Vec3 at, net.minecraft.core.Holder<SoundEvent> event, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, event.value(), SoundSource.HOSTILE, volume, pitch);
    }

    /** Unused-parameter guard so callers can pass any particle without importing the types. */
    public static void burst(ServerLevel level, ParticleOptions p, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(p, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }
}
